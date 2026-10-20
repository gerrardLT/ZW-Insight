package com.zwinsight.workflow.service;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.workflow.domain.BizWorkflowCc;
import com.zwinsight.workflow.mapper.BizWorkflowCcMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WorkflowCcService 单测（V2026_123 / V2026_124 审查修复）：
 * 抄送人有效性、租户隔离、重复 ID、数量上限、空列表。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowCcServiceTest {

    @Mock
    private BizWorkflowCcMapper ccMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @InjectMocks
    private WorkflowCcService service;

    @BeforeEach
    void context() {
        SecurityContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clear();
    }

    @Test
    @DisplayName("create - 本租户启用用户正常落库")
    void create_insertsForActiveUsers() {
        when(sysUserMapper.selectActiveIdsByTenant(1L, List.of(1L, 2L)))
                .thenReturn(List.of(1L, 2L));

        service.create("proc-1", "PAYMENT_APPLY", 10L, List.of(1L, 2L), 9L);

        verify(ccMapper, times(2)).insert(any(BizWorkflowCc.class));
    }

    @Test
    @DisplayName("create - 禁用/不存在/跨租户用户拒绝（不静默落库）")
    void create_rejectsInactiveOrCrossTenantUser() {
        // 传入 1、2，本租户只命中 1；2 可能是禁用、不存在或属于其他租户
        when(sysUserMapper.selectActiveIdsByTenant(1L, List.of(1L, 2L)))
                .thenReturn(List.of(1L));

        assertThrows(BusinessException.class,
                () -> service.create("proc-1", "PAYMENT_APPLY", 10L, List.of(1L, 2L), 9L));
        verify(ccMapper, never()).insert(any(BizWorkflowCc.class));
    }

    @Test
    @DisplayName("create - 重复 ID 先去重，只落一条且不误判为不存在")
    void create_deduplicatesIds() {
        when(sysUserMapper.selectActiveIdsByTenant(1L, List.of(1L)))
                .thenReturn(List.of(1L));

        service.create("proc-1", "PAYMENT_APPLY", 10L, List.of(1L, 1L), 9L);

        verify(ccMapper, times(1)).insert(any(BizWorkflowCc.class));
    }

    @Test
    @DisplayName("create - 抄送人超过 50 人拒绝")
    void create_rejectsTooManyUsers() {
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 51).boxed().toList();

        assertThrows(BusinessException.class,
                () -> service.create("proc-1", "PAYMENT_APPLY", 10L, ids, 9L));
        verifyNoInteractions(sysUserMapper, ccMapper);
    }

    @Test
    @DisplayName("create - 空列表直接返回（不查库不落库）")
    void create_emptyListIsNoop() {
        service.create("proc-1", "PAYMENT_APPLY", 10L, List.of(), 9L);

        verifyNoInteractions(sysUserMapper, ccMapper);
    }

    @Test
    @DisplayName("create - 流程实例为空拒绝")
    void create_requiresProcessInstance() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(null, "PAYMENT_APPLY", 10L, List.of(1L), 9L));
        assertEquals("抄送参数不完整：processInstanceId/businessType/businessId 必填", error.getMessage());
    }
}
