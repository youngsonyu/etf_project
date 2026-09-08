package com.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.entity.EtfFiveDimensionResonance;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EtfFiveDimensionResonanceMapper extends BaseMapper<EtfFiveDimensionResonance> {
	String selectLatestKlineTradeDate();
	
	List<String> selectDayTradeDates(@Param("startDate") String startDate,
									 @Param("endDate") String endDate);

	List<String> selectAllDayTradeDates();

	String selectLatestBatchDateByTradeDate(@Param("tradeDate") String tradeDate);

	String selectPreviousTradeDateByTradeDate(@Param("tradeDate") String tradeDate);

	int countByLastTriggeredDate(@Param("tradeDate") String tradeDate);

	int upsertByBatchDate(@Param("latestBatchDate") String latestBatchDate,
						  @Param("previousTradeDate") String previousTradeDate,
						  @Param("tradeDate") String tradeDate);

	int upsertAllHistory();

	String selectMaxLastTriggeredDate();
}
