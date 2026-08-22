package com.zwinsight.finance.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 资金计划条目 VO（按月应付预测）
 */
@Data
public class FundPlanItemVO {

    /** 计划月份（格式：YYYY-MM） */
    private String month;

    /** 计划付款金额合计（已审批付款申请） */
    private BigDecimal plannedAmount;

    /** 付款申请笔数 */
    private Integer applyCount;
}
