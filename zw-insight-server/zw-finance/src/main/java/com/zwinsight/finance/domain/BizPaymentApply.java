package com.zwinsight.finance.domain;

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

    /** 付款日期 */
    private LocalDate paymentDate;

    /** 累计结算金额快照 */
    private BigDecimal cumulativeSettlementSnapshot;

    /** 未付金额快照 */
    private BigDecimal unpaidAmountSnapshot;

    /** 状态（DRAFT/APPROVED） */
    private String status;

    /** 流程实例ID */
    private String workflowInstanceId;
}
