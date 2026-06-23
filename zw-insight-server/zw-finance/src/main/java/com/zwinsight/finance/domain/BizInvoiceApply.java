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

    /** 合同ID */
    private Long contractId;

    /** 发票类型（SPECIAL/NORMAL） */
    private String invoiceType;

    /** 开票金额 */
    private BigDecimal invoiceAmount;

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
