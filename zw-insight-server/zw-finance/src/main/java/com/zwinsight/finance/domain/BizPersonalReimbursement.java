package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 个人报销实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_personal_reimbursement")
public class BizPersonalReimbursement extends BaseEntity {

    /** 报销总金额 */
    private BigDecimal totalAmount;

    /** 报销日期（业务日期，用于封账校验） */
    private LocalDate reimbursementDate;

    /** 备注 */
    private String remark;

    /** 状态（DRAFT/APPROVED） */
    private String status;

    /** 流程实例ID */
    private String workflowInstanceId;
}
