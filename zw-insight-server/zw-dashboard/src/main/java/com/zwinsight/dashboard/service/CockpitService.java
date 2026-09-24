package com.zwinsight.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.dashboard.domain.BizProfitSnapshot;
import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.mapper.BizRiskRegisterMapper;
import com.zwinsight.finance.domain.BizFundRollingForecast;
import com.zwinsight.finance.mapper.BizBankFlowMapper;
import com.zwinsight.finance.mapper.BizFundRollingForecastMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.ContractPayableMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 经营驾驶舱聚合服务（V2026_59，驾驶舱 V1 五页面的公司级数据源）
 * <p>
 * 口径立场：所有指标来自真实单据/台账/快照聚合，无 mock、无均摊模拟；
 * 预计利润 = 合同收入 − CBS 完工预测总成本（{@link ProfitSnapshotService} 权威口径）；
 * 已实现利润（realizedProfit）= total_income − total_expense（历史收付口径），
 * 两者并存且分别命名，禁止混用。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CockpitService {

    private final BizProjectMapper projectMapper;
    private final BizRiskRegisterMapper riskMapper;
    private final BizFundRollingForecastMapper rollingForecastMapper;
    private final BizBankFlowMapper bankFlowMapper;
    private final BizPaymentApplyMapper paymentApplyMapper;
    private final ContractPayableMapper contractPayableMapper;
    private final com.zwinsight.contract.mapper.BizConstructionContractMapper constructionContractMapper;
    private final com.zwinsight.finance.mapper.BizPaymentReceivedMapper paymentReceivedMapper;
    private final com.zwinsight.dashboard.mapper.BizProfitSnapshotMapper snapshotMapper;
    private final ProfitSnapshotService profitSnapshotService;
    private final DashboardService dashboardService;

    /**
     * 目标利润率（UI §4.1“目标 18.0%”的可配置实现）。
     * <p>旧实现把“目标 ≥ 5%”<b>硬编码在前端文案</b>里，既不可配置也与后端判级无关；
     * 现改为后端配置项并随 overview 下发，前端仅渲染（与风险规则阈值同一管理模式）。</p>
     */
    @org.springframework.beans.factory.annotation.Value("${cockpit.target-profit-rate:0.08}")
    private BigDecimal targetProfitRate;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * 经营总览（驾驶舱首页指标卡，§4 / 资金流转 §12）：
     * 经营结果 4 卡（合同收入/预计总成本/预计利润/利润率）
     * + 资金状态（累计回款/累计支付/应收未收/<b>应付未付</b>/90天资金缺口）+ 账户资金。
     * <p><b>应付未付 vs 已批未付（两者并列，语义不同不可互替）</b>：
     * 前者 = 已确认付款义务（Σ合同 cumulative_settlement − cumulative_paid），
     * 后者 = 已进入付款流程但银行未划款（status=APPROVED 且 pay_status≠PAID 的剩余未付额）。
     * 只用后者会漏掉“已结算但尚未提交付款申请”的义务（UI §9.1 要求的是前者）。</p>
     * <p><b>90 天资金缺口（资金流转 §10.4）</b>：未来预计支付 − 可用资金，<b>正数=缺钱</b>。
     * 可用资金 = 账户余额快照合计 + 窗口内预计回款。旧实现只累加正净缺口、
     * 从不减可用资金，会把“账面能覆盖的缺口”误报为重大风险（2026-09-24 审计修正）。</p>
     */
    public Map<String, Object> getOverview() {
        return getOverview(null, null);
    }

    /**
     * 经营总览 + 全局筛选（UI §14 的公司/项目维度）。
     * <p><b>筛选口径纪律</b>：项目级指标（合同收入/预计总成本/预计利润/利润率/累计回款/
     * 累计支付/应收未收/应付未付/已批未付）严格按筛选范围聚合；资金缺口改读<b>项目级</b>
     * 滚动预测快照（FundForecastTask 逐项目生成）。但<b>账户余额无法按项目/公司拆分</b>
     * （银行账户属公司级资金池），故有筛选时 availableFund 仅含窗口内预计回款，
     * 缺口偏保守（可能高估资金压力），并在 {@code gapBasis} 中显式标明，
     * <b>不得把公司级余额冒充项目级可用资金</b>。</p>
     * <p>UI §14 另要求「区域」「项目经理」两个筛选项：<b>当前无数据源</b>
     * （biz_project 无 region / project_manager 列，全仓亦无项目经理字段），
     * 故不提供该筛选，避免做出选了不生效的假下拉。</p>
     *
     * @param ownerCompanyId 所属公司ID（空=不限）
     * @param projectId      项目ID（空=不限；与 ownerCompanyId 同时传时取交集）
     */
    public Map<String, Object> getOverview(Long ownerCompanyId, Long projectId) {
        boolean scoped = ownerCompanyId != null || projectId != null;
        // 筛选范围 → 项目集合（公司筛选取其下全部项目，项目筛选取单项目）
        List<BizProject> projects = projectMapper.selectList(new LambdaQueryWrapper<BizProject>()
                .eq(ownerCompanyId != null, BizProject::getOwnerCompanyId, ownerCompanyId)
                .eq(projectId != null, BizProject::getId, projectId));
        Set<Long> scopeIds = new java.util.HashSet<>();
        for (BizProject p : projects) {
            scopeIds.add(p.getId());
        }

        // 实时逐项目计算预计利润（不依赖快照是否已生成，口径同源）
        List<Map<String, Object>> forecasts = profitSnapshotService.listProjectForecasts();
        BigDecimal contractIncome = BigDecimal.ZERO;
        BigDecimal forecastCost = BigDecimal.ZERO;
        BigDecimal forecastProfit = BigDecimal.ZERO;
        for (Map<String, Object> row : forecasts) {
            if (scoped && !scopeIds.contains((Long) row.get("projectId"))) {
                continue;
            }
            contractIncome = contractIncome.add((BigDecimal) row.get("contractIncome"));
            forecastCost = forecastCost.add((BigDecimal) row.get("forecastTotalCost"));
            forecastProfit = forecastProfit.add((BigDecimal) row.get("forecastProfit"));
        }

        // 资金状态：项目回写字段聚合（total_income/total_expense 为审批回写口径的权威账）
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal realizedProfit = BigDecimal.ZERO;
        for (BizProject p : projects) {
            received = received.add(nvl(p.getTotalIncome()));
            paid = paid.add(nvl(p.getTotalExpense()));
            receivable = receivable.add(nvl(p.getReceivableAmount()));
            realizedProfit = realizedProfit.add(nvl(p.getTotalIncome())).subtract(nvl(p.getTotalExpense()));
        }

        // 90 天资金缺口（含构成明细，不隐藏口径）；有筛选时读项目级快照
        Map<String, BigDecimal> gap = computeFundGap90Days(scoped ? scopeIds : null);

        Map<String, Object> result = new HashMap<>();
        result.put("contractIncome", contractIncome);
        result.put("forecastTotalCost", forecastCost);
        result.put("forecastProfit", forecastProfit);
        result.put("forecastProfitRate", contractIncome.signum() > 0
                ? forecastProfit.divide(contractIncome, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        result.put("realizedProfit", realizedProfit);
        result.put("cumulativeReceived", received);
        result.put("cumulativePaid", paid);
        result.put("receivableOutstanding", receivable);
        // 资金缺口（§10.4 口径）及其构成（账户余额/预计回款/可用资金/预计支付）
        result.put("gap90Days", gap.get("gap"));
        result.put("gap90DaysDetail", gap);
        result.put("accountBalance", gap.get("accountBalance"));
        result.put("availableFund", gap.get("availableFund"));
        // 口径标识：前端必须据此告知“账户余额未计入”，不得静默展示偏保守的缺口
        result.put("gapBasis", scoped
                ? "PROJECT_SNAPSHOT_WITHOUT_ACCOUNT_BALANCE"
                : "COMPANY_SNAPSHOT_WITH_ACCOUNT_BALANCE");
        // 应付未付（已确认义务）与已批未付（已进入付款流程）并列，语义不同不可互替
        result.put("payableOutstanding", sumPayableScoped(scoped, scopeIds, projectId));
        result.put("approvedUnpaid", sumApprovedUnpaidScoped(scoped, scopeIds, projectId));
        // 资金流转 §12：本月现金需求 / 三个月资金需求
        result.put("currentMonthCashNeed", gap.get("currentMonthPayments"));
        result.put("threeMonthCashNeed", gap.get("expectedPayments"));
        // UI §4 数字卡四要素之二（与上期变化）与之三（目标/预算）
        result.put("changes", computeChanges(contractIncome, forecastCost, forecastProfit));
        Map<String, Object> targets = new HashMap<>();
        targets.put("forecastProfitRate", targetProfitRate);
        targets.put("basis", "CONFIG:cockpit.target-profit-rate");
        result.put("targets", targets);
        // 筛选元信息（前端据此展示“当前统计 N 个项目”与口径提示）
        Map<String, Object> scope = new HashMap<>();
        scope.put("ownerCompanyId", ownerCompanyId);
        scope.put("projectId", projectId);
        scope.put("projectCount", projects.size());
        scope.put("filtered", scoped);
        result.put("scope", scope);
        return result;
    }

    /**
     * 应付未付（按筛选范围）。
     * <p>无筛选走全量口径；指定项目走单项目口径；按公司筛选走项目集合口径
     * （集合为空 = 该公司下无项目，应付自然为 0，<b>不得回退到全量</b>）。</p>
     */
    private BigDecimal sumPayableScoped(boolean scoped, Set<Long> scopeIds, Long projectId) {
        if (!scoped) {
            return nvl(contractPayableMapper.sumPayableOutstanding(null));
        }
        if (projectId != null) {
            return nvl(contractPayableMapper.sumPayableOutstanding(projectId));
        }
        if (scopeIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return nvl(contractPayableMapper.sumPayableOutstandingByProjects(scopeIds));
    }

    /** 已批未付（按筛选范围），分支逻辑同 {@link #sumPayableScoped}。 */
    private BigDecimal sumApprovedUnpaidScoped(boolean scoped, Set<Long> scopeIds, Long projectId) {
        if (!scoped) {
            return nvl(paymentApplyMapper.sumApprovedUnpaidRemaining(null));
        }
        if (projectId != null) {
            return nvl(paymentApplyMapper.sumApprovedUnpaidRemaining(projectId));
        }
        if (scopeIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return nvl(paymentApplyMapper.sumApprovedUnpaidRemainingByProjects(scopeIds));
    }

    /**
     * 全局筛选器可选项（UI §14）：所属公司清单 + 项目清单。
     * <p>数据源为真实项目表的 distinct 值，<b>不造虚拟公司</b>；未登记所属公司的项目
     * 归入“未分配公司”且不可作为筛选项（否则筛不到任何数据）。
     * 区域/项目经理无数据源，故不返回该维度。</p>
     */
    public Map<String, Object> getFilterOptions() {
        // 不用 .select(...) 限定列：该重载会在调用时立即解析 lambda 列名（依赖 MyBatis-Plus
        // 的 TableInfo 缓存），而纯 Mockito 单测未初始化该缓存会直接抛
        // MybatisPlusException: can not find lambda cache。项目表量级有限，全列查询代价可接受。
        List<BizProject> projects = projectMapper.selectList(new LambdaQueryWrapper<BizProject>());
        Map<Long, String> companies = new java.util.LinkedHashMap<>();
        List<Map<String, Object>> projectOptions = new ArrayList<>();
        for (BizProject p : projects) {
            if (p.getOwnerCompanyId() != null) {
                companies.putIfAbsent(p.getOwnerCompanyId(),
                        p.getOwnerCompanyName() != null ? p.getOwnerCompanyName()
                                : "公司" + p.getOwnerCompanyId());
            }
            Map<String, Object> opt = new HashMap<>();
            opt.put("projectId", p.getId());
            opt.put("projectName", p.getProjectName());
            opt.put("projectCode", p.getProjectCode());
            opt.put("ownerCompanyId", p.getOwnerCompanyId());
            projectOptions.add(opt);
        }
        List<Map<String, Object>> companyOptions = new ArrayList<>();
        companies.forEach((id, name) -> {
            Map<String, Object> opt = new HashMap<>();
            opt.put("companyId", id);
            opt.put("companyName", name);
            companyOptions.add(opt);
        });

        Map<String, Object> result = new HashMap<>();
        result.put("companies", companyOptions);
        result.put("projects", projectOptions);
        // 快捷筛选可选项（与 applyQuickFilter 的合法值同源，前端不写死）
        result.put("quickFilters", List.of(
                Map.of("code", "ALL", "label", "全部项目"),
                Map.of("code", "HIGH_RISK", "label", "高风险"),
                Map.of("code", "LOSS", "label", "亏损"),
                Map.of("code", "FUND_TIGHT", "label", "资金紧张"),
                Map.of("code", "PROFIT_DOWN", "label", "利润下降"),
                Map.of("code", "MONTH_ABNORMAL", "label", "本月异常")));
        // 无数据源的维度如实告知（前端据此置灰并说明原因，不做假下拉）
        result.put("unsupportedDimensions", List.of(
                Map.of("code", "REGION", "reason", "biz_project 无区域列，仅有自由文本 project_address"),
                Map.of("code", "PROJECT_MANAGER", "reason", "全仓无项目经理字段（无 project_manager/manager_id 列）")));
        return result;
    }

    /**
     * 指标环比（UI §4 要求每张卡呈现“与上期变化”）。
     * <p>口径分开且各自标明，不混用：
     * ① 合同收入/预计总成本/预计利润 → 与<b>上月公司级快照</b>对比（basis=VS_LAST_MONTH_SNAPSHOT）；
     * 无上月快照时 delta 为 <b>null</b> 并标 basis=NO_BASELINE，<b>不伪造 0</b>（前端显示“—”）；
     * ② 累计回款 → 本月新增回款（receive_date 落本月的 APPROVED 回款登记，真实单据）；
     * ③ 累计支付<b>不给环比</b>：无可靠的“本月审批”时间字段（updated_at 会被任意修改污染），
     * 改由 currentMonthCashNeed（本月现金需求）表达当月付款压力，宁缺勿滥。</p>
     */
    private Map<String, Object> computeChanges(BigDecimal contractIncome, BigDecimal forecastCost,
                                               BigDecimal forecastProfit) {
        Map<String, Object> changes = new HashMap<>();
        String currentMonth = YearMonth.now().format(MONTH_FMT);
        // getTrend 升序返回；取最后一个非当月快照作为对比基期
        List<BizProfitSnapshot> trend = profitSnapshotService.getTrend(null, 2);
        BizProfitSnapshot baseline = null;
        if (trend != null) {
            for (BizProfitSnapshot s : trend) {
                if (!currentMonth.equals(s.getSnapshotMonth())) {
                    baseline = s;
                }
            }
        }
        if (baseline != null) {
            changes.put("basis", "VS_LAST_MONTH_SNAPSHOT");
            changes.put("baseMonth", baseline.getSnapshotMonth());
            changes.put("contractIncome", contractIncome.subtract(nvl(baseline.getContractIncome())));
            changes.put("forecastTotalCost", forecastCost.subtract(nvl(baseline.getForecastTotalCost())));
            changes.put("forecastProfit", forecastProfit.subtract(nvl(baseline.getForecastProfit())));
        } else {
            // 无上期快照（首次部署或快照任务未跑）：如实置 null，前端显示“—”而非假 0
            changes.put("basis", "NO_BASELINE");
            changes.put("baseMonth", null);
            changes.put("contractIncome", null);
            changes.put("forecastTotalCost", null);
            changes.put("forecastProfit", null);
        }
        changes.put("receivedThisMonth", sumReceivedThisMonth());
        return changes;
    }

    /**
     * 本月新增回款（APPROVED 回款登记、receive_date 落本月）——真实单据口径，非快照差值。
     */
    private BigDecimal sumReceivedThisMonth() {
        LocalDate from = YearMonth.now().atDay(1);
        LocalDate to = YearMonth.now().atEndOfMonth();
        List<com.zwinsight.finance.domain.BizPaymentReceived> list = paymentReceivedMapper.selectList(
                new LambdaQueryWrapper<com.zwinsight.finance.domain.BizPaymentReceived>()
                        .eq(com.zwinsight.finance.domain.BizPaymentReceived::getStatus, "APPROVED")
                        .isNotNull(com.zwinsight.finance.domain.BizPaymentReceived::getReceiveDate)
                        .ge(com.zwinsight.finance.domain.BizPaymentReceived::getReceiveDate, from)
                        .le(com.zwinsight.finance.domain.BizPaymentReceived::getReceiveDate, to));
        BigDecimal total = BigDecimal.ZERO;
        if (list == null) {
            return total;
        }
        for (com.zwinsight.finance.domain.BizPaymentReceived r : list) {
            total = total.add(nvl(r.getReceiveAmount()));
        }
        return total;
    }

    /**
     * 利润趋势（驾驶舱 §5.1）：预计利润快照曲线 + 已实现收支月度曲线（同源 profit-trend 真实口径）。
     *
     * @param projectId 项目ID（NULL=公司级）
     * @param months    快照月数（1-36）
     * @param year      已实现收支柱状图年份（默认当年）
     */
    public Map<String, Object> getProfitTrend(Long projectId, int months, Integer year) {
        List<BizProfitSnapshot> snapshots = profitSnapshotService.getTrend(projectId, months);
        Map<String, Object> realized = dashboardService.getProfitTrend(year);

        Map<String, Object> result = new HashMap<>();
        result.put("snapshots", snapshots);
        result.put("realized", realized);
        return result;
    }

    /**
     * 项目经营健康度（驾驶舱 §5.2）：规则自动定级 🟢🟡🔴 + 四级排序
     * （风险等级 → 预计亏损 → 利润率 → 资金相关影响额），异常项目排最前。
     */
    public List<Map<String, Object>> getProjectHealth() {
        return getProjectHealth(null);
    }

    /**
     * 项目经营健康度 + 快捷筛选（UI §14：全部/高风险/亏损/资金紧张/利润下降/本月异常项目）。
     * <p>筛选均在已算出的真实指标上进行，不额外估算：
     * HIGH_RISK=health为RED；LOSS=预计利润&lt;0；FUND_TIGHT=存在 FUND_GAP 活跃风险；
     * PROFIT_DOWN=当月快照 profitDelta&lt;0（<b>无上月基期时不入选</b>，不把“无法判断”当“下降”）；
     * MONTH_ABNORMAL=存在任何活跃风险。非法值抛异常，不静默当作全部。</p>
     *
     * @param quickFilter 快捷筛选码（空/ALL = 不筛选）
     */
    public List<Map<String, Object>> getProjectHealth(String quickFilter) {
        return getProjectHealth(quickFilter, null, null);
    }

    /**
     * 项目经营健康度 + 快捷筛选 + 全局筛选（UI §14）。
     * <p>公司/项目筛选先于快捷筛选生效（先缩小范围再按异常类型过滤），
     * 两层筛选均基于已算出的真实指标，不额外估算。筛选范围内无项目时返回空列表
     * （而非全量），避免“选了公司却看到其他公司项目”的口径错位。</p>
     *
     * @param quickFilter    快捷筛选码（空/ALL = 不筛选）
     * @param ownerCompanyId 所属公司ID（空=不限）
     * @param projectId      项目ID（空=不限）
     */
    public List<Map<String, Object>> getProjectHealth(String quickFilter, Long ownerCompanyId, Long projectId) {
        List<Map<String, Object>> forecasts = profitSnapshotService.listProjectForecasts();
        boolean scoped = ownerCompanyId != null || projectId != null;
        // 项目回写字段一次查出，兼作两用：① 筛选范围判定 ② §16.1「点击数字→项目构成」
        // 所需的累计回款/累计支付/应收未收逐项目值（否则前端无法把公司级数字摊到项目）
        List<BizProject> scopeProjects = projectMapper.selectList(new LambdaQueryWrapper<BizProject>()
                .eq(ownerCompanyId != null, BizProject::getOwnerCompanyId, ownerCompanyId)
                .eq(projectId != null, BizProject::getId, projectId));
        Map<Long, BizProject> projectById = new HashMap<>();
        for (BizProject p : scopeProjects) {
            projectById.put(p.getId(), p);
        }
        if (scoped) {
            forecasts = forecasts.stream()
                    .filter(row -> projectById.containsKey((Long) row.get("projectId")))
                    .toList();
        }

        // 活跃风险按项目聚合（OPEN/PROCESSING）
        List<BizRiskRegister> activeRisks = riskMapper.selectList(new LambdaQueryWrapper<BizRiskRegister>()
                .in(BizRiskRegister::getHandleStatus,
                        BizRiskRegister.HANDLE_OPEN, BizRiskRegister.HANDLE_PROCESSING));
        Map<Long, List<BizRiskRegister>> risksByProject = new HashMap<>();
        for (BizRiskRegister risk : activeRisks) {
            if (risk.getProjectId() != null) {
                risksByProject.computeIfAbsent(risk.getProjectId(), k -> new ArrayList<>()).add(risk);
            }
        }
        // 当月项目级快照的利润环比（§5.2“利润变化”列 + PROFIT_DOWN 筛选依据）
        Map<Long, BigDecimal> deltaByProject = loadCurrentMonthProfitDeltas();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> forecast : forecasts) {
            // 变量名避开方法入参 projectId（筛选条件），此处为行所属项目
            Long rowProjectId = (Long) forecast.get("projectId");
            BigDecimal profit = (BigDecimal) forecast.get("forecastProfit");
            List<BizRiskRegister> risks = risksByProject.getOrDefault(rowProjectId, List.of());
            boolean hasRed = risks.stream().anyMatch(r -> BizRiskRegister.SEVERITY_RED.equals(r.getSeverity()));
            boolean hasYellow = risks.stream().anyMatch(r -> BizRiskRegister.SEVERITY_YELLOW.equals(r.getSeverity()));

            // 健康度定级规则（§15：规则自动产生，非人工选择）
            String health = "GREEN";
            if (profit.signum() < 0 || hasRed) {
                health = "RED";
            } else if (hasYellow) {
                health = "YELLOW";
            }

            Map<String, Object> row = new HashMap<>(forecast);
            row.put("health", health);
            row.put("redCount", risks.stream()
                    .filter(r -> BizRiskRegister.SEVERITY_RED.equals(r.getSeverity())).count());
            row.put("yellowCount", risks.stream()
                    .filter(r -> BizRiskRegister.SEVERITY_YELLOW.equals(r.getSeverity())).count());
            // §5.2 要求呈现“利润变化”与“资金缺口”（无基期/无缺口时如实为 null/0，不伪造）
            row.put("profitDelta", deltaByProject.get(rowProjectId));
            BigDecimal fundGap = risks.stream()
                    .filter(r -> "FUND_GAP".equals(r.getRiskType()))
                    .map(r -> nvl(r.getImpactAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            row.put("fundGapAmount", fundGap);
            // §16.1 项目构成：资金状态三卡（累计回款/累计支付/应收未收）的逐项目值。
            // 取项目表审批回写字段（与 overview 同源）；项目不存在时为 null，<b>不用 0 冒充</b>。
            BizProject scopeProject = projectById.get(rowProjectId);
            row.put("cumulativeReceived", scopeProject != null ? nvl(scopeProject.getTotalIncome()) : null);
            row.put("cumulativePaid", scopeProject != null ? nvl(scopeProject.getTotalExpense()) : null);
            row.put("receivableOutstanding",
                    scopeProject != null ? nvl(scopeProject.getReceivableAmount()) : null);
            row.put("topRisks", risks.stream()
                    .sorted(Comparator
                            .comparingInt((BizRiskRegister r) ->
                                    BizRiskRegister.SEVERITY_RED.equals(r.getSeverity()) ? 0 : 1)
                            .thenComparing(Comparator.comparing(
                                    (BizRiskRegister r) -> nvl(r.getImpactAmount())).reversed()))
                    .limit(3)
                    .map(BizRiskRegister::getTitle)
                    .toList());
            result.add(row);
        }

        // 四级排序：健康度(RED>YELLOW>GREEN) → 预计亏损(利润升序) → 利润率升序
        result.sort(Comparator
                .comparingInt((Map<String, Object> m) -> healthRank((String) m.get("health")))
                .thenComparing(m -> (BigDecimal) m.get("forecastProfit"))
                .thenComparing(m -> (BigDecimal) m.get("profitRate")));
        return applyQuickFilter(result, quickFilter);
    }

    // ==================== 私有方法 ====================

    /** 快捷筛选合法值（UI §14） */
    private static final Set<String> QUICK_FILTERS =
            Set.of("ALL", "HIGH_RISK", "LOSS", "FUND_TIGHT", "PROFIT_DOWN", "MONTH_ABNORMAL");

    /**
     * 应用快捷筛选（UI §14 老板常用：全部/高风险/亏损/资金紧张/利润下降/本月异常）。
     * <p>非法筛选码抛 400，<b>不静默当作“全部”</b>（否则前端传错会误以为无异常项目）。</p>
     */
    private List<Map<String, Object>> applyQuickFilter(List<Map<String, Object>> rows, String quickFilter) {
        if (quickFilter == null || quickFilter.isBlank() || "ALL".equalsIgnoreCase(quickFilter)) {
            return rows;
        }
        String code = quickFilter.trim().toUpperCase(java.util.Locale.ROOT);
        if (!QUICK_FILTERS.contains(code)) {
            throw new com.zwinsight.common.exception.BusinessException(400,
                    "快捷筛选不合法，可选值: " + QUICK_FILTERS);
        }
        return rows.stream().filter(row -> switch (code) {
            case "HIGH_RISK" -> "RED".equals(row.get("health"));
            case "LOSS" -> nvl((BigDecimal) row.get("forecastProfit")).signum() < 0;
            case "FUND_TIGHT" -> nvl((BigDecimal) row.get("fundGapAmount")).signum() > 0;
            // 无上月基期（profitDelta 为 null）时不入选：不得把“无法判断”当成“利润下降”
            case "PROFIT_DOWN" -> row.get("profitDelta") != null
                    && ((BigDecimal) row.get("profitDelta")).signum() < 0;
            case "MONTH_ABNORMAL" -> ((Number) row.getOrDefault("redCount", 0L)).longValue()
                    + ((Number) row.getOrDefault("yellowCount", 0L)).longValue() > 0;
            default -> true;
        }).toList();
    }

    /**
     * 当月项目级快照的利润环比（{projectId: profitDelta}）。
     * <p>无当月快照的项目不入图（前端得到 null 显示“—”），不用 0 冒充“无变化”。</p>
     */
    private Map<Long, BigDecimal> loadCurrentMonthProfitDeltas() {
        String currentMonth = YearMonth.now().format(MONTH_FMT);
        List<BizProfitSnapshot> snapshots = snapshotMapper.selectList(
                new LambdaQueryWrapper<BizProfitSnapshot>()
                        .eq(BizProfitSnapshot::getSnapshotMonth, currentMonth)
                        .isNotNull(BizProfitSnapshot::getProjectId));
        Map<Long, BigDecimal> map = new HashMap<>();
        if (snapshots == null) {
            return map;
        }
        for (BizProfitSnapshot s : snapshots) {
            if (s.getProfitDelta() != null) {
                map.put(s.getProjectId(), s.getProfitDelta());
            }
        }
        return map;
    }

    /**
     * 90 天（当月 + 后两个月）资金缺口与可用资金明细。
     * <p>口径（资金流转 §10.4）：{@code 缺口 = 未来预计支付 − 可用资金}，正数表示缺钱；
     * {@code 可用资金 = 账户余额快照合计 + 窗口内预计回款}（回款是窗口内真实可用来源，
     * 只算账面余额会高估缺口）。</p>
     * <p>数据源为公司级滚动预测快照（projectId IS NULL，FundForecastTask 每日 01:15 刷新）。
     * 若快照缺失（任务未执行或首次部署）则各项为 0，<b>不伪造估算值</b>；
     * 同时如实返回 accountBalance，供前端提示“账户余额未登记”（biz_bank_balance 为空时）。</p>
     *
     * @return {expectedPayments, expectedReceipts, accountBalance, availableFund, gap, currentMonthPayments}
     */
    private Map<String, BigDecimal> computeFundGap90Days() {
        return computeFundGap90Days(null);
    }

    /**
     * 90 天资金缺口（可限定项目集合）。
     * <p>{@code scopeProjectIds == null} → 公司级快照（projectId IS NULL）+ 全量账户余额；
     * 非 null → 项目级快照累加，且<b>账户余额计 0</b>（银行账户属公司级资金池，
     * 无法按项目拆分），因此缺口偏保守（高估资金压力），调用方必须向用户标明口径。</p>
     * <p>空集合（筛选范围内无项目）直接返回全 0，不得生成 {@code IN ()} 非法 SQL。</p>
     */
    private Map<String, BigDecimal> computeFundGap90Days(Set<Long> scopeProjectIds) {
        boolean scoped = scopeProjectIds != null;
        Map<String, BigDecimal> detail = new HashMap<>();
        if (scoped && scopeProjectIds.isEmpty()) {
            detail.put("expectedPayments", BigDecimal.ZERO);
            detail.put("expectedReceipts", BigDecimal.ZERO);
            detail.put("accountBalance", BigDecimal.ZERO);
            detail.put("availableFund", BigDecimal.ZERO);
            detail.put("gap", BigDecimal.ZERO);
            detail.put("currentMonthPayments", BigDecimal.ZERO);
            return detail;
        }
        YearMonth current = YearMonth.now();
        YearMonth end = current.plusMonths(2);
        String currentMonthKey = current.format(MONTH_FMT);
        LambdaQueryWrapper<BizFundRollingForecast> query = new LambdaQueryWrapper<BizFundRollingForecast>()
                .ge(BizFundRollingForecast::getForecastMonth, currentMonthKey)
                .le(BizFundRollingForecast::getForecastMonth, end.format(MONTH_FMT));
        if (scoped) {
            query.in(BizFundRollingForecast::getProjectId, scopeProjectIds);
        } else {
            query.isNull(BizFundRollingForecast::getProjectId);
        }
        List<BizFundRollingForecast> snapshots = rollingForecastMapper.selectList(query);
        BigDecimal expectedPayments = BigDecimal.ZERO;
        BigDecimal expectedReceipts = BigDecimal.ZERO;
        BigDecimal currentMonthPayments = BigDecimal.ZERO;
        for (BizFundRollingForecast f : snapshots) {
            expectedPayments = expectedPayments.add(nvl(f.getExpectedPayments()));
            expectedReceipts = expectedReceipts.add(nvl(f.getExpectedReceipts()));
            // 累加而非赋值：项目级快照同一月份有多行（每项目一行），赋值会只保留最后一项
            if (currentMonthKey.equals(f.getForecastMonth())) {
                currentMonthPayments = currentMonthPayments.add(nvl(f.getExpectedPayments()));
            }
        }
        BigDecimal accountBalance = scoped ? BigDecimal.ZERO : nvl(bankFlowMapper.sumLatestBalances());
        BigDecimal availableFund = accountBalance.add(expectedReceipts);
        BigDecimal gap = expectedPayments.subtract(availableFund);

        detail.put("expectedPayments", expectedPayments);
        detail.put("expectedReceipts", expectedReceipts);
        detail.put("accountBalance", accountBalance);
        detail.put("availableFund", availableFund);
        detail.put("gap", gap);
        detail.put("currentMonthPayments", currentMonthPayments);
        return detail;
    }

    /**
     * 90 天资金缺口（对外只读口径，供风险规则与单测复用）。
     * <p>正数 = 缺钱（预计支付 &gt; 可用资金）；负数 = 有富余。</p>
     */
    public BigDecimal getGap90Days() {
        return computeFundGap90Days().get("gap");
    }

    /**
     * 当前可用资金（账户余额快照 + 未来 90 天预计回款）——供资金风险规则判定“是否可覆盖”。
     */
    public BigDecimal getAvailableFund() {
        return computeFundGap90Days().get("availableFund");
    }

    /** 计入合同执行率的施工合同状态（与 {@link ProfitSnapshotService} 收入口径一致） */
    private static final List<String> EXECUTION_CONTRACT_STATUSES = List.of("EFFECTIVE", "SETTLED");

    /**
     * 资金比率（资金流转 §10.2 支付率、§10.3 合同执行率）。
     * <p><b>支付率口径声明（不得笼统称“实际支付”）</b>：
     * {@code paymentRate} = Σ合同 cumulative_paid ÷ Σ cumulative_settlement，其中 cumulative_paid
     * 由付款申请<b>审批通过</b>回写，属<b>审批口径</b>；另并列返回现金口径
     * {@code cashPaidTotal}/{@code cashPaymentRate}（银行流水勾稽合计）。
     * 未导入银行流水时现金口径为 0，此时必须如实呈现而非用审批口径冒充。</p>
     * <p>合同执行率 = Σ累计完成产值 ÷ Σ动态合同金额（合同额 + 累计变更），
     * 仅计 EFFECTIVE/SETTLED 施工合同（与预计利润的收入口径同源，避免两套口径打架）。</p>
     */
    public Map<String, Object> getFundRatios() {
        // §10.2 支付率
        BigDecimal settlementTotal = nvl(contractPayableMapper.sumConfirmedPayable(null));
        BigDecimal paidTotal = nvl(contractPayableMapper.sumCumulativePaid(null));
        BigDecimal cashPaidTotal = BigDecimal.ZERO;
        Map<Long, BigDecimal> matched = bankFlowMapper.matchedAmountByPaymentApply();
        if (matched != null) {
            for (BigDecimal v : matched.values()) {
                cashPaidTotal = cashPaidTotal.add(v);
            }
        }

        // §10.3 合同执行率（动态合同金额 = 合同额 + 累计变更）
        List<com.zwinsight.contract.domain.BizConstructionContract> contracts =
                constructionContractMapper.selectList(
                        new LambdaQueryWrapper<com.zwinsight.contract.domain.BizConstructionContract>()
                                .in(com.zwinsight.contract.domain.BizConstructionContract::getStatus,
                                        EXECUTION_CONTRACT_STATUSES));
        BigDecimal outputTotal = BigDecimal.ZERO;
        BigDecimal dynamicContractTotal = BigDecimal.ZERO;
        if (contracts != null) {
            for (com.zwinsight.contract.domain.BizConstructionContract c : contracts) {
                outputTotal = outputTotal.add(nvl(c.getCumulativeOutput()));
                dynamicContractTotal = dynamicContractTotal
                        .add(nvl(c.getContractAmount()))
                        .add(nvl(c.getCumulativeChangeAmount()));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("settlementTotal", settlementTotal);
        result.put("paidTotal", paidTotal);
        result.put("paidBasis", "APPROVAL_WRITEBACK");
        result.put("paymentRate", settlementTotal.signum() > 0
                ? paidTotal.divide(settlementTotal, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        // 现金口径并列返回（无银行流水时为 0，如实呈现）
        result.put("cashPaidTotal", cashPaidTotal);
        result.put("cashPaymentRate", settlementTotal.signum() > 0
                ? cashPaidTotal.divide(settlementTotal, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        result.put("outputTotal", outputTotal);
        result.put("dynamicContractTotal", dynamicContractTotal);
        result.put("contractExecutionRate", dynamicContractTotal.signum() > 0
                ? outputTotal.divide(dynamicContractTotal, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        return result;
    }

    private static int healthRank(String health) {
        return switch (health) {
            case "RED" -> 0;
            case "YELLOW" -> 1;
            default -> 2;
        };
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** 供任务/测试触发的当月快照刷新入口透传（避免多路径实现） */
    public int refreshSnapshot() {
        return profitSnapshotService.generateSnapshot();
    }

    /** 快照日期（供控制器透出"数据更新时间"） */
    public LocalDate today() {
        return LocalDate.now();
    }
}
