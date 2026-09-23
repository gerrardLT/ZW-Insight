package com.zwinsight.dashboard.risk.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.risk.RiskFinding;
import com.zwinsight.dashboard.risk.RiskRule;
import com.zwinsight.finance.domain.BizWageSpecialAccount;
import com.zwinsight.finance.mapper.BizWageSpecialAccountMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 工资专户合规风险规则（包装既有散点预警入统一台账；国务院令第724号合规要求）：
 * <pre>
 * complianceFlag = OVERDUE（拨付逾期，条例第24条）  → RED
 * complianceFlag = INSUFFICIENT（拨付不足）          → YELLOW
 * </pre>
 * 数据源：biz_wage_special_account（status=ACTIVE 且 complianceFlag != COMPLIANT，
 * 与 FundDashboardService.countWageWarnings 同口径）。
 */
@Component
@RequiredArgsConstructor
public class WageComplianceRiskRule implements RiskRule {

    public static final String TYPE = "WAGE_COMPLIANCE";

    private final BizWageSpecialAccountMapper wageAccountMapper;
    private final BizProjectMapper projectMapper;

    @Override
    public String riskType() {
        return TYPE;
    }

    @Override
    public List<RiskFinding> evaluate() {
        List<BizWageSpecialAccount> accounts = wageAccountMapper.selectList(
                new LambdaQueryWrapper<BizWageSpecialAccount>()
                        .eq(BizWageSpecialAccount::getStatus, BizWageSpecialAccount.STATUS_ACTIVE)
                        .ne(BizWageSpecialAccount::getComplianceFlag,
                                BizWageSpecialAccount.COMPLIANCE_COMPLIANT));
        List<RiskFinding> findings = new ArrayList<>();
        for (BizWageSpecialAccount account : accounts) {
            boolean overdue = BizWageSpecialAccount.COMPLIANCE_OVERDUE.equals(account.getComplianceFlag());
            String severity = overdue
                    ? BizRiskRegister.SEVERITY_RED : BizRiskRegister.SEVERITY_YELLOW;
            BizProject project = projectMapper.selectById(account.getProjectId());
            String projectName = project != null ? project.getProjectName() : String.valueOf(account.getProjectId());
            String flagDesc = overdue ? "拨付逾期（超1个月无人工费到账）" : "拨付不足";
            String title = String.format("项目【%s】农民工工资专户%s（账户 %s）",
                    projectName, flagDesc, account.getAccountNo());
            // 影响金额：工资预算与已拨付差额（数据不全时按 0，如实呈现）
            BigDecimal budget = account.getWageBudget() != null ? account.getWageBudget() : BigDecimal.ZERO;
            BigDecimal received = account.getTotalReceived() != null ? account.getTotalReceived() : BigDecimal.ZERO;
            BigDecimal impact = budget.subtract(received).max(BigDecimal.ZERO);
            String reason = String.format(
                    "{\"accountId\":%d,\"accountNo\":\"%s\",\"complianceFlag\":\"%s\",\"wageBudget\":%s,\"totalReceived\":%s,\"totalPaid\":%s}",
                    account.getId(), account.getAccountNo(), account.getComplianceFlag(),
                    budget, received,
                    account.getTotalPaid() != null ? account.getTotalPaid() : BigDecimal.ZERO);
            findings.add(new RiskFinding(TYPE, account.getProjectId(), severity, title,
                    impact, reason,
                    overdue ? "立即核实人工费拨付，逾期拨付违反条例第24条，存在行政处罚风险"
                            : "补足工资专户拨付缺口，确保按月足额发放",
                    "WAGE_ACCOUNT", account.getId(), null));
        }
        return findings;
    }
}
