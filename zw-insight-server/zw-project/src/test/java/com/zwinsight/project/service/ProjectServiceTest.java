package com.zwinsight.project.service;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.event.project.ProjectDeletedEvent;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.mapper.BizProjectMemberMapper;
import com.zwinsight.project.mapper.BizProjectWbsNodeMapper;
import com.zwinsight.project.mapper.SysUserProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
    // R7-02 级联删除新增依赖：不声明则 @InjectMocks 会给它们传 null，delete 用例直接 NPE
    @Mock private BizProjectMemberMapper projectMemberMapper;
    @Mock private BizProjectWbsNodeMapper wbsNodeMapper;
    @Mock private SysUserProjectMapper userProjectMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

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
    @DisplayName("删除：R7-02 级联——自有子表清理 + 发布 ProjectDeletedEvent，且事件在删主表之后")
    void testDelete_cascadesOwnTablesAndPublishesEvent() {
        sampleProject.setTenantId(9999L);
        sampleProject.setProjectCode("PRJ-T9-0001");
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);
        when(projectMemberMapper.logicDeleteByProjectId(1L)).thenReturn(3);
        when(wbsNodeMapper.logicDeleteByProjectId(1L)).thenReturn(7);
        when(userProjectMapper.deleteByProjectId(1L)).thenReturn(2);

        projectService.delete(1L);

        // 第 1 层：本模块自有子表必须逐一清理
        verify(projectMemberMapper).logicDeleteByProjectId(1L);
        verify(wbsNodeMapper).logicDeleteByProjectId(1L);
        verify(userProjectMapper).deleteByProjectId(1L);

        // 第 2 层：事件载荷必须带齐 projectId/tenantId/code/name，否则各模块无法定位与记日志
        ArgumentCaptor<ProjectDeletedEvent> captor = ArgumentCaptor.forClass(ProjectDeletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ProjectDeletedEvent event = captor.getValue();
        assertThat(event.getProjectId()).isEqualTo(1L);
        assertThat(event.getTenantId()).isEqualTo(9999L);
        assertThat(event.getProjectCode()).isEqualTo("PRJ-T9-0001");
        assertThat(event.getProjectName()).isEqualTo("测试项目");

        // 顺序：先删主表再发事件，避免监听方回查项目时读到「仍存在」的中间态
        InOrder inOrder = inOrder(projectMemberMapper, projectMapper, eventPublisher);
        inOrder.verify(projectMemberMapper).logicDeleteByProjectId(1L);
        inOrder.verify(projectMapper).deleteById(1L);
        inOrder.verify(eventPublisher).publishEvent(any(ProjectDeletedEvent.class));
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
    @DisplayName("删除：E2E_TEST_ 标记数据非DRAFT放行（E2eTestGuard）")
    void testDelete_e2eMarkerBypass() {
        BizProject e2e = new BizProject();
        e2e.setId(2L);
        e2e.setStatus("FILED");
        e2e.setProjectName("E2E_TEST_1723900000000_项目");
        when(projectMapper.selectById(2L)).thenReturn(e2e);

        projectService.delete(2L);

        verify(projectMapper).deleteById(2L);
    }

    @Test
    @DisplayName("删除：存在关联投标报名拦截（2026-08-21 台账缺陷#2 引用检查）")
    void testDelete_withTenderRegisters_rejected() {
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);
        when(projectMapper.countTenderRegisters(1L)).thenReturn(2L);

        assertThatThrownBy(() -> projectService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目存在关联投标报名，不可删除");

        verify(projectMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除：无关联投标报名放行")
    void testDelete_noTenderRegisters_allowed() {
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);
        when(projectMapper.countTenderRegisters(1L)).thenReturn(0L);

        projectService.delete(1L);

        verify(projectMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除：项目不存在抛异常")
    void testDelete_notFound() {
        when(projectMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> projectService.delete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
    }

    @Test
    @DisplayName("删除：任一前置校验失败时不得触发级联与事件（R7-02 防半成品状态）")
    void testDelete_rejected_noCascadeNoEvent() {
        sampleProject.setStatus("FILED");
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);

        assertThatThrownBy(() -> projectService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");

        verify(projectMemberMapper, never()).logicDeleteByProjectId(anyLong());
        verify(wbsNodeMapper, never()).logicDeleteByProjectId(anyLong());
        verify(userProjectMapper, never()).deleteByProjectId(anyLong());
        verify(eventPublisher, never()).publishEvent(any(ProjectDeletedEvent.class));
    }

    // =====================================================================
    // batchDelete
    // =====================================================================

    @Test
    @DisplayName("批量删除：全部 DRAFT 且无关联报名时逐条删除")
    void testBatchDelete_allDraft_deletesAll() {
        BizProject p2 = new BizProject();
        p2.setId(2L);
        p2.setStatus("DRAFT");
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);
        when(projectMapper.selectById(2L)).thenReturn(p2);

        projectService.batchDelete(java.util.List.of(1L, 2L));

        verify(projectMapper).deleteById(1L);
        verify(projectMapper).deleteById(2L);
    }

    @Test
    @DisplayName("批量删除：空列表拒绝，不执行任何删除")
    void testBatchDelete_emptyList_rejected() {
        assertThatThrownBy(() -> projectService.batchDelete(java.util.List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ID 列表不能为空");

        verifyNoInteractions(projectMapper);
    }

    @Test
    @DisplayName("批量删除：含非 DRAFT 项抛异常（整体事务回滚）")
    void testBatchDelete_nonDraftItem_throws() {
        BizProject filed = new BizProject();
        filed.setId(2L);
        filed.setStatus("FILED");
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);
        when(projectMapper.selectById(2L)).thenReturn(filed);

        assertThatThrownBy(() -> projectService.batchDelete(java.util.List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
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
        when(projectMapper.updateById(any())).thenReturn(1);

        projectService.updateStatus(1L, "COMPLETED");

        verify(projectMapper).updateById(argThat(p -> "COMPLETED".equals(p.getStatus())));
    }

    @Test
    @DisplayName("更新状态：乐观锁返回0行（并发修改）时抛错不丢失更新（待决策#4B）")
    void testUpdateStatus_concurrentConflict_throws() {
        when(projectMapper.selectById(1L)).thenReturn(sampleProject);
        when(projectMapper.updateById(any())).thenReturn(0);

        assertThatThrownBy(() -> projectService.updateStatus(1L, "CONSTRUCTION"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("并发修改");
    }

    @Test
    @DisplayName("更新状态：项目不存在抛异常")
    void testUpdateStatus_notFound() {
        when(projectMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> projectService.updateStatus(999L, "COMPLETED"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
    }

    @Test
    @DisplayName("更新状态：终态项目（CLOSED/COMPLETED）拒绝变更（D1 状态机守卫）")
    void testUpdateStatus_terminalProject_rejected() {
        for (String terminalStatus : new String[]{"CLOSED", "COMPLETED"}) {
            BizProject project = new BizProject();
            project.setId(2L);
            project.setStatus(terminalStatus);
            when(projectMapper.selectById(2L)).thenReturn(project);

            assertThatThrownBy(() -> projectService.updateStatus(2L, "TENDERING"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不可变更状态");
        }
        verify(projectMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("更新状态：结项审批中（CLOSING）拒绝变更（D4 状态机守卫）")
    void testUpdateStatus_closingRejected() {
        BizProject project = new BizProject();
        project.setId(3L);
        project.setStatus("CLOSING");
        when(projectMapper.selectById(3L)).thenReturn(project);

        assertThatThrownBy(() -> projectService.updateStatus(3L, "CONSTRUCTION"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结项审批中");

        verify(projectMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("更新状态：非法目标状态拒绝（D4 目标白名单）")
    void testUpdateStatus_invalidTargetRejected() {
        BizProject project = new BizProject();
        project.setId(4L);
        project.setStatus("FILED");
        when(projectMapper.selectById(4L)).thenReturn(project);

        assertThatThrownBy(() -> projectService.updateStatus(4L, "HACKED_STATUS"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法的项目状态");
        assertThatThrownBy(() -> projectService.updateStatus(4L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法的项目状态");

        verify(projectMapper, never()).updateById(any());
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
        // 条件4：存在已审批的最终结算单
        when(projectMapper.countApprovedSettlement(1L)).thenReturn(1L);
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

    // =====================================================================
    // portfolio
    // =====================================================================

    private BizProject portfolioProject(String status, String contractAmount, String budgetAmount, String output) {
        BizProject p = new BizProject();
        p.setStatus(status);
        p.setContractAmount(new BigDecimal(contractAmount));
        p.setBudgetAmount(new BigDecimal(budgetAmount));
        p.setCumulativeOutput(new BigDecimal(output));
        return p;
    }

    @Test
    @DisplayName("项目组合看板：状态 × 金额分布聚合")
    void testPortfolio_aggregatesByStatus() {
        when(projectMapper.selectList(any())).thenReturn(java.util.List.of(
                portfolioProject("CONSTRUCTION", "100000", "90000", "30000"),
                portfolioProject("CONSTRUCTION", "200000", "180000", "50000"),
                portfolioProject("CLOSED", "50000", "45000", "50000")));

        com.zwinsight.project.vo.ProjectPortfolioVO vo = projectService.portfolio();

        assertThat(vo.getTotalProjectCount()).isEqualTo(3);
        assertThat(vo.getTotalContractAmount()).isEqualByComparingTo("350000");
        assertThat(vo.getStatusList()).hasSize(2);
        // TreeMap 按状态字典序：CLOSED 在前
        assertThat(vo.getStatusList().get(0).getStatus()).isEqualTo("CLOSED");
        assertThat(vo.getStatusList().get(1).getStatus()).isEqualTo("CONSTRUCTION");
        assertThat(vo.getStatusList().get(1).getCount()).isEqualTo(2);
        assertThat(vo.getStatusList().get(1).getContractAmount()).isEqualByComparingTo("300000");
        assertThat(vo.getStatusList().get(1).getCumulativeOutput()).isEqualByComparingTo("80000");
    }

    @Test
    @DisplayName("项目组合看板：空项目列表返回零值")
    void testPortfolio_empty() {
        when(projectMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());

        com.zwinsight.project.vo.ProjectPortfolioVO vo = projectService.portfolio();

        assertThat(vo.getTotalProjectCount()).isZero();
        assertThat(vo.getTotalContractAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getStatusList()).isEmpty();
    }
}
