package com.zwinsight.workflow.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.workflow.domain.WfApprovalRecord;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.mapper.WfApprovalRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
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
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private WfApprovalRecordMapper approvalRecordMapper;

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
        mockTask = mock(Task.class);
        lenient().when(mockTask.getId()).thenReturn("task-001");
        lenient().when(mockTask.getProcessInstanceId()).thenReturn("pi-001");
        lenient().when(mockTask.getName()).thenReturn("部门经理审批");
        lenient().when(mockTask.getTaskDefinitionKey()).thenReturn("deptManagerApprove");
        lenient().when(mockTask.getAssignee()).thenReturn("100");
    }

    // =====================================================================
    // startProcess
    // =====================================================================

    @Test
    @DisplayName("发起流程：正常发起，验证变量设置和 businessKey 格式")
    void testStartProcess_success() {
        try (var sc1 = mockStatic(SecurityContextHolder.class)) {
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
        try (var sc = mockStatic(SecurityContextHolder.class)) {
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
        try (var sc = mockStatic(SecurityContextHolder.class)) {
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
        try (var sc = mockStatic(SecurityContextHolder.class)) {
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
        try (var sc = mockStatic(SecurityContextHolder.class)) {
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
    @DisplayName("办理通过：任务不存在抛 BusinessException")
    void testComplete_taskNotFound() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
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
        try (var sc = mockStatic(SecurityContextHolder.class)) {
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
        try (var sc = mockStatic(SecurityContextHolder.class)) {
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
        try (var sc = mockStatic(SecurityContextHolder.class)) {
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
        try (var sc = mockStatic(SecurityContextHolder.class)) {
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
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId("task-001")).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(mockTask);

            approvalService.transfer("task-001", "300", "出差代审");

            verify(taskService).addComment("task-001", "pi-001", "【转办】出差代审");
            verify(taskService).setAssignee("task-001", "300");
            verify(approvalRecordMapper).insert(argThat(r -> "TRANSFER".equals(r.getOperationType())));
        }
    }

    // =====================================================================
    // delegate
    // =====================================================================

    @Test
    @DisplayName("委托：验证 delegateTask")
    void testDelegate_success() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId("task-001")).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(mockTask);

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
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(200L);

            Task task2 = mock(Task.class);
            lenient().when(task2.getId()).thenReturn("task-002");
            lenient().when(task2.getProcessInstanceId()).thenReturn("pi-002");
            lenient().when(task2.getName()).thenReturn("审批节点2");
            lenient().when(task2.getTaskDefinitionKey()).thenReturn("node2");

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
                    .thenReturn(mockTask)
                    .thenReturn(task2)
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
    @DisplayName("我的待办：分页查询返回正确结构")
    void testGetMyTodoTasks() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskAssignee("100")).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(1L);
        when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
        when(taskQuery.desc()).thenReturn(taskQuery);
        when(taskQuery.listPage(0, 10)).thenReturn(List.of(mockTask));
        when(taskService.getVariables("task-001")).thenReturn(Map.of("businessType", "CONTRACT", "businessId", 1L));

        PageResult<Map<String, Object>> result = approvalService.getMyTodoTasks(100L, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).get("taskId")).isEqualTo("task-001");
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
}
