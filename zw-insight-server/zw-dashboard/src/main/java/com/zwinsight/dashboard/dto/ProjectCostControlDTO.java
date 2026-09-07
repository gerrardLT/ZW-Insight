package com.zwinsight.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 项目成本控制看板 DTO（Project Cost 360）
 * <p>
 * 聚合项目的 CBS 成本账户数据，提供 Baseline / Current / Commitment / Actual / Forecast / Variance
 * 六个维度的成本视图，支持按 WBS 和费用类别下钻。
 * </p>
 */
@Data
public class ProjectCostControlDTO {

    /** 项目 ID */
    private Long projectId;

    /** 项目名称 */
    private String projectName;

    /** WBS 节点数量 */
    private Integer wbsCount;

    /** 成本账户数量 */
    private Integer accountCount;

    /** 汇总指标 */
    private CostTotals totals;

    /** 成本账户明细列表 */
    private List<CostAccountSummary> accounts;

    /** 按费用类别分组汇总 */
    private List<CategorySummary> categorySummaries;

    /** 趋势数据（月度） */
    private List<MonthlyTrend> trends;

    /**
     * 成本汇总指标
     */
    @Data
    public static class CostTotals {
        /** 基准预算总额 */
        private BigDecimal baselineTotal;
        /** 当前预算总额 */
        private BigDecimal currentTotal;
        /** 已承诺总额 */
        private BigDecimal commitmentTotal;
        /** 实际成本总额 */
        private BigDecimal actualTotal;
        /** 预测完工总额（EAC） */
        private BigDecimal forecastTotal;
        /** 剩余预算（current - actual） */
        private BigDecimal remainingBudget;
        /** 偏差金额（current - forecast，正数表示节约） */
        private BigDecimal varianceAmount;
        /** 偏差率（variance / current * 100） */
        private BigDecimal varianceRate;
        /** 预算使用率（actual / current * 100） */
        private BigDecimal usageRate;
        /** 承诺率（commitment / current * 100） */
        private BigDecimal commitmentRate;
    }

    /**
     * 成本账户摘要
     */
    @Data
    public static class CostAccountSummary {
        /** 账户 ID */
        private Long accountId;
        /** 账户编码 */
        private String code;
        /** 账户名称 */
        private String name;
        /** 费用类别 */
        private String costCategory;
        /** 费用子类 */
        private String costSubcategory;
        /** WBS 编码 */
        private String wbsCode;
        /** WBS 名称 */
        private String wbsName;
        /** 基准预算 */
        private BigDecimal baseline;
        /** 当前预算 */
        private BigDecimal current;
        /** 已承诺 */
        private BigDecimal commitment;
        /** 实际成本 */
        private BigDecimal actual;
        /** 预测（EAC） */
        private BigDecimal forecast;
        /** 剩余预算 */
        private BigDecimal remaining;
        /** 偏差金额 */
        private BigDecimal variance;
        /** 使用率 */
        private BigDecimal usageRate;
        /** 状态 */
        private String status;
    }

    /**
     * 按费用类别分组汇总
     */
    @Data
    public static class CategorySummary {
        /** 费用类别 */
        private String costCategory;
        /** 类别名称（中文） */
        private String categoryName;
        /** 基准预算 */
        private BigDecimal baseline;
        /** 当前预算 */
        private BigDecimal current;
        /** 已承诺 */
        private BigDecimal commitment;
        /** 实际成本 */
        private BigDecimal actual;
        /** 预测 */
        private BigDecimal forecast;
        /** 偏差 */
        private BigDecimal variance;
        /** 账户数量 */
        private Integer accountCount;
    }

    /**
     * 月度趋势数据
     */
    @Data
    public static class MonthlyTrend {
        /** 月份（YYYY-MM） */
        private String month;
        /** 当月实际成本 */
        private BigDecimal monthlyActual;
        /** 累计实际成本 */
        private BigDecimal cumulativeActual;
        /** 累计承诺 */
        private BigDecimal cumulativeCommitment;
        /** 累计预测 */
        private BigDecimal cumulativeForecast;
    }
}
