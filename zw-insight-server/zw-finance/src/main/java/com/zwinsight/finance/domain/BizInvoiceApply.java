package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 开票申请实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_invoice_apply")
public class BizInvoiceApply extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 项目名称（冗余展示字段） */
    private String projectName;

    /** 合同ID */
    private Long contractId;

    /** 申请日期 */
    private String applyDate;

    /** 发票类型（SPECIAL/NORMAL） */
    private String invoiceType;

    /** 开票金额 */
    private BigDecimal invoiceAmount;

    /** 税率(%)，引用税率字典或手动输入 */
    private BigDecimal taxRate;

    /** 按含税金额派生，缺税率时不伪造零税额。 */
    public BigDecimal getAmountWithoutTax() {
        if (invoiceAmount == null || taxRate == null || taxRate.signum() < 0
                || taxRate.compareTo(BigDecimal.valueOf(100)) > 0) return null;
        return invoiceAmount.divide(BigDecimal.ONE.add(taxRate.movePointLeft(2)),
                2, java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal getTaxAmount() {
        BigDecimal net = getAmountWithoutTax();
        return net == null ? null : invoiceAmount.subtract(net);
    }

    /** 发票抬头 */
    private String invoiceTitle;

    /** 纳税人识别号 */
    private String taxpayerId;

    /** 银行账号 */
    private String bankAccount;

    /** 银行名称 */
    private String bankName;

    /** 合同金额快照 */
    private BigDecimal contractAmountSnapshot;

    /** 结算金额快照 */
    private BigDecimal settlementAmountSnapshot;

    /** 历史已开票金额快照 */
    private BigDecimal historicalInvoicedSnapshot;

    /** 状态（DRAFT/APPROVED） */
    private String status;

    /** 流程实例ID */
    private String workflowInstanceId;
}
