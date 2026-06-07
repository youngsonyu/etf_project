package com.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.entity.EtfMarketSnapshot;
import com.demo.mapper.EtfMarketSnapshotMapper;
import com.demo.service.EtfMarketSnapshotService;
import org.springframework.stereotype.Service;

@Service
public class EtfMarketSnapshotServiceImpl extends ServiceImpl<EtfMarketSnapshotMapper, EtfMarketSnapshot> implements EtfMarketSnapshotService {
    @Override
    public String getDefaultSortColumn() {
        return "trade_time";
    }
}
