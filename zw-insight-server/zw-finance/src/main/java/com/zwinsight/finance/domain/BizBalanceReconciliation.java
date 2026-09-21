package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 银行存款余额调节表实体（银行对账单 vs 企业账面，未达账项双向调节）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_balance_reconciliation")
public class BizBalanceReconciliation extends BaseEntity {

    /** 银行账户ID */
    private Long accountId;

    /** 调节基准日 */
    private LocalDate reconciliationDate;

    /** 银行对账单余额 */
    private BigDecimal bankStatementBalance;

    /** 企业账面余额 */
    private BigDecimal bookBalance;

    /** 企业已收银行未收（金额） */
    private BigDecimal enterpriseDepositBankNot;

    /** 企业已付银行未付（金额） */
    private BigDecimal enterprisePaymentBankNot;

    /** 银行收企业未收（金额） */
    private BigDecimal bankDepositEnterpriseNot;

    /** 银行付企业未付（金额） */
    private BigDecimal bankPaymentEnterpriseNot;

    /** 调节后银行余额（=银行对账单 + 银行收企业未收 - 银行付企业未付） */
    @TableField(exist = false) // 非表字段，由计算得出
    private BigDecimal adjustedBankBalance;

    /** 调节后账面余额（=账面 + 企业已收银行未收 - 企业已付银行未付） */
    @TableField(exist = false) // 非表字段，由计算得出
    private BigDecimal adjustedBookBalance;

    /** 是否调平（两调节后余额相等，1-是 0-否） */
    private Integer balanced;

    /** 备注 */
    private String remark;
}
