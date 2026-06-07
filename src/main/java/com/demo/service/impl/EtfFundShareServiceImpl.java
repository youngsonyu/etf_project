package com.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.entity.EtfFundShare;
import com.demo.mapper.EtfFundShareMapper;
import com.demo.service.EtfFundShareService;
import org.springframework.stereotype.Service;

@Service
public class EtfFundShareServiceImpl extends ServiceImpl<EtfFundShareMapper, EtfFundShare> implements EtfFundShareService {
    @Override
    public String getDefaultSortColumn() {
        return "change_date";
    }
}
