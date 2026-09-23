package com.zwinsight.dashboard.risk.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.risk.RiskFinding;
import com.zwinsight.dashboard.risk.RiskRule;
import com.zwinsight.finance.domain.BizRetentionMoney;
import com.zwinsight.finance.mapper.BizRetentionMoneyMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 质保金逾期未退风险规则（包装既有散点预警入统一台账，只读引用不迁移原逻辑）：
 * <pre>
 * 逾期未退 &gt; red-days(默认90天) → RED
 * 逾期未退 ≤ red-days           → YELLOW
 * </pre>
 * 数据源：biz_retention_money（status != RETURNED 且 expireDate 已过，
 * 与 RetentionWarningTask 的 OVERDUE 判定同口径）。影响金额 = 未退余额。
 */
@Component
@RequiredArgsConstructor
public class RetentionOverdueRiskRule implements RiskRule {

    public static final String TYPE = "RETENTION_OVERDUE";

    private final BizRetentionMoneyMapper retentionMoneyMapper;
    private final BizProjectMapper projectMapper;

    /** 红色预警逾期天数（默认 90 天） */
    @Value("${zw.risk.retention-red-days:90}")
    private long redDays;

    @Override
    public String riskType() {
        return TYPE;
    }

    @Override
    public List<RiskFinding> evaluate() {
        LocalDate today = LocalDate.now();
        List<BizRetentionMoney> records = retentionMoneyMapper.selectList(
                new LambdaQueryWrapper<BizRetentionMoney>()
                        .ne(BizRetentionMoney::getStatus, "RETURNED")
                        .lt(BizRetentionMoney::getExpireDate, today));
        List<RiskFinding> findings = new ArrayList<>();
        for (BizRetentionMoney r : records) {
            BigDecimal returned = r.getReturnedAmount() != null ? r.getReturnedAmount() : BigDecimal.ZERO;
            BigDecimal balance = (r.getRetentionAmount() != null ? r.getRetentionAmount() : BigDecimal.ZERO)
                    .subtract(returned);
            if (balance.signum() <= 0) {
                continue;
            }
            long overdueDays = ChronoUnit.DAYS.between(r.getExpireDate(), today);
            String severity = overdueDays > redDays
                    ? BizRiskRegister.SEVERITY_RED : BizRiskRegister.SEVERITY_YELLOW;
            BizProject project = projectMapper.selectById(r.getProjectId());
            String projectName = project != null ? project.getProjectName() : String.valueOf(r.getProjectId());
            String title = String.format("项目【%s】质保金 %s 元到期未退（逾期 %d 天）",
                    projectName, balance, overdueDays);
            String reason = String.format(
                    "{\"retentionId\":%d,\"expireDate\":\"%s\",\"retentionAmount\":%s,\"returnedAmount\":%s,\"overdueDays\":%d}",
                    r.getId(), r.getExpireDate(), r.getRetentionAmount(), returned, overdueDays);
            String ruleParams = String.format("{\"redDays\":%d}", redDays);
            findings.add(new RiskFinding(TYPE, r.getProjectId(), severity, title,
                    balance, reason,
                    "向甲方发起质保金退还申请，逾期超 90 天建议发函催告",
                    "RETENTION", r.getId(), ruleParams));
        }
        return findings;
    }
}
