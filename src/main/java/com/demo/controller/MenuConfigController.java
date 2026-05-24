package com.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.entity.MenuConfig;
import com.demo.service.MenuConfigService;
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
@RequestMapping("/api/menu_config")
public class MenuConfigController {

    @Autowired
    private MenuConfigService menuConfigService;

    @GetMapping("/visible")
    public R<List<MenuConfig>> visibleMenus() {
        LambdaQueryWrapper<MenuConfig> wrapper = new LambdaQueryWrapper<MenuConfig>()
                .eq(MenuConfig::getIsActive, 1)
                .eq(MenuConfig::getIsVisible, 1)
                .orderByAsc(MenuConfig::getSort)
                .orderByAsc(MenuConfig::getId);
        return R.ok(menuConfigService.list(wrapper));
    }

    @PostMapping("/page")
    public R<PageResult<MenuConfig>> page(@RequestBody PageParam param) {
        return R.ok(menuConfigService.pageQuery(param, MenuConfig::getMenuCode, MenuConfig::getMenuName));
    }

    @GetMapping("/{id}")
    public R<MenuConfig> getById(@PathVariable Long id) {
        return R.ok(menuConfigService.getById(id));
    }

    @PostMapping
    public R<Boolean> save(@RequestBody MenuConfig entity) {
        return R.ok(menuConfigService.save(entity));
    }

    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody MenuConfig entity) {
        entity.setId(id);
        return R.ok(menuConfigService.updateById(entity));
    }

    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(menuConfigService.removeById(id));
    }

    @DeleteMapping("/batch")
    public R<Boolean> deleteBatch(@RequestBody List<Long> ids) {
        return R.ok(menuConfigService.removeByIds(ids));
    }
}