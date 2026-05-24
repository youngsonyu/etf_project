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
@TableName("etf_security_master")
public class EtfSecurityMaster {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("etf_code")
    private String etfCode;
    @TableField("market")
    private String market;
    @TableField("symbol")
    private String symbol;
    @TableField("security_status")
    private String securityStatus;
    @TableField("pre_close")
    private BigDecimal preClose;
    @TableField("high_limited")
    private BigDecimal highLimited;
    @TableField("low_limited")
    private BigDecimal lowLimited;
    @TableField("price_tick")
    private BigDecimal priceTick;
    @TableField("is_active")
    private Integer isActive;
    @TableField("list_date")
    private LocalDate listDate;
    @TableField("delist_date")
    private LocalDate delistDate;
    @TableField("source")
    private String source;
    @TableField("etl_batch_no")
    private String etlBatchNo;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
