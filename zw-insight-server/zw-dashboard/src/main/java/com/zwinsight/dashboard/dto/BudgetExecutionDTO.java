package com.zwinsight.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预算执行数据
 */
@Data
public class BudgetExecutionDTO {

    /** 预算总额 */
    private BigDecimal totalBudget;

    /** 已使用金额 */
    private BigDecimal usedAmount;

    /** 使用率（保留4位小数） */
    private BigDecimal usageRate;

    /** 各科目执行明细 */
    private List<SubjectDetailDTO> subjects;
}
