package com.zwinsight.dashboard.risk.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.risk.RiskFinding;
import com.zwinsight.dashboard.risk.RiskRule;
import com.zwinsight.dashboard.service.ProfitSnapshotService;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 利润风险规则（驾驶舱 V1 §15 判定式）：
 * <pre>
 * 预计利润 &lt; 0                    → RED（预计亏损，需管理层介入）
 * 利润率 &lt; yellow-profit-rate     → YELLOW（明显偏差）
 * 其余                             → 不产生风险
 * </pre>
 * 仅评估 CONSTRUCTION/COMPLETED/CLOSING 项目（在建及竣工未结阶段利润预测有意义；
 * TENDERING/WON 成本数据未建立，评估会产生大量误报）。
 * 预计利润口径见 {@link ProfitSnapshotService}（合同收入 − CBS 完工预测总成本）。
 */
@Component
@RequiredArgsConstructor
public class ProfitLossRiskRule implements RiskRule {

    public static final String TYPE = "PROFIT_LOSS";

    private final BizProjectMapper projectMapper;
    private final ProfitSnapshotService profitSnapshotService;

    /** 利润率黄色预警阈值（默认 5%，可配置） */
    @Value("${zw.risk.profit-yellow-rate:0.05}")
    private BigDecimal yellowProfitRate;

    private static final Set<String> EVALUATED_STATUSES = Set.of("CONSTRUCTION", "COMPLETED", "CLOSING");

    @Override
    public String riskType() {
        return TYPE;
    }

    @Override
    public List<RiskFinding> evaluate() {
        List<BizProject> projects = projectMapper.selectList(new LambdaQueryWrapper<BizProject>()
                .in(BizProject::getStatus, EVALUATED_STATUSES));
        List<RiskFinding> findings = new ArrayList<>();
        for (BizProject project : projects) {
            ProfitSnapshotService.ProjectForecast forecast =
                    profitSnapshotService.computeProjectForecast(project);
            BigDecimal profit = forecast.forecastProfit();
            BigDecimal income = forecast.contractIncome();
            BigDecimal profitRate = income.signum() > 0
                    ? profit.divide(income, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            String severity = null;
            String title = null;
            if (profit.signum() < 0) {
                severity = BizRiskRegister.SEVERITY_RED;
                title = String.format("项目【%s】预计亏损 %s 万元", project.getProjectName(),
                        profit.abs().divide(BigDecimal.valueOf(10000), 2, RoundingMode.HALF_UP));
            } else if (profitRate.compareTo(yellowProfitRate) < 0) {
                severity = BizRiskRegister.SEVERITY_YELLOW;
                title = String.format("项目【%s】预计利润率 %s%% 低于阈值 %s%%",
                        project.getProjectName(),
                        profitRate.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP),
                        yellowProfitRate.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP));
            }
            if (severity == null) {
                continue;
            }
            String reason = String.format(
                    "{\"contractIncome\":%s,\"forecastTotalCost\":%s,\"profitRate\":%s,\"costBasis\":\"%s\"}",
                    income, forecast.forecastTotalCost(), profitRate, forecast.costBasis());
            String ruleParams = String.format("{\"yellowProfitRate\":%s,\"profit\":%s}", yellowProfitRate, profit);
            findings.add(new RiskFinding(TYPE, project.getId(), severity, title,
                    profit.abs(), reason,
                    "下钻成本中心核查超支类别，评估压降/变更索赔/调整预算",
                    "PROJECT", project.getId(), ruleParams));
        }
        return findings;
    }
}
