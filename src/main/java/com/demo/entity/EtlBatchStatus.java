package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("etl_batch_status")
public class EtlBatchStatus {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("batch_no")
    private String batchNo;

    @TableField("job_name")
    private String jobName;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("status")
    private String status;

    @TableField("trade_date_start")
    private Integer tradeDateStart;

    @TableField("trade_date_end")
    private Integer tradeDateEnd;

    @TableField("etf_count")
    private Integer etfCount;

    @TableField("kline_count")
    private Integer klineCount;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
