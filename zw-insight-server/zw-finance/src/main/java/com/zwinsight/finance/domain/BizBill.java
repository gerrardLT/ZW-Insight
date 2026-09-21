package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 票据台账实体（应收/应付承兑汇票）
 * <p>应收票据（RECEIVABLE）：收到的承兑汇票，可持有到期兑现、背书转让、贴现变现；
 * 应付票据（PAYABLE）：开给供应商的承兑汇票，到期兑付。
 * 贴现利息 = 面值 × 贴现年利率 × 剩余天数 / 360（银行惯例单利）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_bill")
public class BizBill extends BaseEntity {

    /** 方向：应收票据 */
    public static final String DIRECTION_RECEIVABLE = "RECEIVABLE";
    /** 方向：应付票据 */
    public static final String DIRECTION_PAYABLE = "PAYABLE";

    /** 状态：持有 */
    public static final String STATUS_HELD = "HELD";
    /** 状态：已背书转让 */
    public static final String STATUS_ENDORSED = "ENDORSED";
    /** 状态：已贴现 */
    public static final String STATUS_DISCOUNTED = "DISCOUNTED";
    /** 状态：已到期兑现（应收） */
    public static final String STATUS_REDEEMED = "REDEEMED";
    /** 状态：已到期兑付（应付） */
    public static final String STATUS_PAID_OUT = "PAID_OUT";

    /** 关联项目ID */
    private Long projectId;

    /** 关联合同ID */
    private Long contractId;

    /** 票据号码 */
    private String billNo;

    /** 方向（RECEIVABLE-应收票据/PAYABLE-应付票据） */
    private String direction;

    /** 票据类型（BANK_ACCEPTANCE-银行承兑/COMMERCIAL_ACCEPTANCE-商业承兑） */
    private String billType;

    /** 票面金额 */
    private BigDecimal faceAmount;

    /** 出票日期 */
    private LocalDate issueDate;

    /** 到期日期 */
    private LocalDate dueDate;

    /** 出票人（应付票据=我方；应收票据=对方） */
    private String drawerName;

    /** 收款人（应收票据=我方；应付票据=对方） */
    private String payeeName;

    /** 承兑人（银行/企业） */
    private String acceptorName;

    /** 状态（HELD/ENDORSED/DISCOUNTED/REDEEMED/PAID_OUT） */
    private String status;

    /** 被背书人（背书转让对象） */
    private String endorseeName;

    /** 背书日期 */
    private LocalDate endorseDate;

    /** 贴现日期 */
    private LocalDate discountDate;

    /** 贴现年利率（小数，如0.048） */
    private BigDecimal discountRate;

    /** 贴现利息 = 面值×贴现率×剩余天数/360 */
    private BigDecimal discountInterest;

    /** 贴现净额（实收金额） */
    private BigDecimal discountNetAmount;

    /** 备注 */
    private String remark;
}
