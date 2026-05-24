package com.demo.service;

import com.demo.entity.EtfFundFlowSummary;
import com.demo.vo.PageParam;
import com.demo.vo.PageResult;

import java.util.List;
import java.util.Map;

public interface EtfFundFlowSummaryService extends BaseCrudService<EtfFundFlowSummary> {
    PageResult<EtfFundFlowSummary> pageByTradeDate(PageParam param);

    int accumulateByDateRange(String startDate, String endDate);

    List<EtfFundFlowSummary> listByDateRange(String startDate, String endDate);

    /**
     * Verify if two dates are consecutive trading days and return structured data
     * @param startDate First trading date (yyyy-MM-dd)
     * @param endDate Second trading date (yyyy-MM-dd)
     * @return Map with "yesterday" and "today" lists, or error message
     */
    Map<String, Object> getConsecutiveTradingDayData(String startDate, String endDate);
}
