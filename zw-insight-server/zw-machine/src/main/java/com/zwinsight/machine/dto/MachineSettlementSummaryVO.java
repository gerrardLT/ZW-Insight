package com.zwinsight.machine.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 机械结算项目费用总览
 */
@Data
public class MachineSettlementSummaryVO {

    /** 项目ID */
    private Long projectId;

    /** 累计结算总金额 */
    private BigDecimal totalSettledAmount;

    /** 累计已付款金额 */
    private BigDecimal totalPaidAmount;

    /** 未付款金额 = totalSettledAmount - totalPaidAmount */
    private BigDecimal unpaidAmount;

    /** 结算单数量 */
    private Integer settlementCount;
}
