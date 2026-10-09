package com.zwinsight.workflow.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.exception.DataPermissionException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.workflow.domain.WfApprovalRecord;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.mapper.WfApprovalRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.flowable.engine.HistoryService;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ChangeActivityStateBuilder;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ApprovalService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock private RuntimeService runtimeService;
    @Mock private TaskService taskService;
    @Mock private HistoryService historyService;
    @Mock private IdentityService identityService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private WfApprovalRecordMapper approvalRecordMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private MockedStatic<SecurityContextHolder> defaultContext;

    @InjectMocks
    private ApprovalService approvalService;

    private Task mockTask;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                WfApprovalRecord.class);
    }

    @BeforeEach
    void setUp() {
        defaultContext = mockStatic(SecurityContextHolder.class);
        defaultContext.when(SecurityContextHolder::getUserId).thenReturn(200L);
        defaultContext.when(SecurityContextHolder::getTenantId).thenReturn(9999L);
        mockTask = mock(Task.class);
        lenient().when(mockTask.getTenantId()).thenReturn("9999");
        lenient().when(taskService.getVariable(anyString(), eq("initiator"))).thenReturn("300");
        lenient().when(taskService.getVariable(anyString(), eq("businessType"))).thenReturn("PAYMENT_APPLY");
        lenient().when(taskService.getVariable(anyString(), eq("businessId"))).thenReturn(55L);
        lenient().when(jdbc.queryForList(anyString(), anyLong(), anyLong())).thenReturn(List.of(Map.of("status", "SUBMITTED", "workflow_instance_id", "pi-001")));
        lenient().when(mockTask.getId()).thenReturn("task-001");
        lenient().when(mockTask.getProcessInstanceId()).thenReturn("pi-001");
        lenient().when(mockTask.getName()).thenReturn("部门经理审批");
        lenient().when(mockTask.getTaskDefinitionKey()).thenReturn("deptManagerApprove");
        // assignee 与审批操作用例的当前用户(userId=200)一致，满足 assertTaskAssignee 处理人校验
        lenient().when(mockTask.getAssignee()).thenReturn("200");
    }

    @AfterEach
    void closeContext() { if (!defaultContext.isClosed()) defaultContext.close(); }

    private MockedStatic<SecurityContextHolder> context() {
        defaultContext.close();
        defaultContext = mockStatic(SecurityContextHolder.class);
        defaultContext.when(SecurityContextHolder::getUserId).thenReturn(200L);
        defaultContext.when(SecurityContextHolder::getTenantId).thenReturn(9999L);
        return defaultContext;
    }

    // =====================================================================
    // startProcess
    // =====================================================================

    @Test
    @DisplayName("发起流程：正常发起，验证变量设置和 businessKey 格式")
    void testStartProcess_success() {
        try (var sc1 = context()) {
            sc1.when(SecurityContextHolder::getUserId).thenReturn(100L);
            sc1.when(SecurityContextHolder::getTenantId).thenReturn(1L);

            ProcessInstance pi = mock(ProcessInstance.class);
            when(pi.getId()).thenReturn("pi-001");
            when(runtimeService.startProcessInstanceByKeyAndTenantId(
                    eq("contract_approval"), eq("CONTRACT:500"), anyMap(), eq("1")))
                    .thenReturn(pi);

            Map<String, Object> vars = new HashMap<>();
            vars.put("amount", 50000);

            String result = approvalService.startProcess("CONTRACT", 500L, "contract_approval", vars);

            assertThat(result).isEqualTo("pi-001");
            verify(runtimeService).startProcessInstanceByKeyAndTenantId(
                    eq("contract_approval"), eq("CONTRACT:500"), argThat(m ->
                            "CONTRACT".equals(m.get("businessType"))
                                    && Long.valueOf(500L).equals(m.get("businessId"))
                                    && "100".equals(m.get("initiator"))
                                    && Integer.valueOf(50000).equals(m.get("amount"))
                    ), eq("1"));
        }
    }

    @Test
    @DisplayName("发起流程：variables 为 null 时自动初始化空 Map")
    void testStartProcess_nullVariables() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(100L);
            sc.when(SecurityContextHolder::getTenantId).thenReturn(1L);

            ProcessInstance pi = mock(ProcessInstance.class);
            when(pi.getId()).thenReturn("pi-002");
            when(runtimeService.startProcessInstanceByKeyAndTenantId(anyString(), anyString(), anyMap(), anyString()))
                    .thenReturn(pi);

            String result = approvalService.startProcess("PROJECT", 1L, "project_approval", null);

            assertThat(result).isEqualTo("pi-002");
        }
    }

    @Test
    @DisplayName("发起流程：流程定义不存在时 RuntimeService 抛异常上抛")
    void testStartProcess_processDefinitionNotFound() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(100L);
            sc.when(SecurityContextHolder::getTenantId).thenReturn(1L);

            when(runtimeService.startProcessInstanceByKeyAndTenantId(
                    eq("nonexistent_process"), anyString(), anyMap(), anyString()))
                    .thenThrow(new org.flowable.common.engine.api.FlowableObjectNotFoundException(
                            "no processes deployed with key 'nonexistent_process'"));

            assertThatThrownBy(() -> approvalService.startProcess(
                    "CONTRACT", 100L, "nonexistent_process", null))
                    .isInstanceOf(org.flowable.common.engine.api.FlowableObjectNotFoundException.class)
                    .hasMessageContaining("nonexistent_process");
        }
    }

    // =====================================================================
    // complete
    // =====================================================================

    @Test
    @DisplayName("办理通过：含审批意见，验证 addComment + complete + saveRecord")
    void testComplete_withComment() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId("task-001")).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(mockTask);

            approvalService.complete("task-001", "同意", null);

            verify(taskService).addComment("task-001", "pi-001", "同意");
            verify(taskService).complete("task-001");
            verify(approvalRecordMapper).insert(argThat(r ->
                    "APPROVE".equals(r.getOperationType()) && "同意".equals(r.getComment())));
        }
    }

    @Test
    @DisplayName("办理通过：含流程变量，验证带变量 complete")
    void testComplete_withVariables() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId("task-001")).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(mockTask);

            Map<String, Object> vars = Map.of("approved", true);
            approvalService.complete("task-001", null, vars);

            verify(taskService, never()).addComment(anyString(), anyString(), anyString());
            verify(taskService).complete("task-001", vars);
        }
    }

    @Test
    @DisplayName("办理通过：客户端传入的协议变量（initiator/businessId/businessType）被剔除，业务变量保留")
    void testComplete_protocolVariablesStripped() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId("task-001")).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(mockTask);

            Map<String, Object> vars = new HashMap<>();
            vars.put("approved", true);
            vars.put("initiator", "200");
            vars.put("businessId", 999L);
            vars.put("businessType", "PROJECT_CLOSE");
            approvalService.complete("task-001", null, vars);

            verify(taskService).complete("task-001", Map.of("approved", true));
        }
    }

    @Test
    @DisplayName("办理通过：金额分档路由变量 approvalTier/tierName 不可被审批人改写")
    void testComplete_approvalTierNotOverridable() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId("task-001")).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(mockTask);

            Map<String, Object> vars = new HashMap<>();
            vars.put("approvalTier", 0);
            vars.put("tierName", "最低档");
            vars.put("approved", true);
            approvalService.complete("task-001", null, vars);

            verify(taskService).complete("task-001", Map.of("approved", true));
        }
    }

    @Test
    @DisplayName("我的待办：缺失租户时拒绝查询，不以本人签收降级")
    void testGetMyTodoTasks_noTenantContext_rejected() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getTenantId).thenReturn(null);
            when(sysUserMapper.selectRoleCodesByUserId(100L)).thenReturn(List.of("PROJECT_MANAGER"));
            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            assertThatThrownBy(() -> approvalService.getMyTodoTasks(100L, 1, 10))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("租户上下文缺失");
            verify(taskQuery, never()).taskAssignee(anyString());
        }
    }

    @Test
    void taskView_sameTenantAssignee_allowed() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);
            sc.when(SecurityContextHolder::getTenantId).thenReturn(9999L);
            when(mockTask.getTenantId()).thenReturn("9999");
            TaskQuery query = mock(TaskQuery.class, RETURNS_SELF);
            when(taskService.createTaskQuery()).thenReturn(query);
            when(query.singleResult()).thenReturn(mockTask);
            approvalService.assertCanViewTask("task-001");
            verifyNoInteractions(historyService, approvalRecordMapper);
        }
    }

    @Test
    void taskView_crossTenantSuperAdmin_rejectedBeforeRoleLookup() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);
            sc.when(SecurityContextHolder::getTenantId).thenReturn(9999L);
            when(mockTask.getTenantId()).thenReturn("1");
            TaskQuery query = mock(TaskQuery.class, RETURNS_SELF);
            when(taskService.createTaskQuery()).thenReturn(query);
            when(query.singleResult()).thenReturn(mockTask);
            assertThatThrownBy(() -> approvalService.getTaskDetail("task-001"))
                    .isInstanceOf(DataPermissionException.class)
                    .hasMessage("任务不存在或已被处理")
                    .satisfies(e -> assertThat(((DataPermissionException) e).getCode()).isEqualTo(403));
            verifyNoInteractions(sysUserMapper, historyService, approvalRecordMapper);
        }
    }

    @Test
    @DisplayName("办理通过：只传协议变量时等同无变量 complete")
    void testComplete_onlyProtocolVariables() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId("task-001")).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(mockTask);

            approvalService.complete("task-001", null, new HashMap<>(Map.of("initiator", "200")));

            verify(taskService).complete("task-001");
            verify(taskService, never()).complete(eq("task-001"), anyMap());
        }
    }

    @Test
    @DisplayName("办理通过：候选角色成员办理未签收任务时自动签收后办理")
    void testComplete_candidateAutoClaim() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            when(mockTask.getAssignee()).thenReturn(null);
            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId("task-001")).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(mockTask);
            when(sysUserMapper.selectRoleCodesByUserId(200L)).thenReturn(List.of("PROJECT_MANAGER"));
            org.flowable.identitylink.api.IdentityLink link = mock(org.flowable.identitylink.api.IdentityLink.class);
            when(link.getType()).thenReturn("candidate");
            when(link.getGroupId()).thenReturn("PROJECT_MANAGER");
            when(taskService.getIdentityLinksForTask("task-001")).thenReturn(List.of(link));

            approvalService.complete("task-001", "同意", null);

            verify(taskService).claim("task-001", "200");
            verify(taskService).complete("task-001");
        }
    }

    @Test
    @DisplayName("办理通过：非候选人办理未签收任务被拒绝，且不签收不办理")
    void testComplete_nonCandidateRejected() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            when(mockTask.getAssignee()).thenReturn(null);
            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId("task-001")).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(mockTask);
            when(sysUserMapper.selectRoleCodesByUserId(200L)).thenReturn(List.of("HR_STAFF"));
            org.flowable.identitylink.api.IdentityLink link = mock(org.flowable.identitylink.api.IdentityLink.class);
            when(link.getType()).thenReturn("candidate");
            when(link.getGroupId()).thenReturn("PROJECT_MANAGER");
            when(taskService.getIdentityLinksForTask("task-001")).thenReturn(List.of(link));

            assertThatThrownBy(() -> approvalService.complete("task-001", "同意", null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不属于任务候选人");
            verify(taskService, never()).claim(anyString(), anyString());
            verify(taskService, never()).complete(anyString());
        }
    }

    @Test
    @DisplayName("办理通过：任务不存在抛 BusinessException")
    void testComplete_taskNotFound() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId("nonexist")).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(null);

            assertThatThrownBy(() -> approvalService.complete("nonexist", "ok", null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("任务不存在");
        }
    }

    // =====================================================================
    // rejectToPrevious
    // =====================================================================

    @Test
    @DisplayName("退回至上一节点：正常退回，验证 changeState + 发布事件")
    void testRejectToPrevious_success() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId("task-001")).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(mockTask);

            // mock 历史节点
            HistoricActivityInstanceQuery histQuery = mock(HistoricActivityInstanceQuery.class);
            when(historyService.createHistoricActivityInstanceQuery()).thenReturn(histQuery);
            when(histQuery.processInstanceId("pi-001")).thenReturn(histQuery);
            when(histQuery.activityType("userTask")).thenReturn(histQuery);
            when(histQuery.finished()).thenReturn(histQuery);
            when(histQuery.orderByHistoricActivityInstanceEndTime()).thenReturn(histQuery);
            when(histQuery.desc()).thenReturn(histQuery);

            HistoricActivityInstance prevNode = mock(HistoricActivityInstance.class);
            when(prevNode.getActivityId()).thenReturn("submitNode");
            when(histQuery.list()).thenReturn(List.of(prevNode));

            // mock changeState
            ChangeActivityStateBuilder builder = mock(ChangeActivityStateBuilder.class);
            when(runtimeService.createChangeActivityStateBuilder()).thenReturn(builder);
            when(builder.processInstanceId("pi-001")).thenReturn(builder);
            when(builder.moveActivityIdTo("deptManagerApprove", "submitNode")).thenReturn(builder);

            // mock process variables
            Map<String, Object> vars = new HashMap<>();
            vars.put("businessType", "CONTRACT");
            vars.put("businessId", 500L);
            when(runtimeService.getVariables("pi-001")).thenReturn(vars);

            approvalService.rejectToPrevious("task-001", "数据有误");

            verify(taskService).addComment("task-001", "pi-001", "【退回】数据有误");
            verify(builder).changeState();
            verify(approvalRecordMapper).insert(argThat(r -> "REJECT".equals(r.getOperationType())));
            verify(eventPublisher).publishEvent(any(ApprovalRejectEvent.class));
        }
    }

    @Test
    @DisplayName("退回至上一节点：无历史节点时抛异常")
    void testRejectToPrevious_noPreviousNode() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId("task-001")).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(mockTask);

            HistoricActivityInstanceQuery histQuery = mock(HistoricActivityInstanceQuery.class);
            when(historyService.createHistoricActivityInstanceQuery()).thenReturn(histQuery);
            when(histQuery.processInstanceId("pi-001")).thenReturn(histQuery);
            when(histQuery.activityType("userTask")).thenReturn(histQuery);
            when(histQuery.finished()).thenReturn(histQuery);
            when(histQuery.orderByHistoricActivityInstanceEndTime()).thenReturn(histQuery);
            when(histQuery.desc()).thenReturn(histQuery);
            when(histQuery.list()).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> approvalService.rejectToPrevious("task-001", "test"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("没有可退回的节点");
        }
    }

    // =====================================================================
    // rejectToStart
    // =====================================================================

    @Test
    @DisplayName("退回至发起人：验证首节点定位")
    void testRejectToStart_success() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId("task-001")).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(mockTask);

            HistoricActivityInstanceQuery histQuery = mock(HistoricActivityInstanceQuery.class);
            when(historyService.createHistoricActivityInstanceQuery()).thenReturn(histQuery);
            when(histQuery.processInstanceId("pi-001")).thenReturn(histQuery);
            when(histQuery.activityType("userTask")).thenReturn(histQuery);
            when(histQuery.orderByHistoricActivityInstanceStartTime()).thenReturn(histQuery);
            when(histQuery.asc()).thenReturn(histQuery);

            HistoricActivityInstance startNode = mock(HistoricActivityInstance.class);
            when(startNode.getActivityId()).thenReturn("startApproval");
            when(histQuery.list()).thenReturn(List.of(startNode));

            ChangeActivityStateBuilder builder = mock(ChangeActivityStateBuilder.class);
            when(runtimeService.createChangeActivityStateBuilder()).thenReturn(builder);
            when(builder.processInstanceId("pi-001")).thenReturn(builder);
            when(builder.moveActivityIdTo("deptManagerApprove", "startApproval")).thenReturn(builder);

            Map<String, Object> vars = new HashMap<>();
            vars.put("businessType", "BUDGET");
            vars.put("businessId", 99L);
            when(runtimeService.getVariables("pi-001")).thenReturn(vars);

            approvalService.rejectToStart("task-001", "重做");

            verify(taskService).addComment("task-001", "pi-001", "【退回发起人】重做");
            verify(builder).changeState();
            verify(approvalRecordMapper).insert(argThat(r -> "REJECT_TO_START".equals(r.getOperationType())));
        }
    }

    // =====================================================================
    // terminate
    // =====================================================================

    @Test
    @DisplayName("终止流程：验证 deleteProcessInstance + 发布 WITHDRAW 事件")
    void testTerminate_success() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId("task-001")).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(mockTask);

            Map<String, Object> vars = new HashMap<>();
            vars.put("businessType", "CONTRACT");
            vars.put("businessId", 777L);
            when(taskService.getVariables("task-001")).thenReturn(vars);

            approvalService.terminate("task-001", "项目取消");

            verify(taskService).addComment("task-001", "pi-001", "【终止】项目取消");
            verify(runtimeService).deleteProcessInstance("pi-001", "终止：项目取消");
            verify(approvalRecordMapper).insert(argThat(r -> "TERMINATE".equals(r.getOperationType())));
            verify(eventPublisher).publishEvent(argThat(e ->
                    e instanceof ApprovalRejectEvent are && "WITHDRAW".equals(are.getRejectType())));
        }
    }

    // =====================================================================
    // transfer
    // =====================================================================

    @Test
    @DisplayName("转办：验证 setAssignee")
    void testTransfer_success() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);
            sc.when(SecurityContextHolder::getTenantId).thenReturn(9999L);
            when(mockTask.getTenantId()).thenReturn("9999");

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId("task-001")).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(mockTask);

            SysUser target = new SysUser();
            target.setId(300L);
            target.setStatus(1);
            target.setTenantId(9999L);
            when(sysUserMapper.selectById(300L)).thenReturn(target);

            approvalService.transfer("task-001", "300", "出差代审");

            verify(taskService).addComment("task-001", "pi-001", "【转办】出差代审");
            verify(taskService).setAssignee("task-001", "300");
            verify(approvalRecordMapper).insert(argThat(r -> "TRANSFER".equals(r.getOperationType())));
        }
    }

    @Test
    @DisplayName("转办：目标用户不存在 / 已停用 / 就是自己 / 跨租户均拒绝，且不改办理人")
    void testTransfer_invalidTargetRejected() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);
            sc.when(SecurityContextHolder::getTenantId).thenReturn(9999L);
            when(mockTask.getTenantId()).thenReturn("9999");

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId("task-001")).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(mockTask);

            // 不存在
            assertThatThrownBy(() -> approvalService.transfer("task-001", "301", "x"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("目标用户不存在");

            // 已停用
            SysUser disabled = new SysUser();
            disabled.setId(302L);
            disabled.setStatus(0);
            when(sysUserMapper.selectById(302L)).thenReturn(disabled);
            assertThatThrownBy(() -> approvalService.transfer("task-001", "302", "x"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("已停用");

            // 跨租户
            SysUser other = new SysUser();
            other.setId(303L);
            other.setStatus(1);
            other.setTenantId(2L);
            when(sysUserMapper.selectById(303L)).thenReturn(other);
            assertThatThrownBy(() -> approvalService.transfer("task-001", "303", "x"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("不属于当前租户");

            SysUser missingStatus = new SysUser();
            missingStatus.setId(304L);
            missingStatus.setTenantId(9999L);
            when(sysUserMapper.selectById(304L)).thenReturn(missingStatus);
            assertThatThrownBy(() -> approvalService.transfer("task-001", "304", "x"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("已停用");

            SysUser missingTenant = new SysUser();
            missingTenant.setId(305L);
            missingTenant.setStatus(1);
            when(sysUserMapper.selectById(305L)).thenReturn(missingTenant);
            assertThatThrownBy(() -> approvalService.transfer("task-001", "305", "x"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("不属于当前租户");

            // 自己
            assertThatThrownBy(() -> approvalService.transfer("task-001", "200", "x"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("自己");

            // 非数字
            assertThatThrownBy(() -> approvalService.transfer("task-001", "abc", "x"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("无效");

            verify(taskService, never()).setAssignee(anyString(), anyString());
        }
    }

    @Test
    @DisplayName("转办：防自审类型不得转给发起人")
    void testTransfer_toInitiatorRejectedForGuardedType() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);
            sc.when(SecurityContextHolder::getTenantId).thenReturn(9999L);
            when(mockTask.getTenantId()).thenReturn("9999");

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId("task-001")).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(mockTask);
            when(taskService.getVariable("task-001", "initiator")).thenReturn("300");
            when(taskService.getVariable("task-001", "businessType")).thenReturn("PURCHASE_SETTLEMENT");

            SysUser target = new SysUser();
            target.setId(300L);
            target.setStatus(1);
            target.setTenantId(9999L);
            when(sysUserMapper.selectById(300L)).thenReturn(target);

            assertThatThrownBy(() -> approvalService.transfer("task-001", "300", "x"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("发起人不能审批自己的单据");
            verify(taskService, never()).setAssignee(anyString(), anyString());
        }
    }

    // =====================================================================
    // delegate
    // =====================================================================

    @Test
    @DisplayName("委托：验证 delegateTask")
    void testDelegate_success() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);
            sc.when(SecurityContextHolder::getTenantId).thenReturn(9999L);
            when(mockTask.getTenantId()).thenReturn("9999");

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId("task-001")).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(mockTask);

            SysUser target = new SysUser();
            target.setId(400L);
            target.setStatus(1);
            target.setTenantId(9999L);
            when(sysUserMapper.selectById(400L)).thenReturn(target);

            approvalService.delegate("task-001", "400", "休假委托");

            verify(taskService).addComment("task-001", "pi-001", "【委托】休假委托");
            verify(taskService).delegateTask("task-001", "400");
            verify(approvalRecordMapper).insert(argThat(r -> "DELEGATE".equals(r.getOperationType())));
        }
    }

    // =====================================================================
    // batchApprove
    // =====================================================================

    @Test
    @DisplayName("批量通过：逐个调用 complete")
    void testBatchApprove_callsCompleteForEach() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            Task task2 = mock(Task.class);
            lenient().when(task2.getTenantId()).thenReturn("9999");
            lenient().when(task2.getId()).thenReturn("task-002");
            lenient().when(task2.getProcessInstanceId()).thenReturn("pi-001");
            lenient().when(task2.getName()).thenReturn("审批节点2");
            lenient().when(task2.getTaskDefinitionKey()).thenReturn("node2");
            lenient().when(task2.getAssignee()).thenReturn("200");

            TaskQuery q1 = mock(TaskQuery.class);
            when(taskService.createTaskQuery())
                    .thenReturn(q1)   // task-001
                    .thenReturn(q1)   // task-001 (complete 内部)
                    .thenReturn(q1)   // task-002
                    .thenReturn(q1);  // task-002 (complete 内部)
            when(q1.taskId("task-001")).thenReturn(q1);
            when(q1.taskId("task-002")).thenReturn(q1);
            // 交替返回两个 task
            when(q1.singleResult())
                    .thenReturn(mockTask)
                    .thenReturn(task2);

            approvalService.batchApprove(List.of("task-001", "task-002"), "批量同意");

            verify(taskService, times(2)).complete(anyString());
            verify(approvalRecordMapper, times(2)).insert(any(WfApprovalRecord.class));
        }
    }

    // =====================================================================
    // getMyTodoTasks / getMyDoneTasks
    // =====================================================================

    @Test
    @DisplayName("我的待办：分页查询返回正确结构（变量随查询批量携带，无 N+1）")
    void testGetMyTodoTasks() {
        TaskQuery taskQuery = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskAssignee("100")).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(1L);
        when(taskQuery.includeProcessVariables()).thenReturn(taskQuery);
        when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
        when(taskQuery.desc()).thenReturn(taskQuery);
        when(taskQuery.listPage(0, 10)).thenReturn(List.of(mockTask));
        // includeProcessVariables 后变量由 Task 自身携带，不再逐任务 getVariables
        when(mockTask.getProcessVariables())
                .thenReturn(Map.of("businessType", "CONTRACT", "businessId", 1L));

        PageResult<Map<String, Object>> result = approvalService.getMyTodoTasks(100L, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).get("taskId")).isEqualTo("task-001");
        assertThat(result.getRecords().get(0).get("businessType")).isEqualTo("CONTRACT");
        // 变量无 initiator 时 startUserName 为 null，且不触发用户表查询（无 N+1）
        assertThat(result.getRecords().get(0).get("startUserName")).isNull();
        verify(taskService, never()).getVariables(anyString());
        verify(sysUserMapper, never()).selectBatchIds(anyList());
    }

    @Test
    @DisplayName("我的待办：持有候选角色时按（本人办理 OR 候选组）且限定租户查询")
    void testGetMyTodoTasks_includesCandidateGroupsScopedByTenant() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getTenantId).thenReturn(1L);
            when(sysUserMapper.selectRoleCodesByUserId(100L)).thenReturn(List.of("PROJECT_MANAGER", "FINANCE_STAFF"));

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskTenantId("1")).thenReturn(taskQuery);
            when(taskQuery.or()).thenReturn(taskQuery);
            when(taskQuery.taskAssignee("100")).thenReturn(taskQuery);
            when(taskQuery.taskCandidateUser("100")).thenReturn(taskQuery);
            when(taskQuery.taskCandidateGroupIn(List.of("PROJECT_MANAGER", "FINANCE_STAFF"))).thenReturn(taskQuery);
            when(taskQuery.endOr()).thenReturn(taskQuery);
            when(taskQuery.count()).thenReturn(1L);
            when(taskQuery.includeProcessVariables()).thenReturn(taskQuery);
            when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
            when(taskQuery.desc()).thenReturn(taskQuery);
            when(taskQuery.listPage(0, 10)).thenReturn(List.of(mockTask));
            when(mockTask.getProcessVariables()).thenReturn(Map.of("businessType", "LABOR_CONTRACT"));

            PageResult<Map<String, Object>> result = approvalService.getMyTodoTasks(100L, 1, 10);

            assertThat(result.getTotal()).isEqualTo(1);
            verify(taskQuery, atLeastOnce()).taskTenantId("1");
            verify(taskQuery, atLeastOnce()).taskCandidateUser("100");
            verify(taskQuery, atLeastOnce()).taskCandidateGroupIn(List.of("PROJECT_MANAGER", "FINANCE_STAFF"));
        }
    }

    @Test
    @DisplayName("我的待办：无任何角色时仍按（本人办理 OR 点名候选人）查询，不带候选组")
    void testGetMyTodoTasks_candidateUserWithoutRoles() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getTenantId).thenReturn(1L);
            when(sysUserMapper.selectRoleCodesByUserId(100L)).thenReturn(List.of());

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskTenantId("1")).thenReturn(taskQuery);
            when(taskQuery.or()).thenReturn(taskQuery);
            when(taskQuery.taskAssignee("100")).thenReturn(taskQuery);
            when(taskQuery.taskCandidateUser("100")).thenReturn(taskQuery);
            when(taskQuery.endOr()).thenReturn(taskQuery);
            when(taskQuery.count()).thenReturn(0L);
            when(taskQuery.includeProcessVariables()).thenReturn(taskQuery);
            when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
            when(taskQuery.desc()).thenReturn(taskQuery);
            when(taskQuery.listPage(0, 10)).thenReturn(List.of());

            approvalService.getMyTodoTasks(100L, 1, 10);

            verify(taskQuery, atLeastOnce()).taskCandidateUser("100");
            verify(taskQuery, never()).taskCandidateGroupIn(anyList());
        }
    }

    @Test
    @DisplayName("按业务撤回：任务属于其他租户时按不存在处理，不终止流程")
    void testWithdrawByBusiness_crossTenantRejected() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getTenantId).thenReturn(1L);

            when(mockTask.getTenantId()).thenReturn("2");
            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.processInstanceBusinessKey("PAYMENT_APPLY:55")).thenReturn(taskQuery);
            when(taskQuery.listPage(0, 1)).thenReturn(List.of(mockTask));

            assertThatThrownBy(() -> approvalService.withdrawByBusiness("PAYMENT_APPLY", 55L))
                    .isInstanceOf(DataPermissionException.class)
                    .hasMessageContaining("任务不存在");
            verify(runtimeService, never()).deleteProcessInstance(anyString(), anyString());
        }
    }

    @Test
    @DisplayName("我的待办：发起人 userId 批量翻译为 realName 填充 startUserName")
    void testGetMyTodoTasks_startUserName() {
        TaskQuery taskQuery = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskAssignee("100")).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(1L);
        when(taskQuery.includeProcessVariables()).thenReturn(taskQuery);
        when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
        when(taskQuery.desc()).thenReturn(taskQuery);
        when(taskQuery.listPage(0, 10)).thenReturn(List.of(mockTask));
        when(mockTask.getProcessVariables())
                .thenReturn(Map.of("businessType", "CONTRACT", "initiator", "100"));

        SysUser starter = new SysUser();
        starter.setId(100L);
        starter.setRealName("张三");
        when(sysUserMapper.selectBatchIds(anyList())).thenReturn(List.of(starter));

        PageResult<Map<String, Object>> result = approvalService.getMyTodoTasks(100L, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).get("startUserName")).isEqualTo("张三");
        // 多个任务只批量查询一次用户表
        verify(sysUserMapper, times(1)).selectBatchIds(anyList());
    }

    @Test
    @DisplayName("我的已办：分页查询返回正确结构")
    void testGetMyDoneTasks() {
        HistoricTaskInstanceQuery histTaskQuery = mock(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(histTaskQuery);
        when(histTaskQuery.taskAssignee("100")).thenReturn(histTaskQuery);
        when(histTaskQuery.finished()).thenReturn(histTaskQuery);
        when(histTaskQuery.count()).thenReturn(2L);
        when(histTaskQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(histTaskQuery);
        when(histTaskQuery.desc()).thenReturn(histTaskQuery);

        HistoricTaskInstance hti = mock(HistoricTaskInstance.class);
        when(hti.getId()).thenReturn("ht-001");
        when(hti.getName()).thenReturn("已审批任务");
        when(hti.getTaskDefinitionKey()).thenReturn("node1");
        when(hti.getProcessInstanceId()).thenReturn("pi-done");
        when(hti.getAssignee()).thenReturn("100");
        when(histTaskQuery.listPage(0, 10)).thenReturn(List.of(hti));

        PageResult<Map<String, Object>> result = approvalService.getMyDoneTasks(100L, 1, 10);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).get("taskName")).isEqualTo("已审批任务");
    }

    // =====================================================================
    // withdrawByBusiness（2026-08-13 二次事故：高速提交下待办扫描回收失配，
    // 改用 businessKey O(1) 定位撤回）
    // =====================================================================

    @Test
    @DisplayName("按业务撤回：发起人定位运行中任务并终止+发撤回事件")
    void testWithdrawByBusiness_success() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.processInstanceBusinessKey("PAYMENT_APPLY:77")).thenReturn(taskQuery);
            when(taskQuery.listPage(0, 1)).thenReturn(List.of(mockTask));

            HistoricProcessInstanceQuery hpiQuery = mock(HistoricProcessInstanceQuery.class);
            HistoricProcessInstance hpi = mock(HistoricProcessInstance.class);
            when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hpiQuery);
            when(hpiQuery.processInstanceId("pi-001")).thenReturn(hpiQuery);
            when(hpiQuery.singleResult()).thenReturn(hpi);
            // 发起人 = 当前用户（200）
            when(hpi.getStartUserId()).thenReturn("200");

            boolean result = approvalService.withdrawByBusiness("PAYMENT_APPLY", 77L);

            assertThat(result).isTrue();
            verify(runtimeService).deleteProcessInstance(eq("pi-001"), contains("自动化撤回"));
            verify(eventPublisher).publishEvent(any(ApprovalRejectEvent.class));
            verify(approvalRecordMapper).insert(any(WfApprovalRecord.class));
        }
    }

    @Test
    @DisplayName("按业务撤回：无运行中流程幂等返回 false（清理语义不报错）")
    void testWithdrawByBusiness_noRunningProcess_returnsFalse() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.processInstanceBusinessKey("PAYMENT_APPLY:88")).thenReturn(taskQuery);
            when(taskQuery.listPage(0, 1)).thenReturn(List.of());

            boolean result = approvalService.withdrawByBusiness("PAYMENT_APPLY", 88L);

            assertThat(result).isFalse();
            verify(runtimeService, never()).deleteProcessInstance(anyString(), anyString());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Test
    @DisplayName("按业务撤回：非发起人拒绝（防越权终止他人流程）")
    void testWithdrawByBusiness_notInitiator_throws() {
        try (var sc = context()) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.processInstanceBusinessKey("PAYMENT_APPLY:99")).thenReturn(taskQuery);
            when(taskQuery.listPage(0, 1)).thenReturn(List.of(mockTask));

            HistoricProcessInstanceQuery hpiQuery = mock(HistoricProcessInstanceQuery.class);
            HistoricProcessInstance hpi = mock(HistoricProcessInstance.class);
            when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hpiQuery);
            when(hpiQuery.processInstanceId("pi-001")).thenReturn(hpiQuery);
            when(hpiQuery.singleResult()).thenReturn(hpi);
            // 发起人 = 999，非当前用户 200
            when(hpi.getStartUserId()).thenReturn("999");

            assertThatThrownBy(() -> approvalService.withdrawByBusiness("PAYMENT_APPLY", 99L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅流程发起人可撤回");
            verify(runtimeService, never()).deleteProcessInstance(anyString(), anyString());
        }
    }

    // =====================================================================
    // getTaskDetail（移动端审批详情聚合端点）
    // =====================================================================

    @Test
    @DisplayName("审批详情：运行中任务聚合流程信息/业务数据去内部变量/审批时间线映射")
    void testGetTaskDetail_runningTask_success() {
        TaskQuery taskQuery = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-001")).thenReturn(taskQuery);
        when(taskQuery.includeProcessVariables()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(mockTask);

        Map<String, Object> vars = new HashMap<>();
        vars.put("businessType", "CONTRACT");
        vars.put("businessId", 500L);
        vars.put("initiator", "100");
        vars.put("businessTitle", "办公楼装修合同");
        vars.put("amount", 50000);
        when(mockTask.getProcessVariables()).thenReturn(vars);

        HistoricProcessInstanceQuery hpiQuery = mock(HistoricProcessInstanceQuery.class);
        HistoricProcessInstance hpi = mock(HistoricProcessInstance.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hpiQuery);
        when(hpiQuery.processInstanceId("pi-001")).thenReturn(hpiQuery);
        when(hpiQuery.singleResult()).thenReturn(hpi);
        when(hpi.getProcessDefinitionName()).thenReturn("合同审批流程");
        Date startTime = new Date();
        when(hpi.getStartTime()).thenReturn(startTime);
        when(hpi.getStartUserId()).thenReturn("100");

        // 审批记录 assigneeName 为空 → 回退查 sys_user.realName
        WfApprovalRecord record = new WfApprovalRecord();
        record.setId(11L);
        record.setTaskName("部门经理审批");
        record.setAssignee("200");
        record.setAssigneeName("");
        record.setOperationType("APPROVE");
        record.setComment("同意");
        record.setOperTime(LocalDateTime.of(2026, 9, 1, 10, 0));
        when(approvalRecordMapper.selectList(any())).thenReturn(List.of(record));

        SysUser starter = new SysUser();
        starter.setId(100L);
        starter.setRealName("张三");
        SysUser approver = new SysUser();
        approver.setId(200L);
        approver.setRealName("李四");
        when(sysUserMapper.selectBatchIds(anyList())).thenReturn(List.of(starter, approver));

        Map<String, Object> detail = approvalService.getTaskDetail("task-001");

        assertThat(detail.get("taskId")).isEqualTo("task-001");
        assertThat(detail).containsEntry("assignee", "200");
        assertThat(detail.get("taskName")).isEqualTo("部门经理审批");
        assertThat(detail.get("processName")).isEqualTo("合同审批流程");
        assertThat(detail.get("createTime")).isEqualTo(startTime);
        assertThat(detail.get("status")).isEqualTo("pending");
        assertThat(detail.get("startUserName")).isEqualTo("张三");
        assertThat(detail.get("businessType")).isEqualTo("CONTRACT");
        assertThat(detail.get("businessId")).isEqualTo(500L);
        assertThat(detail.get("businessTitle")).isEqualTo("办公楼装修合同");
        // businessData 过滤内部变量（businessType/businessId/initiator），保留业务变量
        assertThat(detail.get("businessData")).isEqualTo(Map.of("amount", 50000));

        List<Map<String, Object>> recordMaps =
                castList(detail.get("approvalRecords"));
        assertThat(recordMaps).hasSize(1);
        assertThat(recordMaps.get(0).get("result")).isEqualTo("approved");
        assertThat(recordMaps.get(0).get("resultText")).isEqualTo("已通过");
        assertThat(recordMaps.get(0).get("assigneeName")).isEqualTo("李四");
        assertThat(recordMaps.get(0).get("comment")).isEqualTo("同意");
    }

    @Test
    @DisplayName("审批详情：运行中任务不存在时回退历史任务，status=done")
    void testGetTaskDetail_historicTask_statusDone() {
        TaskQuery taskQuery = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-h")).thenReturn(taskQuery);
        when(taskQuery.includeProcessVariables()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);

        HistoricTaskInstanceQuery histQuery = mock(HistoricTaskInstanceQuery.class);
        HistoricTaskInstance hti = mock(HistoricTaskInstance.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(histQuery);
        when(histQuery.taskId("task-h")).thenReturn(histQuery);
        when(histQuery.includeProcessVariables()).thenReturn(histQuery);
        when(histQuery.singleResult()).thenReturn(hti);
        when(hti.getTenantId()).thenReturn("9999");
        when(hti.getAssignee()).thenReturn("200");
        when(hti.getProcessInstanceId()).thenReturn("pi-h");
        when(hti.getName()).thenReturn("历史审批任务");
        when(hti.getProcessVariables()).thenReturn(Map.of("businessType", "PAYMENT"));

        HistoricProcessInstanceQuery hpiQuery = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hpiQuery);
        when(hpiQuery.processInstanceId("pi-h")).thenReturn(hpiQuery);
        when(hpiQuery.singleResult()).thenReturn(null);
        when(approvalRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> detail = approvalService.getTaskDetail("task-h");

        assertThat(detail.get("status")).isEqualTo("done");
        assertThat(detail).containsEntry("assignee", "200");
        assertThat(detail.get("taskName")).isEqualTo("历史审批任务");
        assertThat(detail.get("processName")).isNull();
        assertThat(detail.get("startUserName")).isNull();
        assertThat(detail.get("businessType")).isEqualTo("PAYMENT");
        assertThat(detail.get("businessTitle")).isNull();
        assertThat(castList(detail.get("approvalRecords"))).isEmpty();
    }

    @Test
    @DisplayName("审批详情：运行中与历史任务均不存在抛 BusinessException")
    void testGetTaskDetail_notFound() {
        TaskQuery taskQuery = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("nonexist")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);

        HistoricTaskInstanceQuery histQuery = mock(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(histQuery);
        when(histQuery.taskId("nonexist")).thenReturn(histQuery);
        when(histQuery.singleResult()).thenReturn(null);

        assertThatThrownBy(() -> approvalService.getTaskDetail("nonexist"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权查看");
    }

    // =====================================================================
    // getApprovalTrace（单据穿透链末环，驾驶舱 P2-4）
    // =====================================================================

    @Test
    @DisplayName("审批轨迹：按流程实例聚合流程信息 + 记录时间线（与 getTaskDetail 同口径）")
    void testGetApprovalTrace_success() {
        HistoricProcessInstanceQuery hpiQuery = mock(HistoricProcessInstanceQuery.class);
        HistoricProcessInstance hpi = mock(HistoricProcessInstance.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hpiQuery);
        when(hpiQuery.processInstanceId("pi-001")).thenReturn(hpiQuery);
        when(hpiQuery.singleResult()).thenReturn(hpi);
        when(hpi.getTenantId()).thenReturn("9999");
        when(sysUserMapper.selectRoleCodesByUserId(200L)).thenReturn(List.of("SUPER_ADMIN"));
        when(hpi.getProcessDefinitionName()).thenReturn("付款审批流程");
        // Flowable 历史接口时间为 java.util.Date（非 LocalDateTime）
        when(hpi.getStartTime()).thenReturn(new java.util.Date(1756700000000L));
        when(hpi.getEndTime()).thenReturn(new java.util.Date(1756786400000L));
        when(hpi.getStartUserId()).thenReturn("100");

        WfApprovalRecord record = new WfApprovalRecord();
        record.setProcessInstanceId("pi-001");
        record.setTaskName("财务复核");
        record.setAssignee("200");
        record.setAssigneeName(null);
        record.setOperationType("APPROVE");
        record.setComment("无异议");
        record.setOperTime(LocalDateTime.of(2026, 9, 2, 10, 0));
        when(approvalRecordMapper.selectList(any())).thenReturn(List.of(record));

        SysUser starter = new SysUser();
        starter.setId(100L);
        starter.setRealName("张三");
        SysUser approver = new SysUser();
        approver.setId(200L);
        approver.setRealName("李四");
        when(sysUserMapper.selectBatchIds(anyList())).thenReturn(List.of(starter, approver));

        Map<String, Object> trace = approvalService.getApprovalTrace("pi-001");

        assertThat(trace.get("status")).isEqualTo("COMPLETED");
        assertThat(trace.get("processName")).isEqualTo("付款审批流程");
        assertThat(trace.get("startUserName")).isEqualTo("张三");
        assertThat(trace.get("note")).isNull();
        List<Map<String, Object>> records = castList(trace.get("approvalRecords"));
        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("assigneeName")).isEqualTo("李四");
        assertThat(records.get(0).get("resultText")).isEqualTo("已通过");
    }

    @Test
    @DisplayName("审批轨迹：流程实例不存在 → UNKNOWN + 如实提示，不伪造轨迹")
    void testGetApprovalTrace_instanceMissing_honestNote() {
        HistoricProcessInstanceQuery hpiQuery = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hpiQuery);
        when(hpiQuery.processInstanceId("pi-ghost")).thenReturn(hpiQuery);
        when(hpiQuery.singleResult()).thenReturn(null);
        assertThatThrownBy(() -> approvalService.getApprovalTrace("pi-ghost"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("无权查看");
        verifyNoInteractions(approvalRecordMapper);

    }

    @Test
    @DisplayName("审批轨迹：无审批记录时给出口径提示（区分于实例不存在）")
    void testGetApprovalTrace_emptyRecords_note() {
        HistoricProcessInstanceQuery hpiQuery = mock(HistoricProcessInstanceQuery.class);
        HistoricProcessInstance hpi = mock(HistoricProcessInstance.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hpiQuery);
        when(hpiQuery.processInstanceId("pi-run")).thenReturn(hpiQuery);
        when(hpiQuery.singleResult()).thenReturn(hpi);
        when(hpi.getTenantId()).thenReturn("9999");
        when(hpi.getStartUserId()).thenReturn("200");
        when(hpi.getEndTime()).thenReturn(null);
        when(approvalRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> trace = approvalService.getApprovalTrace("pi-run");

        assertThat(trace.get("status")).isEqualTo("RUNNING");
        assertThat(String.valueOf(trace.get("note"))).contains("无审批操作记录");
    }

    @Test
    @DisplayName("审批轨迹：空流程实例ID 拒绝（400 语义，不返空对象静默）")
    void testGetApprovalTrace_blankId_rejected() {
        assertThatThrownBy(() -> approvalService.getApprovalTrace(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程实例ID");
        assertThatThrownBy(() -> approvalService.getApprovalTrace(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void sourceGuard_missingRowBlocksSingleAndBatch() {
        TaskQuery query = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(mockTask);
        when(jdbc.queryForList(anyString(), anyLong(), anyLong())).thenReturn(List.of());
        assertThatThrownBy(() -> approvalService.complete("task-001", "同意", null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> approvalService.batchApprove(List.of("task-001"), "同意"))
                .isInstanceOf(BusinessException.class);
        verify(taskService, never()).complete(anyString());
        verify(taskService, never()).addComment(anyString(), anyString(), anyString());
        verifyNoInteractions(approvalRecordMapper);
    }

    @Test
    void sourceGuard_queryFailureBlocksApproval() {
        TaskQuery query = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(mockTask);
        when(jdbc.queryForList(anyString(), anyLong(), anyLong()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("unavailable"));
        assertThatThrownBy(() -> approvalService.complete("task-001", null, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("禁止办理");
        verify(taskService, never()).complete(anyString());
    }

    @Test
    void sourceGuard_unknownTypeAndOverflowRejected() {
        TaskQuery query = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(mockTask);
        when(taskService.getVariable("task-001", "businessType")).thenReturn("UNKNOWN");
        assertThatThrownBy(() -> approvalService.complete("task-001", null, null))
                .isInstanceOf(BusinessException.class);
        when(taskService.getVariable("task-001", "businessType")).thenReturn("PAYMENT_APPLY");
        when(taskService.getVariable("task-001", "businessId")).thenReturn("9999999999999999999");
        assertThatThrownBy(() -> approvalService.complete("task-001", null, null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void taskView_unrelatedUserDeniedBeforeBusinessRead() {
        when(mockTask.getAssignee()).thenReturn("300");
        TaskQuery query = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(mockTask);
        when(taskService.getIdentityLinksForTask("task-001")).thenReturn(List.of());
        HistoricProcessInstanceQuery history = mock(HistoricProcessInstanceQuery.class, RETURNS_SELF);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(history);
        assertThatThrownBy(() -> approvalService.getTaskDetail("task-001"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("无权查看");
        verify(query, never()).includeProcessVariables();
        verifyNoInteractions(jdbc);
    }

    @Test
    void taskView_candidateUserAllowed() {
        when(mockTask.getAssignee()).thenReturn(null);
        TaskQuery query = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(mockTask);
        org.flowable.identitylink.api.IdentityLink link = mock(org.flowable.identitylink.api.IdentityLink.class);
        when(link.getType()).thenReturn("candidate");
        when(link.getUserId()).thenReturn("200");
        when(taskService.getIdentityLinksForTask("task-001")).thenReturn(List.of(link));
        approvalService.assertCanViewTask("task-001");
        verifyNoInteractions(historyService);
    }

    @Test
    void taskView_historicParticipantAllowed() {
        when(mockTask.getAssignee()).thenReturn("300");
        TaskQuery query = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(mockTask);
        when(taskService.getIdentityLinksForTask("task-001")).thenReturn(List.of());
        HistoricProcessInstanceQuery history = mock(HistoricProcessInstanceQuery.class, RETURNS_SELF);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(history);
        when(approvalRecordMapper.selectCount(any())).thenReturn(1L);
        approvalService.assertCanViewTask("task-001");
    }

    @Test
    void selfApproval_variableReadFailureDoesNotApprove() {
        TaskQuery query = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(mockTask);
        when(taskService.getVariable("task-001", "initiator")).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(() -> approvalService.complete("task-001", null, null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jdbc);
        verify(taskService, never()).complete(anyString());
    }

    @Test
    void taskView_initiatorAllowed() {
        when(mockTask.getAssignee()).thenReturn("300");
        TaskQuery query = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(mockTask);
        when(taskService.getIdentityLinksForTask("task-001")).thenReturn(List.of());
        HistoricProcessInstanceQuery history = mock(HistoricProcessInstanceQuery.class, RETURNS_SELF);
        HistoricProcessInstance instance = mock(HistoricProcessInstance.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(history);
        when(history.singleResult()).thenReturn(instance);
        when(instance.getTenantId()).thenReturn("9999");
        when(instance.getStartUserId()).thenReturn("200");
        approvalService.assertCanViewTask("task-001");
        verifyNoInteractions(approvalRecordMapper);
    }

    @Test
    void sourceGuard_stateAndInstanceSingleAndBatch() {
        TaskQuery query = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(mockTask);
        for (String state : List.of("DRAFT", "APPROVED", "CLOSED")) {
            when(jdbc.queryForList(anyString(), anyLong(), anyLong())).thenReturn(List.of(Map.of("status", state, "workflow_instance_id", "pi-001")));
            assertThatThrownBy(() -> approvalService.complete("task-001", null, null)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> approvalService.batchApprove(List.of("task-001"), null)).isInstanceOf(BusinessException.class);
        }
        when(jdbc.queryForList(anyString(), anyLong(), anyLong())).thenReturn(List.of(Map.of("status", "SUBMITTED", "workflow_instance_id", "pi-new")));
        assertThatThrownBy(() -> approvalService.complete("task-001", null, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> approvalService.batchApprove(List.of("task-001"), null)).isInstanceOf(BusinessException.class);
        verify(taskService, never()).complete(anyString());
        when(jdbc.queryForList(anyString(), anyLong(), anyLong())).thenReturn(List.of(Map.of("status", "REJECTED", "workflow_instance_id", "pi-001")));
        approvalService.complete("task-001", null, null);
        approvalService.batchApprove(List.of("task-001"), null);
        verify(taskService, times(2)).complete("task-001");
    }


    @Test
    void finalSettlement_draftRequiresSameInstanceAndRejectedRecord_singleAndBatch() {
        TaskQuery query = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(mockTask);
        when(taskService.getVariable("task-001", "businessType")).thenReturn("FINAL_SETTLEMENT");
        when(taskService.getVariable("task-001", "initiator")).thenReturn("300");
        when(jdbc.queryForList(anyString(), anyLong(), anyLong())).thenReturn(List.of(Map.of("status", "DRAFT", "workflow_instance_id", "pi-001")));
        when(approvalRecordMapper.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> approvalService.complete("task-001", null, null)).isInstanceOf(BusinessException.class).hasMessageContaining("源单状态或流程实例不匹配");
        assertThatThrownBy(() -> approvalService.batchApprove(List.of("task-001"), null)).isInstanceOf(BusinessException.class).hasMessageContaining("源单状态或流程实例不匹配");
        when(approvalRecordMapper.selectCount(any())).thenReturn(1L);
        when(jdbc.queryForList(anyString(), anyLong(), anyLong())).thenReturn(List.of(Map.of("status", "DRAFT", "workflow_instance_id", "pi-new")));
        assertThatThrownBy(() -> approvalService.complete("task-001", null, null)).isInstanceOf(BusinessException.class).hasMessageContaining("源单状态或流程实例不匹配");
        assertThatThrownBy(() -> approvalService.batchApprove(List.of("task-001"), null)).isInstanceOf(BusinessException.class).hasMessageContaining("源单状态或流程实例不匹配");
        verify(taskService, never()).complete(anyString());
        when(jdbc.queryForList(anyString(), anyLong(), anyLong())).thenReturn(List.of(Map.of("status", "DRAFT", "workflow_instance_id", "pi-001")));
        approvalService.complete("task-001", null, null);
        approvalService.batchApprove(List.of("task-001"), null);
        verify(taskService, times(2)).complete("task-001");
        verify(approvalRecordMapper, times(6)).selectCount(any());
    }

    @Test
    void hrBinding_oldInstanceCannotApproveNewSubmission() {
        TaskQuery query = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(mockTask);
        for (String type : List.of("REGULAR_APPLY", "RESIGN_APPLY", "SEAL_APPLY", "TRANSFER_APPLY", "VEHICLE_APPLY")) {
            when(taskService.getVariable("task-001", "businessType")).thenReturn(type);
            when(jdbc.queryForList(anyString(), anyLong(), anyLong())).thenReturn(List.of(Map.of("status", "SUBMITTED", "workflow_instance_id", "pi-new")));
            assertThatThrownBy(() -> approvalService.complete("task-001", null, null)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> approvalService.batchApprove(List.of("task-001"), null)).isInstanceOf(BusinessException.class);
            when(jdbc.queryForList(anyString(), anyLong(), anyLong())).thenReturn(List.of(Map.of("status", "SUBMITTED", "workflow_instance_id", "pi-001")));
            approvalService.complete("task-001", null, null);
        }
        verify(taskService, times(5)).complete("task-001");
    }

    @Test
    void staleInstanceRejectActionsAndWithdrawCannotMutateNewHrSubmission() {
        TaskQuery query = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(mockTask);
        when(query.listPage(0, 1)).thenReturn(List.of(mockTask));
        when(taskService.getVariable("task-001", "businessType")).thenReturn("SEAL_APPLY");
        when(jdbc.queryForList(anyString(), anyLong(), anyLong())).thenReturn(List.of(Map.of("workflow_instance_id", "pi-new")));
        assertThatThrownBy(() -> approvalService.rejectToPrevious("task-001", "x")).hasMessageContaining("源单流程实例不匹配");
        assertThatThrownBy(() -> approvalService.rejectToStart("task-001", "x")).hasMessageContaining("源单流程实例不匹配");
        assertThatThrownBy(() -> approvalService.terminate("task-001", "x")).hasMessageContaining("源单流程实例不匹配");
        HistoricProcessInstanceQuery history = mock(HistoricProcessInstanceQuery.class, RETURNS_SELF);
        HistoricProcessInstance instance = mock(HistoricProcessInstance.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(history);
        when(history.singleResult()).thenReturn(instance);
        when(instance.getStartUserId()).thenReturn("200");
        assertThatThrownBy(() -> approvalService.withdrawByBusiness("SEAL_APPLY", 55L)).hasMessageContaining("源单流程实例不匹配");
        verifyNoInteractions(runtimeService, eventPublisher, approvalRecordMapper);
        verify(taskService, never()).addComment(anyString(), anyString(), anyString());
    }
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object obj) {
        return (List<Map<String, Object>>) obj;
    }
}
