package com.zwinsight.dashboard.risk.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.risk.RiskFinding;
import com.zwinsight.dashboard.risk.RiskRule;
import com.zwinsight.dashboard.service.CockpitService;
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
 * 未来90天无资金缺口           → 不产生风险（🟢）
 * 存在可覆盖的缺口（可用资金 ≥ 缺口） → YELLOW（🟡）
 * 存在不可覆盖的重大缺口         → RED（🔴）
 * </pre>
 * <p><b>2026-09-24 修正</b>：原实现按 {@code forecast.riskLevel}（收款覆盖率）判级，
 * 完全没有“可覆盖”维度——不对比账户可用资金，导致：① 🟢 档永不产生（只要有缺口就 YELLOW 起）；
 * ② 账面资金能覆盖的缺口也被报为严重风险。现按文档判定式，引入
 * {@link CockpitService#getAvailableFund()}（账户余额快照 + 窗口内预计回款）作为覆盖能力基准。</p>
 * <p>项目级缺口同样以公司可用资金判定覆盖能力（资金由公司统筹），
 * 并在 reasonDetail 中如实记录 availableFund/coverable 供追溯。</p>
 * <p>数据源：biz_fund_rolling_forecast 快照（FundForecastTask 每日 01:15 刷新；
 * 付款侧=已批未付申请含逾期，收款侧=应收台账到期，V2026_56/57/63 口径）。
 * 粒度 = 项目 × 预测月份（公司级快照 projectId=NULL 同样入台账）。</p>
 */
@Component
@RequiredArgsConstructor
public class FundGapRiskRule implements RiskRule {

    public static final String TYPE = "FUND_GAP";

    private final BizFundRollingForecastMapper rollingForecastMapper;
    private final BizProjectMapper projectMapper;
    private final CockpitService cockpitService;

    @Override
    public String riskType() {
        return TYPE;
    }

    @Override
    public List<RiskFinding> evaluate() {
        List<BizFundRollingForecast> forecasts = rollingForecastMapper.selectList(
                new LambdaQueryWrapper<BizFundRollingForecast>()
                        .gt(BizFundRollingForecast::getNetGap, BigDecimal.ZERO));
        if (forecasts.isEmpty()) {
            return List.of();
        }
        // 可用资金取一次（循环内复用，避免逐条重复查库）
        BigDecimal availableFund = cockpitService.getAvailableFund();
        List<RiskFinding> findings = new ArrayList<>();
        for (BizFundRollingForecast f : forecasts) {
            // §15 判定式：可用资金 ≥ 缺口 → 可覆盖（YELLOW）；否则重大缺口（RED）
            boolean coverable = availableFund.compareTo(f.getNetGap()) >= 0;
            String severity = coverable
                    ? BizRiskRegister.SEVERITY_YELLOW : BizRiskRegister.SEVERITY_RED;
            String scope;
            if (f.getProjectId() != null) {
                BizProject project = projectMapper.selectById(f.getProjectId());
                scope = "项目【" + (project != null ? project.getProjectName() : f.getProjectId()) + "】";
            } else {
                scope = "公司整体";
            }
            String title = String.format("%s %s 预计资金缺口 %s 元（预计付款 %s / 预计收款 %s / 可用资金 %s，%s）",
                    scope, f.getForecastMonth(), f.getNetGap(),
                    f.getExpectedPayments(), f.getExpectedReceipts(), availableFund,
                    coverable ? "账面可覆盖" : "账面无法覆盖");
            String reason = String.format(
                    "{\"month\":\"%s\",\"expectedPayments\":%s,\"expectedReceipts\":%s,\"netGap\":%s,"
                            + "\"availableFund\":%s,\"coverable\":%s,\"forecastRiskLevel\":\"%s\"}",
                    f.getForecastMonth(), f.getExpectedPayments(), f.getExpectedReceipts(),
                    f.getNetGap(), availableFund, coverable, f.getRiskLevel());
            String ruleParams = String.format("{\"snapshotDate\":\"%s\"}", f.getSnapshotDate());
            findings.add(new RiskFinding(TYPE, f.getProjectId(), severity, title,
                    f.getNetGap(), reason,
                    coverable
                            ? "缺口在账面可用资金覆盖范围内：确认回款到账节奏即可，无需紧急筹资"
                            : "催收应收/延后非必要付款/安排融资，确认缺口月份资金安排",
                    "FORECAST", f.getId(), ruleParams));
        }
        return findings;
    }
}
