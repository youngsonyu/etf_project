package com.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.entity.EtfPcfInfo;
import com.demo.mapper.EtfPcfInfoMapper;
import com.demo.service.EtfPcfInfoService;
import org.springframework.stereotype.Service;

@Service
public class EtfPcfInfoServiceImpl extends ServiceImpl<EtfPcfInfoMapper, EtfPcfInfo> implements EtfPcfInfoService {
    @Override
    public String getDefaultSortColumn() {
        return "trading_day";
    }
}
