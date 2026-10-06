package com.zwinsight.project.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 项目状态流转大事记（V2026_81）。
 * <p>
 * 每次合法流转一条记录：审计追溯 + 详情页"项目大事记"时间线共用。
 * 事件语义见 {@link com.zwinsight.project.domain.enums.ProjectEvent}。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "biz_project_status_log", autoResultMap = true)
public class BizProjectStatusLog extends BaseEntity {

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
}
