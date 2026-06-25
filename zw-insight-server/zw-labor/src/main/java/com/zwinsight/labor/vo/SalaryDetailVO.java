package com.zwinsight.labor.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 工人薪资明细 VO
 */
@Data
public class SalaryDetailVO {

    /** 工人ID */
    private Long workerId;

    /** 工人姓名 */
    private String workerName;

    /** 身份证后4位 */
    private String idCardLast4;

    /** 出勤天数 */
    private Integer attendanceDays;

    /** 加班工时 */
    private BigDecimal overtimeHours;

    /** 应发金额 */
    private BigDecimal payable;

    /** 扣款金额 */
    private BigDecimal deduction;

    /** 实发金额 */
    private BigDecimal actual;

    /** 用工类型（FIXED-自有劳务/TEMPORARY-零星用工） */
    private String orderType;
}
