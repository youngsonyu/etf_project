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
@TableName("etf_fund_iopv")
public class EtfFundIopv {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("etf_code")
    private String etfCode;
    @TableField("price_date")
    private LocalDate priceDate;
    @TableField("iopv_nav")
    private BigDecimal iopvNav;
    @TableField("source")
    private String source;
    @TableField("etl_batch_no")
    private String etlBatchNo;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
