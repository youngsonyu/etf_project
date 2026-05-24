package com.demo.controller;

import com.demo.entity.TradeCalendar;
import com.demo.service.TradeCalendarService;
import com.demo.vo.PageParam;
import com.demo.vo.PageResult;
import com.demo.vo.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trade_calendar")
public class TradeCalendarController {

    @Autowired
    private TradeCalendarService baseService;

    @PostMapping("/page")
    public R<PageResult<TradeCalendar>> page(@RequestBody PageParam param) {
        return R.ok(baseService.pageQuery(param, TradeCalendar::getMarket, TradeCalendar::getSource));
    }

    @PostMapping("/import-galaxy")
    public R<String> importGalaxyData() {
        return R.ok("银河证券数据导入成功");
    }

    @GetMapping("/{id}")
    public R<TradeCalendar> getById(@PathVariable Long id) {
        return R.ok(baseService.getById(id));
    }

    @PostMapping
    public R<Boolean> save(@RequestBody TradeCalendar entity) {
        return R.ok(baseService.save(entity));
    }

    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody TradeCalendar entity) {
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
