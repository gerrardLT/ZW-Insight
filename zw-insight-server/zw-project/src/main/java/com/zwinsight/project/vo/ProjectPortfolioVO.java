package com.zwinsight.project.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 项目组合看板 VO（状态 × 金额分布）
 */
@Data
public class ProjectPortfolioVO {

    /** 项目总数 */
    private Integer totalProjectCount;

    /** 合同金额合计 */
    private BigDecimal totalContractAmount;

    /** 预算金额合计 */
    private BigDecimal totalBudgetAmount;

    /** 累计产值合计 */
    private BigDecimal totalCumulativeOutput;

    /** 按状态分布明细 */
    private List<StatusItem> statusList;

    /**
     * 状态分布条目
     */
    @Data
    public static class StatusItem {

        /** 项目状态 */
        private String status;

        /** 项目数量 */
        private Integer count;

        /** 合同金额合计 */
        private BigDecimal contractAmount;

        /** 预算金额合计 */
        private BigDecimal budgetAmount;

        /** 累计产值合计 */
        private BigDecimal cumulativeOutput;
    }
}
