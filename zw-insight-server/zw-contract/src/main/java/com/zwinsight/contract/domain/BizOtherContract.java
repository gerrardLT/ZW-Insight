package com.zwinsight.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 其他合同实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_other_contract")
public class BizOtherContract extends BaseEntity {

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 合同名称
     */
    private String contractName;

    /**
     * 合同分类（OTHER_INCOME-其他收入/OTHER_EXPENSE-其他支出）
     */
    private String contractCategory;

    /**
     * 甲方名称
     */
    private String partyAName;

    /**
     * 乙方名称
     */
    private String partyBName;

    /**
     * 合同金额
     */
    private BigDecimal contractAmount;

    /**
     * 税率
     */
    private BigDecimal taxRate;

    /**
     * 不含税金额
     */
    private BigDecimal amountWithoutTax;

    /**
     * 税额
     */
    private BigDecimal taxAmount;

    /**
     * 签订日期
     */
    private LocalDate signingDate;

    /**
     * 付款条款
     */
    private String paymentTerms;

    /**
     * 合作内容
     */
    private String cooperationContent;

    /**
     * 累计开票
     */
    private BigDecimal cumulativeInvoice;

    /**
     * 累计收款
     */
    private BigDecimal cumulativeReceived;

    /**
     * 累计结算
     */
    private BigDecimal cumulativeSettlement;

    /**
     * 累计付款
     */
    private BigDecimal cumulativePaid;

    /**
     * 状态（DRAFT/EFFECTIVE）
     */
    private String status;
}
