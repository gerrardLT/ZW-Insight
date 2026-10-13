package com.zwinsight.workflow.notify;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.common.event.ApprovalNotifyEvent;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.workflow.domain.WfBusinessType;
import com.zwinsight.workflow.mapper.WfBusinessTypeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ApprovalNotifyPublisher 单元测试：收件人解析（处理人/候选人/候选组）与结果通知。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalNotifyPublisherTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                WfBusinessType.class);
    }

    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private WfBusinessTypeMapper businessTypeMapper;
    @Mock private TaskService taskService;

    @InjectMocks
    private ApprovalNotifyPublisher publisher;

    private Task task(String assignee) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getAssignee()).thenReturn(assignee);
        when(task.getTenantId()).thenReturn("5");
        when(task.getName()).thenReturn("项目负责人审批");
        when(task.getProcessInstanceId()).thenReturn("pi-1");
        return task;
    }

    private void stubTaskAndVariables(Task task) {
        stubTaskQuery(task);
        when(taskService.getVariables("task-1")).thenReturn(Map.of("businessType", "PAYMENT_APPLY", "businessId", 55L));
        when(businessTypeMapper.selectOne(any())).thenReturn(null);
    }

    /** 链式查询需逐级打桩：Mockito 对未打桩的 createTaskQuery() 返回 null，直接链式会 NPE */
    private void stubTaskQuery(Task task) {
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("task-1")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);
    }

    private ApprovalNotifyEvent captureEvent() {
        ArgumentCaptor<ApprovalNotifyEvent> captor = ArgumentCaptor.forClass(ApprovalNotifyEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("待办通知：通知已指派处理人")
    void notifyTodo_assignee() {
        Task task = task("777");
        stubTaskAndVariables(task);

        publisher.notifyTodo("task-1");

        ApprovalNotifyEvent event = captureEvent();
        assertThat(event.getNotifyType()).isEqualTo(ApprovalNotifyEvent.TYPE_TODO);
        assertThat(event.getTargetUserIds()).containsExactly(777L);
        assertThat(event.getTenantId()).isEqualTo(5L);
        assertThat(event.getTaskId()).isEqualTo("task-1");
    }

    @Test
    @DisplayName("待办通知：候选组（角色）解析为租户内用户")
    void notifyTodo_candidateGroup_resolvesUsers() {
        Task task = task(null);
        stubTaskAndVariables(task);
        IdentityLink link = mock(IdentityLink.class);
        when(link.getType()).thenReturn("candidate");
        when(link.getGroupId()).thenReturn("PROJECT_MANAGER");
        when(link.getUserId()).thenReturn(null);
        when(taskService.getIdentityLinksForTask("task-1")).thenReturn(List.of(link));
        when(sysUserMapper.selectUserIdsByRoleCodes(eq(List.of("PROJECT_MANAGER")), eq(5L)))
                .thenReturn(List.of(11L, 22L));

        publisher.notifyTodo("task-1");

        ApprovalNotifyEvent event = captureEvent();
        assertThat(event.getTargetUserIds()).containsExactlyInAnyOrder(11L, 22L);
    }

    @Test
    @DisplayName("待办通知：无处理人且无候选人时不发通知")
    void notifyTodo_noRecipients_skips() {
        Task task = task(null);
        stubTaskAndVariables(task);
        when(taskService.getIdentityLinksForTask("task-1")).thenReturn(List.of());

        publisher.notifyTodo("task-1");

        verify(eventPublisher, never()).publishEvent(any(ApprovalNotifyEvent.class));
    }

    @Test
    @DisplayName("待办通知：任务已被推进（查不到）时不发通知")
    void notifyTodo_taskGone_skips() {
        stubTaskQuery(null);

        publisher.notifyTodo("task-1");

        verify(eventPublisher, never()).publishEvent(any(ApprovalNotifyEvent.class));
    }

    @Test
    @DisplayName("结果通知（通过）：通知发起人，类型 APPROVED")
    void notifyResult_approved() {
        when(businessTypeMapper.selectOne(any())).thenReturn(null);

        publisher.notifyResult("PAYMENT_APPLY", 55L, "pi-1", "300", ApprovalNotifyEvent.TYPE_APPROVED, 5L);

        ApprovalNotifyEvent event = captureEvent();
        assertThat(event.getNotifyType()).isEqualTo(ApprovalNotifyEvent.TYPE_APPROVED);
        assertThat(event.getTargetUserIds()).containsExactly(300L);
    }

    @Test
    @DisplayName("结果通知（驳回）：类型 REJECTED，文案含驳回")
    void notifyResult_rejected() {
        when(businessTypeMapper.selectOne(any())).thenReturn(null);

        publisher.notifyResult("PAYMENT_APPLY", 55L, "pi-1", "300", "REJECT", 5L);

        ApprovalNotifyEvent event = captureEvent();
        assertThat(event.getNotifyType()).isEqualTo(ApprovalNotifyEvent.TYPE_REJECTED);
        assertThat(event.getTitle()).contains("审批驳回");
    }

    @Test
    @DisplayName("结果通知：发起人缺失或租户缺失时不发通知")
    void notifyResult_missingInitiatorOrTenant_skips() {
        publisher.notifyResult("PAYMENT_APPLY", 55L, "pi-1", null, "APPROVED", 5L);
        publisher.notifyResult("PAYMENT_APPLY", 55L, "pi-1", "300", "APPROVED", null);

        verify(eventPublisher, never()).publishEvent(any(ApprovalNotifyEvent.class));
    }

    @Test
    @DisplayName("业务类型名称解析：命中 wf_business_type 时用中文名")
    void notifyResult_usesBusinessTypeName() {
        WfBusinessType type = new WfBusinessType();
        type.setTypeName("付款申请");
        when(businessTypeMapper.selectOne(any())).thenReturn(type);

        publisher.notifyResult("PAYMENT_APPLY", 55L, "pi-1", "300", ApprovalNotifyEvent.TYPE_APPROVED, 5L);

        ApprovalNotifyEvent event = captureEvent();
        assertThat(event.getTitle()).isEqualTo("【审批通过】付款申请");
        assertThat(event.getContent()).contains("付款申请");
    }
}
