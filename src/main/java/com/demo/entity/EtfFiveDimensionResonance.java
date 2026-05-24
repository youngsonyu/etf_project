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
@TableName("etf_five_dimension_resonance")
public class EtfFiveDimensionResonance {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("etf_code")
    private String etfCode;

    @TableField("etf_name")
    private String etfName;

    @TableField("trade_date")
    private LocalDate tradeDate;

    @TableField("trade_time")
    private LocalDateTime tradeTime;

    @TableField("signal_trend_long")
    private Integer signalTrendLong;

    @TableField("signal_momentum_long")
    private Integer signalMomentumLong;

    @TableField("signal_warning")
    private Integer signalWarning;

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

    @TableField("dif")
    private BigDecimal dif;

    @TableField("dea")
    private BigDecimal dea;

    @TableField("macd")
    private BigDecimal macd;

    @TableField("is_macd_golden_state")
    private Integer isMacdGoldenState;

    @TableField("is_macd_red")
    private Integer isMacdRed;

    @TableField("macd_hist_direction")
    private Integer macdHistDirection;

    @TableField("sar_value")
    private BigDecimal sarValue;

    @TableField("is_sar_bullish")
    private Integer isSarBullish;

    @TableField("sar_trend")
    private Integer sarTrend;

    @TableField("ma5")
    private BigDecimal ma5;

    @TableField("ma10")
    private BigDecimal ma10;

    @TableField("ma20")
    private BigDecimal ma20;

    @TableField("ma30")
    private BigDecimal ma30;

    @TableField("ma60")
    private BigDecimal ma60;

    @TableField("is_ma5_above_ma10")
    private Integer isMa5AboveMa10;

    @TableField("is_ma10_above_ma20")
    private Integer isMa10AboveMa20;

    @TableField("is_close_above_ma20")
    private Integer isCloseAboveMa20;

    @TableField("is_close_above_ma60")
    private Integer isCloseAboveMa60;

    @TableField("rsi6")
    private BigDecimal rsi6;

    @TableField("rsi12")
    private BigDecimal rsi12;

    @TableField("rsi24")
    private BigDecimal rsi24;

    @TableField("k_value")
    private BigDecimal kValue;

    @TableField("d_value")
    private BigDecimal dValue;

    @TableField("j_value")
    private BigDecimal jValue;

    @TableField("boll_mid")
    private BigDecimal bollMid;

    @TableField("boll_upper")
    private BigDecimal bollUpper;

    @TableField("boll_lower")
    private BigDecimal bollLower;

    @TableField("atr14")
    private BigDecimal atr14;

    @TableField("adx14")
    private BigDecimal adx14;

    @TableField("is_yesterday_triggered")
    private Integer isYesterdayTriggered;

    @TableField("is_today_triggered")
    private Integer isTodayTriggered;

    @TableField("first_triggered_date_of_year")
    private LocalDate firstTriggeredDateOfYear;

    @TableField("previous_triggered_date")
    private LocalDate previousTriggeredDate;

    @TableField("last_triggered_date")
    private LocalDate lastTriggeredDate;

    @TableField("source")
    private String source;

    @TableField("etl_batch_no")
    private String etlBatchNo;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
