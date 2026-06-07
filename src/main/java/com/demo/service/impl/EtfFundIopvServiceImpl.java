package com.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.entity.EtfFundIopv;
import com.demo.mapper.EtfFundIopvMapper;
import com.demo.service.EtfFundIopvService;
import org.springframework.stereotype.Service;

@Service
public class EtfFundIopvServiceImpl extends ServiceImpl<EtfFundIopvMapper, EtfFundIopv> implements EtfFundIopvService {
    @Override
    public String getDefaultSortColumn() {
        return "price_date";
    }
}
