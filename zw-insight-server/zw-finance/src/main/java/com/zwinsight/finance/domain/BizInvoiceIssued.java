package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销项发票台账（V2026_110，C3）——真实票据事实层。
 * <p>
 * 区别于 {@code biz_invoice_apply}（开票申请，审批口径累计开票的事实源）：
 * 本表登记实际开具的发票（数电/纸质），含蓝/红字与原票关联、查验与入账状态。
 * <b>不回写</b>合同 {@code cumulative_invoice_amount}（语义保持申请审批口径），
 * 两口径并列展示。红字必须挂原票且金额不超过原票可红冲余额。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_invoice_issued")
public class BizInvoiceIssued extends BaseEntity {

    /** 蓝字（正数发票） */
    public static final String DIRECTION_BLUE = "BLUE";
    /** 红字（冲红发票） */
    public static final String DIRECTION_RED = "RED";

    /** 查验状态：未查验 */
    public static final String VERIFY_UNVERIFIED = "UNVERIFIED";
    /** 查验状态：已查验（人工核对税务平台） */
    public static final String VERIFY_VERIFIED = "VERIFIED";
    /** 查验状态：查验不符 */
    public static final String VERIFY_MISMATCH = "MISMATCH";

    /** 入账状态：未入账 */
    public static final String BOOK_UNBOOKED = "UNBOOKED";
    /** 入账状态：已入账 */
    public static final String BOOK_BOOKED = "BOOKED";

    /** 项目ID */
    private Long projectId;

    /** 施工合同ID */
    private Long contractId;

    /** 来源开票申请ID（可空：手工登记的发票可无申请） */
    private Long applyId;

    /** 发票号码（数电20位或传统8位） */
    private String invoiceNumber;

    /** 发票代码（传统票10/12位；数电票为空） */
    private String invoiceCode;

    /** 开票日期（实际开具日，非申请日） */
    private LocalDate issueDate;

    /** 发票类型（如 SpecialBlue 增值税专用发票-蓝字） */
    private String invoiceType;

    /** 方向（BLUE/RED） */
    private String direction;

    /** 原票ID（红字必填） */
    private Long originalId;

    /** 价税合计（红字为负数） */
    private BigDecimal amount;

    /** 税率(%) */
    private BigDecimal taxRate;

    /** 税额（差额法派生；红字为负） */
    private BigDecimal taxAmount;

    /** 不含税金额（派生） */
    private BigDecimal amountWithoutTax;

    /** 查验状态（UNVERIFIED/VERIFIED/MISMATCH） */
    private String verifyStatus;

    /** 查验时间 */
    private LocalDateTime verifyTime;

    /** 查验人 */
    private Long verifiedBy;

    /** 入账状态（UNBOOKED/BOOKED） */
    private String bookkeepStatus;
}
