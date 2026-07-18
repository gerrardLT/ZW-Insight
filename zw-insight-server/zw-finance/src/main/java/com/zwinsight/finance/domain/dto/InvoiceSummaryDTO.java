package com.zwinsight.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 发票明细汇总 DTO
 * <p>
 * 按项目维度统一汇总已开票（biz_invoice_apply）与已收票（biz_invoice_received）数据，
 * 含笔数、金额及税额（税额 = 金额 × 税率 / 100）。
 * </p>
 */
@Data
public class InvoiceSummaryDTO {

    /** 项目ID */
    private Long projectId;

    /** 项目名称 */
    private String projectName;

    /** 已开票笔数 */
    private Integer invoicedCount;

    /** 已开票金额合计 */
    private BigDecimal invoicedAmount;

    /** 已开票税额合计 */
    private BigDecimal invoicedTaxAmount;

    /** 已收票笔数 */
    private Integer receivedCount;

    /** 已收票金额合计 */
    private BigDecimal receivedAmount;

    /** 已收票税额合计 */
    private BigDecimal receivedTaxAmount;
}
