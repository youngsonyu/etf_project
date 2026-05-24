package com.demo.vo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class PageParam {
    private int pageNum = 1;
    private int pageSize = 10;
    private String keyword;
    @JsonIgnore
    private Map<String, Object> filters = new HashMap<>();

    public int getPageNum() {
        return pageNum <= 0 ? 1 : pageNum;
    }

    public int getPageSize() {
        return pageSize <= 0 ? 10 : pageSize;
    }

    @JsonAnySetter
    public void putFilter(String key, Object value) {
        if ("pageNum".equals(key) || "pageSize".equals(key) || "keyword".equals(key)) {
            return;
        }
        filters.put(key, value);
    }
}
