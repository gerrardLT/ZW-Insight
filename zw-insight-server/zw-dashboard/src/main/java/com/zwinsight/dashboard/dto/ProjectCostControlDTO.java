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

    /**
     * 按 UI 原型 §7.2「七类成本结构」口径分组汇总
     * （材料/分包/人工/机械/措施/管理/商务，另附未能归类的 OTHER）。
     * <p>与 {@link #categorySummaries}（CBS 六类原始口径）<b>并存不互替</b>：
     * 前者回答“钱花到哪个业务类别”，后者回答“CBS 账户如何分类”。</p>
     */
    private List<DocCategorySummary> docCategories;

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
        /**
         * 预计超支（UI §7.1）= forecastTotal − baselineTotal，<b>正数 = 预计超出目标成本</b>。
         * <p>与 {@link #varianceAmount}（相对当前预算的节约/超支）口径不同：
         * 本字段以<b>目标成本（原始批准基准）</b>为分母基准，回答“比最初批的多了多少”。</p>
         */
        private BigDecimal forecastOverrun;
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
        /**
         * 偏差率（variance / current * 100，UI §7.2 要求逐类展示）。
         * <p>当前预算为 0 时为 <b>null</b>（无基准不可算率），不用 0 冒充“无偏差”。</p>
         */
        private BigDecimal varianceRate;
        /** 使用率（actual / current * 100） */
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
        /** 偏差率（variance / current * 100）；当前预算为 0 时 null */
        private BigDecimal varianceRate;
        /** 账户数量 */
        private Integer accountCount;
    }

    /**
     * UI §7.2 文档口径的成本类别汇总（七类）。
     * <p>归类依据写在 {@code basis} 里，<b>不隐藏映射规则</b>：</p>
     * <ul>
     *   <li>{@code CBS_CATEGORY}：由 cost_category 直接映射（材料/人工/机械/分包）</li>
     *   <li>{@code CBS_SUBCATEGORY_KEYWORD}：INDIRECT/OTHER 下按子类名关键词归入（措施/管理/商务）</li>
     *   <li>{@code UNCLASSIFIED}：未能归类（子类名为空或不在关键词表），如实单列不并入其他类</li>
     * </ul>
     */
    @Data
    public static class DocCategorySummary {
        /** 文档类别码（MATERIAL/SUBCONTRACT/LABOR/MACHINE/MEASURE/ADMIN/BUSINESS/OTHER） */
        private String code;
        /** 文档类别中文名 */
        private String name;
        /** 归类依据（见类注释） */
        private String basis;
        /** 预算（当前预算） */
        private BigDecimal current;
        /** 实际发生 */
        private BigDecimal actual;
        /** 预计最终（EAC） */
        private BigDecimal forecast;
        /** 偏差金额（current - forecast，正数=节约） */
        private BigDecimal variance;
        /** 偏差率（%）；预算为 0 时 null */
        private BigDecimal varianceRate;
        /** 风险等级（RED：预计超支 &gt;10%；YELLOW：预计超支 &gt;0；GREEN：未超支；INFO：无预算基准） */
        private String riskLevel;
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
