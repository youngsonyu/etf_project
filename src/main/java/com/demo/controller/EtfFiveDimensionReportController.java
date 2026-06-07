package com.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.entity.EtfFiveDimensionReport;
import com.demo.service.EtfFiveDimensionReportGenerationService;
import com.demo.service.EtfFiveDimensionReportService;
import com.demo.vo.PageParam;
import com.demo.vo.PageResult;
import com.demo.vo.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
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
        Page<EtfFiveDimensionReport> page = new Page<>(param.getPageNum(), param.getPageSize());
        LambdaQueryWrapper<EtfFiveDimensionReport> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(param.getKeyword())) {
            String kw = param.getKeyword().trim();
            wrapper.and(w -> w
                .like(EtfFiveDimensionReport::getTitle, kw)
                .or().like(EtfFiveDimensionReport::getPublisher, kw)
            );
        }

        if (param.getFilters() != null && StringUtils.hasText(String.valueOf(param.getFilters().get("publishDate")))) {
            String dateStr = String.valueOf(param.getFilters().get("publishDate")).trim();
            if (dateStr.matches("\\d{8}")) {
                String formatted = dateStr.substring(0, 4) + "-" + dateStr.substring(4, 6) + "-" + dateStr.substring(6, 8);
                wrapper.apply("DATE(publish_time) = {0}", formatted);
            }
        }

        wrapper.orderByDesc(EtfFiveDimensionReport::getPublishTime)
               .orderByDesc(EtfFiveDimensionReport::getId);

        return R.ok(new PageResult<>(baseService.page(page, wrapper)));
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
