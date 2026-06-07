package com.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.demo.utils.QueryBuilder;
import com.demo.utils.R;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public abstract class BaseCrudController<T> {

    protected abstract IService<T> getService();

    protected abstract Class<T> getEntityClass();

    @PostMapping("/page")
    public R page(@RequestBody Map<String, Object> params) {
        long pageNum = Long.parseLong(String.valueOf(params.getOrDefault("pageNum", 1)));
        long pageSize = Long.parseLong(String.valueOf(params.getOrDefault("pageSize", 10)));

        QueryWrapper<T> wrapper = QueryBuilder.build(params, getEntityClass());
        applyDefaultSort(wrapper, params);
        IPage<T> result = getService().page(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(result);
    }

    private void applyDefaultSort(QueryWrapper<T> wrapper, Map<String, Object> params) {
        Object sortFieldObj = params.get("sortField");
        Object sortOrderObj = params.get("sortOrder");
        if (sortFieldObj != null && sortOrderObj != null) {
            String sortField = String.valueOf(sortFieldObj).trim();
            String sortOrder = String.valueOf(sortOrderObj).trim();
            if (!sortField.isEmpty() && !sortOrder.isEmpty()) {
                wrapper.orderBy(true, "asc".equalsIgnoreCase(sortOrder), QueryBuilder.toSnakeCase(sortField));
                return;
            }
        }
        String defaultColumn = getDefaultSortColumn();
        if (defaultColumn != null) {
            wrapper.orderByDesc(defaultColumn);
        }
    }

    protected String getDefaultSortColumn() {
        return null;
    }

    @GetMapping("/{id}")
    public R detail(@PathVariable Long id) {
        return R.ok(getService().getById(id));
    }

    @PostMapping
    public R create(@RequestBody T entity) {
        getService().save(entity);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R update(@PathVariable Long id, @RequestBody T entity) {
        setId(entity, id);
        getService().updateById(entity);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        getService().removeById(id);
        return R.ok();
    }

    @DeleteMapping("/batch")
    public R deleteBatch(@RequestBody List<Long> ids) {
        getService().removeByIds(ids);
        return R.ok();
    }

    private void setId(T entity, Long id) {
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("鏇存柊澶辫触锛氬疄浣撶己灏慽d瀛楁", e);
        }
    }
}
