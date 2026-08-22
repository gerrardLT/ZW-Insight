package com.zwinsight.labor.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 劳务成本占比 VO
 */
@Data
public class LaborCostRatioVO {

    /** 项目ID */
    private Long projectId;

    /** 生效劳务合同金额合计 */
    private BigDecimal contractAmountTotal;

    /** 劳务结算总额（已审批/已结算工资单） */
    private BigDecimal settlementTotal;

    /** 劳务已付总额 */
    private BigDecimal paidTotal;

    /** 劳务未付总额 */
    private BigDecimal unpaidTotal;

    /** 成本占比（结算总额 / 劳务合同金额，合同金额为 0 时为 null） */
    private BigDecimal costRatio;

    /** 付款比例（已付总额 / 结算总额，结算总额为 0 时为 null） */
    private BigDecimal paymentRatio;
}
