package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应付台账实体（V2026_115，对称 {@link BizReceivable}）
 * <p>生成：五类支出合同结算审批通过时按<b>本次结算金额</b>生成（单据事实，
 * 由 ExpenseSettlementPayableEvent 驱动）；合同历史余额（结算−已付）由迁移
 * BACKFILL 行一次性覆盖，两者按来源类型区分不重复计量。</p>
 * <p>核销：付款申请审批生效时按到期日 FIFO 冲减同合同 OPEN 余额；
 * OPEN 余额 = payable_amount − paid_amount。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_payable")
public class BizPayable extends BaseEntity {

    /** 来源类型：分包结算 */
    public static final String SOURCE_SUBCONTRACT_SETTLEMENT = "SUBCONTRACT_SETTLEMENT";
    /** 来源类型：采购结算 */
    public static final String SOURCE_PURCHASE_SETTLEMENT = "PURCHASE_SETTLEMENT";
    /** 来源类型：劳务结算 */
    public static final String SOURCE_LABOR_SETTLEMENT = "LABOR_SETTLEMENT";
    /** 来源类型：机械结算 */
    public static final String SOURCE_MACHINE_SETTLEMENT = "MACHINE_SETTLEMENT";
    /** 来源类型：迁移回填（合同粒度历史余额，sourceId=合同ID） */
    public static final String SOURCE_BACKFILL = "BACKFILL";

    /** 状态：未结清 */
    public static final String STATUS_OPEN = "OPEN";
    /** 状态：已结清 */
    public static final String STATUS_CLOSED = "CLOSED";

    /** 项目ID */
    private Long projectId;

    /** 支出合同ID（五类合同之一） */
    private Long contractId;

    /** 合同类别（SUBCONTRACT/PURCHASE/LABOR/MACHINE/OTHER，与付款申请 contractCategory 同码表） */
    private String contractCategory;

    /** 供应商/分包方名称（冗余展示） */
    private String supplierName;

    /** 来源类型（结算类型/BACKFILL） */
    private String sourceType;

    /** 来源单据ID（结算单ID；BACKFILL 为合同ID） */
    private Long sourceId;

    /** 应付金额（结算金额） */
    private BigDecimal payableAmount;

    /** 已核销金额（付款生效时 FIFO 冲减） */
    private BigDecimal paidAmount;

    /** 约定付款到期日 */
    private LocalDate dueDate;

    /** 状态（OPEN-未结清/CLOSED-已结清） */
    private String status;

    /** 备注 */
    private String remark;

    /** 项目名称（冗余展示字段，不持久化） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String projectName;
}
