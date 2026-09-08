package com.demo.controller;

import com.demo.entity.EtlBatchStatus;
import com.demo.service.EtlBatchStatusService;
import com.demo.service.impl.EtlPythonScheduleService;
import com.demo.vo.PageParam;
import com.demo.vo.PageResult;
import com.demo.vo.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/etl_batch_status")
public class EtlBatchStatusController {

    @Autowired
    private EtlBatchStatusService baseService;

    @Autowired
    private EtlPythonScheduleService etlPythonScheduleService;

    @PostMapping("/page")
    public R<PageResult<EtlBatchStatus>> page(@RequestBody PageParam param) {
        return R.ok(baseService.pageQuery(param, EtlBatchStatus::getBatchNo, EtlBatchStatus::getJobName, EtlBatchStatus::getStatus));
    }

    @PostMapping("/import-galaxy")
    public R<String> importGalaxyData() {
        try {
            return R.ok(etlPythonScheduleService.triggerPythonScriptAsync());
        } catch (Exception ex) {
            return R.error(ex.getMessage());
        }
    }

    @PostMapping("/calc-ta")
    public R<String> calcTaIndicator() {
        try {
            return R.ok(etlPythonScheduleService.triggerTaCalcAsync());
        } catch (Exception ex) {
            return R.error(ex.getMessage());
        }
    }

    @PostMapping("/calc-ta-daily")
    public R<String> calcTaIndicatorDaily() {
        try {
            return R.ok(etlPythonScheduleService.triggerTaDailyCalcAsync());
        } catch (Exception ex) {
            return R.error(ex.getMessage());
        }
    }

    @GetMapping("/{id}")
    public R<EtlBatchStatus> getById(@PathVariable Long id) {
        return R.ok(baseService.getById(id));
    }

    @PostMapping
    public R<Boolean> save(@RequestBody EtlBatchStatus entity) {
        return R.ok(baseService.save(entity));
    }

    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody EtlBatchStatus entity) {
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
