package com.zwinsight.workflow.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.event.UrgeNotifyEvent;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.workflow.domain.WfUrgeConfig;
import com.zwinsight.workflow.domain.WfUrgeRecord;
import com.zwinsight.workflow.mapper.WfUrgeConfigMapper;
import com.zwinsight.workflow.mapper.WfUrgeRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
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
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * UrgeService 单元测试
 * <p>2026-09-25 催办修复配套：逐租户 taskTenantId 过滤、事件携带 tenantId
 * （@Async 监听器恢复上下文依赖）、手动催办无租户上下文拒绝。</p>
 */
@ExtendWith(MockitoExtension.class)
class UrgeServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), WfUrgeRecord.class);
    }

    @Mock private TaskService taskService;
    @Mock private RuntimeService runtimeService;
    @Mock private WfUrgeConfigMapper urgeConfigMapper;
    @Mock private WfUrgeRecordMapper urgeRecordMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UrgeService urgeService;

    /** 构造通过前置检查（存在/有处理人/是发起人）的 manualUrge mock 环境 */
    private Task stubManualUrgePreconditions() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getAssignee()).thenReturn("2");
        when(task.getProcessInstanceId()).thenReturn("pi-1");
        Map<String, Object> vars = new HashMap<>();
        vars.put("initiator", "1");
        when(runtimeService.getVariables("pi-1")).thenReturn(vars);
        return task;
    }

    @Test
    @DisplayName("催办次数查询：委托 mapper.countByTaskId")
    void testGetUrgeCount_delegates() {
        when(urgeRecordMapper.countByTaskId("task-1")).thenReturn(2);

        assertThat(urgeService.getUrgeCount("task-1")).isEqualTo(2);
    }

    @Test
    @DisplayName("自动催办：未启用时直接返回0，不扫描任务")
    void testAutoUrge_disabled_returnsZero() {
        WfUrgeConfig config = new WfUrgeConfig();
        config.setAutoUrgeEnabled(0);
        when(urgeConfigMapper.selectOne(any())).thenReturn(config);

        int result = urgeService.autoUrge(1L);

        assertThat(result).isZero();
        verify(taskService, never()).createTaskQuery();
    }

    @Test
    @DisplayName("自动催办：按本租户 taskTenantId 过滤，事件携带 tenantId（防 @Async 丢上下文）")
    void testAutoUrge_filtersByTenant_andEventCarriesTenant() {
        WfUrgeConfig config = new WfUrgeConfig();
        config.setAutoUrgeEnabled(1);
        config.setTimeoutHours(24);
        config.setIntervalHours(4);
        config.setMaxUrgeCount(3);
        when(urgeConfigMapper.selectOne(any())).thenReturn(config);

        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskTenantId("7")).thenReturn(taskQuery);
        when(taskQuery.taskCreatedBefore(any(Date.class))).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));
        when(task.getAssignee()).thenReturn("2");
        when(task.getId()).thenReturn("task-1");
        when(task.getName()).thenReturn("财务复核");
        when(task.getProcessInstanceId()).thenReturn("pi-1");
        when(urgeRecordMapper.countByTaskId("task-1")).thenReturn(0);
        when(urgeRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        int result = urgeService.autoUrge(7L);

        assertThat(result).isEqualTo(1);
        // 租户过滤硬断言：不加 taskTenantId 就会跨租户错乱（本次修复核心）
        verify(taskQuery).taskTenantId("7");
        verify(urgeRecordMapper).insert(any(WfUrgeRecord.class));
        ArgumentCaptor<UrgeNotifyEvent> captor = ArgumentCaptor.forClass(UrgeNotifyEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("手动催办：无租户上下文直接拒绝（防站内消息落入幽灵租户 0）")
    void testManualUrge_withoutTenantContext_rejected() {
        stubManualUrgePreconditions();
        when(urgeConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(urgeRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(urgeRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getTenantId).thenReturn(null);

            assertThatThrownBy(() -> urgeService.manualUrge("task-x", "1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("租户上下文缺失");
            verify(urgeRecordMapper, never()).insert(any());
        }
    }

    @Test
    @DisplayName("手动催办正常路径：记录落库且事件携带当前租户")
    void testManualUrge_success_eventCarriesTenant() {
        Task task = stubManualUrgePreconditions();
        when(urgeConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(urgeRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(urgeRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(task.getName()).thenReturn("部门审批");
        when(task.getId()).thenReturn("task-1");
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getTenantId).thenReturn(3L);

            urgeService.manualUrge("task-1", "1");
        }

        verify(urgeRecordMapper).insert(any(WfUrgeRecord.class));
        ArgumentCaptor<UrgeNotifyEvent> captor = ArgumentCaptor.forClass(UrgeNotifyEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("手动催办：任务不存在抛异常")
    void testManualUrge_taskNotFound() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);

        assertThatThrownBy(() -> urgeService.manualUrge("task-x", "1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务不存在或已被处理");
    }

    @Test
    @DisplayName("手动催办：任务无处理人抛异常")
    void testManualUrge_noAssignee() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getAssignee()).thenReturn(null);

        assertThatThrownBy(() -> urgeService.manualUrge("task-x", "1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂无处理人");
    }

    @Test
    @DisplayName("手动催办：非发起人催办被拒绝")
    void testManualUrge_notInitiator_rejected() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId(anyString())).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getAssignee()).thenReturn("2");
        when(task.getProcessInstanceId()).thenReturn("pi-1");

        Map<String, Object> vars = new HashMap<>();
        vars.put("initiator", "99");
        when(runtimeService.getVariables("pi-1")).thenReturn(vars);

        assertThatThrownBy(() -> urgeService.manualUrge("task-x", "1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅流程发起人可以催办");
    }

    @Test
    @DisplayName("手动催办：次数达上限拒绝（限流分支钉住）")
    void testManualUrge_countExceedsLimit_rejected() {
        stubManualUrgePreconditions();
        when(urgeConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null); // 默认配置 maxUrgeCount=3
        // 已手动催办 3 次 = 上限
        when(urgeRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        assertThatThrownBy(() -> urgeService.manualUrge("task-x", "1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("催办次数已达上限");

        verify(urgeRecordMapper, never()).insert(any());
    }

    @Test
    @DisplayName("手动催办：距上次催办间隔不足拒绝（限流分支钉住）")
    void testManualUrge_intervalNotExceeded_rejected() {
        stubManualUrgePreconditions();
        when(urgeConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null); // 默认 intervalHours=4
        when(urgeRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        // 上次催办在 1 小时前，未到 4 小时间隔
        WfUrgeRecord lastRecord = new WfUrgeRecord();
        lastRecord.setUrgeTime(LocalDateTime.now().minusHours(1));
        when(urgeRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(lastRecord);

        assertThatThrownBy(() -> urgeService.manualUrge("task-x", "1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("催办间隔不足");

        verify(urgeRecordMapper, never()).insert(any());
    }
}
