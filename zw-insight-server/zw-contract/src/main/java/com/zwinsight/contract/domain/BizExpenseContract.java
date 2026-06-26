package com.zwinsight.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 通用支出合同实体（采购/劳务/机械/分包/其它收支）
 * <p>
 * 该实体映射 biz_expense_contract 表，作为多种合同类型的统一存储。
 * 合同到期扫描（功能5）和档案展示（功能8）依赖此实体的 endDate 和 contractName 字段。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_expense_contract")
public class BizExpenseContract extends BaseEntity {

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
     * 合同分类(MATERIAL/LABOR/MACHINE/SUBCONTRACT/OTHER_INCOME/OTHER_EXPENSE)
     */
    private String contractCategory;

    /**
     * 甲方ID
     */
    private Long partyAId;

    /**
     * 甲方名称
     */
    private String partyAName;

    /**
     * 乙方ID(供应商)
     */
    private Long partyBId;

    /**
     * 乙方名称(供应商名称)
     */
    private String partyBName;

    /**
     * 签订日期
     */
    private LocalDate signingDate;

    /**
     * 合同到期日期
     */
    private LocalDate endDate;

    /**
     * 关联预算ID
     */
    private Long budgetId;

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
     * 税金
     */
    private BigDecimal taxAmount;

    /**
     * 付款条件
     */
    private String paymentTerms;

    /**
     * 合作内容
     */
    private String cooperationContent;

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
     * 合同负责人ID
     */
    private Long responsibleUserId;

    /**
     * 状态(DRAFT/EFFECTIVE/SETTLED/CLOSED/TERMINATED)
     */
    private String status;

    /**
     * 流程实例ID
     */
    private String workflowInstanceId;
}
