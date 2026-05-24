package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("etf_ta_indicator")
public class EtfTaIndicator {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("etf_code")
    private String etfCode;
    @TableField("period")
    private String period;
    @TableField("trade_time")
    private LocalDateTime tradeTime;
    @TableField("open_price")
    private BigDecimal openPrice;
    @TableField("high_price")
    private BigDecimal highPrice;
    @TableField("low_price")
    private BigDecimal lowPrice;
    @TableField("close_price")
    private BigDecimal closePrice;
    @TableField("dif")
    private BigDecimal dif;
    @TableField("dea")
    private BigDecimal dea;
    @TableField("macd")
    private BigDecimal macd;
    @TableField("is_macd_red")
    private Integer isMacdRed;
    @TableField("macd_hist_direction")
    private Integer macdHistDirection;
    @TableField("sar_value")
    private java.math.BigDecimal sarValue;
    @TableField("is_sar_bullish")
    private Integer isSarBullish;
    @TableField("sar_trend")
    private Integer sarTrend;
    @TableField("signal_trend_long")
    private Integer signalTrendLong;
    @TableField("signal_momentum_long")
    private Integer signalMomentumLong;
    @TableField("signal_warning")
    private Integer signalWarning;
    @TableField(exist = false)
    private String etfName;
    @TableField("source")
    private String source;
    @TableField("etl_batch_no")
    private String etlBatchNo;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
