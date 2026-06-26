package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 项目报销实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_project_reimbursement")
public class BizProjectReimbursement extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 报销总金额 */
    private BigDecimal totalAmount;

    /** 报销日期（业务日期，用于封账校验） */
    private LocalDate reimbursementDate;

    /** 是否冲抵备用金（0-否 1-是） */
    private Integer offsetReserve;

    /** 备用金申请ID */
    private Long reserveApplyId;

    /** 冲抵金额 */
    private BigDecimal offsetAmount;

    /** 状态（DRAFT/APPROVED） */
    private String status;

    /** 流程实例ID */
    private String workflowInstanceId;
}
