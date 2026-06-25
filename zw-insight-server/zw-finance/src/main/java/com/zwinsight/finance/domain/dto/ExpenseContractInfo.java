package com.zwinsight.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支出合同信息 DTO
 * <p>
 * 用于结算单生成时跨表查询各类支出合同的基本信息。
 * </p>
 */
@Data
public class ExpenseContractInfo {

    /** 合同ID */
    private Long id;

    /** 合同编号 */
    private String contractCode;

    /** 合同名称 */
    private String contractName;

    /** 合同金额 */
    private BigDecimal contractAmount;

    /** 累计结算金额 */
    private BigDecimal cumulativeSettlement;

    /** 累计付款金额 */
    private BigDecimal cumulativePaid;
}
