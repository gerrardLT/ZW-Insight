package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 银行账户余额登记实体（日报头寸数据源）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_bank_balance")
public class BizBankBalance extends BaseEntity {

    /** 银行账户ID */
    private Long accountId;

    /** 余额日期 */
    private LocalDate snapshotDate;

    /** 账户余额 */
    private BigDecimal balance;

    /** 来源（MANUAL-手工录入/IMPORT-流水导入推算） */
    private String source;

    /** 备注 */
    private String remark;
}
