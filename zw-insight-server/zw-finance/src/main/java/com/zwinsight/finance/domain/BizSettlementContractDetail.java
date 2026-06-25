package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 结算合同明细实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_settlement_contract_detail")
public class BizSettlementContractDetail extends BaseEntity {

    /** 结算单ID */
    private Long settlementId;

    /** 合同类型(SUBCONTRACT/LABOR/MATERIAL/MACHINE/OTHER) */
    private String contractType;

    /** 合同ID */
    private Long contractId;

    /** 合同编号 */
    private String contractCode;

    /** 合同名称 */
    private String contractName;

    /** 合同金额 */
    private BigDecimal contractAmount;

    /** 已结算金额 */
    private BigDecimal settledAmount;

    /** 已付款金额 */
    private BigDecimal paidAmount;

    /** 未结金额 */
    private BigDecimal unsettledAmount;

    /** 合同结算状态 */
    private String settlementStatus;
}
