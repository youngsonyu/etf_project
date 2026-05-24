package com.demo.mapper;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.entity.EtfMarketKline;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EtfMarketKlineMapper extends BaseMapper<EtfMarketKline> {
        /**
         * Page query K-line with ETF name
         */
        List<com.demo.vo.EtfMarketKlineWithNameVO> pageWithEtfName(@org.apache.ibatis.annotations.Param("offset") int offset,
                                                                                  @org.apache.ibatis.annotations.Param("size") int size,
                                                                                  @org.apache.ibatis.annotations.Param("etfCode") String etfCode,
                                                                                  @org.apache.ibatis.annotations.Param("etfName") String etfName,
                                                                                  @org.apache.ibatis.annotations.Param("period") String period,
                                                                                  @org.apache.ibatis.annotations.Param("tradeTimeDate") String tradeTimeDate,
                                                                                  @org.apache.ibatis.annotations.Param("sortField") String sortField,
                                                                                  @org.apache.ibatis.annotations.Param("sortOrder") String sortOrder);

        long countWithEtfName(@org.apache.ibatis.annotations.Param("etfCode") String etfCode,
                                                  @org.apache.ibatis.annotations.Param("etfName") String etfName,
                                                  @org.apache.ibatis.annotations.Param("period") String period,
                                                  @org.apache.ibatis.annotations.Param("tradeTimeDate") String tradeTimeDate);
}