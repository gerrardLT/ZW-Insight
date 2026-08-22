package com.zwinsight.contract.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 产值完成率趋势条目 VO
 */
@Data
public class OutputTrendItemVO {

    /** 报告期间（如 2024-01） */
    private String period;

    /** 本期产值合计 */
    private BigDecimal monthlyOutput;

    /** 累计产值（各期本期产值滚动累加） */
    private BigDecimal cumulativeOutput;

    /** 产值完成率（累计产值 / 合同金额合计，合同金额为 0 时为 null） */
    private BigDecimal completionRate;
}
