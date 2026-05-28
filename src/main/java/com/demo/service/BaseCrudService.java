package com.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.demo.utils.QueryBuilder;
import com.demo.vo.PageParam;
import com.demo.vo.PageResult;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

public interface BaseCrudService<T> extends IService<T> {

    default PageResult<T> pageQuery(PageParam param, SFunction<T, ?>... likeColumns) {
        Page<T> page = new Page<>(param.getPageNum(), param.getPageSize());
        QueryWrapper<T> wrapper = new QueryWrapper<>();

        // 处理关键词搜索（LIKE）
        boolean hasKeyword = StringUtils.hasText(param.getKeyword()) && likeColumns != null && likeColumns.length > 0;
        if (hasKeyword) {
            wrapper.and(w -> {
                boolean first = true;
                for (SFunction<T, ?> col : likeColumns) {
                    String column = lambdaToColumn(col);
                    if (first) {
                        w.like(column, param.getKeyword());
                        first = false;
                    } else {
                        w.or().like(column, param.getKeyword());
                    }
                }
                
            });
        }

        // 处理动态字段过滤（等值条件）
        if (param.getFilters() != null && !param.getFilters().isEmpty()) {
            for (Map.Entry<String, Object> entry : param.getFilters().entrySet()) {
                String filterKey = entry.getKey();
                Object value = entry.getValue();
                if (value == null) {
                    continue;
                }
                String str = String.valueOf(value).trim();
                if (str.isEmpty()) {
                    continue;
                }

                if ("sortField".equals(filterKey) || "sortOrder".equals(filterKey)) {
                    continue;
                }

                if ("tradeTimeDate".equals(filterKey)) {
                    String normalizedDate = normalizeDateString(value);
                    if (normalizedDate != null) {
                        wrapper.apply("DATE(trade_time) = {0}", normalizedDate);
                    }
                    continue;
                }

                if ("tradeDate".equals(filterKey) || "tradingDay".equals(filterKey)
                        || "priceDate".equals(filterKey) || "changeDate".equals(filterKey)) {
                    String normalizedDate = normalizeDateString(value);
                    if (normalizedDate != null) {
                        wrapper.eq(QueryBuilder.toSnakeCase(filterKey), normalizedDate);
                    }
                    continue;
                }

                if (filterKey.endsWith("Like")) {
                    String actualKey = filterKey.substring(0, filterKey.length() - 4);
                    wrapper.like(QueryBuilder.toSnakeCase(actualKey), value);
                    continue;
                }

                wrapper.eq(QueryBuilder.toSnakeCase(filterKey), value);
            }
        }

        applySort(wrapper, param);

        return new PageResult<>(this.page(page, wrapper));
    }

    private void applySort(QueryWrapper<T> wrapper, PageParam param) {
        if (param.getFilters() == null) {
            return;
        }

        Object sortFieldObj = param.getFilters().get("sortField");
        Object sortOrderObj = param.getFilters().get("sortOrder");
        if (sortFieldObj == null || sortOrderObj == null) {
            return;
        }

        String sortField = String.valueOf(sortFieldObj).trim();
        String sortOrder = String.valueOf(sortOrderObj).trim();
        if (!StringUtils.hasText(sortField) || !StringUtils.hasText(sortOrder)) {
            return;
        }

        if (!sortField.matches("[A-Za-z][A-Za-z0-9]*")) {
            return;
        }

        wrapper.orderBy(true, "asc".equalsIgnoreCase(sortOrder), QueryBuilder.toSnakeCase(sortField));
    }

    private String lambdaToColumn(SFunction<T, ?> fn) {
        String methodName = LambdaUtils.extract(fn).getImplMethodName();
        String property = PropertyNamer.methodToProperty(methodName);
        return QueryBuilder.toSnakeCase(property);
    }

    private String normalizeDateString(Object value) {
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value).trim();
        if (str.isEmpty()) {
            return null;
        }

        String datePart = str.length() >= 10 ? str.substring(0, 10) : str;
        try {
            return LocalDate.parse(datePart).toString();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
