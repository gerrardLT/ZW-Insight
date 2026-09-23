package com.zwinsight.dashboard.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 预计利润月度快照实体（V2026_59）
 * <p>
 * 驾驶舱「预计利润」权威口径：forecast_profit = contract_income − forecast_total_cost。
 * 收入/成本口径由 income_basis / cost_basis 如实标记（不静默混用）：
 * 收入优先取生效施工合同合计（CONSTRUCTION_CONTRACT），无施工合同回退项目合同额
 * （PROJECT_CONTRACT_AMOUNT）；成本优先取 CBS 根账户完工预测合计（CBS_FORECAST），
 * 无成本账户回退已实现支出（FALLBACK_TOTAL_EXPENSE，此时"预计"退化为"已实现"，
 * 报表须按 cost_basis 区分展示）。
 * </p>
 * <p>唯一性：snapshot_month + project_id（公司级 project_id=NULL）应用层 upsert 保证，
 * 同月重复快照覆盖更新（MySQL 唯一索引对 NULL 不去重，不依赖库级约束）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_profit_snapshot")
public class BizProfitSnapshot extends BaseEntity {

    /** 收入口径：生效施工合同金额合计 */
    public static final String BASIS_CONSTRUCTION_CONTRACT = "CONSTRUCTION_CONTRACT";
    /** 收入口径：回退项目合同额 */
    public static final String BASIS_PROJECT_CONTRACT_AMOUNT = "PROJECT_CONTRACT_AMOUNT";
    /** 成本口径：CBS 成本账户完工预测（EAC）合计 */
    public static final String BASIS_CBS_FORECAST = "CBS_FORECAST";
    /** 成本口径：回退已实现支出（无成本账户） */
    public static final String BASIS_FALLBACK_EXPENSE = "FALLBACK_TOTAL_EXPENSE";

    /** 快照月份（yyyy-MM） */
    private String snapshotMonth;

    /** 项目ID（NULL=公司级聚合） */
    private Long projectId;

    /** 合同收入（预计利润计算基数） */
    private BigDecimal contractIncome;

    /** 收入口径标记 */
    private String incomeBasis;

    /** 累计产值（快照时点） */
    private BigDecimal cumulativeOutput;

    /** 已发生成本 */
    private BigDecimal actualCost;

    /** 预计最终总成本（EAC） */
    private BigDecimal forecastTotalCost;

    /** 成本口径标记 */
    private String costBasis;

    /** 预计利润 = 合同收入 − 预计最终总成本 */
    private BigDecimal forecastProfit;

    /** 较上期快照利润变化（归因基数） */
    private BigDecimal profitDelta;

    /** 成本类别分解 JSON（{category: {baseline,current,actual,forecast}}） */
    private String categoryBreakdown;

    /** 快照生成日期 */
    private LocalDate snapshotDate;
}
