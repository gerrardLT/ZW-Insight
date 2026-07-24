package com.zwinsight.budget.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 目标成本变更主表实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_budget_change")
public class BizBudgetChange extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 项目名称（冗余展示字段，不持久化） */
    @TableField(exist = false)
    private String projectName;

    /** 原预算编制ID */
    private Long budgetId;

    /** 变更单编号 */
    private String changeCode;

    /** 变更原因 */
    private String changeReason;

    /** 调整总额 */
    private BigDecimal totalAdjustAmount;

    /** 状态(DRAFT/SUBMITTED/APPROVED/REJECTED/WITHDRAWN) */
    private String status;

    /** 流程实例ID */
    private String workflowInstanceId;
}
