package com.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.entity.EtfTaIndicator;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface EtfTaIndicatorMapper extends BaseMapper<EtfTaIndicator> {
	List<EtfTaIndicator> pageSafe(@Param("offset") int offset,
								  @Param("size") int size,
								  @Param("keyword") String keyword,
								  @Param("etfName") String etfName,
								  @Param("period") Collection<String> period,
								  @Param("tradeTimeDate") String tradeTimeDate,
								  @Param("source") String source,
								  @Param("signalTrendLong") Integer signalTrendLong,
								  @Param("isMacdRed") Integer isMacdRed,
								  @Param("macdHistDirection") Integer macdHistDirection,
								  @Param("isSarBullish") Integer isSarBullish,
								  @Param("sarTrend") Integer sarTrend,
								  @Param("signalMomentumLong") Integer signalMomentumLong,
								  @Param("signalWarning") Integer signalWarning,
								  @Param("sortField") String sortField,
								  @Param("sortOrder") String sortOrder);

	long countSafe(@Param("keyword") String keyword,
				   @Param("etfName") String etfName,
				   @Param("period") Collection<String> period,
				   @Param("tradeTimeDate") String tradeTimeDate,
				   @Param("source") String source,
				   @Param("signalTrendLong") Integer signalTrendLong,
				   @Param("isMacdRed") Integer isMacdRed,
				   @Param("macdHistDirection") Integer macdHistDirection,
				   @Param("isSarBullish") Integer isSarBullish,
				   @Param("sarTrend") Integer sarTrend,
				   @Param("signalMomentumLong") Integer signalMomentumLong,
				   @Param("signalWarning") Integer signalWarning);
}
