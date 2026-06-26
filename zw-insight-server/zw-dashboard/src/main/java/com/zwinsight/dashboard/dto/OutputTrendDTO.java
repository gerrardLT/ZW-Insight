package com.zwinsight.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 产值上报汇总数据
 */
@Data
public class OutputTrendDTO {

    /** 累计上报产值 */
    private BigDecimal totalOutput;

    /** 本月产值 */
    private BigDecimal monthOutput;

    /** 近12月产值趋势 */
    private List<MonthlyOutputDTO> trend;
}
