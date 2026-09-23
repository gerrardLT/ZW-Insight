package com.zwinsight.dashboard.risk.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.risk.RiskFinding;
import com.zwinsight.dashboard.risk.RiskRule;
import com.zwinsight.finance.domain.BizReceivable;
import com.zwinsight.finance.mapper.BizReceivableMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 应收逾期风险规则（驾驶舱 V1 §10 回款风险）：
 * <pre>
 * 逾期 &gt; red-days(默认90天)   → RED
 * 逾期 ≤ red-days 的 OPEN 应收 → YELLOW（按项目聚合）
 * </pre>
 * 数据源：biz_receivable OPEN 记录（V2026_57 结算驱动生成，回款 FIFO 核销）。
 * 粒度 = 项目（同项目多笔逾期聚合为一条，影响金额=逾期余额合计，
 * reasonDetail 逐笔列出到期日/金额/逾期天数供下钻）。
 */
@Component
@RequiredArgsConstructor
public class ReceivableOverdueRiskRule implements RiskRule {

    public static final String TYPE = "RECEIVABLE_OVERDUE";

    private final BizReceivableMapper receivableMapper;
    private final BizProjectMapper projectMapper;

    /** 红色预警逾期天数（默认 90 天，对齐账龄 OVER_90 桶） */
    @Value("${zw.risk.receivable-red-days:90}")
    private long redDays;

    @Override
    public String riskType() {
        return TYPE;
    }

    @Override
    public List<RiskFinding> evaluate() {
        LocalDate today = LocalDate.now();
        List<BizReceivable> opens = receivableMapper.selectList(new LambdaQueryWrapper<BizReceivable>()
                .eq(BizReceivable::getStatus, BizReceivable.STATUS_OPEN)
                .lt(BizReceivable::getDueDate, today));
        // 按项目聚合
        Map<Long, List<BizReceivable>> byProject = new HashMap<>();
        for (BizReceivable r : opens) {
            BigDecimal balance = r.getReceivableAmount().subtract(
                    r.getWrittenOffAmount() != null ? r.getWrittenOffAmount() : BigDecimal.ZERO);
            if (balance.signum() > 0) {
                byProject.computeIfAbsent(r.getProjectId(), k -> new ArrayList<>()).add(r);
            }
        }

        List<RiskFinding> findings = new ArrayList<>();
        for (Map.Entry<Long, List<BizReceivable>> entry : byProject.entrySet()) {
            BigDecimal totalOverdue = BigDecimal.ZERO;
            long maxOverdueDays = 0;
            StringBuilder items = new StringBuilder("[");
            for (BizReceivable r : entry.getValue()) {
                BigDecimal balance = r.getReceivableAmount().subtract(
                        r.getWrittenOffAmount() != null ? r.getWrittenOffAmount() : BigDecimal.ZERO);
                long days = ChronoUnit.DAYS.between(r.getDueDate(), today);
                totalOverdue = totalOverdue.add(balance);
                maxOverdueDays = Math.max(maxOverdueDays, days);
                if (items.length() > 1) {
                    items.append(',');
                }
                items.append(String.format(
                        "{\"receivableId\":%d,\"dueDate\":\"%s\",\"balance\":%s,\"overdueDays\":%d}",
                        r.getId(), r.getDueDate(), balance, days));
            }
            items.append(']');
            String severity = maxOverdueDays > redDays
                    ? BizRiskRegister.SEVERITY_RED : BizRiskRegister.SEVERITY_YELLOW;
            BizProject project = projectMapper.selectById(entry.getKey());
            String projectName = project != null ? project.getProjectName() : String.valueOf(entry.getKey());
            String title = String.format("项目【%s】应收逾期 %s 元（最长逾期 %d 天，%d 笔）",
                    projectName, totalOverdue, maxOverdueDays, entry.getValue().size());
            String ruleParams = String.format("{\"redDays\":%d,\"maxOverdueDays\":%d}", redDays, maxOverdueDays);
            findings.add(new RiskFinding(TYPE, entry.getKey(), severity, title,
                    totalOverdue, items.toString(),
                    "核对甲方审核/开票状态，启动催收并更新收款计划",
                    "RECEIVABLE", null, ruleParams));
        }
        return findings;
    }
}
