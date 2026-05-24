package com.demo.controller;

import com.demo.entity.EtfFundIopv;
import com.demo.service.EtfFundIopvService;
import com.demo.vo.PageParam;
import com.demo.vo.PageResult;
import com.demo.vo.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/etf_fund_iopv")
public class EtfFundIopvController {

    @Autowired
    private EtfFundIopvService baseService;

    @PostMapping("/page")
    public R<PageResult<EtfFundIopv>> page(@RequestBody PageParam param) {
        return R.ok(baseService.pageQuery(param, EtfFundIopv::getEtfCode, EtfFundIopv::getSource));
    }

    @GetMapping("/{id}")
    public R<EtfFundIopv> getById(@PathVariable Long id) {
        return R.ok(baseService.getById(id));
    }

    @PostMapping
    public R<Boolean> save(@RequestBody EtfFundIopv entity) {
        return R.ok(baseService.save(entity));
    }

    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody EtfFundIopv entity) {
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
