package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("trade_calendar")
public class TradeCalendar {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("market")
    private String market;
    @TableField("trade_date")
    private LocalDate tradeDate;
    @TableField("is_open")
    private Integer isOpen;
    @TableField("source")
    private String source;
    @TableField("etl_batch_no")
    private String etlBatchNo;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
