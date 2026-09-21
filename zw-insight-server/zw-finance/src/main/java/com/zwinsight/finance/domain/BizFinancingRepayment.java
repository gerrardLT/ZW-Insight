package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 融资还款计划实体（按期生成，还款按期核销）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_financing_repayment")
public class BizFinancingRepayment extends BaseEntity {

    /** 状态：待还 */
    public static final String STATUS_PENDING = "PENDING";
    /** 状态：部分还款 */
    public static final String STATUS_PARTIAL = "PARTIAL";
    /** 状态：已还清 */
    public static final String STATUS_PAID = "PAID";

    /** 融资台账ID */
    private Long financingId;

    /** 期数（从1开始） */
    private Integer periodNo;

    /** 应还日期 */
    private LocalDate dueDate;

    /** 应还本金 */
    private BigDecimal principalDue;

    /** 应还利息 */
    private BigDecimal interestDue;

    /** 实还本金 */
    private BigDecimal principalPaid;

    /** 实还利息 */
    private BigDecimal interestPaid;

    /** 实还日期 */
    private LocalDate paidDate;

    /** 状态（PENDING/PARTIAL/PAID） */
    private String status;
}
