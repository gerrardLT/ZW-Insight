package com.zwinsight.project.service;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ProjectService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock private BizProjectMapper projectMapper;
    @Mock private SerialNumberService serialNumberService;
    @Mock private ProjectMemberService memberService;
    @Mock private ApprovalService approvalService;

    @InjectMocks
    private ProjectService projectService;

    private BizProject sampleProject;

    @BeforeEach
    void setUp() {
        sampleProject = new BizProject();
        sampleProject.setId(1L);
        sampleProject.setProjectName("测试项目");
        sampleProject.setStatus("DRAFT");
    }

    // =====================================================================
    // save
    // =====================================================================

    @Test
    @DisplayName("新增项目：自动生成编号 + DRAFT 初始化 + 零金额初始化")
    void testSave_autoNumberingAndDefaults() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(100L);

            when(serialNumberService.generate("PROJECT")).thenReturn("PRJ-2026-001");
            when(projectMapper.insert(any(BizProject.class))).thenReturn(1);

            BizProject project = new BizProject();
            project.setProjectName("新项目");
            projectService.save(project);

            assertThat(project.getProjectCode()).isEqualTo("PRJ-2026-001");
            assertThat(project.getStatus()).isEqualTo("DRAFT");
            assertThat(project.getBudgetAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(project.getContractAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(project.getCumulativeOutput()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(project.getSettlementAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(project.getTotalIncome()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(project.getTotalExpense()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(project.getTotalOtherPayment()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(projectMapper).insert(project);
        }
    }

    @Test
    @DisplayName("新增项目：金额已有值时不覆盖")
    void testSave_existingAmountsPreserved() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(100L);

            when(serialNumberService.generate("PROJECT")).thenReturn("PRJ-002");
            when(projectMapper.insert(any(BizProject.class))).thenReturn(1);

            BizProject project = new BizProject();
            project.setProjectName("带预算项目");
            project.setBudgetAmount(new BigDecimal("500000.00"));
            project.setContractAmount(new BigDecimal("300000.00"));
            projectService.save(project);

            // 已有值不应被覆盖为 ZERO
            assertThat(project.getBudgetAmount()).isEqualByComparingTo(new BigDecimal("500000.00"));
            assertThat(project.getContractAmount()).isEqualByComparingTo(new BigDecimal("300000.00"));
            // 未设置的字段应被初始化为 ZERO
            assertThat(project.getCumulativeOutput()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(project.getSettlementAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(project.getTotalIncome()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(project.getTotalExpense()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(project.getTotalOtherPayment()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    @DisplayName("新增项目：创建人自动添加为项目经理")
    void testSave_addCreatorAsManager() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(100L);

            when(serialNumberService.generate("PROJECT")).thenReturn("PRJ-001");
            when(projectMapper.insert(any(BizProject.class))).thenAnswer(inv -> {
                BizProject p = inv.getArgument(0);
                p.setId(1L);
                return 1;
            });

            BizProject project = new BizProject();
            project.setProjectName("新项目");
            projectService.save(project);

            verify(memberService).addCreatorAsProjectManager(1L, 100L, null);
        }
    }

    // =====================================================================
    // getById
    // =====================================================================

    @Test
    @DisplayName("查询详情：存在则返回")
    void testGetById_found() {
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);

        BizProject result = projectService.getById(1L);

        assertThat(result.getProjectName()).isEqualTo("测试项目");
    }

    @Test
    @DisplayName("查询详情：不存在抛异常")
    void testGetById_notFound() {
        when(projectMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> projectService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
    }

    // =====================================================================
    // update
    // =====================================================================

    @Test
    @DisplayName("更新：DRAFT 可编辑")
    void testUpdate_draftAllowed() {
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);

        BizProject update = new BizProject();
        update.setId(1L);
        update.setProjectName("修改后名称");
        projectService.update(update);

        verify(projectMapper).updateById(update);
    }

    @Test
    @DisplayName("更新：非 DRAFT 拒绝")
    void testUpdate_nonDraftRejected() {
        sampleProject.setStatus("FILED");
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);

        BizProject update = new BizProject();
        update.setId(1L);

        assertThatThrownBy(() -> projectService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可编辑");
    }

    // =====================================================================
    // delete
    // =====================================================================

    @Test
    @DisplayName("删除：DRAFT 可删")
    void testDelete_draftAllowed() {
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);

        projectService.delete(1L);

        verify(projectMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除：非 DRAFT 拒绝")
    void testDelete_nonDraftRejected() {
        sampleProject.setStatus("FILED");
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);

        assertThatThrownBy(() -> projectService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("删除：项目不存在抛异常")
    void testDelete_notFound() {
        when(projectMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> projectService.delete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
    }

    // =====================================================================
    // submit
    // =====================================================================

    @Test
    @DisplayName("提交：DRAFT→FILED")
    void testSubmit_draftToFiled() {
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);

        projectService.submit(1L);

        verify(projectMapper).updateById(argThat(p -> "FILED".equals(p.getStatus())));
    }

    @Test
    @DisplayName("提交：非 DRAFT 拒绝")
    void testSubmit_nonDraftRejected() {
        sampleProject.setStatus("FILED");
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);

        assertThatThrownBy(() -> projectService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    // =====================================================================
    // updateStatus
    // =====================================================================

    @Test
    @DisplayName("更新状态：正常更新")
    void testUpdateStatus_success() {
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);

        projectService.updateStatus(1L, "COMPLETED");

        verify(projectMapper).updateById(argThat(p -> "COMPLETED".equals(p.getStatus())));
    }

    @Test
    @DisplayName("更新状态：项目不存在抛异常")
    void testUpdateStatus_notFound() {
        when(projectMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> projectService.updateStatus(999L, "COMPLETED"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
    }

    // =====================================================================
    // closeProject
    // =====================================================================

    @Test
    @DisplayName("结项：非 COMPLETED 状态抛异常")
    void testCloseProject_statusNotCompleted() {
        // 项目状态为 ACTIVE（非 COMPLETED），结项条件不满足
        sampleProject.setStatus("ACTIVE");
        sampleProject.setTotalIncome(BigDecimal.ZERO);
        sampleProject.setCumulativeOutput(BigDecimal.ZERO);
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);

        assertThatThrownBy(() -> projectService.closeProject(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法结项");
    }

    @Test
    @DisplayName("结项：DRAFT 状态抛异常（未进入施工阶段）")
    void testCloseProject_draftStatus() {
        sampleProject.setStatus("DRAFT");
        sampleProject.setTotalIncome(BigDecimal.ZERO);
        sampleProject.setCumulativeOutput(BigDecimal.ZERO);
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);

        assertThatThrownBy(() -> projectService.closeProject(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法结项");
    }

    @Test
    @DisplayName("结项：项目不存在抛异常")
    void testCloseProject_notFound() {
        when(projectMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> projectService.closeProject(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
    }

    @Test
    @DisplayName("结项：条件满足时发起审批，状态置 CLOSING 并记录流程实例ID")
    void testCloseProject_startsApproval() {
        sampleProject.setStatus("COMPLETED");
        sampleProject.setTotalIncome(new BigDecimal("1000"));
        sampleProject.setCumulativeOutput(new BigDecimal("1000"));
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);
        when(approvalService.startProcess(eq("PROJECT_CLOSE"), eq(1L), eq("project_close_approval"), anyMap()))
                .thenReturn("proc-1");

        projectService.closeProject(1L);

        verify(approvalService).startProcess(eq("PROJECT_CLOSE"), eq(1L), eq("project_close_approval"), anyMap());
        verify(projectMapper).updateById(argThat(p ->
                "CLOSING".equals(p.getStatus()) && "proc-1".equals(p.getWorkflowInstanceId())));
    }

    // =====================================================================
    // onCloseApproved / onCloseRejected
    // =====================================================================

    @Test
    @DisplayName("结项审批通过：CLOSING → CLOSED")
    void testOnCloseApproved_toClosed() {
        sampleProject.setStatus("CLOSING");
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);

        projectService.onCloseApproved(1L);

        verify(projectMapper).updateById(argThat(p -> "CLOSED".equals(p.getStatus())));
    }

    @Test
    @DisplayName("结项审批驳回：CLOSING → COMPLETED（回退）")
    void testOnCloseRejected_backToCompleted() {
        sampleProject.setStatus("CLOSING");
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);

        projectService.onCloseRejected(1L);

        verify(projectMapper).updateById(argThat(p -> "COMPLETED".equals(p.getStatus())));
    }

    @Test
    @DisplayName("结项审批通过：项目不存在抛异常")
    void testOnCloseApproved_notFound() {
        when(projectMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> projectService.onCloseApproved(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
    }
}
