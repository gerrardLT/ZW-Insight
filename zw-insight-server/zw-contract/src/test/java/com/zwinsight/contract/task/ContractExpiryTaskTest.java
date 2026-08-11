package com.zwinsight.contract.task;

import com.zwinsight.contract.dto.ContractExpiryDTO;
import com.zwinsight.contract.service.ContractExpiryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ContractExpiryTask 单元测试
 * <p>到期提醒任务：分布式锁控制、分级通知、Redis 去重、单条异常不影响整体。</p>
 */
@ExtendWith(MockitoExtension.class)
class ContractExpiryTaskTest {

    @Mock
    private ContractExpiryService expiryService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private com.zwinsight.security.service.TenantTaskRunner tenantTaskRunner;

    @InjectMocks
    private ContractExpiryTask task;

    @org.junit.jupiter.api.BeforeEach
    void stubTenantRunner() {
        // 逐租户执行器透传：直接执行单租户逻辑（lenient：部分用例未走到该分支）
        org.mockito.Mockito.lenient().doAnswer(inv -> {
            ((java.util.function.LongConsumer) inv.getArgument(1)).accept(9999L);
            return null;
        }).when(tenantTaskRunner).runForActiveTenants(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    private ContractExpiryDTO contract(Long id, LocalDate endDate) {
        ContractExpiryDTO dto = new ContractExpiryDTO();
        dto.setId(id);
        dto.setContractCode("C-" + id);
        dto.setEndDate(endDate);
        return dto;
    }

    // ── execute（锁控制）──────────────────────────

    @Test
    @DisplayName("execute - 未获取到锁直接跳过")
    void execute_lockNotAcquired_skips() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);

        task.execute();

        verify(expiryService, never()).queryExpiringContracts(any(), any());
    }

    @Test
    @DisplayName("execute - Redis 异常视为未获取锁（不抛出）")
    void execute_redisError_skipsGracefully() {
        when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis 宕机"));

        task.execute();

        verify(expiryService, never()).queryExpiringContracts(any(), any());
    }

    @Test
    @DisplayName("execute - 获取锁成功执行业务，最终释放锁")
    void execute_lockAcquired_runsAndReleases() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(expiryService.queryExpiringContracts(any(), any())).thenReturn(Collections.emptyList());

        task.execute();

        verify(expiryService).queryExpiringContracts(any(), any());
        verify(stringRedisTemplate).delete("task:contract-expiry:lock");
    }

    // ── doExecute ──────────────────────────────────

    @Test
    @DisplayName("doExecute - 无到期合同直接返回")
    void doExecute_noContracts_returns() {
        when(expiryService.queryExpiringContracts(any(), any())).thenReturn(Collections.emptyList());

        task.doExecute();

        verify(expiryService, never()).shouldSkip(any());
    }

    @Test
    @DisplayName("doExecute - 单条处理异常不影响其他合同")
    void doExecute_singleFailure_continues() {
        ContractExpiryDTO bad = contract(1L, LocalDate.now().plusDays(5));
        ContractExpiryDTO good = contract(2L, LocalDate.now().plusDays(5));
        when(expiryService.queryExpiringContracts(any(), any()))
                .thenReturn(Arrays.asList(bad, good));
        when(expiryService.shouldSkip(bad)).thenThrow(new RuntimeException("脏数据"));
        when(expiryService.shouldSkip(good)).thenReturn(false);
        when(expiryService.determineLevel(good.getEndDate(), LocalDate.now())).thenReturn("URGENT");
        when(expiryService.shouldSendNotification(2L, "URGENT")).thenReturn(true);

        task.doExecute();

        // 第二条正常发送通知
        verify(expiryService).sendExpiryNotification(eq(good), eq("URGENT"), any());
        verify(expiryService).markAsSent(2L, "URGENT");
    }

    // ── processContract ──────────────────────────────────

    @Test
    @DisplayName("processContract - 各跳过分支返回 false")
    void processContract_skipBranches_returnFalse() {
        LocalDate today = LocalDate.now();
        ContractExpiryDTO c = contract(1L, today.plusDays(10));

        // 分支1：状态跳过
        when(expiryService.shouldSkip(c)).thenReturn(true);
        assertThat(task.processContract(c, today)).isFalse();

        // 分支2：级别为 null
        when(expiryService.shouldSkip(c)).thenReturn(false);
        when(expiryService.determineLevel(c.getEndDate(), today)).thenReturn(null);
        assertThat(task.processContract(c, today)).isFalse();

        // 分支3：Redis 去重（已提醒过）
        when(expiryService.determineLevel(c.getEndDate(), today)).thenReturn("UPCOMING");
        when(expiryService.shouldSendNotification(1L, "UPCOMING")).thenReturn(false);
        assertThat(task.processContract(c, today)).isFalse();
    }

    @Test
    @DisplayName("processContract - 正常发送通知并标记，返回 true")
    void processContract_success_sendsAndMarks() {
        LocalDate today = LocalDate.now();
        ContractExpiryDTO c = contract(1L, today.plusDays(3));
        when(expiryService.shouldSkip(c)).thenReturn(false);
        when(expiryService.determineLevel(c.getEndDate(), today)).thenReturn("URGENT");
        when(expiryService.shouldSendNotification(1L, "URGENT")).thenReturn(true);

        boolean result = task.processContract(c, today);

        assertThat(result).isTrue();
        verify(expiryService).sendExpiryNotification(c, "URGENT", today);
        verify(expiryService).markAsSent(1L, "URGENT");
    }
}
