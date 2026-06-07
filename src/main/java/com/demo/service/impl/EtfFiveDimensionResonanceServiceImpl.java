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

@Service
public class EtfFiveDimensionResonanceServiceImpl extends ServiceImpl<EtfFiveDimensionResonanceMapper, EtfFiveDimensionResonance>
        implements EtfFiveDimensionResonanceService {

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

        String previousBatchDate = baseMapper.selectPreviousBatchDateByTradeDate(latestTradeDate);
        if (previousBatchDate == null || previousBatchDate.trim().isEmpty()) {
            previousBatchDate = latestBatchDate;
        }

        int inserted = baseMapper.upsertByBatchDate(latestBatchDate, previousBatchDate, latestTradeDate);

        Map<String, Object> result = new HashMap<>();
        result.put("tradeDate", latestTradeDate);
        result.put("inserted", inserted);
        result.put("skipped", false);
        result.put("existingCount", existingCount);
        result.put("hadLatestData", existingCount > 0);
        result.put("latestBatchDate", latestBatchDate);
        result.put("previousBatchDate", previousBatchDate);
        return result;
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
