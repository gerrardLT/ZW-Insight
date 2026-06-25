package com.zwinsight.labor.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 班组薪资汇总 VO
 */
@Data
public class TeamSalaryVO {

    /** 班组ID */
    private Long teamId;

    /** 班组名称 */
    private String teamName;

    /** 班组长姓名 */
    private String leaderName;

    /** 人数 */
    private Integer headCount;

    /** 应发总额 */
    private BigDecimal totalPayable;

    /** 扣款总额 */
    private BigDecimal totalDeduction;

    /** 实发总额 */
    private BigDecimal totalActual;

    /** 用工类型（FIXED-自有劳务/TEMPORARY-零星用工） */
    private String orderType;
}
