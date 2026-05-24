package com.demo.controller;

import com.demo.entity.EtfPcfConstituent;
import com.demo.service.EtfPcfConstituentService;
import com.demo.vo.PageParam;
import com.demo.vo.PageResult;
import com.demo.vo.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/etf_pcf_constituent")
public class EtfPcfConstituentController {

    @Autowired
    private EtfPcfConstituentService baseService;

    @PostMapping("/page")
    public R<PageResult<EtfPcfConstituent>> page(@RequestBody PageParam param) {
        return R.ok(baseService.pageQuery(param, EtfPcfConstituent::getEtfCode, EtfPcfConstituent::getSource));
    }

    @GetMapping("/{id}")
    public R<EtfPcfConstituent> getById(@PathVariable Long id) {
        return R.ok(baseService.getById(id));
    }

    @PostMapping
    public R<Boolean> save(@RequestBody EtfPcfConstituent entity) {
        return R.ok(baseService.save(entity));
    }

    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody EtfPcfConstituent entity) {
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
