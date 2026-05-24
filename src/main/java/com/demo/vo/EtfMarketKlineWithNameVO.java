package com.demo.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EtfMarketKlineWithNameVO {
    private Long id;
    private String etfCode;
    private String etfName;
    private String period;
    private LocalDateTime tradeTime;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private Long volume;
    private BigDecimal amount;
    private String source;
    private String etlBatchNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
