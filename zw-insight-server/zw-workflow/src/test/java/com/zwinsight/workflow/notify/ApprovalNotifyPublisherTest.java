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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ApprovalNotifyPublisher 单元测试：收件人解析（处理人/候选人/候选组）、结果通知，
 * 以及「提交后才发布、回滚不发布」的事务时序约束。
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

    /**
     * taskService 是 {@code @Lazy @Autowired} 的非 final 字段（延迟注入以打破与
     * FlowableConfig 的构造期循环依赖）；Mockito 在构造器注入成功后不再做字段注入，
     * 故此处用反射补注入，否则调用链上会 NPE。
     */
    @BeforeEach
    void injectLazyTaskService() throws Exception {
        java.lang.reflect.Field field = ApprovalNotifyPublisher.class.getDeclaredField("taskService");
        field.setAccessible(true);
        field.set(publisher, taskService);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Task task(String assignee, String tenantId) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getAssignee()).thenReturn(assignee);
        when(task.getTenantId()).thenReturn(tenantId);
        when(task.getName()).thenReturn("项目负责人审批");
        when(task.getProcessInstanceId()).thenReturn("pi-1");
        return task;
    }

    /** 流程实例 pi-1 的活动任务查询（逐级打桩：未打桩的链式调用会返回 null） */
    private void stubActiveTasks(Task... tasks) {
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.processInstanceId("pi-1")).thenReturn(query);
        when(query.list()).thenReturn(List.of(tasks));
        when(taskService.getVariables("task-1"))
                .thenReturn(Map.of("businessType", "PAYMENT_APPLY", "businessId", 55L));
        when(businessTypeMapper.selectOne(any())).thenReturn(null);
    }

    private ApprovalNotifyEvent captureEvent() {
        ArgumentCaptor<ApprovalNotifyEvent> captor = ArgumentCaptor.forClass(ApprovalNotifyEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("活动任务通知：通知已指派处理人，事件携带租户与任务ID")
    void notifyActiveTasks_assignee() {
        stubActiveTasks(task("777", "5"));

        publisher.notifyActiveTasks("pi-1");

        ApprovalNotifyEvent event = captureEvent();
        assertThat(event.getNotifyType()).isEqualTo(ApprovalNotifyEvent.TYPE_TODO);
        assertThat(event.getTargetUserIds()).containsExactly(777L);
        assertThat(event.getTenantId()).isEqualTo(5L);
        assertThat(event.getTaskId()).isEqualTo("task-1");
        assertThat(event.getBusinessType()).isEqualTo("PAYMENT_APPLY");
        assertThat(event.getBusinessId()).isEqualTo(55L);
    }

    @Test
    @DisplayName("活动任务通知：候选组（角色）解析为租户内用户")
    void notifyActiveTasks_candidateGroup_resolvesUsers() {
        stubActiveTasks(task(null, "5"));
        IdentityLink link = mock(IdentityLink.class);
        when(link.getType()).thenReturn("candidate");
        when(link.getGroupId()).thenReturn("PROJECT_MANAGER");
        when(link.getUserId()).thenReturn(null);
        when(taskService.getIdentityLinksForTask("task-1")).thenReturn(List.of(link));
        when(sysUserMapper.selectUserIdsByRoleCodes(List.of("PROJECT_MANAGER"), 5L))
                .thenReturn(List.of(11L, 22L));

        publisher.notifyActiveTasks("pi-1");

        assertThat(captureEvent().getTargetUserIds()).containsExactlyInAnyOrder(11L, 22L);
    }

    @Test
    @DisplayName("活动任务通知：点名候选人与候选组去重合并")
    void notifyActiveTasks_candidateUserAndGroup_deduplicated() {
        stubActiveTasks(task(null, "5"));
        IdentityLink userLink = mock(IdentityLink.class);
        when(userLink.getType()).thenReturn("candidate");
        when(userLink.getUserId()).thenReturn("11");
        IdentityLink groupLink = mock(IdentityLink.class);
        when(groupLink.getType()).thenReturn("candidate");
        when(groupLink.getGroupId()).thenReturn("FINANCE_STAFF");
        when(taskService.getIdentityLinksForTask("task-1")).thenReturn(List.of(userLink, groupLink));
        when(sysUserMapper.selectUserIdsByRoleCodes(List.of("FINANCE_STAFF"), 5L))
                .thenReturn(List.of(11L, 33L));

        publisher.notifyActiveTasks("pi-1");

        assertThat(captureEvent().getTargetUserIds()).containsExactlyInAnyOrder(11L, 33L);
    }

    @Test
    @DisplayName("活动任务通知：无处理人且无候选人时不发通知")
    void notifyActiveTasks_noRecipients_skips() {
        stubActiveTasks(task(null, "5"));
        when(taskService.getIdentityLinksForTask("task-1")).thenReturn(List.of());

        publisher.notifyActiveTasks("pi-1");

        verify(eventPublisher, never()).publishEvent(any(ApprovalNotifyEvent.class));
    }

    @Test
    @DisplayName("活动任务通知：无活动任务（流程已结束）时不发通知")
    void notifyActiveTasks_noActiveTasks_skips() {
        stubActiveTasks();

        publisher.notifyActiveTasks("pi-1");

        verify(eventPublisher, never()).publishEvent(any(ApprovalNotifyEvent.class));
    }

    @Test
    @DisplayName("活动任务通知：任务缺少租户时跳过（不伪造归属）")
    void notifyActiveTasks_missingTenant_skips() {
        stubActiveTasks(task("777", null));

        publisher.notifyActiveTasks("pi-1");

        verify(eventPublisher, never()).publishEvent(any(ApprovalNotifyEvent.class));
    }

    @Test
    @DisplayName("活动任务通知：流程实例ID为空时直接返回")
    void notifyActiveTasks_blankProcessInstance_skips() {
        publisher.notifyActiveTasks(" ");

        verify(taskService, never()).createTaskQuery();
    }

    @Test
    @DisplayName("活动任务通知：引擎查询异常被吞并，不影响审批主流程")
    void notifyActiveTasks_queryFailure_swallowed() {
        when(taskService.createTaskQuery()).thenThrow(new IllegalStateException("engine down"));

        assertThatCode(() -> publisher.notifyActiveTasks("pi-1")).doesNotThrowAnyException();
        verify(eventPublisher, never()).publishEvent(any(ApprovalNotifyEvent.class));
    }

    @Test
    @DisplayName("事务时序：事务内只登记、提交后才发布")
    void publishesOnlyAfterCommit() {
        stubActiveTasks(task("777", "5"));
        TransactionSynchronizationManager.initSynchronization();

        publisher.notifyActiveTasks("pi-1");

        verify(eventPublisher, never()).publishEvent(any(ApprovalNotifyEvent.class));
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertThat(syncs).hasSize(1);
        syncs.forEach(TransactionSynchronization::afterCommit);
        assertThat(captureEvent().getTargetUserIds()).containsExactly(777L);
    }

    @Test
    @DisplayName("事务时序：主事务回滚（afterCommit 不触发）时不发布，不留幽灵消息")
    void rollback_doesNotPublish() {
        stubActiveTasks(task("777", "5"));
        TransactionSynchronizationManager.initSynchronization();

        publisher.notifyActiveTasks("pi-1");
        publisher.notifyResult("PAYMENT_APPLY", 55L, "pi-1", "300", ApprovalNotifyEvent.TYPE_APPROVED, 5L);
        // 回滚：只触发 afterCompletion(STATUS_ROLLED_BACK)，不触发 afterCommit
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

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
        assertThat(event.getTenantId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("结果通知（驳回/撤回）：类型 REJECTED，文案含驳回")
    void notifyResult_rejected() {
        when(businessTypeMapper.selectOne(any())).thenReturn(null);

        publisher.notifyResult("PAYMENT_APPLY", 55L, "pi-1", "300", "WITHDRAW", 5L);

        ApprovalNotifyEvent event = captureEvent();
        assertThat(event.getNotifyType()).isEqualTo(ApprovalNotifyEvent.TYPE_REJECTED);
        assertThat(event.getTitle()).contains("审批驳回");
    }

    @Test
    @DisplayName("结果通知：发起人缺失/非法或租户缺失时不发通知")
    void notifyResult_missingInitiatorOrTenant_skips() {
        publisher.notifyResult("PAYMENT_APPLY", 55L, "pi-1", null, "APPROVED", 5L);
        publisher.notifyResult("PAYMENT_APPLY", 55L, "pi-1", "not-a-number", "APPROVED", 5L);
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

    @Test
    @DisplayName("业务类型名称解析：查询异常回退编码，不阻断通知")
    void notifyResult_businessTypeLookupFailure_fallsBackToCode() {
        when(businessTypeMapper.selectOne(any())).thenThrow(new IllegalStateException("db down"));

        publisher.notifyResult("PAYMENT_APPLY", 55L, "pi-1", "300", ApprovalNotifyEvent.TYPE_APPROVED, 5L);

        assertThat(captureEvent().getTitle()).isEqualTo("【审批通过】PAYMENT_APPLY");
    }
}
