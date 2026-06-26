package com.zwinsight.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 合同回款数据
 */
@Data
public class ContractReceiptDTO {

    /** 施工合同总额 */
    private BigDecimal contractTotal;

    /** 累计开票金额 */
    private BigDecimal invoicedAmount;

    /** 累计回款金额 */
    private BigDecimal receivedAmount;

    /** 回款率（保留4位小数） */
    private BigDecimal receiptRate;
}
