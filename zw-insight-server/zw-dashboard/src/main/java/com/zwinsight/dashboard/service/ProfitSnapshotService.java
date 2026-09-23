package com.zwinsight.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.dashboard.domain.BizProfitSnapshot;
import com.zwinsight.dashboard.mapper.BizProfitSnapshotMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 预计利润快照服务（V2026_59，驾驶舱 2A）
 * <p>
 * 权威口径：预计利润 = 合同收入 − 预计最终总成本（EAC）。
 * 收入 = 生效/已结算施工合同金额合计（无施工合同回退项目 contract_amount，income_basis 如实标记）；
 * 成本 = CBS 根成本账户 forecastAmount 合计（无成本账户回退项目 total_expense，cost_basis 如实标记，
 * 此时"预计"退化为"已实现"，消费方必须按 cost_basis 区分展示，不静默混口径）。
 * </p>
 * <p>快照按月 upsert（同月同项目覆盖更新），profit_delta = 本月预计利润 − 上月快照利润；
 * 归因 = 两期 category_breakdown 的 forecast 差额按成本类别分解（驾驶舱 §5.1 点击月份展开）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfitSnapshotService {

    private final BizProfitSnapshotMapper snapshotMapper;
    private final BizProjectMapper projectMapper;
    private final BizConstructionContractMapper constructionContractMapper;
    private final BizCostAccountMapper costAccountMapper;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 纳入快照的项目状态（排除 DRAFT/FILED：未中标无收入基数，快照无意义） */
    private static final Set<String> SNAPSHOT_STATUSES =
            Set.of("TENDERING", "WON", "CONSTRUCTION", "COMPLETED", "CLOSING", "CLOSED");

    /** 计入合同收入的施工合同状态（生效中 + 已结算） */
    private static final List<String> INCOME_CONTRACT_STATUSES = List.of("EFFECTIVE", "SETTLED");

    /**
     * 生成当月快照（公司级 + 全部在算项目；覆盖式 upsert，幂等可重跑）。
     *
     * @return 本次生成/更新的项目级快照数
     */
    @Transactional(rollbackFor = Exception.class)
    public int generateSnapshot() {
        String monthKey = YearMonth.now().format(MONTH_FMT);
        LocalDate today = LocalDate.now();
        String prevMonthKey = YearMonth.now().minusMonths(1).format(MONTH_FMT);

        List<BizProject> projects = projectMapper.selectList(new LambdaQueryWrapper<BizProject>()
                .in(BizProject::getStatus, SNAPSHOT_STATUSES));

        ObjectNode companyBreakdown = objectMapper.createObjectNode();
        BigDecimal companyIncome = BigDecimal.ZERO;
        BigDecimal companyForecastCost = BigDecimal.ZERO;
        BigDecimal companyActualCost = BigDecimal.ZERO;
        BigDecimal companyOutput = BigDecimal.ZERO;
        int count = 0;

        for (BizProject project : projects) {
            ProjectForecast forecast = computeProjectForecast(project);
            BizProfitSnapshot prev = findSnapshot(prevMonthKey, project.getId());
            BizProfitSnapshot snapshot = upsertSnapshot(monthKey, project.getId(), forecast, today,
                    prev != null ? prev.getForecastProfit() : null);
            mergeBreakdown(companyBreakdown, forecast.categoryForecast());

            companyIncome = companyIncome.add(forecast.contractIncome());
            companyForecastCost = companyForecastCost.add(forecast.forecastTotalCost());
            companyActualCost = companyActualCost.add(forecast.actualCost());
            companyOutput = companyOutput.add(forecast.cumulativeOutput());
            count++;
            log.debug("项目预计利润快照, projectId={}, income={}, forecastCost={}, profit={}, costBasis={}",
                    project.getId(), forecast.contractIncome(), forecast.forecastTotalCost(),
                    snapshot.getForecastProfit(), forecast.costBasis());
        }

        // 公司级聚合快照（projectId=NULL）
        ProjectForecast company = new ProjectForecast(
                companyIncome, BizProfitSnapshot.BASIS_CONSTRUCTION_CONTRACT,
                companyActualCost, companyForecastCost, BizProfitSnapshot.BASIS_CBS_FORECAST,
                companyIncome.subtract(companyForecastCost), companyOutput,
                parseBreakdown(companyBreakdown.toString()));
        BizProfitSnapshot prevCompany = findSnapshot(prevMonthKey, null);
        upsertSnapshot(monthKey, null, company, today,
                prevCompany != null ? prevCompany.getForecastProfit() : null);

        log.info("预计利润快照生成完成, month={}, 项目数={}", monthKey, count);
        return count;
    }

    /**
     * 计算单个项目的预计利润（实时口径，驾驶舱项目详情/健康度直接消费，不依赖快照）。
     */
    public ProjectForecast computeProjectForecast(BizProject project) {
        // 收入侧：生效/已结算施工合同合计
        List<BizConstructionContract> contracts = constructionContractMapper.selectList(
                new LambdaQueryWrapper<BizConstructionContract>()
                        .eq(BizConstructionContract::getProjectId, project.getId())
                        .in(BizConstructionContract::getStatus, INCOME_CONTRACT_STATUSES));
        BigDecimal income = BigDecimal.ZERO;
        for (BizConstructionContract c : contracts) {
            income = income.add(c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO);
        }
        String incomeBasis = BizProfitSnapshot.BASIS_CONSTRUCTION_CONTRACT;
        if (contracts.isEmpty()) {
            income = project.getContractAmount() != null ? project.getContractAmount() : BigDecimal.ZERO;
            incomeBasis = BizProfitSnapshot.BASIS_PROJECT_CONTRACT_AMOUNT;
        }

        // 成本侧：CBS 根账户 EAC 合计 + 类别分解
        List<BizCostAccount> accounts = costAccountMapper.selectList(
                new LambdaQueryWrapper<BizCostAccount>()
                        .eq(BizCostAccount::getProjectId, project.getId()));
        BigDecimal actualCost;
        BigDecimal forecastCost;
        String costBasis;
        Map<String, BigDecimal> categoryForecast = new LinkedHashMap<>();
        if (accounts.isEmpty()) {
            // 无成本账户：回退已实现支出（口径如实标记，不伪装有预测）
            actualCost = nvl(project.getTotalExpense());
            forecastCost = actualCost;
            costBasis = BizProfitSnapshot.BASIS_FALLBACK_EXPENSE;
        } else {
            Set<Long> ids = new HashSet<>();
            for (BizCostAccount a : accounts) {
                ids.add(a.getId());
            }
            actualCost = BigDecimal.ZERO;
            forecastCost = BigDecimal.ZERO;
            for (BizCostAccount a : accounts) {
                // 只累加根账户，防父子层级重复计数
                boolean isRoot = a.getParentId() == null || !ids.contains(a.getParentId());
                if (!isRoot) {
                    continue;
                }
                actualCost = actualCost.add(nvl(a.getActualAmount()));
                // forecast 为空时以 actual 兜底（未做 EAC 估算的账户按已发生计，低估标记由类别分解可见）
                BigDecimal forecast = a.getForecastAmount() != null ? a.getForecastAmount() : nvl(a.getActualAmount());
                forecastCost = forecastCost.add(forecast);
                String category = a.getCostCategory() != null ? a.getCostCategory() : "OTHER";
                categoryForecast.merge(category, forecast, BigDecimal::add);
            }
            costBasis = BizProfitSnapshot.BASIS_CBS_FORECAST;
        }

        return new ProjectForecast(
                income, incomeBasis, actualCost, forecastCost, costBasis,
                income.subtract(forecastCost),
                nvl(project.getCumulativeOutput()),
                categoryForecast);
    }

    /**
     * 利润趋势（驾驶舱 §5.1）：近 months 个月快照曲线（项目级或公司级）。
     *
     * @param projectId 项目ID（NULL=公司级）
     * @param months    月数（1-36）
     * @return 按月份升序的快照列表
     */
    public List<BizProfitSnapshot> getTrend(Long projectId, int months) {
        if (months < 1 || months > 36) {
            throw new BusinessException(400, "趋势月数需在1-36之间");
        }
        String fromMonth = YearMonth.now().minusMonths(months - 1L).format(MONTH_FMT);
        return snapshotMapper.selectList(new LambdaQueryWrapper<BizProfitSnapshot>()
                .eq(projectId != null, BizProfitSnapshot::getProjectId, projectId)
                .isNull(projectId == null, BizProfitSnapshot::getProjectId)
                .ge(BizProfitSnapshot::getSnapshotMonth, fromMonth)
                .orderByAsc(BizProfitSnapshot::getSnapshotMonth));
    }

    /**
     * 利润变化归因（驾驶舱 §5.1 点击月份展开）：本期 vs 上期快照按成本类别分解 forecast 差额。
     *
     * @param projectId    项目ID（NULL=公司级）
     * @param snapshotMonth 目标月份 yyyy-MM
     * @return {month, profitDelta, items: [{category, delta, forecast, prevForecast}]}，按影响绝对值降序；
     *         无上期快照时 items 为空且 profitDelta 为本期利润（如实标注首期无对比基期）
     */
    public Map<String, Object> attributeProfitChange(Long projectId, String snapshotMonth) {
        BizProfitSnapshot current = findSnapshot(snapshotMonth, projectId);
        if (current == null) {
            throw new BusinessException(404, "快照不存在: " + snapshotMonth);
        }
        String prevMonth = YearMonth.parse(snapshotMonth, MONTH_FMT).minusMonths(1).format(MONTH_FMT);
        BizProfitSnapshot prev = findSnapshot(prevMonth, projectId);

        Map<String, Object> result = new HashMap<>();
        result.put("month", snapshotMonth);
        result.put("forecastProfit", current.getForecastProfit());
        List<Map<String, Object>> items = new ArrayList<>();
        if (prev == null) {
            result.put("profitDelta", null);
            result.put("hasBaseline", false);
            result.put("items", items);
            return result;
        }
        result.put("profitDelta", current.getForecastProfit().subtract(prev.getForecastProfit()));
        result.put("hasBaseline", true);

        Map<String, BigDecimal> currentBreakdown = parseBreakdown(current.getCategoryBreakdown());
        Map<String, BigDecimal> prevBreakdown = parseBreakdown(prev.getCategoryBreakdown());
        Set<String> categories = new HashSet<>();
        categories.addAll(currentBreakdown.keySet());
        categories.addAll(prevBreakdown.keySet());
        for (String category : categories) {
            BigDecimal cur = currentBreakdown.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal old = prevBreakdown.getOrDefault(category, BigDecimal.ZERO);
            Map<String, Object> item = new HashMap<>();
            item.put("category", category);
            item.put("forecast", cur);
            item.put("prevForecast", old);
            // 成本上升 → 利润下降，差额取负号呈现（对齐驾驶舱"利润变化原因"视角）
            item.put("delta", old.subtract(cur));
            items.add(item);
        }
        items.sort(Comparator.comparing((Map<String, Object> m) ->
                ((BigDecimal) m.get("delta")).abs()).reversed());
        result.put("items", items);
        return result;
    }

    /**
     * 项目预计利润一览（驾驶舱项目经营页/健康度数据源，实时计算不落快照）。
     */
    public List<Map<String, Object>> listProjectForecasts() {
        List<BizProject> projects = projectMapper.selectList(new LambdaQueryWrapper<BizProject>()
                .in(BizProject::getStatus, SNAPSHOT_STATUSES));
        List<Map<String, Object>> result = new ArrayList<>();
        for (BizProject project : projects) {
            ProjectForecast f = computeProjectForecast(project);
            Map<String, Object> row = new HashMap<>();
            row.put("projectId", project.getId());
            row.put("projectName", project.getProjectName());
            row.put("status", project.getStatus());
            row.put("contractIncome", f.contractIncome());
            row.put("incomeBasis", f.incomeBasis());
            row.put("actualCost", f.actualCost());
            row.put("forecastTotalCost", f.forecastTotalCost());
            row.put("costBasis", f.costBasis());
            row.put("forecastProfit", f.forecastProfit());
            row.put("profitRate", f.contractIncome().signum() > 0
                    ? f.forecastProfit().divide(f.contractIncome(), 4, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            result.add(row);
        }
        return result;
    }

    // ==================== 私有方法 ====================

    private BizProfitSnapshot findSnapshot(String monthKey, Long projectId) {
        return snapshotMapper.selectOne(new LambdaQueryWrapper<BizProfitSnapshot>()
                .eq(BizProfitSnapshot::getSnapshotMonth, monthKey)
                .eq(projectId != null, BizProfitSnapshot::getProjectId, projectId)
                .isNull(projectId == null, BizProfitSnapshot::getProjectId));
    }

    private BizProfitSnapshot upsertSnapshot(String monthKey, Long projectId, ProjectForecast forecast,
                                             LocalDate today, BigDecimal prevProfit) {
        BizProfitSnapshot snapshot = findSnapshot(monthKey, projectId);
        boolean isNew = snapshot == null;
        if (isNew) {
            snapshot = new BizProfitSnapshot();
            snapshot.setSnapshotMonth(monthKey);
            snapshot.setProjectId(projectId);
        }
        snapshot.setContractIncome(forecast.contractIncome());
        snapshot.setIncomeBasis(forecast.incomeBasis());
        snapshot.setCumulativeOutput(forecast.cumulativeOutput());
        snapshot.setActualCost(forecast.actualCost());
        snapshot.setForecastTotalCost(forecast.forecastTotalCost());
        snapshot.setCostBasis(forecast.costBasis());
        snapshot.setForecastProfit(forecast.forecastProfit());
        snapshot.setProfitDelta(prevProfit != null ? forecast.forecastProfit().subtract(prevProfit) : null);
        snapshot.setCategoryBreakdown(toJson(forecast.categoryForecast()));
        snapshot.setSnapshotDate(today);
        if (isNew) {
            snapshotMapper.insert(snapshot);
        } else {
            snapshotMapper.updateById(snapshot);
        }
        return snapshot;
    }

    private void mergeBreakdown(ObjectNode target, Map<String, BigDecimal> categoryForecast) {
        for (Map.Entry<String, BigDecimal> entry : categoryForecast.entrySet()) {
            BigDecimal existing = target.has(entry.getKey())
                    ? target.get(entry.getKey()).decimalValue() : BigDecimal.ZERO;
            target.put(entry.getKey(), existing.add(entry.getValue()));
        }
    }

    private String toJson(Map<String, BigDecimal> categoryForecast) {
        ObjectNode node = objectMapper.createObjectNode();
        categoryForecast.forEach((k, v) -> node.put(k, v));
        return node.toString();
    }

    private Map<String, BigDecimal> parseBreakdown(String json) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            var node = objectMapper.readTree(json);
            node.fields().forEachRemaining(e ->
                    result.put(e.getKey(), e.getValue().decimalValue()));
        } catch (Exception e) {
            log.warn("解析成本类别分解失败（按空分解处理，不中断趋势展示）: {}", e.getMessage());
        }
        return result;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /**
     * 项目预计利润计算结果（口径自描述：incomeBasis/costBasis 如实标记数据来源）
     */
    public record ProjectForecast(
            BigDecimal contractIncome,
            String incomeBasis,
            BigDecimal actualCost,
            BigDecimal forecastTotalCost,
            String costBasis,
            BigDecimal forecastProfit,
            BigDecimal cumulativeOutput,
            Map<String, BigDecimal> categoryForecast) {
    }
}
