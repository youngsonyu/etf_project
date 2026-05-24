package com.demo.controller;

import com.demo.entity.EtfMarketKline;
import com.demo.service.EtfMarketKlineService;
import com.demo.vo.PageParam;
import com.demo.vo.PageResult;
import com.demo.vo.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/etf_market_kline")
public class EtfMarketKlineController {

    @Autowired
    private EtfMarketKlineService baseService;

    @PostMapping("/page")
    public R<PageResult<com.demo.vo.EtfMarketKlineWithNameVO>> page(@RequestBody PageParam param) {
        return R.ok(baseService.pageWithName(param));
    }

    @GetMapping("/{id}")
    public R<EtfMarketKline> getById(@PathVariable Long id) {
        return R.ok(baseService.getById(id));
    }

    @PostMapping
    public R<Boolean> save(@RequestBody EtfMarketKline entity) {
        return R.ok(baseService.save(entity));
    }

    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody EtfMarketKline entity) {
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
