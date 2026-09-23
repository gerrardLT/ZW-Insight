package com.zwinsight.dashboard.risk.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.risk.RiskFinding;
import com.zwinsight.dashboard.risk.RiskRule;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 预算超支风险规则（资金流转文档 §11 预算预警判定式）：
 * <pre>
 * 实际发生 / 预算 &gt; red-rate(默认100%)    → RED
 * 实际发生 / 预算 &gt; yellow-rate(默认80%)  → YELLOW
 * </pre>
 * 粒度 = 项目 × 费用类别（根账户按 costCategory 聚合 actual/current；
 * 只取根账户防父子层级重复计数）。与事前拦截的 BudgetControlConfig（BLOCK/WARN）互补：
 * 本规则是事后扫描入风险台账，阈值独立配置（zw-dashboard 不依赖 zw-budget 的项目级配置读取，
 * 默认值与其一致：80/100）。
 */
@Component
@RequiredArgsConstructor
public class BudgetOverRiskRule implements RiskRule {

    public static final String TYPE = "BUDGET_OVER";

    private final BizCostAccountMapper costAccountMapper;
    private final BizProjectMapper projectMapper;

    /** 黄色预警执行率（默认 80%，对齐 BudgetControlConfig 默认） */
    @Value("${zw.risk.budget-yellow-rate:80}")
    private BigDecimal yellowRate;

    /** 红色预警执行率（默认 100%） */
    @Value("${zw.risk.budget-red-rate:100}")
    private BigDecimal redRate;

    /** 仅扫描在建阶段项目（CLOSED 项目预算已定格，扫描无纠偏意义） */
    private static final Set<String> SCANNED_PROJECT_STATUSES =
            Set.of("CONSTRUCTION", "COMPLETED", "CLOSING");

    @Override
    public String riskType() {
        return TYPE;
    }

    @Override
    public List<RiskFinding> evaluate() {
        List<BizProject> projects = projectMapper.selectList(new LambdaQueryWrapper<BizProject>()
                .in(BizProject::getStatus, SCANNED_PROJECT_STATUSES));
        List<RiskFinding> findings = new ArrayList<>();
        for (BizProject project : projects) {
            findings.addAll(evaluateProject(project));
        }
        return findings;
    }

    private List<RiskFinding> evaluateProject(BizProject project) {
        List<BizCostAccount> accounts = costAccountMapper.selectList(
                new LambdaQueryWrapper<BizCostAccount>()
                        .eq(BizCostAccount::getProjectId, project.getId()));
        if (accounts.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new java.util.HashSet<>();
        for (BizCostAccount a : accounts) {
            ids.add(a.getId());
        }
        // 项目 × 类别聚合（仅根账户）
        Map<String, BigDecimal[]> byCategory = new LinkedHashMap<>();
        for (BizCostAccount a : accounts) {
            boolean isRoot = a.getParentId() == null || !ids.contains(a.getParentId());
            if (!isRoot) {
                continue;
            }
            String category = a.getCostCategory() != null ? a.getCostCategory() : "OTHER";
            BigDecimal[] sums = byCategory.computeIfAbsent(category,
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            sums[0] = sums[0].add(a.getActualAmount() != null ? a.getActualAmount() : BigDecimal.ZERO);
            sums[1] = sums[1].add(a.getCurrentAmount() != null ? a.getCurrentAmount() : BigDecimal.ZERO);
        }

        List<RiskFinding> findings = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> entry : byCategory.entrySet()) {
            BigDecimal actual = entry.getValue()[0];
            BigDecimal budget = entry.getValue()[1];
            if (budget.signum() <= 0) {
                continue; // 无预算基数的类别不评估（防除零与全额误报）
            }
            BigDecimal rate = actual.divide(budget, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            String severity;
            if (rate.compareTo(redRate) > 0) {
                severity = BizRiskRegister.SEVERITY_RED;
            } else if (rate.compareTo(yellowRate) > 0) {
                severity = BizRiskRegister.SEVERITY_YELLOW;
            } else {
                continue;
            }
            BigDecimal overAmount = actual.subtract(budget).max(BigDecimal.ZERO);
            String title = String.format("项目【%s】%s 预算执行率 %s%%（实际 %s / 预算 %s）",
                    project.getProjectName(), entry.getKey(),
                    rate.setScale(1, RoundingMode.HALF_UP), actual, budget);
            String reason = String.format(
                    "{\"category\":\"%s\",\"actual\":%s,\"budget\":%s,\"rate\":%s}",
                    entry.getKey(), actual, budget, rate);
            String ruleParams = String.format(
                    "{\"yellowRate\":%s,\"redRate\":%s}", yellowRate, redRate);
            findings.add(new RiskFinding(TYPE, project.getId(), severity, title,
                    overAmount, reason,
                    "核查该类别超支单据，暂停非必要申请或走预算变更审批",
                    "PROJECT", project.getId(), ruleParams));
        }
        return findings;
    }
}
