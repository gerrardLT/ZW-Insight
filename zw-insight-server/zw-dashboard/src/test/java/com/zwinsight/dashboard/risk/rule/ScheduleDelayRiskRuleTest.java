package com.zwinsight.dashboard.risk.rule;

import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.risk.RiskFinding;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.site.domain.BizSchedulePlan;
import com.zwinsight.site.service.SchedulePlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 工期滞后风险规则单元测试（2026-10-12）
 */
@ExtendWith(MockitoExtension.class)
class ScheduleDelayRiskRuleTest {

    @Mock
    private SchedulePlanService schedulePlanService;

    @Mock
    private BizProjectMapper projectMapper;

    private ScheduleDelayRiskRule buildRule() {
        ScheduleDelayRiskRule rule = new ScheduleDelayRiskRule(schedulePlanService, projectMapper);
        ReflectionTestUtils.setField(rule, "redDays", 30L);
        return rule;
    }

    private BizSchedulePlan delayedPlan(Long id, Long projectId, String name, int delayDays) {
        BizSchedulePlan p = new BizSchedulePlan();
        p.setId(id);
        p.setProjectId(projectId);
        p.setTaskName(name);
        p.setPlanEndDate(LocalDate.now().minusDays(delayDays));
        p.setDelayed(true);
        p.setDelayDays(delayDays);
        p.setTaskStatus("DELAYED");
        return p;
    }

    @Test
    @DisplayName("无滞后任务时返回空集合")
    void evaluate_noDelayedTasks_returnsEmpty() {
        when(schedulePlanService.listDelayed()).thenReturn(List.of());

        List<RiskFinding> findings = buildRule().evaluate();

        assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("按项目聚合：最长滞后 > 30 天判 RED，≤ 30 天判 YELLOW")
    void evaluate_groupsByProjectAndAssignsSeverity() {
        when(schedulePlanService.listDelayed()).thenReturn(List.of(
                delayedPlan(101L, 1L, "基础开挖", 45),
                delayedPlan(102L, 1L, "防水施工", 10),
                delayedPlan(201L, 2L, "路基回填", 15)));

        BizProject p1 = new BizProject();
        p1.setId(1L);
        p1.setProjectName("滨江花园一期");
        when(projectMapper.selectById(1L)).thenReturn(p1);

        BizProject p2 = new BizProject();
        p2.setId(2L);
        p2.setProjectName("城南市政道路");
        when(projectMapper.selectById(2L)).thenReturn(p2);

        List<RiskFinding> findings = buildRule().evaluate();

        assertThat(findings).hasSize(2);

        RiskFinding f1 = findings.get(0);
        assertThat(f1.riskType()).isEqualTo(ScheduleDelayRiskRule.TYPE);
        assertThat(f1.projectId()).isEqualTo(1L);
        assertThat(f1.severity()).isEqualTo(BizRiskRegister.SEVERITY_RED);
        assertThat(f1.title()).contains("滨江花园一期").contains("2 项进度任务滞后").contains("45 天");
        assertThat(f1.bizRefType()).isEqualTo("SCHEDULE");
        assertThat(f1.reasonDetail()).contains("基础开挖").contains("\"maxDelayDays\":45");

        RiskFinding f2 = findings.get(1);
        assertThat(f2.projectId()).isEqualTo(2L);
        assertThat(f2.severity()).isEqualTo(BizRiskRegister.SEVERITY_YELLOW);
        assertThat(f2.title()).contains("城南市政道路").contains("1 项进度任务滞后").contains("15 天");
    }
}
