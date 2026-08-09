package com.zwinsight.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.domain.BizProjectMember;
import com.zwinsight.project.domain.SysUserProject;
import com.zwinsight.project.domain.dto.ProjectMemberAddRequest;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.mapper.BizProjectMemberMapper;
import com.zwinsight.project.mapper.SysUserProjectMapper;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 项目模块变异补强测试（测试成熟度 2.1.3）
 * <p>
 * 针对 PIT 存活变异补断言：结项四条件逐项判定与容差边界、结项审批流发起、
 * 状态守卫（update/submit/onCloseRejected）、成员添加字段写入与 sys_user_project 同步幂等、
 * 唯一项目经理保护的 PM 计数。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("项目模块变异补强测试")
class ProjectMutationTest {

    @Mock
    private BizProjectMapper projectMapper;
    @Mock
    private SerialNumberService serialNumberService;
    @Mock
    private ProjectMemberService memberService;
    @Mock
    private ApprovalService approvalService;
    @Mock
    private BizProjectMemberMapper memberMapper;
    @Mock
    private SysUserProjectMapper userProjectMapper;

    private static final Long TENANT_ID = 9999L;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setTenantId(TENANT_ID);
        SecurityContextHolder.setUserId(999901L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    private ProjectService projectService() {
        return new ProjectService(projectMapper, serialNumberService, memberService, approvalService);
    }

    private BizProject project(String status, String totalIncome, String output) {
        BizProject p = new BizProject();
        p.setId(1L);
        p.setProjectName("补强项目");
        p.setStatus(status);
        p.setTotalIncome(totalIncome == null ? null : new BigDecimal(totalIncome));
        p.setCumulativeOutput(output == null ? null : new BigDecimal(output));
        return p;
    }

    // ==================== checkCloseConditions 四条件 ====================

    @Test
    @DisplayName("结项条件检查：全部满足（欠款恰为容差100元边界）")
    void checkCloseConditions_allPass_withBoundaryTolerance() {
        // 未收 = 产值 - 收入 = 1000100 - 1000000 = 100，恰等于容差上限（<=100 边界变异时被误拒）
        when(projectMapper.selectById(1L)).thenReturn(project("COMPLETED", "1000000", "1000100"));
        when(projectMapper.countApprovedSettlement(1L)).thenReturn(1L);

        Map<String, Object> result = projectService().checkCloseConditions(1L);

        assertThat(result.get("allPassed")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<String> conditions = (List<String>) result.get("conditions");
        @SuppressWarnings("unchecked")
        List<String> failedReasons = (List<String>) result.get("failedReasons");
        assertThat(conditions).hasSize(4);
        assertThat(failedReasons).isEmpty();
    }

    @Test
    @DisplayName("结项条件检查：三种失败原因逐项给出（未竣工/欠款超容差/无已批结算单）")
    void checkCloseConditions_failures_listedPerCondition() {
        // 状态 CONSTRUCTION：条件1失败；未收 500 > 100：条件2失败；无已批结算单：条件4失败
        when(projectMapper.selectById(1L)).thenReturn(project("CONSTRUCTION", "1000000", "1000500"));
        when(projectMapper.countApprovedSettlement(1L)).thenReturn(0L);

        Map<String, Object> result = projectService().checkCloseConditions(1L);

        assertThat(result.get("allPassed")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<String> failedReasons = (List<String>) result.get("failedReasons");
        assertThat(failedReasons).hasSize(3);
        assertThat(String.join("；", failedReasons))
                .contains("竣工验收")
                .contains("未收款")
                .contains("结算");
    }

    @Test
    @DisplayName("结项条件检查：金额字段 null 兜底为 0 不抛异常")
    void checkCloseConditions_nullAmounts_treatedAsZero() {
        when(projectMapper.selectById(1L)).thenReturn(project("COMPLETED", null, null));
        when(projectMapper.countApprovedSettlement(1L)).thenReturn(1L);

        Map<String, Object> result = projectService().checkCloseConditions(1L);
        assertThat(result.get("allPassed")).isEqualTo(true);
    }

    // ==================== closeProject ====================

    @Test
    @DisplayName("结项：条件不满足拒绝并附原因；满足则发起审批置 CLOSING")
    void closeProject_guardAndApprovalFlow() {
        // 条件不满足 → 拒绝
        when(projectMapper.selectById(1L)).thenReturn(project("CONSTRUCTION", null, null));
        when(projectMapper.countApprovedSettlement(1L)).thenReturn(0L);
        assertThatThrownBy(() -> projectService().closeProject(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法结项");
        verify(approvalService, never()).startProcess(anyString(), anyLong(), anyString(), anyMap());

        // 条件满足 → 发起审批 + 状态 CLOSING + 记录流程实例
        when(projectMapper.selectById(2L)).thenReturn(project("COMPLETED", "1000000", "1000000"));
        when(projectMapper.countApprovedSettlement(2L)).thenReturn(1L);
        when(approvalService.startProcess(eq("PROJECT_CLOSE"), eq(2L), eq("project_close_approval"), anyMap()))
                .thenReturn("wf-close-001");

        projectService().closeProject(2L);

        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CLOSING");
        assertThat(captor.getValue().getWorkflowInstanceId()).isEqualTo("wf-close-001");
    }

    // ==================== 状态守卫 ====================

    @Test
    @DisplayName("状态守卫：update/submit 非草稿拒绝、onCloseRejected 不存在拒绝且回退 COMPLETED")
    void statusGuards() {
        ProjectService service = projectService();

        BizProject submitted = project("FILED", null, null);
        when(projectMapper.selectById(1L)).thenReturn(submitted);
        assertThatThrownBy(() -> service.update(submitted))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可编辑");
        assertThatThrownBy(() -> service.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");

        when(projectMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.onCloseRejected(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");

        BizProject closing = project("CLOSING", null, null);
        when(projectMapper.selectById(2L)).thenReturn(closing);
        service.onCloseRejected(2L);
        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
    }

    // ==================== ProjectMemberService ====================

    private ProjectMemberService memberServiceReal() {
        return new ProjectMemberService(memberMapper, userProjectMapper);
    }

    private ProjectMemberAddRequest addRequest(Long userId, List<String> roles) {
        ProjectMemberAddRequest request = new ProjectMemberAddRequest();
        request.setUserId(userId);
        request.setUserName("用户" + userId);
        request.setProjectRoles(roles);
        return request;
    }

    @Test
    @DisplayName("添加成员：角色为空/非法拒绝；成功时字段完整写入并同步用户项目表")
    void addMember_guardsAndFieldWrites() {
        ProjectMemberService service = memberServiceReal();

        // 角色为空 → 拒绝
        assertThatThrownBy(() -> service.addMember(1L, addRequest(2L, Collections.emptyList())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目角色不能为空");
        // 非法角色 → 拒绝
        assertThatThrownBy(() -> service.addMember(1L, addRequest(2L, List.of("NOT_A_ROLE"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效的项目角色");

        // 成功：成员字段写入 + sys_user_project 同步
        when(memberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userProjectMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.addMember(1L, addRequest(2L, Arrays.asList("PROJECT_MANAGER", "CONSTRUCTOR")));

        ArgumentCaptor<BizProjectMember> memberCaptor = ArgumentCaptor.forClass(BizProjectMember.class);
        verify(memberMapper).insert(memberCaptor.capture());
        BizProjectMember member = memberCaptor.getValue();
        assertThat(member.getProjectId()).isEqualTo(1L);
        assertThat(member.getUserId()).isEqualTo(2L);
        assertThat(member.getUserName()).isEqualTo("用户2");
        assertThat(member.getProjectRoles()).containsExactly("PROJECT_MANAGER", "CONSTRUCTOR");
        assertThat(member.getJoinDate()).isNotNull();
        assertThat(member.getStatus()).isEqualTo(1);

        ArgumentCaptor<SysUserProject> upCaptor = ArgumentCaptor.forClass(SysUserProject.class);
        verify(userProjectMapper).insert(upCaptor.capture());
        SysUserProject up = upCaptor.getValue();
        assertThat(up.getUserId()).isEqualTo(2L);
        assertThat(up.getProjectId()).isEqualTo(1L);
        assertThat(up.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(up.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("添加成员：sys_user_project 已存在时幂等跳过插入")
    void addMember_userProjectExists_skipsSyncInsert() {
        ProjectMemberService service = memberServiceReal();
        when(memberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userProjectMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        service.addMember(1L, addRequest(2L, List.of("CONSTRUCTOR")));

        verify(memberMapper).insert(any(BizProjectMember.class));
        verify(userProjectMapper, never()).insert(any(SysUserProject.class));
    }

    @Test
    @DisplayName("移除成员：唯一项目经理保护依赖 PM 计数（≥2 放行）")
    void removeMember_pmCountAllowsRemoval() {
        ProjectMemberService service = memberServiceReal();

        BizProjectMember pm = new BizProjectMember();
        pm.setId(10L);
        pm.setProjectId(1L);
        pm.setUserId(2L);
        pm.setProjectRoles(List.of("PROJECT_MANAGER"));
        pm.setStatus(1);
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pm);
        // PM 计数 = 2（另一名 PM 存在）→ 允许移除；变异为 0 时将误拒
        when(memberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        service.removeMember(1L, 2L);

        verify(memberMapper).deleteById(10L);
        verify(userProjectMapper).delete(any(LambdaQueryWrapper.class));
    }

    // ==================== ProjectService.closeProject null 兜底 ====================

    @Test
    @DisplayName("结项：未收金额 null 兜底为 0 且容差边界放行")
    void closeProject_nullToleranceBoundary() {
        // totalIncome/cumulativeOutput 均为 null → 视为 0 → 未收=0 ≤ 100 容差
        when(projectMapper.selectById(1L)).thenReturn(project("COMPLETED", null, null));
        when(projectMapper.countApprovedSettlement(1L)).thenReturn(1L);

        Map<String, Object> result = projectService().checkCloseConditions(1L);
        assertThat(result.get("allPassed")).isEqualTo(true);

        // 实际 closeProject 调用应发起审批
        when(approvalService.startProcess(eq("PROJECT_CLOSE"), eq(1L), eq("project_close_approval"), anyMap()))
                .thenReturn("wf-close-null-test");
        projectService().closeProject(1L);

        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CLOSING");
        assertThat(captor.getValue().getWorkflowInstanceId()).isEqualTo("wf-close-null-test");
    }
}
