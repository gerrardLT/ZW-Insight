package com.zwinsight.labor.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 薪资统计汇总 VO
 */
@Data
public class SalaryStatsSummary {

    /** 项目ID */
    private Long projectId;

    /** 统计月份（格式：YYYY-MM） */
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

    /** 自有劳务应发总额 */
    private BigDecimal fixedPayable;

    /** 零星用工应发总额 */
    private BigDecimal temporaryPayable;

    /** 班组明细列表 */
    private List<TeamSalaryVO> teamList;
}
