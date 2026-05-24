package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("etf_market_snapshot")
public class EtfMarketSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("etf_code")
    private String etfCode;
    @TableField("trade_time")
    private LocalDateTime tradeTime;
    @TableField("last_price")
    private BigDecimal lastPrice;
    @TableField("open_price")
    private BigDecimal openPrice;
    @TableField("high_price")
    private BigDecimal highPrice;
    @TableField("low_price")
    private BigDecimal lowPrice;
    @TableField("close_price")
    private BigDecimal closePrice;
    @TableField("volume")
    private Long volume;
    @TableField("amount")
    private BigDecimal amount;
    @TableField("source")
    private String source;
    @TableField("etl_batch_no")
    private String etlBatchNo;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
