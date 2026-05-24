package com.demo.controller;

import com.demo.service.EtlProgressService;
import com.demo.vo.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/etl_progress")
public class EtlProgressController {

    @Autowired
    private EtlProgressService etlProgressService;

    @GetMapping("/current-trade-date")
    public R<Integer> currentTradeDate() {
        return R.ok(etlProgressService.getCurrentTradeDate());
    }
}
