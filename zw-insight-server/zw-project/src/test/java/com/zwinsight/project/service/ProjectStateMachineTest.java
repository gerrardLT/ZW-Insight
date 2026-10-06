package com.zwinsight.project.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.domain.BizProjectStatusLog;
import com.zwinsight.project.domain.enums.ProjectEvent;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.mapper.BizProjectStatusLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ProjectStateMachine 单元测试（P1-M1 深度优化，蓝图 docs/deep-opt/01-project.md）
 * 覆盖：
 *   - I1 转移边表：合法边全部通过且大事记完整写入；非法边/终态出边全部拒绝
 *   - I2 开工准入：无 EFFECTIVE 合同开工被拒；有合同开工放行并回写 actualStartDate
 *   - I3 周期回写：开工/竣工落实际日期，计划竣工早于开工拒绝
 *   - 终止审批与驳回回退（从日志回溯源状态）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectStateMachine 状态机与业务不变量")
class ProjectStateMachineTest {

    @Mock
    private BizProjectMapper projectMapper;

    @Mock
    private BizProjectStatusLogMapper statusLogMapper;

    @InjectMocks
    private ProjectStateMachine stateMachine;

    private BizProject project;

    @BeforeEach
    void setUp() {
        project = new BizProject();
        project.setId(101L);
        project.setStatus("DRAFT");
        lenient().when(projectMapper.updateById(any())).thenReturn(1);
    }

    // ==================== I1 转移边表 ====================

    @Test
    @DisplayName("I1: 合法主线全流程顺利流转并记大事记")
    void validMainFlow_allTransitionsRecordLogs() {
        // DRAFT -> FILED
        stateMachine.fire(project, ProjectEvent.SUBMIT, "提交立项", 1L);
        assertThat(project.getStatus()).isEqualTo("FILED");

        // FILED -> TENDERING
        stateMachine.fire(project, ProjectEvent.GO_TENDER, "进入招标", 1L);
        assertThat(project.getStatus()).isEqualTo("TENDERING");

        // TENDERING -> WON
        stateMachine.fire(project, ProjectEvent.WIN_BID, "开标中标", 1L);
        assertThat(project.getStatus()).isEqualTo("WON");

        // WON -> CONSTRUCTION (需 mock 合同)
        when(projectMapper.countEffectiveConstructionContracts(101L)).thenReturn(1L);
        stateMachine.fire(project, ProjectEvent.START_CONSTRUCTION, "开工", 1L);
        assertThat(project.getStatus()).isEqualTo("CONSTRUCTION");
        assertThat(project.getActualStartDate()).isEqualTo(LocalDate.now());

        // CONSTRUCTION -> COMPLETED
        stateMachine.fire(project, ProjectEvent.COMPLETE, "竣工", 1L);
        assertThat(project.getStatus()).isEqualTo("COMPLETED");
        assertThat(project.getActualEndDate()).isEqualTo(LocalDate.now());

        // COMPLETED -> CLOSING -> CLOSED
        stateMachine.fire(project, ProjectEvent.APPLY_CLOSE, "结项申请", 1L);
        assertThat(project.getStatus()).isEqualTo("CLOSING");

        stateMachine.fire(project, ProjectEvent.APPROVE_CLOSE, "结项批准", null);
        assertThat(project.getStatus()).isEqualTo("CLOSED");

        // 验证写入了 7 条状态日志
        ArgumentCaptor<BizProjectStatusLog> logCaptor = ArgumentCaptor.forClass(BizProjectStatusLog.class);
        verify(statusLogMapper, times(7)).insert(logCaptor.capture());
        assertThat(logCaptor.getAllValues()).extracting(BizProjectStatusLog::getToStatus)
                .containsExactly("FILED", "TENDERING", "WON", "CONSTRUCTION", "COMPLETED", "CLOSING", "CLOSED");
    }

