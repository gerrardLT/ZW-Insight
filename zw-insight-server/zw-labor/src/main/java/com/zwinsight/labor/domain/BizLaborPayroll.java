package com.zwinsight.labor.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 劳务工资单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_labor_payroll")
public class BizLaborPayroll extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 班组ID */
    private Long teamId;

    /** 周期开始日期 */
    private LocalDate periodStart;

    /** 周期结束日期 */
    private LocalDate periodEnd;

    /** 结算总额 */
    private BigDecimal totalSettlement;

    /** 已付总额 */
    private BigDecimal totalPaid;

    /** 未付金额 */
    private BigDecimal unpaid;

    /** 用工类型（FIXED-固定/TEMPORARY-临时） */
    private String orderType;

    /** 状态（DRAFT-草稿/APPROVED-已审批/SETTLED-已结算） */
    private String status;
}
