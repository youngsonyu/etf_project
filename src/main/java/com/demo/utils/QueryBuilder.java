package com.demo.utils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class QueryBuilder {

    private QueryBuilder() {
    }

    public static <T> QueryWrapper<T> build(Map<String, Object> params, Class<T> entityClass) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        if (params == null || params.isEmpty()) {
            return wrapper;
        }

        Set<String> entityFields = new HashSet<>();
        for (Field field : entityClass.getDeclaredFields()) {
            entityFields.add(field.getName());
        }

        Object keywordObj = params.get("keyword");
        if (keywordObj != null && String.valueOf(keywordObj).trim().length() > 0) {
            String keyword = String.valueOf(keywordObj).trim();
            Field[] fields = entityClass.getDeclaredFields();
            boolean addedKeyword = false;
            for (Field field : fields) {
                if (field.getType() == String.class) {
                    String column = toSnakeCase(field.getName());
                    if (!addedKeyword) {
                        wrapper.like(column, keyword);
                        addedKeyword = true;
                    } else {
                        wrapper.or().like(column, keyword);
                    }
                }
            }
        }

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if ("pageNum".equals(key) || "pageSize".equals(key) || "keyword".equals(key)) {
                continue;
            }
            if (value == null) {
                continue;
            }
            String strValue = String.valueOf(value).trim();
            if (strValue.isEmpty()) {
                continue;
            }

            // ETF代码查询改为模糊匹配，提升检索体验
            if ("etfCode".equals(key) && entityFields.contains("etfCode")) {
                wrapper.like("etf_code", strValue);
                continue;
            }

            // 日期筛选：优先匹配实体中的日期/时间字段
            if ("tradeDate".equals(key)) {
                if (entityFields.contains("tradeDate")) {
                    wrapper.eq("trade_date", strValue);
                } else if (entityFields.contains("tradeTime")) {
                    wrapper.apply("DATE(trade_time) = {0}", strValue);
                } else if (entityFields.contains("tradingDay")) {
                    wrapper.eq("trading_day", strValue);
                } else if (entityFields.contains("priceDate")) {
                    wrapper.eq("price_date", strValue);
                } else if (entityFields.contains("changeDate")) {
                    wrapper.eq("change_date", strValue);
                }
                continue;
            }

            // 只对实体中存在的字段追加过滤条件，避免未知列导致SQL报错
            if (!entityFields.contains(key)) {
                continue;
            }
            wrapper.eq(toSnakeCase(key), value);
        }
        return wrapper;
    }

    public static String toSnakeCase(String camel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
