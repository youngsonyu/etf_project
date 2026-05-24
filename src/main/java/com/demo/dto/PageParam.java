package com.demo.dto;

import lombok.Data;

@Data
public class PageParam {
    private int pageNum = 1;
    private int pageSize = 10;
    private String keyword;
}
