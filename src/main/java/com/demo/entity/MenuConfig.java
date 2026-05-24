package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("menu_config")
public class MenuConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("menu_code")
    private String menuCode;

    @TableField("menu_name")
    private String menuName;

    @TableField("parent_code")
    private String parentCode;

    @TableField("path")
    private String path;

    @TableField("icon")
    private String icon;

    @TableField("sort")
    private Integer sort;

    @TableField("is_visible")
    private Integer isVisible;

    @TableField("is_active")
    private Integer isActive;

    @TableField("remark")
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}