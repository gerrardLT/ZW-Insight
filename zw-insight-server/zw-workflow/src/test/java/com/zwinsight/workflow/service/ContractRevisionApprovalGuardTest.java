package com.zwinsight.workflow.service;
import com.zwinsight.common.config.SecurityContextHolder;
import org.flowable.task.api.Task;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ContractRevisionApprovalGuardTest {
    ApprovalService service;
    TaskService tasks;
    JdbcTemplate jdbc;
    Task task;
    @BeforeEach void setup() {
        service = mock(ApprovalService.class, CALLS_REAL_METHODS); tasks = mock(TaskService.class); jdbc = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service, "taskService", tasks); ReflectionTestUtils.setField(service, "jdbc", jdbc);
        task = mock(Task.class); when(task.getId()).thenReturn("task");
        when(tasks.getVariable("task", "businessType")).thenReturn("CONTRACT_REVISION");
        SecurityContextHolder.setTenantId(9999L);
    }
    @AfterEach void clear() { SecurityContextHolder.clear(); }
    @Test void rejectedCurrentProcessRemainsCompletable() {
        when(tasks.getVariable("task", "businessId")).thenReturn(4L); when(task.getProcessInstanceId()).thenReturn("old");
        when(jdbc.queryForList(anyString(), eq(4L), eq(9999L))).thenReturn(List.of(Map.of("status", "REJECTED", "workflow_instance_id", "old")));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "assertSourceExists", task));
    }
    @Test void rejectedDifferentProcessIsDenied() {
        when(tasks.getVariable("task", "businessId")).thenReturn(4L); when(task.getProcessInstanceId()).thenReturn("other");
        when(jdbc.queryForList(anyString(), eq(4L), eq(9999L))).thenReturn(List.of(Map.of("status", "REJECTED", "workflow_instance_id", "old")));
        assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(service, "assertSourceExists", task));
    }
    @Test void initiatorNeverGetsSelfApprovalFallback() {
        when(tasks.getVariable("task", "initiator")).thenReturn("9");
        assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(service, "assertNotInitiator", task, 9L));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "assertNotInitiator", task, 10L));
    }
}
