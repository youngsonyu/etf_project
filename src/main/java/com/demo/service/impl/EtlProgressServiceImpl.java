package com.demo.service.impl;

import com.demo.mapper.EtlProgressMapper;
import com.demo.service.EtlProgressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class EtlProgressServiceImpl implements EtlProgressService {

    @Autowired
    private EtlProgressMapper etlProgressMapper;

    @Override
    public Integer getCurrentTradeDate() {
        Integer tradeDate = queryCheckpointDate();
        if (tradeDate != null) {
            return tradeDate;
        }
        return queryBatchStatusDate();
    }

    private Integer queryCheckpointDate() {
        try {
            return etlProgressMapper.selectCurrentTradeDateFromCheckpoint();
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private Integer queryBatchStatusDate() {
        try {
            return etlProgressMapper.selectCurrentTradeDateFromBatchStatus();
        } catch (DataAccessException ex) {
            return null;
        }
    }
}
