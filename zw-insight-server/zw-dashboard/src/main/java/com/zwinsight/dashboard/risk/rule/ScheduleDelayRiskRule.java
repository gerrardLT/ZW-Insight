package com.zwinsight.dashboard.risk.rule;

import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.risk.RiskFinding;
import com.zwinsight.dashboard.risk.RiskRule;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.site.domain.BizSchedulePlan;
import com.zwinsight.site.service.SchedulePlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工期滞后风险规则（2026-10-12 新增，风险中心第 8 类）。
 *
 * <p>背景：此前风险中心 7 类规则全部是财务/合规维度（预算超支、预计亏损、资金缺口、
 * 应收逾期、质保金逾期、工资合规、招待费异常），**没有任何一类与工程进度/工期相关**，
 * 而 {@code biz_schedule_plan.taskStatus='DELAYED'} 也没有任何写入点——即系统能录进度、
 * 能看甘特，却从不告诉任何人"哪个任务拖期了"。本规则补上这条监督链路。
 *
 * <pre>
 * 判定式：planEndDate &lt; today 且 taskStatus != 'COMPLETED'
 *         最长滞后 &gt; red-days(默认30天) → RED
 *         最长滞后 ≤ red-days            → YELLOW
 * </pre>
 *
 * <p>口径与 {@link SchedulePlanService#listDelayed()} 完全一致（复用同一判定，避免两处规则漂移）。
 * 按**项目聚合**产出（一个项目一条风险），避免逐任务刷屏；滞后最严重的若干任务写入归因明细。
 *
 * <p>影响金额：工期滞后无直接金额，取 0；具体滞后事实见 reasonDetail。
 */
@Component
@RequiredArgsConstructor
public class ScheduleDelayRiskRule implements RiskRule {

    public static final String TYPE = "SCHEDULE_DELAY";

    private final SchedulePlanService schedulePlanService;
    private final BizProjectMapper projectMapper;

    /** 红色预警的滞后天数（默认 30 天） */
    @Value("${zw.risk.schedule-delay-red-days:30}")
    private long redDays;

    /** 归因明细中最多列出的滞后任务数 */
    private static final int MAX_TASKS_IN_DETAIL = 10;

    @Override
    public String riskType() {
        return TYPE;
    }

    @Override
    public List<RiskFinding> evaluate() {
        List<BizSchedulePlan> delayed = schedulePlanService.listDelayed();
        if (delayed == null || delayed.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, List<BizSchedulePlan>> byProject = delayed.stream()
                .filter(p -> p.getProjectId() != null)
                .collect(Collectors.groupingBy(BizSchedulePlan::getProjectId,
                        LinkedHashMap::new, Collectors.toList()));

        List<RiskFinding> findings = new ArrayList<>();
        for (Map.Entry<Long, List<BizSchedulePlan>> entry : byProject.entrySet()) {
            Long projectId = entry.getKey();
            List<BizSchedulePlan> tasks = entry.getValue();

            int maxDelayDays = tasks.stream()
                    .mapToInt(t -> t.getDelayDays() != null ? t.getDelayDays() : 0)
                    .max().orElse(0);
            String severity = maxDelayDays > redDays
                    ? BizRiskRegister.SEVERITY_RED : BizRiskRegister.SEVERITY_YELLOW;

            BizProject project = projectMapper.selectById(projectId);
            String projectName = project != null ? project.getProjectName() : String.valueOf(projectId);

            String title = String.format("项目【%s】有 %d 项进度任务滞后（最长 %d 天）",
                    projectName, tasks.size(), maxDelayDays);
            String reasonDetail = buildReasonDetail(tasks, maxDelayDays);
            String ruleParams = String.format("{\"redDays\":%d,\"delayedTaskCount\":%d}",
                    redDays, tasks.size());

            findings.add(new RiskFinding(TYPE, projectId, severity, title,
                    BigDecimal.ZERO, reasonDetail,
                    "核对滞后任务的计划日期与实际进展：调整计划、补充资源，或因甲方/设计原因顺延工期的走变更签证",
                    "SCHEDULE", null, ruleParams));
        }
        return findings;
    }

    /** 归因明细：按滞后天数降序列出最严重的若干任务 */
    private String buildReasonDetail(List<BizSchedulePlan> tasks, int maxDelayDays) {
        List<BizSchedulePlan> top = tasks.stream()
                .sorted((a, b) -> Integer.compare(
                        b.getDelayDays() != null ? b.getDelayDays() : 0,
                        a.getDelayDays() != null ? a.getDelayDays() : 0))
                .limit(MAX_TASKS_IN_DETAIL)
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("{\"maxDelayDays\":").append(maxDelayDays).append(",\"tasks\":[");
        for (int i = 0; i < top.size(); i++) {
            BizSchedulePlan t = top.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":").append(t.getId())
                    .append(",\"taskName\":\"").append(escapeJson(t.getTaskName())).append('"')
                    .append(",\"planEndDate\":\"").append(t.getPlanEndDate()).append('"')
                    .append(",\"delayDays\":").append(t.getDelayDays() != null ? t.getDelayDays() : 0)
                    .append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    /** 极简 JSON 字符串转义（任务名可能含引号/反斜杠/换行） */
    private static String escapeJson(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}
