package com.zwinsight.project.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目关键字段变更台账（V2026_81，不变量 I4，append-only 流水表）。
 * <p>
 * 立项（FILED 及之后）状态下修改关键字段必须落此台账：who/when/old/new 全留痕。
 * 不继承 BaseEntity，与 biz_project_change_log 表物理列严格对齐。
 * </p>
 */
@Data
@TableName(value = "biz_project_change_log", autoResultMap = true)
public class BizProjectChangeLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("project_id")
    private Long projectId;

    @TableField("field_name")
    private String fieldName;

    @TableField("old_value")
    private String oldValue;

    @TableField("new_value")
    private String newValue;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