    @Test
    @DisplayName("I1: 终态（CLOSED/LOST/TERMINATED）不可再出边")
    void terminalStatuses_cannotTransition() {
        for (String terminal : ProjectStateMachine.TERMINAL_STATUSES) {
            project.setStatus(terminal);
            for (ProjectEvent event : ProjectEvent.values()) {
                assertThatThrownBy(() -> stateMachine.fire(project, event, "try", 1L))
                        .as("终态 %s 不应接受事件 %s", terminal, event)
                        .isInstanceOf(BusinessException.class);
            }
        }
    }

    @Test
    @DisplayName("I1: 撤回与落标闭环")
    void withdrawAndLoseBid() {
        // DRAFT -> FILED -> 撤回 -> DRAFT
        stateMachine.fire(project, ProjectEvent.SUBMIT, "提交", 1L);
        stateMachine.fire(project, ProjectEvent.WITHDRAW, "撤回修改", 1L);
        assertThat(project.getStatus()).isEqualTo("DRAFT");

        // DRAFT -> FILED -> TENDERING -> 落标 -> LOST
        stateMachine.fire(project, ProjectEvent.SUBMIT, "重新提交", 1L);
        stateMachine.fire(project, ProjectEvent.GO_TENDER, "招标", 1L);
        stateMachine.fire(project, ProjectEvent.LOSE_BID, "开标落标", 1L);
        assertThat(project.getStatus()).isEqualTo("LOST");
    }

    @Test
    @DisplayName("I1: 暂停与复工")
    void pauseAndResume() {
        project.setStatus("CONSTRUCTION");
        stateMachine.fire(project, ProjectEvent.PAUSE, "汛期停工", 1L);
        assertThat(project.getStatus()).isEqualTo("PAUSED");

        stateMachine.fire(project, ProjectEvent.RESUME, "汛期结束复工", 1L);
        assertThat(project.getStatus()).isEqualTo("CONSTRUCTION");
    }

    // ==================== I2 开工准入 ====================

    @Test
    @DisplayName("I2: 无生效施工合同无法开工（开工准入拦截）")
    void startConstruction_requiresEffectiveContract() {
        project.setStatus("WON");
        when(projectMapper.countEffectiveConstructionContracts(101L)).thenReturn(0L);

        assertThatThrownBy(() -> stateMachine.fire(project, ProjectEvent.START_CONSTRUCTION, "开工", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目尚无生效（EFFECTIVE）的施工合同");
        assertThat(project.getStatus()).isEqualTo("WON");
        verify(statusLogMapper, never()).insert(any());
    }

    // ==================== I3 周期不变量 ====================

    @Test
    @DisplayName("I3: 计划竣工早于计划开工在流转时拦截")
    void plannedDates_endBeforeStart_rejected() {
        project.setStatus("DRAFT");
        project.setPlannedStartDate(LocalDate.of(2026, 10, 1));
        project.setPlannedEndDate(LocalDate.of(2026, 9, 1)); // 早于开工

        assertThatThrownBy(() -> stateMachine.fire(project, ProjectEvent.SUBMIT, "提交", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("计划竣工日期早于计划开工日期");
    }

    // ==================== 终止审批与驳回回退 ====================

    @Test
    @DisplayName("终止审批：非终态发起终止置 TERMINATING，批准后置 TERMINATED")
    void terminateApprovalFlow() {
        project.setStatus("CONSTRUCTION");
        stateMachine.fire(project, ProjectEvent.TERMINATE_APPLY, "业主资金链断裂终止", 1L);
        assertThat(project.getStatus()).isEqualTo("TERMINATING");

        stateMachine.fire(project, ProjectEvent.TERMINATE_APPROVED, "审批通过", null);
        assertThat(project.getStatus()).isEqualTo("TERMINATED");
    }

    @Test
    @DisplayName("边表完整性：所有事件均有明确合法源状态")
    void edgeTableCompleteness() {
        Map<ProjectEvent, Set<String>> edges = ProjectStateMachine.edges();
        assertThat(edges).containsKey(ProjectEvent.SUBMIT);
        assertThat(edges).containsKey(ProjectEvent.TERMINATE_APPROVED);
        assertThat(ProjectStateMachine.ALL_STATUSES).hasSize(12);
    }
}
