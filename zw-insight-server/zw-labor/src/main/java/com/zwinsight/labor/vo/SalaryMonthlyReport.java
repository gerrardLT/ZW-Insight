package com.zwinsight.labor.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 薪资月度报表 VO
 */
@Data
public class SalaryMonthlyReport {

    /** 项目ID */
    private Long projectId;

    /** 统计月份 */
    private String month;

    /** 班组总数 */
    private Integer teamCount;

    /** 工人总数 */
    private Integer totalHeadCount;

    /** 应发总额 */
    private BigDecimal totalPayable;

    /** 扣款总额 */
    private BigDecimal totalDeduction;

    /** 实发总额 */
    private BigDecimal totalActual;

    /** 自有劳务小计 */
    private BigDecimal fixedSubtotal;

    /** 零星用工小计 */
    private BigDecimal temporarySubtotal;

    /** 环比变化率 */
    private BigDecimal momRate;

    /** 同比变化率 */
    private BigDecimal yoyRate;

    /** 班组汇总列表 */
    private List<TeamSalaryVO> teamSummaryList;

    /** 工人明细列表 */
    private List<SalaryDetailVO> detailList;
}
