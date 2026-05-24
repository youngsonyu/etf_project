package com.demo.controller;

import com.demo.entity.EtfFiveDimensionResonance;
import com.demo.service.EtfFiveDimensionResonanceService;
import com.demo.vo.PageParam;
import com.demo.vo.PageResult;
import com.demo.vo.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/etf_five_dimension_resonance")
public class EtfFiveDimensionResonanceController {

    @Autowired
    private EtfFiveDimensionResonanceService baseService;

    @PostMapping("/page")
    public R<PageResult<EtfFiveDimensionResonance>> page(@RequestBody PageParam param) {
        return R.ok(baseService.pageByCondition(param));
    }

    @PostMapping("/export-data")
    public R<List<EtfFiveDimensionResonance>> exportData(@RequestBody(required = false) PageParam param) {
        PageParam safeParam = param == null ? new PageParam() : param;
        return R.ok(baseService.listForExport(safeParam));
    }

    @GetMapping("/latest-trade-date")
    public R<String> latestTradeDate() {
        return R.ok(baseService.getLatestTradeDate());
    }

    @PostMapping("/refresh-latest")
    public R<Map<String, Object>> refreshLatest() {
        return R.ok(baseService.refreshLatestTradeDate());
    }

    @GetMapping("/{id}")
    public R<EtfFiveDimensionResonance> getById(@PathVariable Long id) {
        return R.ok(baseService.getById(id));
    }

    @PostMapping
    public R<Boolean> save(@RequestBody EtfFiveDimensionResonance entity) {
        return R.ok(baseService.save(entity));
    }

    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody EtfFiveDimensionResonance entity) {
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
