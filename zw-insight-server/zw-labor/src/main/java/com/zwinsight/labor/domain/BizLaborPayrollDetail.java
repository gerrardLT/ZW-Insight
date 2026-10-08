package com.zwinsight.labor.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 劳务工资单包含的工单明细快照（LI-3）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_labor_payroll_detail")
public class BizLaborPayrollDetail extends BaseEntity {

    /** 工资单ID */
    private Long payrollId;

    /** 用工单ID */
    private Long workOrderId;

    /** 工人ID */
    private Long workerId;

    /** 工人姓名（快照） */
    private String workerName;

    /** 用工类型 */
    private String orderType;

    /** 工作日期 */
    private LocalDate workDate;

    /** 工单应发金额（快照） */
    private BigDecimal amount;
}
