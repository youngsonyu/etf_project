package com.demo.mapper;

import org.apache.ibatis.annotations.Select;

public interface EtlProgressMapper {

    @Select("SELECT MAX(last_trade_date) FROM etl_checkpoint")
    Integer selectCurrentTradeDateFromCheckpoint();

    @Select("SELECT MAX(trade_date_end) FROM etl_batch_status WHERE status IN ('SUCCESS', 'PARTIAL')")
    Integer selectCurrentTradeDateFromBatchStatus();
}
