package com.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.entity.EtfFundFlowSummary;
import com.demo.entity.EtfSecurityMaster;
import com.demo.entity.TradeCalendar;
import com.demo.mapper.EtfFundFlowSummaryMapper;
import com.demo.mapper.EtfSecurityMasterMapper;
import com.demo.mapper.TradeCalendarMapper;
import com.demo.service.EtfFundFlowSummaryService;
import com.demo.vo.PageParam;
import com.demo.vo.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EtfFundFlowSummaryServiceImpl extends ServiceImpl<EtfFundFlowSummaryMapper, EtfFundFlowSummary> implements EtfFundFlowSummaryService {
	@Autowired
	private EtfSecurityMasterMapper etfSecurityMasterMapper;

	@Autowired
	private TradeCalendarMapper tradeCalendarMapper;
	@Override
	public PageResult<EtfFundFlowSummary> pageByTradeDate(PageParam param) {
		Page<EtfFundFlowSummary> page = new Page<>(param.getPageNum(), param.getPageSize());
		QueryWrapper<EtfFundFlowSummary> wrapper = new QueryWrapper<>();

		if (param.getKeyword() != null && !param.getKeyword().trim().isEmpty()) {
			wrapper.like("etf_code", param.getKeyword().trim());
		}

		Map<String, Object> filters = param.getFilters();
		Object etfCodeObj = filters == null ? null : filters.get("etfCode");
		if (etfCodeObj != null && !String.valueOf(etfCodeObj).trim().isEmpty()) {
			wrapper.like("etf_code", String.valueOf(etfCodeObj).trim());
		}

		Object etfNameObj = filters == null ? null : filters.get("etfName");
		if (etfNameObj != null && !String.valueOf(etfNameObj).trim().isEmpty()) {
			wrapper.like("etf_name", String.valueOf(etfNameObj).trim());
		}

		Object tradeDateObj = filters == null ? null : filters.get("tradeDate");
		LocalDate tradeDate = parseDate(tradeDateObj == null ? null : tradeDateObj.toString());
		if (tradeDate != null) {
			wrapper.eq("trade_date", tradeDate);
		}

		applySort(wrapper, filters);

		Page<EtfFundFlowSummary> resultPage = this.page(page, wrapper);
		PageResult<EtfFundFlowSummary> result = new PageResult<>();
		result.setCurrent(resultPage.getCurrent());
		result.setSize(resultPage.getSize());
		result.setTotal(resultPage.getTotal());
		result.setRecords(resultPage.getRecords());
		return result;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int accumulateByDateRange(String startDate, String endDate) {
		LocalDate start = parseDate(startDate);
		LocalDate end = parseDate(endDate);
		if (start == null || end == null) {
			throw new IllegalArgumentException("开始日期和结束日期不能为空，格式应为yyyy-MM-dd");
		}
		if (start.isAfter(end)) {
			throw new IllegalArgumentException("开始日期不能大于结束日期");
		}

		int inserted = baseMapper.insertAccumulatedByDateRange(start.toString(), end.toString());
		baseMapper.updateCumulativeFlowByDateRange(start.toString(), end.toString());
		return inserted;
	}

	@Override
	public List<EtfFundFlowSummary> listByDateRange(String startDate, String endDate) {
		LocalDate start = parseDate(startDate);
		LocalDate end = parseDate(endDate);
		if (start == null || end == null) {
			throw new IllegalArgumentException("开始日期和结束日期不能为空，格式应为yyyy-MM-dd");
		}
		if (start.isAfter(end)) {
			throw new IllegalArgumentException("开始日期不能大于结束日期");
		}

		QueryWrapper<EtfFundFlowSummary> wrapper = new QueryWrapper<>();
		wrapper.ge("trade_date", start)
				.le("trade_date", end)
				.orderByAsc("trade_date")
				.orderByAsc("etf_code");
		List<EtfFundFlowSummary> rows = this.list(wrapper);
		enrichEtfNameFromMaster(rows);
		return rows;
	}

	@Override
	public Map<String, Object> getConsecutiveTradingDayData(String startDate, String endDate) {
		LocalDate date1 = parseDate(startDate);
		LocalDate date2 = parseDate(endDate);
		if (date1 == null || date2 == null) {
			Map<String, Object> error = new HashMap<>();
			error.put("error", "日期格式不正确，应为yyyy-MM-dd");
			return error;
		}

		if (date1.equals(date2)) {
			Map<String, Object> error = new HashMap<>();
			error.put("error", "请选择连续两个交易日，开始日期和结束日期不能相同");
			return error;
		}

		// 确保date1 <= date2，随后严格校验用户选择的两天是否就是相邻开市日
		LocalDate earlier = date1.isBefore(date2) ? date1 : date2;
		LocalDate later = date1.isBefore(date2) ? date2 : date1;

		// 查询这两个日期之间的所有交易日
		QueryWrapper<TradeCalendar> tcWrapper = new QueryWrapper<>();
		tcWrapper.eq("is_open", 1)
				.ge("trade_date", earlier)
				.le("trade_date", later)
				.orderByAsc("trade_date");
		List<TradeCalendar> tradingDays = tradeCalendarMapper.selectList(tcWrapper);
		List<LocalDate> uniqueTradeDates = tradingDays.stream()
				.map(TradeCalendar::getTradeDate)
				.filter(Objects::nonNull)
				.distinct()
				.sorted()
				.collect(Collectors.toList());

		// 必须刚好两个唯一交易日，并且就是用户选择的两个日期
		if (uniqueTradeDates.size() != 2) {
			Map<String, Object> error = new HashMap<>();
			error.put("error", "请选择连续两个交易日，当前区间内不是相邻开市日");
			return error;
		}

		LocalDate tc1Date = uniqueTradeDates.get(0);
		LocalDate tc2Date = uniqueTradeDates.get(1);
		if (!tc1Date.equals(earlier) || !tc2Date.equals(later)) {
			Map<String, Object> error = new HashMap<>();
			error.put("error", "请选择连续两个交易日，当前选择的日期不是相邻开市日");
			return error;
		}

		String yesterday = tc1Date.toString();
		String today = tc2Date.toString();

		// 查询两天的数据
		List<EtfFundFlowSummary> yesterdayData = listByDateRange(yesterday, yesterday);
		List<EtfFundFlowSummary> todayData = listByDateRange(today, today);

		Map<String, Object> result = new HashMap<>();
		result.put("success", true);
		result.put("yesterday", yesterday);
		result.put("today", today);
		result.put("yesterdayData", yesterdayData);
		result.put("todayData", todayData);
		return result;
	}

	private void enrichEtfNameFromMaster(List<EtfFundFlowSummary> rows) {
		if (rows == null || rows.isEmpty()) {
			return;
		}

		Set<String> codes = rows.stream()
				.map(EtfFundFlowSummary::getEtfCode)
				.filter(code -> code != null && !code.trim().isEmpty())
				.collect(Collectors.toSet());
		if (codes.isEmpty()) {
			return;
		}

		QueryWrapper<EtfSecurityMaster> masterWrapper = new QueryWrapper<>();
		masterWrapper.in("etf_code", codes).select("etf_code", "symbol");
		List<EtfSecurityMaster> masters = etfSecurityMasterMapper.selectList(masterWrapper);
		Map<String, String> codeToSymbol = masters.stream()
				.filter(item -> item.getEtfCode() != null && item.getSymbol() != null && !item.getSymbol().trim().isEmpty())
				.collect(Collectors.toMap(EtfSecurityMaster::getEtfCode, EtfSecurityMaster::getSymbol, (left, right) -> left));

		for (EtfFundFlowSummary row : rows) {
			String symbol = codeToSymbol.get(row.getEtfCode());
			if (symbol != null && !symbol.trim().isEmpty()) {
				row.setEtfName(symbol.trim());
			}
		}
	}

	private void applySort(QueryWrapper<EtfFundFlowSummary> wrapper, Map<String, Object> filters) {
		if (filters == null) {
			wrapper.orderByDesc("fund_flow").orderByDesc("trade_date").orderByAsc("id");
			return;
		}

		String sortField = String.valueOf(filters.getOrDefault("sortField", "")).trim();
		String sortOrder = String.valueOf(filters.getOrDefault("sortOrder", "")).trim();
		if (sortField.isEmpty() || sortOrder.isEmpty()) {
			wrapper.orderByDesc("fund_flow").orderByDesc("trade_date").orderByAsc("id");
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
			case "fundFlow":
				wrapper.orderBy(true, asc, "fund_flow");
				break;
			case "cumulativeFlow":
				wrapper.orderBy(true, asc, "cumulative_flow");
				break;
			case "changeReason":
				wrapper.orderBy(true, asc, "change_reason");
				break;
			default:
				wrapper.orderByDesc("fund_flow").orderByDesc("trade_date").orderByAsc("id");
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
}
