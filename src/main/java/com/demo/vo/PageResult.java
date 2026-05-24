package com.demo.vo;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    public PageResult() {}
    private List<T> records;
    private long total;
    private long size;
    private long current;
    private long pages;

    public PageResult(com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page) {
        this.records = page.getRecords();
        this.total = page.getTotal();
        this.size = page.getSize();
        this.current = page.getCurrent();
        this.pages = page.getPages();
    }
}
