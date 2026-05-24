package com.demo.controller;

import com.demo.entity.SysParam;
import com.demo.service.SysParamService;
import com.demo.vo.PageParam;
import com.demo.vo.PageResult;
import com.demo.vo.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sys_param")
public class SysParamController {

    @Autowired
    private SysParamService baseService;

    @PostMapping("/page")
    public R<PageResult<SysParam>> page(@RequestBody PageParam param) {
        return R.ok(baseService.pageQuery(param, SysParam::getParamKey, SysParam::getDescription));
    }

    @GetMapping("/{id}")
    public R<SysParam> getById(@PathVariable Long id) {
        return R.ok(baseService.getById(id));
    }

    @PostMapping
    public R<Boolean> save(@RequestBody SysParam entity) {
        return R.ok(baseService.save(entity));
    }

    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody SysParam entity) {
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
