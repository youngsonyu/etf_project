package com.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.entity.EtfFiveDimensionResonance;
import com.demo.mapper.EtfFiveDimensionResonanceMapper;
import com.demo.service.EtfFiveDimensionResonanceService;
import com.demo.vo.PageParam;
import com.demo.vo.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class EtfFiveDimensionResonanceServiceImpl extends ServiceImpl<EtfFiveDimensionResonanceMapper, EtfFiveDimensionResonance>
        implements EtfFiveDimensionResonanceService {

    private final Map<String, Map<String, Object>> tasks = new ConcurrentHashMap<>();
    private final AtomicBoolean taskRunning = new AtomicBoolean(false);

    @Override
    public PageResult<EtfFiveDimensionResonance> pageByCondition(PageParam param) {
        Page<EtfFiveDimensionResonance> page = new Page<>(param.getPageNum(), param.getPageSize());
        QueryWrapper<EtfFiveDimensionResonance> wrapper = buildWrapper(param);
        Page<EtfFiveDimensionResonance> resultPage = this.page(page, wrapper);

        PageResult<EtfFiveDimensionResonance> result = new PageResult<>();
        result.setCurrent(resultPage.getCurrent());
        result.setSize(resultPage.getSize());
        result.setTotal(resultPage.getTotal());
        result.setRecords(resultPage.getRecords());
        return result;
    }

    @Override
    public List<EtfFiveDimensionResonance> listForExport(PageParam param) {
        QueryWrapper<EtfFiveDimensionResonance> wrapper = buildWrapper(param);
        return this.list(wrapper);
    }

    @Override
    public String getLatestTradeDate() {
        String latestTradeDate = baseMapper.selectLatestKlineTradeDate();
        if (latestTradeDate == null || latestTradeDate.trim().isEmpty()) {
            return null;
        }
        return latestTradeDate;
    }

    @Override
    public String getLastTriggeredDate() {
        return baseMapper.selectMaxLastTriggeredDate();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> refreshLatestTradeDate() {
        String latestTradeDate = baseMapper.selectLatestKlineTradeDate();
        if (latestTradeDate == null || latestTradeDate.trim().isEmpty()) {
            throw new IllegalStateException("未找到可用的最新K线交易日，无法刷新五维共振结果");
        }

        int existingCount = baseMapper.countByLastTriggeredDate(latestTradeDate);

        String latestBatchDate = baseMapper.selectLatestBatchDateByTradeDate(latestTradeDate);
        if (latestBatchDate == null || latestBatchDate.trim().isEmpty()) {
            throw new IllegalStateException("未找到最新K线交易日对应的etf_ta_indicator批次数据，无法刷新五维共振结果");
        }

        String previousTradeDate = baseMapper.selectPreviousTradeDateByTradeDate(latestTradeDate);

        int inserted = baseMapper.upsertByBatchDate(latestBatchDate, previousTradeDate, latestTradeDate);

        Map<String, Object> result = new HashMap<>();
        result.put("tradeDate", latestTradeDate);
        result.put("inserted", inserted);
        result.put("skipped", false);
        result.put("existingCount", existingCount);
        result.put("hadLatestData", existingCount > 0);
        result.put("latestBatchDate", latestBatchDate);
        result.put("previousTradeDate", previousTradeDate);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> backfillByDateRange(String startDate, String endDate) {
        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);
        if (start == null || end == null) {
            throw new IllegalArgumentException("开始日期和结束日期不能为空，格式应为yyyy-MM-dd");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("开始日期不能大于结束日期");
        }

        List<String> tradeDates = baseMapper.selectDayTradeDates(start.toString(), end.toString());
        if (tradeDates == null || tradeDates.isEmpty()) {
            throw new IllegalStateException("指定范围内没有可用的日线技术指标数据，无法补算");
        }

        int totalRows = 0;
        for (String tradeDate : tradeDates) {
            String latestBatchDate = baseMapper.selectLatestBatchDateByTradeDate(tradeDate);
            if (latestBatchDate == null || latestBatchDate.trim().isEmpty()) {
                continue;
            }
            String previousTradeDate = baseMapper.selectPreviousTradeDateByTradeDate(tradeDate);
            totalRows += baseMapper.upsertByBatchDate(latestBatchDate, previousTradeDate, tradeDate);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("startDate", start.toString());
        result.put("endDate", end.toString());
        result.put("tradeDateCount", tradeDates.size());
        result.put("affectedRows", totalRows);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> backfillAll() {
        List<String> tradeDates = baseMapper.selectAllDayTradeDates();
        if (tradeDates == null || tradeDates.isEmpty()) {
            throw new IllegalStateException("没有可用的日线技术指标数据，无法全量补算");
        }

        int totalRows = baseMapper.upsertAllHistory();

        Map<String, Object> result = new HashMap<>();
        result.put("tradeDateCount", tradeDates.size());
        result.put("affectedRows", totalRows);
        return result;
    }

    @Override
    public Map<String, Object> submitRefreshLatestTradeDate() {
        ensureTaskAvailable();
        String taskId = UUID.randomUUID().toString();
        Map<String, Object> task = createTask(taskId, "latest");
        CompletableFuture.runAsync(() -> runTask(taskId, () -> refreshLatestTradeDate()))
                .exceptionally(error -> null);
        return task;
    }

    @Override
    public Map<String, Object> submitBackfillByDateRange(String startDate, String endDate) {
        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);
        if (start == null || end == null || start.isAfter(end)) {
            throw new IllegalArgumentException("请输入有效日期范围，且开始日期不能大于结束日期");
        }
        ensureTaskAvailable();
        String taskId = UUID.randomUUID().toString();
        Map<String, Object> task = createTask(taskId, "backfill");
        CompletableFuture.runAsync(() -> runTask(taskId, () -> backfillByDateRange(start.toString(), end.toString())))
                .exceptionally(error -> null);
        return task;
    }

    @Override
    public Map<String, Object> submitBackfillAll() {
        ensureTaskAvailable();
        String taskId = UUID.randomUUID().toString();
        Map<String, Object> task = createTask(taskId, "backfill-all");
        CompletableFuture.runAsync(() -> runTask(taskId, this::backfillAll))
                .exceptionally(error -> null);
        return task;
    }

    @Override
    public Map<String, Object> getTaskStatus(String taskId) {
        Map<String, Object> task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在或已过期");
        }
        return new HashMap<>(task);
    }

    private Map<String, Object> createTask(String taskId, String type) {
        Map<String, Object> task = new ConcurrentHashMap<>();
        task.put("taskId", taskId);
        task.put("type", type);
        task.put("status", "RUNNING");
        task.put("message", "任务已提交，正在后台计算");
        tasks.put(taskId, task);
        return new HashMap<>(task);
    }

    private void runTask(String taskId, java.util.function.Supplier<Map<String, Object>> action) {
        Map<String, Object> task = tasks.get(taskId);
        try {
            Map<String, Object> result = action.get();
            task.putAll(result);
            task.put("status", "SUCCESS");
            task.put("message", "任务完成");
        } catch (Exception ex) {
            task.put("status", "FAILED");
            task.put("message", ex.getMessage() == null ? "任务执行失败" : ex.getMessage());
        } finally {
            taskRunning.set(false);
        }
    }

    private void ensureTaskAvailable() {
        if (!taskRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("已有五维共振任务正在执行，请等待完成");
        }
    }

    private QueryWrapper<EtfFiveDimensionResonance> buildWrapper(PageParam param) {
        QueryWrapper<EtfFiveDimensionResonance> wrapper = new QueryWrapper<>();
        Map<String, Object> filters = param.getFilters();

        if (param.getKeyword() != null && !param.getKeyword().trim().isEmpty()) {
            wrapper.like("etf_code", param.getKeyword().trim());
        }

        Object etfCodeObj = filters == null ? null : filters.get("etfCode");
        if (etfCodeObj != null && !String.valueOf(etfCodeObj).trim().isEmpty()) {
            wrapper.like("etf_code", String.valueOf(etfCodeObj).trim());
        }

        Object etfNameObj = filters == null ? null : filters.get("etfName");
        if (etfNameObj != null && !String.valueOf(etfNameObj).trim().isEmpty()) {
            wrapper.like("etf_name", String.valueOf(etfNameObj).trim());
        }

        Integer yesterdayTriggered = parseInteger(filters == null ? null : filters.get("isYesterdayTriggered"));
        if (yesterdayTriggered != null) {
            wrapper.eq("is_yesterday_triggered", yesterdayTriggered);
        }

        Integer todayTriggered = parseInteger(filters == null ? null : filters.get("isTodayTriggered"));
        if (todayTriggered != null) {
            wrapper.eq("is_today_triggered", todayTriggered);
        }

        Integer signalWarning = parseInteger(filters == null ? null : filters.get("signalWarning"));
        if (signalWarning != null) {
            wrapper.eq("signal_warning", signalWarning);
        }

        Object lastTriggeredDateObj = filters == null ? null : filters.get("lastTriggeredDate");
        LocalDate lastTriggeredDate = parseDate(lastTriggeredDateObj == null ? null : String.valueOf(lastTriggeredDateObj));
        if (lastTriggeredDate != null) {
            wrapper.eq("last_triggered_date", lastTriggeredDate);
        }

        applySort(wrapper, filters);
        return wrapper;
    }

    private void applySort(QueryWrapper<EtfFiveDimensionResonance> wrapper, Map<String, Object> filters) {
        if (filters == null) {
            wrapper.orderByDesc("trade_date").orderByDesc("last_triggered_date").orderByDesc("id");
            return;
        }

        String sortField = String.valueOf(filters.getOrDefault("sortField", "")).trim();
        String sortOrder = String.valueOf(filters.getOrDefault("sortOrder", "")).trim();
        if (sortField.isEmpty() || sortOrder.isEmpty()) {
            wrapper.orderByDesc("trade_date").orderByDesc("last_triggered_date").orderByDesc("id");
            return;
        }

        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        switch (sortField) {
            case "id":
                wrapper.orderBy(true, asc, "id");
                break;
            case "etfCode":
                wrapper.orderBy(true, asc, "etf_code");
                break;
            case "etfName":
                wrapper.orderBy(true, asc, "etf_name");
                break;
            case "tradeDate":
                wrapper.orderBy(true, asc, "trade_date");
                break;
            case "isYesterdayTriggered":
                wrapper.orderBy(true, asc, "is_yesterday_triggered");
                break;
            case "isTodayTriggered":
                wrapper.orderBy(true, asc, "is_today_triggered");
                break;
            case "firstTriggeredDateOfYear":
                wrapper.orderBy(true, asc, "first_triggered_date_of_year");
                break;
            case "previousTriggeredDate":
                wrapper.orderBy(true, asc, "previous_triggered_date");
                break;
            case "lastTriggeredDate":
                wrapper.orderBy(true, asc, "last_triggered_date");
                break;
            default:
                wrapper.orderByDesc("trade_date").orderByDesc("last_triggered_date").orderByDesc("id");
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim().substring(0, 10));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

}
