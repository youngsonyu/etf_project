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
@TableName("etf_fund_flow_summary")
public class EtfFundFlowSummary {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("etf_code")
    private String etfCode;
    @TableField("etf_name")
    private String etfName;
    @TableField("trade_date")
    private LocalDate tradeDate;
    @TableField("fund_share_today")
    private BigDecimal fundShareToday;
    @TableField("share_change")
    private BigDecimal shareChange;
    @TableField("close_price")
    private BigDecimal closePrice;
    @TableField("fund_flow")
    private BigDecimal fundFlow;
    @TableField("cumulative_flow")
    private BigDecimal cumulativeFlow;
    @TableField("ann_date")
    private LocalDate annDate;
    @TableField("change_reason")
    private String changeReason;
    @TableField("create_time")
    private LocalDateTime createdAt;
    @TableField("update_time")
    private LocalDateTime updatedAt;
}
