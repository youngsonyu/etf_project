package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("etf_pcf_info")
public class EtfPcfInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("etf_code")
    private String etfCode;
    @TableField("trading_day")
    private LocalDate tradingDay;
    @TableField("pre_trading_day")
    private LocalDate preTradingDay;
    @TableField("creation_redemption_unit")
    private Long creationRedemptionUnit;
    @TableField("max_cash_ratio")
    private BigDecimal maxCashRatio;
    @TableField("publish")
    private String publish;
    @TableField("creation")
    private String creation;
    @TableField("redemption")
    private String redemption;
    @TableField("symbol")
    private String symbol;
    @TableField("underlying_security_id")
    private String underlyingSecurityId;
    @TableField("source")
    private String source;
    @TableField("etl_batch_no")
    private String etlBatchNo;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
