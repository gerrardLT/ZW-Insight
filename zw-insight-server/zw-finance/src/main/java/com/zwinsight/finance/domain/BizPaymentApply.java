package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 付款申请实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_payment_apply")
public class BizPaymentApply extends BaseEntity {

    /** 支付状态：未支付（审批通过后的默认态） */
    public static final String PAY_STATUS_UNPAID = "UNPAID";
    /** 支付状态：已支付（银行流水勾稽足额后回写） */
    public static final String PAY_STATUS_PAID = "PAID";

    /** 项目ID */
    private Long projectId;

    /** 项目名称（冗余展示字段，不持久化） */
    @TableField(exist = false)
    private String projectName;

    /** 合同ID */
    private Long contractId;

    /** 合同分类 */
    private String contractCategory;

    /** 供应商ID */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 付款金额 */
    private BigDecimal paymentAmount;

    /** 支出分类科目编码（biz_fund_category.code；53_V2026_51 新增） */
    private String paymentCategory;

    /** 关联月度资金计划ID（先计划后支付；53_V2026_51 新增） */
    private Long fundPlanId;

    /** 付款日期 */
    private LocalDate paymentDate;

    /** 累计结算金额快照 */
    private BigDecimal cumulativeSettlementSnapshot;

    /** 未付金额快照 */
    private BigDecimal unpaidAmountSnapshot;

    /** 状态（DRAFT/SUBMITTED/APPROVED/REJECTED） */
    private String status;

    /** 支付状态（UNPAID/PAID；现金口径，与审批状态正交；V2026_56 新增） */
    private String payStatus;

    /** 实际支付日期（银行流水勾稽回写；V2026_56 新增；ALWAYS 策略：撤销支付时需置空） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate payDate;

    /** 支付账户ID（biz_bank_account.id，流水勾稽回写；V2026_56 新增；ALWAYS 策略：撤销支付时需置空） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long payAccountId;

    /** 流程实例ID */
    private String workflowInstanceId;
}
