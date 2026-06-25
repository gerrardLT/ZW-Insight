package com.zwinsight.purchase.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购合同实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_purchase_contract")
public class BizPurchaseContract extends BaseEntity {

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 合同编号
     */
    private String contractCode;

    /**
     * 合同名称
     */
    private String contractName;

    /**
     * 甲方ID
     */
    private Long partyAId;

    /**
     * 甲方名称
     */
    private String partyAName;

    /**
     * 乙方ID（供应商）
     */
    private Long partyBId;

    /**
     * 乙方名称（供应商名称）
     */
    private String partyBName;

    /**
     * 供应商名称（前端展示别名）
     */
    private String supplierName;

    /**
     * 签订日期
     */
    private LocalDate signingDate;

    /**
     * 关联预算ID
     */
    private Long budgetId;

    /**
     * 合同金额
     */
    private BigDecimal contractAmount;

    /**
     * 付款条款
     */
    private String paymentTerms;

    /**
     * 累计入库金额
     */
    private BigDecimal cumulativeInbound;

    /**
     * 累计结算金额
     */
    private BigDecimal cumulativeSettlement;

    /**
     * 累计付款金额
     */
    private BigDecimal cumulativePaid;

    /**
     * 累计收票金额
     */
    private BigDecimal cumulativeInvoiceReceived;

    /**
     * 状态（DRAFT-草稿/EFFECTIVE-生效）
     */
    private String status;

    /**
     * 流程实例ID
     */
    private String workflowInstanceId;
}
