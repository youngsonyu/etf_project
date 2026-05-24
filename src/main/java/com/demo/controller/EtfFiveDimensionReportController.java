package com.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.entity.EtfFiveDimensionReport;
import com.demo.service.EtfFiveDimensionReportGenerationService;
import com.demo.service.EtfFiveDimensionReportService;
import com.demo.vo.PageParam;
import com.demo.vo.PageResult;
import com.demo.vo.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/etf_five_dimension_report")
public class EtfFiveDimensionReportController {

    @Autowired
    private EtfFiveDimensionReportService baseService;

    @Autowired
    private EtfFiveDimensionReportGenerationService generationService;

    @PostMapping("/page")
    public R<PageResult<EtfFiveDimensionReport>> page(@RequestBody PageParam param) {
        return R.ok(baseService.pageQuery(param, EtfFiveDimensionReport::getTitle, EtfFiveDimensionReport::getPublisher));
    }

    @GetMapping("/latest")
    public R<List<EtfFiveDimensionReport>> latest(@RequestParam(defaultValue = "5") Integer size) {
        int querySize = (size == null || size <= 0) ? 5 : Math.min(size, 20);
        LambdaQueryWrapper<EtfFiveDimensionReport> wrapper = new LambdaQueryWrapper<EtfFiveDimensionReport>()
                .eq(EtfFiveDimensionReport::getIsActive, 1)
                .orderByDesc(EtfFiveDimensionReport::getPublishTime)
                .orderByDesc(EtfFiveDimensionReport::getId)
                .last("LIMIT " + querySize);
        return R.ok(baseService.list(wrapper));
    }

    @GetMapping("/{id}")
    public R<EtfFiveDimensionReport> getById(@PathVariable Long id) {
        return R.ok(baseService.getById(id));
    }

    @PostMapping("/generate-latest")
    public R<EtfFiveDimensionReport> generateLatest(@RequestParam(required = false) String publisher) {
        try {
            return R.ok(generationService.generateLatestReport(publisher));
        } catch (IllegalStateException ex) {
            return R.error(ex.getMessage());
        } catch (Exception ex) {
            return R.error("生成报告失败: " + ex.getMessage());
        }
    }

    @PostMapping
    public R<Boolean> save(@RequestBody EtfFiveDimensionReport entity) {
        if (entity.getPublishTime() == null) {
            entity.setPublishTime(LocalDateTime.now());
        }
        if (entity.getIsActive() == null) {
            entity.setIsActive(1);
        }
        return R.ok(baseService.save(entity));
    }

    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody EtfFiveDimensionReport entity) {
        entity.setId(id);
        if (entity.getPublishTime() == null) {
            entity.setPublishTime(LocalDateTime.now());
        }
        if (entity.getIsActive() == null) {
            entity.setIsActive(1);
        }
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
