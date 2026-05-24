package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("etl_checkpoint")
public class EtlCheckpoint {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("checkpoint_key")
    private String checkpointKey;

    @TableField("last_trade_date")
    private Integer lastTradeDate;

    @TableField("last_batch_no")
    private String lastBatchNo;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
