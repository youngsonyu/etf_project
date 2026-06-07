package com.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.entity.EtfFiveDimensionResonance;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EtfFiveDimensionResonanceMapper extends BaseMapper<EtfFiveDimensionResonance> {
	String selectLatestKlineTradeDate();

	String selectLatestBatchDateByTradeDate(@Param("tradeDate") String tradeDate);

	String selectPreviousBatchDateByTradeDate(@Param("tradeDate") String tradeDate);

	int countByLastTriggeredDate(@Param("tradeDate") String tradeDate);

	int upsertByBatchDate(@Param("latestBatchDate") String latestBatchDate,
						  @Param("previousBatchDate") String previousBatchDate,
						  @Param("tradeDate") String tradeDate);

	String selectMaxLastTriggeredDate();
}
