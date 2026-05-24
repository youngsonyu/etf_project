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
@TableName("etf_pcf_constituent")
public class EtfPcfConstituent {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("etf_code")
    private String etfCode;
    @TableField("trading_day")
    private LocalDate tradingDay;
    @TableField("constituent_code")
    private String constituentCode;
    @TableField("underlying_symbol")
    private String underlyingSymbol;
    @TableField("component_share")
    private BigDecimal componentShare;
    @TableField("substitute_flag")
    private String substituteFlag;
    @TableField("substitution_cash_amount")
    private BigDecimal substitutionCashAmount;
    @TableField("source")
    private String source;
    @TableField("etl_batch_no")
    private String etlBatchNo;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
