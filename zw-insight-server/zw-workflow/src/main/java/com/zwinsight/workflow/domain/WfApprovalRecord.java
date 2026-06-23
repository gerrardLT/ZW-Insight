package com.zwinsight.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审批记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_approval_record")
public class WfApprovalRecord extends BaseEntity {

    /**
     * 流程实例ID
     */
    private String processInstanceId;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 处理人
     */
    private String assignee;

    /**
     * 处理人姓名
     */
    private String assigneeName;

    /**
     * 操作类型：APPROVE/REJECT/REJECT_TO_START/TERMINATE/TRANSFER/DELEGATE
     */
    private String operationType;

    /**
     * 审批意见
     */
    private String comment;

    /**
     * 操作时间
     */
    private LocalDateTime operTime;
}
