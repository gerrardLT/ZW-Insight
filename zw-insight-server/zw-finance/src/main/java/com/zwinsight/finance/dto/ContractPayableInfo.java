package com.zwinsight.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 合同可付信息（跨合同类型统一载体）
 * <p>
 * 用于付款申请的付款上限校验：可付上限 = 累计结算 + 净奖惩 - 累计已付。
 * 由 {@code ContractPayableMapper} 按合同类型从对应表读取，屏蔽表结构差异。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContractPayableInfo {

    /** 累计结算金额 */
    private BigDecimal cumulativeSettlement;

    /** 累计已付金额 */
    private BigDecimal cumulativePaid;
}
