package com.zwinsight.workflow.service;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.workflow.mapper.WfApprovalRecordMapper;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessDetailServiceTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private ApprovalService approvalService;
    @Mock private TaskService taskService;
    @Mock private HistoryService historyService;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private WfApprovalRecordMapper approvalRecordMapper;

    @InjectMocks
    private BusinessDetailService service;

    private Map<String, Object> taskDetail(String type, Object id) {
        return Map.of("businessType", type, "businessId", id);
    }

    @Test
    @DisplayName("脱敏与格式化：身份证/手机号保留首尾，布尔转是否，金额不带科学计数")
    void formatMaskAndTypes() {
        assertThat(BusinessDetailService.format("MASK", "110101199001011234")).isEqualTo("110****1234");
        assertThat(BusinessDetailService.format("MASK", "13800138000")).isEqualTo("138****8000");
        assertThat(BusinessDetailService.format("MASK", "abc")).isEqualTo("a***");
        assertThat(BusinessDetailService.format("B", 1)).isEqualTo("是");
        assertThat(BusinessDetailService.format("B", false)).isEqualTo("否");
        assertThat(BusinessDetailService.format("M", new BigDecimal("1E+7"))).isEqualTo("10000000");
        assertThat(BusinessDetailService.format("T", null)).isNull();
    }

    @Test
    @DisplayName("超管取施工合同详情：按白名单列查询，绑定单据ID与租户ID，返回中文标签字段")
    void superAdminGetsContractFields() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(1L);
            sc.when(SecurityContextHolder::getTenantId).thenReturn(1L);
            when(sysUserMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("SUPER_ADMIN"));
            when(approvalService.getTaskDetail("t1")).thenReturn(taskDetail("CONSTRUCTION_CONTRACT", "2108043445773475842"));
            when(jdbc.queryForList(anyString(), eq(2108043445773475842L), eq(1L)))
                    .thenReturn(List.of(Map.of("contract_code", "HT-001", "contract_amount", new BigDecimal("1200000.00"))));

            Map<String, Object> r = service.getForTask("t1");

            assertThat(r.get("supported")).isEqualTo(true);
            assertThat(r.get("found")).isEqualTo(true);
            assertThat(r.get("typeName")).isEqualTo("施工合同");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fields = (List<Map<String, Object>>) r.get("fields");
            assertThat(fields).anySatisfy(f -> {
                assertThat(f.get("label")).isEqualTo("合同编号");
                assertThat(f.get("value")).isEqualTo("HT-001");
            });
            assertThat(fields).anySatisfy(f -> {
                assertThat(f.get("label")).isEqualTo("合同金额");
                assertThat(f.get("value")).isEqualTo("1200000.00");
            });
        }
    }

    @Test
    @DisplayName("白名单外的业务类型：返回 supported=false，不查库")
    void unsupportedTypeDoesNotQuery() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(1L);
            when(sysUserMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("SUPER_ADMIN"));
            when(approvalService.getTaskDetail("t1")).thenReturn(taskDetail("PAYMENT_APPLY", "5"));

            Map<String, Object> r = service.getForTask("t1");

            assertThat(r.get("supported")).isEqualTo(false);
            verify(jdbc, never()).queryForList(anyString(), eq(5L), eq(1L));
        }
    }

    @Test
    @DisplayName("单据已不存在（或不属于本租户）：found=false，不抛错")
    void missingRowReturnsNotFound() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(1L);
            sc.when(SecurityContextHolder::getTenantId).thenReturn(1L);
            when(sysUserMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("SUPER_ADMIN"));
            when(approvalService.getTaskDetail("t1")).thenReturn(taskDetail("SEAL_APPLY", "9"));
            when(jdbc.queryForList(anyString(), eq(9L), eq(1L))).thenReturn(List.of());

            Map<String, Object> r = service.getForTask("t1");

            assertThat(r.get("found")).isEqualTo(false);
        }
    }

    @Test
    @DisplayName("与任务无关的普通用户：403，且不查业务表")
    void unrelatedUserIsDenied() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(7L);
            when(sysUserMapper.selectRoleCodesByUserId(7L)).thenReturn(List.of("STAFF"));
            when(approvalService.getTaskDetail("t1")).thenReturn(taskDetail("SEAL_APPLY", "9"));
            TaskQuery q = mock(TaskQuery.class);
            Task task = mock(Task.class);
            when(taskService.createTaskQuery()).thenReturn(q);
            when(q.taskId("t1")).thenReturn(q);
            when(q.singleResult()).thenReturn(task);
            when(task.getAssignee()).thenReturn("1");
            when(taskService.getIdentityLinksForTask("t1")).thenReturn(List.of());

            assertThatThrownBy(() -> service.getForTask("t1"))
                    .isInstanceOf(BusinessException.class);
            verify(jdbc, never()).queryForList(anyString(), eq(9L), eq(1L));
        }
    }

    @Test
    @DisplayName("业务单据ID不是合法正整数：拒绝，不拼进 SQL")
    void invalidBusinessIdIsRejected() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(1L);
            sc.when(SecurityContextHolder::getTenantId).thenReturn(1L);
            when(sysUserMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("SUPER_ADMIN"));
            when(approvalService.getTaskDetail("t1")).thenReturn(taskDetail("SEAL_APPLY", "1 OR 1=1"));

            assertThatThrownBy(() -> service.getForTask("t1")).isInstanceOf(BusinessException.class);
        }
    }
}
