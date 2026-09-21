package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 年度资金预算实体（资金计划三层之一：年度）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_fund_annual_budget")
public class BizFundAnnualBudget extends BaseEntity {

    /** 预算年度 */
    private Integer budgetYear;

    /** 项目ID（NULL为公司整体） */
    private Long projectId;

    /** 年度预计收款计划 */
    private BigDecimal incomePlan;

    /** 年度预计付款计划 */
    private BigDecimal expensePlan;

    /** 备注 */
    private String remark;

    /** 状态（DRAFT/SUBMITTED/APPROVED/REJECTED） */
    private String status;

    /** 流程实例ID */
    private String workflowInstanceId;
}
