package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 月度经营分析表实体（V2026_67，资金流转流程 §9「每月必须形成一个项目经营表」）
 * <p>粒度 = 项目 × 月份 × 费用类别（§9 十类），六列：预算 / 本月发生 / 累计发生 /
 * 累计支付 / 应付未付 / 预计最终。由 {@code MonthlyAnalysisTask} 每月 1 日 04:00 生成上月，
 * 也可经 {@code MonthlyAnalysisController} 手工生成（同月重跑覆盖更新，幂等）。</p>
 * <p><b>两处如实为 NULL 的口径（不用 0 或估算值填充）</b>：</p>
 * <ul>
 *   <li>{@code currentMonthOccurred}：= 本月末累计 − 上月末累计（取本表上月行）。
 *       首次生成无上月行时为 null，{@code occurredBasis=NO_BASELINE}。
 *       不用单据聚合的原因见迁移脚本头注释（CBS 流水表 0 行、多数结算单无 settlement_date）。</li>
 *   <li>{@code cumulativePaid} / {@code payableOutstanding}：仅直接费四类有权威来源
 *       （对应合同的 cumulative_paid，付款审批回写口径）；间接费六类的支出走报销与其他费用付款，
 *       无法按十类细分归集，故为 null，{@code paidBasis=NO_PAYMENT_SOURCE}。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_monthly_operation_analysis")
public class BizMonthlyOperationAnalysis extends BaseEntity {

    // ==================== §9 十类费用类别码（固定顺序即表格行序）====================
    public static final String CAT_LABOR = "LABOR";
    public static final String CAT_MATERIAL = "MATERIAL";
    public static final String CAT_MACHINE = "MACHINE";
    public static final String CAT_SUBCONTRACT = "SUBCONTRACT";
    public static final String CAT_MEASURE = "MEASURE";
    public static final String CAT_ADMIN = "ADMIN";
    public static final String CAT_ENTERTAIN = "ENTERTAIN";
    public static final String CAT_TRAVEL_VEHICLE = "TRAVEL_VEHICLE";
    public static final String CAT_PROFESSIONAL = "PROFESSIONAL";
    public static final String CAT_TAX = "TAX";
    /** 未归类：CBS 账户子类名不在关键词表内。单列不并入他类，否则合计与 CBS 总额对不上 */
    public static final String CAT_UNCLASSIFIED = "UNCLASSIFIED";

    // ==================== occurred_basis 值域 ====================
    /** 本月发生 = 本月末累计 − 上月末累计（有上月行） */
    public static final String OCCURRED_VS_LAST_MONTH = "VS_LAST_MONTH";
    /** 首次生成，无上月行 → 本月发生为 null */
    public static final String OCCURRED_NO_BASELINE = "NO_BASELINE";
    /** 该类无 CBS 账户（金额全 0，非数据缺失掩盖） */
    public static final String OCCURRED_NO_DATA_SOURCE = "NO_DATA_SOURCE";

    // ==================== paid_basis 值域 ====================
    /** 累计支付取合同 cumulative_paid（付款申请审批通过回写，审批口径非现金口径） */
    public static final String PAID_APPROVAL_WRITEBACK = "APPROVAL_WRITEBACK";
    /** 无按十类细分的付款数据源 → 累计支付与应付未付均为 null */
    public static final String PAID_NO_PAYMENT_SOURCE = "NO_PAYMENT_SOURCE";

    /** 项目ID */
    private Long projectId;

    /** 分析月份 yyyy-MM */
    private String analysisMonth;

    /** 费用类别码（见 CAT_* 常量） */
    private String categoryCode;

    /** 类别中文名（人工/材料/机械/分包/措施/管理/招待/差旅车辆/专业服务/财税/未归类） */
    private String categoryName;

    /** 预算（CBS baseline_amount 按类聚合，即目标成本口径） */
    private BigDecimal budgetAmount;

    /** 本月发生（本月末累计 − 上月末累计）；首次生成为 null */
    private BigDecimal currentMonthOccurred;

    /** 累计发生（CBS actual_amount 按类聚合） */
    private BigDecimal cumulativeOccurred;

    /** 累计支付（直接费=合同 cumulative_paid 审批口径；间接费无数据源时 null） */
    private BigDecimal cumulativePaid;

    /** 应付未付（累计发生 − 累计支付）；累计支付为 null 时同为 null */
    private BigDecimal payableOutstanding;

    /** 预计最终（CBS forecast_amount 按类聚合，EAC 口径） */
    private BigDecimal forecastFinal;

    /** 本月发生口径（OCCURRED_* 常量） */
    private String occurredBasis;

    /** 累计支付口径（PAID_* 常量） */
    private String paidBasis;

    /** 归入本类的 CBS 成本账户数 */
    private Integer accountCount;

    /** 本次生成时间（重跑覆盖时更新） */
    private LocalDateTime generatedAt;
}
