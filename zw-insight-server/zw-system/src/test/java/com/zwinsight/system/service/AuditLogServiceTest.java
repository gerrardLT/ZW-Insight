package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.system.domain.SysAuditLog;
import com.zwinsight.system.mapper.SysAuditLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审计日志服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                SysAuditLog.class);
    }

    @Mock private SysAuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    @DisplayName("分页查询：返回 PageResult 结构")
    void testPage_returnsPageResult() {
        Page<SysAuditLog> page = new Page<>(1, 10);
        SysAuditLog log = new SysAuditLog();
        log.setTableName("sys_user");
        page.setRecords(List.of(log));
        page.setTotal(1);
        when(auditLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<SysAuditLog> result = auditLogService.page(1, 10, "sys_user", 100L);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1L);
    }

    @Test
    @DisplayName("记录审计日志：字段完整落库")
    void testSave_fieldsCaptured() {
        auditLogService.save("biz_contract", 88L, "contract_amount", "100", "200");

        ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        SysAuditLog saved = captor.getValue();
        assertThat(saved.getTableName()).isEqualTo("biz_contract");
        assertThat(saved.getRecordId()).isEqualTo(88L);
        assertThat(saved.getFieldName()).isEqualTo("contract_amount");
        assertThat(saved.getOldValue()).isEqualTo("100");
        assertThat(saved.getNewValue()).isEqualTo("200");
        assertThat(saved.getOperTime()).isNotNull();
    }

    @Test
    @DisplayName("记录审计日志：写入租户与操作人上下文（跨租户越权修复配套 2026-08-14）")
    void testSave_capturesTenantContext() {
        SecurityContextHolder.setTenantId(9999L);
        SecurityContextHolder.setUserId(200L);
        try {
            auditLogService.save("biz_contract", 1L, "amount", "1", "2");

            ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
            verify(auditLogMapper).insert(captor.capture());
            assertThat(captor.getValue().getTenantId()).isEqualTo(9999L);
            assertThat(captor.getValue().getOperUserId()).isEqualTo(200L);
        } finally {
            SecurityContextHolder.clear();
        }
    }

    @Test
    @DisplayName("分页查询：有租户上下文时 wrapper 含 tenant_id 过滤（跨租户越权修复 2026-08-14）")
    @SuppressWarnings("unchecked")
    void testPage_withTenantContext_filtersByTenant() {
        SecurityContextHolder.setTenantId(1L);
        try {
            Page<SysAuditLog> page = new Page<>(1, 10);
            page.setRecords(List.of());
            when(auditLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

            auditLogService.page(1, 10, null, null);

            ArgumentCaptor<LambdaQueryWrapper<SysAuditLog>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(auditLogMapper).selectPage(any(Page.class), captor.capture());
            assertThat(captor.getValue().getSqlSegment()).contains("tenant_id");
        } finally {
            SecurityContextHolder.clear();
        }
    }

    @Test
    @DisplayName("字段变更事件：监听器按事件内容落库，操作人/租户取自事件而非当前线程")
    void onAuditLogEvent_persistsFromEvent() {
        com.zwinsight.common.event.AuditLogRecordEvent event = new com.zwinsight.common.event.AuditLogRecordEvent(
                this, "biz_payment_apply", 7L, "paymentAmount", "100", "200", 3L, 9L, "张三");

        auditLogService.onAuditLogEvent(event);

        ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        SysAuditLog saved = captor.getValue();
        assertThat(saved.getTableName()).isEqualTo("biz_payment_apply");
        assertThat(saved.getRecordId()).isEqualTo(7L);
        assertThat(saved.getFieldName()).isEqualTo("paymentAmount");
        assertThat(saved.getOldValue()).isEqualTo("100");
        assertThat(saved.getNewValue()).isEqualTo("200");
        assertThat(saved.getTenantId()).isEqualTo(3L);
        assertThat(saved.getOperUserId()).isEqualTo(9L);
        assertThat(saved.getOperUserName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("字段变更事件：超长值截断到 2000 字符；落库异常被吞掉，不影响发布方业务事务")
    void onAuditLogEvent_truncatesAndSwallowsFailure() {
        String longValue = "x".repeat(2500);
        auditLogService.onAuditLogEvent(new com.zwinsight.common.event.AuditLogRecordEvent(
                this, "biz_project", 1L, "projectOverview", longValue, "短", 1L, 1L, null));
        ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getOldValue()).hasSize(2000);

        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(auditLogMapper).insert(any(SysAuditLog.class));
        org.assertj.core.api.Assertions.assertThatCode(() ->
                auditLogService.onAuditLogEvent(new com.zwinsight.common.event.AuditLogRecordEvent(
                        this, "biz_project", 1L, "f", "a", "b", 1L, 1L, null)))
                .doesNotThrowAnyException();
    }
}
