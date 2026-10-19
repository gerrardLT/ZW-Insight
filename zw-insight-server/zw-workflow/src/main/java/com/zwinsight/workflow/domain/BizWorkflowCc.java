package com.zwinsight.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审批抄送记录实体（V2026_117）
 * <p>发起人提交单据时可指定抄送人；抄送人在审批中心「抄送我的」页签查看。
 * 同一流程实例+同一被抄送人只插入一条（幂等由 Service 层 NOT EXISTS 保证）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_workflow_cc")
public class BizWorkflowCc extends BaseEntity {

    /** 流程实例ID */
    private String processInstanceId;

    /** 业务类型 */
    private String businessType;

    /** 业务单据ID */
    private Long businessId;

    /** 被抄送人用户ID */
    private Long ccUserId;

    /** 抄送发起人（流程发起人） */
    private Long ccBy;

    /** 已读状态（0-未读 1-已读） */
    private Integer readStatus;

    /** 阅读时间 */
    private LocalDateTime readAt;

    /** 备注 */
    private String remark;
}
