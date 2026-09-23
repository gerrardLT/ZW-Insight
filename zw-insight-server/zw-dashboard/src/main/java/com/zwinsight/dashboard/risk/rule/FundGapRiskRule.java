package com.zwinsight.dashboard.risk.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.risk.RiskFinding;
import com.zwinsight.dashboard.risk.RiskRule;
import com.zwinsight.finance.domain.BizFundRollingForecast;
import com.zwinsight.finance.mapper.BizFundRollingForecastMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 资金缺口风险规则（驾驶舱 V1 §15 资金风险判定式）：
 * <pre>
 * 滚动预测净缺口 &gt; 0 且风险级 HIGH（收款覆盖 &lt; 50%） → RED
 * 滚动预测净缺口 &gt; 0（MEDIUM）                        → YELLOW
 * 无缺口                                              → 不产生风险
 * </pre>
 * 数据源：biz_fund_rolling_forecast 快照（FundForecastTask 每日刷新；
 * 付款侧=已批未付申请，收款侧=应收台账到期，V2026_56/57 口径）。
 * 粒度 = 项目 × 预测月份（公司级快照 projectId=NULL 同样入台账）。
 */
@Component
@RequiredArgsConstructor
public class FundGapRiskRule implements RiskRule {

    public static final String TYPE = "FUND_GAP";

    private final BizFundRollingForecastMapper rollingForecastMapper;
    private final BizProjectMapper projectMapper;

    @Override
    public String riskType() {
        return TYPE;
    }

    @Override
    public List<RiskFinding> evaluate() {
        List<BizFundRollingForecast> forecasts = rollingForecastMapper.selectList(
                new LambdaQueryWrapper<BizFundRollingForecast>()
                        .gt(BizFundRollingForecast::getNetGap, BigDecimal.ZERO));
        List<RiskFinding> findings = new ArrayList<>();
        for (BizFundRollingForecast f : forecasts) {
            String severity = "HIGH".equals(f.getRiskLevel())
                    ? BizRiskRegister.SEVERITY_RED : BizRiskRegister.SEVERITY_YELLOW;
            String scope;
            if (f.getProjectId() != null) {
                BizProject project = projectMapper.selectById(f.getProjectId());
                scope = "项目【" + (project != null ? project.getProjectName() : f.getProjectId()) + "】";
            } else {
                scope = "公司整体";
            }
            String title = String.format("%s %s 预计资金缺口 %s 元（预计付款 %s / 预计收款 %s）",
                    scope, f.getForecastMonth(), f.getNetGap(),
                    f.getExpectedPayments(), f.getExpectedReceipts());
            String reason = String.format(
                    "{\"month\":\"%s\",\"expectedPayments\":%s,\"expectedReceipts\":%s,\"netGap\":%s,\"forecastRiskLevel\":\"%s\"}",
                    f.getForecastMonth(), f.getExpectedPayments(), f.getExpectedReceipts(),
                    f.getNetGap(), f.getRiskLevel());
            String ruleParams = String.format("{\"snapshotDate\":\"%s\"}", f.getSnapshotDate());
            findings.add(new RiskFinding(TYPE, f.getProjectId(), severity, title,
                    f.getNetGap(), reason,
                    "催收应收/延后非必要付款/安排融资，确认缺口月份资金安排",
                    "FORECAST", f.getId(), ruleParams));
        }
        return findings;
    }
}
