package com.zwinsight.project.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目状态流转大事记（V2026_81，append-only 流水表）。
 * <p>
 * 每次合法流转一条记录：审计追溯 + 详情页"项目大事记"时间线共用。
 * 不继承 BaseEntity，与 biz_project_status_log 表物理列严格对齐（防 MyBatis-Plus 自动注入不存在的 created_by 列）。
 * </p>
 */
@Data
@TableName(value = "biz_project_status_log", autoResultMap = true)
public class BizProjectStatusLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("project_id")
    private Long projectId;

    @TableField("from_status")
    private String fromStatus;

    @TableField("to_status")
    private String toStatus;

    /** 触发事件（ProjectEvent 名称） */
    @TableField("event")
    private String event;

    @TableField("remark")
    private String remark;

    /** 操作人（null=系统回调，如审批监听器） */
    @TableField("operator_id")
    private Long operatorId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
