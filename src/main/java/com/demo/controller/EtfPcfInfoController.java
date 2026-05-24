package com.demo.controller;

import com.demo.entity.EtfPcfInfo;
import com.demo.service.EtfPcfInfoService;
import com.demo.vo.PageParam;
import com.demo.vo.PageResult;
import com.demo.vo.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/etf_pcf_info")
public class EtfPcfInfoController {

    @Autowired
    private EtfPcfInfoService baseService;

    @PostMapping("/page")
    public R<PageResult<EtfPcfInfo>> page(@RequestBody PageParam param) {
        return R.ok(baseService.pageQuery(param, EtfPcfInfo::getEtfCode, EtfPcfInfo::getSource));
    }

    @GetMapping("/{id}")
    public R<EtfPcfInfo> getById(@PathVariable Long id) {
        return R.ok(baseService.getById(id));
    }

    @PostMapping
    public R<Boolean> save(@RequestBody EtfPcfInfo entity) {
        return R.ok(baseService.save(entity));
    }

    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody EtfPcfInfo entity) {
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
