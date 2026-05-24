package com.demo.controller;

import com.demo.entity.EtfFundFlowSummary;
import com.demo.dto.DateRangeParam;
import com.demo.service.EtfFundFlowSummaryService;
import com.demo.vo.PageParam;
import com.demo.vo.PageResult;
import com.demo.vo.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/etf_fund_flow_summary")
public class EtfFundFlowSummaryController {

    @Autowired
    private EtfFundFlowSummaryService baseService;

    @PostMapping("/page")
    public R<PageResult<EtfFundFlowSummary>> page(@RequestBody PageParam param) {
        return R.ok(baseService.pageByTradeDate(param));
    }

    @PostMapping("/accumulate")
    public R<String> accumulate(@RequestBody DateRangeParam param) {
        int inserted = baseService.accumulateByDateRange(param.getStartDate(), param.getEndDate());
        return R.ok("累计新增完成，插入 " + inserted + " 条记录");
    }

    @PostMapping("/chart-data")
    public R<List<EtfFundFlowSummary>> chartData(@RequestBody DateRangeParam param) {
        return R.ok(baseService.listByDateRange(param.getStartDate(), param.getEndDate()));
    }

    @PostMapping("/flow-comparison")
    public R<Map<String, Object>> flowComparison(@RequestBody DateRangeParam param) {
        Map<String, Object> result = baseService.getConsecutiveTradingDayData(param.getStartDate(), param.getEndDate());
        return R.ok(result);
    }

    @GetMapping("/{id}")
    public R<EtfFundFlowSummary> getById(@PathVariable Long id) {
        return R.ok(baseService.getById(id));
    }

    @PostMapping
    public R<Boolean> save(@RequestBody EtfFundFlowSummary entity) {
        return R.ok(baseService.save(entity));
    }

    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody EtfFundFlowSummary entity) {
        entity.setId(id);
        return R.ok(baseService.updateById(entity));
    }

    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(baseService.removeById(id));
    }

    @DeleteMapping("/batch")
    public R<Boolean> deleteBatch(@RequestBody List<Long> ids) {
        return R.ok(baseService.removeByIds(ids));
    }
}
