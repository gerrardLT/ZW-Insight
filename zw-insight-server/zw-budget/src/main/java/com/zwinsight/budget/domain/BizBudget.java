package com.zwinsight.budget.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 预算实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_budget")
public class BizBudget extends BaseEntity {

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 预算类型（ORIGINAL-原始预算/CHANGE-变更预算）
     */
    private String budgetType;

    /**
     * 变更序号（原始预算为0）
     */
    private Integer changeSeq;

    /**
     * 预算总金额
     */
    private BigDecimal totalAmount;

    /**
     * 状态（DRAFT-草稿/APPROVED-已审批）
     */
    private String status;

    /**
     * 流程实例ID
     */
    private String workflowInstanceId;
}
