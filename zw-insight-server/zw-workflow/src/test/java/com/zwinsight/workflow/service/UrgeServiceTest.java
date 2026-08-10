package com.zwinsight.workflow.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.workflow.domain.WfUrgeConfig;
import com.zwinsight.workflow.mapper.WfUrgeConfigMapper;
import com.zwinsight.workflow.mapper.WfUrgeRecordMapper;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * UrgeService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class UrgeServiceTest {

    @Mock private TaskService taskService;
    @Mock private RuntimeService runtimeService;
    @Mock private WfUrgeConfigMapper urgeConfigMapper;
    @Mock private WfUrgeRecordMapper urgeRecordMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UrgeService urgeService;

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

        int result = urgeService.autoUrge();

        assertThat(result).isZero();
        verify(taskService, never()).createTaskQuery();
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
}
