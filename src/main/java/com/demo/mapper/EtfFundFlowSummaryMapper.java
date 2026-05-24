package com.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.entity.EtfFundFlowSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EtfFundFlowSummaryMapper extends BaseMapper<EtfFundFlowSummary> {
	int insertAccumulatedByDateRange(@Param("startDate") String startDate, @Param("endDate") String endDate);

	int updateCumulativeFlowByDateRange(@Param("startDate") String startDate, @Param("endDate") String endDate);
}
