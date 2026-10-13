package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 农民工工资专户代发明细实体
 * <p>
 * 承接劳务域与财务专户的闭环：
 * 劳务工资单审批通过时自动生成待代发记录（PENDING），
 * 出纳在专户确认打款后扣减专户余额并置为已代发（PAID），
 * 杜绝无来源的手工代发，满足条例第31条可追溯要求。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_wage_payment_detail")
public class BizWagePaymentDetail extends BaseEntity {

    /** 待代发状态 */
    public static final String STATUS_PENDING = "PENDING";
    /** 已代发状态 */
    public static final String STATUS_PAID = "PAID";

    /** 项目ID */
    private Long projectId;

    /** 工资专户ID（确认代发时绑定） */
    private Long accountId;

    /** 来源劳务工资单ID */
    private Long payrollId;

    /** 班组ID */
    private Long teamId;

    /** 计薪周期开始 */
    private LocalDate periodStart;

    /** 计薪周期结束 */
    private LocalDate periodEnd;

    /** 应代发金额 */
    private BigDecimal amount;

    /** 代发状态（PENDING-待代发/PAID-已代发） */
    private String payStatus;

    /** 实际代发日期 */
    private LocalDate payDate;

    /** 备注 */
    private String remark;
}
