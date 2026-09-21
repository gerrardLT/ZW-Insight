package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 月度资金计划实体（资金计划三层之二：月度，先计划后支付）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_fund_monthly_plan")
public class BizFundMonthlyPlan extends BaseEntity {

    /** 计划年度 */
    private Integer planYear;

    /** 计划月份（1-12） */
    private Integer planMonth;

    /** 项目ID（NULL为公司整体） */
    private Long projectId;

    /** 当月预计收款 */
    private BigDecimal incomePlan;

    /** 当月预计付款 */
    private BigDecimal expensePlan;

    /** 实际收款（月末统计回填） */
    private BigDecimal actualIncome;

    /** 实际付款（月末统计回填） */
    private BigDecimal actualExpense;

    /** 备注 */
    private String remark;

    /** 状态（DRAFT/APPROVED） */
    private String status;
}
