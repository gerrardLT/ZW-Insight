package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 收票登记实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_invoice_received")
public class BizInvoiceReceived extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 合同ID */
    private Long contractId;

    /** 合同分类 */
    private String contractCategory;

    /** 供应商ID */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 发票金额 */
    private BigDecimal invoiceAmount;

    /** 税率(%)，引用税率字典或手动输入 */
    private BigDecimal taxRate;

    /** 收票日期（业务日期，用于封账校验） */
    private LocalDate invoiceDate;

    /** 状态（APPROVED） */
    private String status;
}
