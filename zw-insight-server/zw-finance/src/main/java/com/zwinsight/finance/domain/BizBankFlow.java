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
 * 银行流水实体（网银导出落库 + 与内部单据勾稽）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_bank_flow")
public class BizBankFlow extends BaseEntity {

    /** 方向：收入 */
    public static final String DIRECTION_IN = "IN";
    /** 方向：支出 */
    public static final String DIRECTION_OUT = "OUT";

    /** 匹配单据类型：付款申请 */
    public static final String MATCH_PAYMENT_APPLY = "PAYMENT_APPLY";
    /** 匹配单据类型：回款登记 */
    public static final String MATCH_PAYMENT_RECEIVED = "PAYMENT_RECEIVED";

    /** 银行账户ID */
    private Long accountId;

    /** 交易日期 */
    private LocalDate flowDate;

    /** 方向（IN-收入/OUT-支出） */
    private String direction;

    /** 交易金额 */
    private BigDecimal amount;

    /** 交易后余额 */
    private BigDecimal balanceAfter;

    /** 银行流水号（导入去重键） */
    private String transactionNo;

    /** 摘要/用途 */
    private String description;

    /** 对方单位 */
    private String counterpartyName;

    /** 是否已勾稽（0-未 1-已） */
    private Integer reconciled;

    /** 匹配单据类型（ALWAYS 策略：取消勾稽时需置空） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String matchedType;

    /** 匹配单据ID（ALWAYS 策略：取消勾稽时需置空） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long matchedId;

    /** 本次勾稽金额（NULL=按流水整笔金额勾稽，兼容存量；V2026_56 新增；ALWAYS 策略：取消勾稽时需置空） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal matchAmount;

    /** 来源（MANUAL-手工/IMPORT-文件导入） */
    private String source;
}
