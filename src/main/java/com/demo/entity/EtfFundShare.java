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
@TableName("etf_fund_share")
public class EtfFundShare {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("etf_code")
    private String etfCode;
    @TableField("change_date")
    private LocalDate changeDate;
    @TableField("ann_date")
    private LocalDate annDate;
    @TableField("fund_share")
    private BigDecimal fundShare;
    @TableField("total_share")
    private BigDecimal totalShare;
    @TableField("float_share")
    private BigDecimal floatShare;
    @TableField("change_reason")
    private String changeReason;
    @TableField("is_consolidated_data")
    private Integer isConsolidatedData;
    @TableField("source")
    private String source;
    @TableField("etl_batch_no")
    private String etlBatchNo;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
