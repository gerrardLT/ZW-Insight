package com.zwinsight.dashboard.task;

import com.zwinsight.dashboard.service.CockpitService;
import com.zwinsight.security.service.TenantTaskRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.LongConsumer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * CockpitWarmupListener 单元测试（启动预热，overview 首调性能修复）。
 * <p>核心断言：预热经逐租户入口执行真实查询链；预热异常不向启动流程传播
 * （尽力而为语义），也不阻断其他租户。</p>
 */
@ExtendWith(MockitoExtension.class)
class CockpitWarmupListenerTest {

    @Mock private CockpitService cockpitService;
    @Mock private TenantTaskRunner tenantTaskRunner;

    @InjectMocks
    private CockpitWarmupListener listener;

    @Test
    @DisplayName("正常路径：逐租户预热 overview + project-health 真实查询链")
    void warmup_invokesRealQueryChainPerTenant() {
        // 模拟 Runner 对租户 1 执行回调
        org.mockito.Mockito.doAnswer(inv -> {
            LongConsumer action = inv.getArgument(1);
            action.accept(1L);
            return null;
        }).when(tenantTaskRunner).runForActiveTenants(anyString(), any(LongConsumer.class));

        listener.warmupOnStartup();

        verify(cockpitService).getOverview(null, null);
        verify(cockpitService).getProjectHealth(null, null, null);
    }

    @Test
    @DisplayName("预热异常不向启动流程传播（应用已就绪，尽力而为不阻断）")
    void warmup_exceptionDoesNotPropagate() {
        doThrow(new RuntimeException("db down"))
                .when(tenantTaskRunner).runForActiveTenants(anyString(), any(LongConsumer.class));

        assertThatCode(() -> listener.warmupOnStartup()).doesNotThrowAnyException();
        verify(cockpitService, never()).getOverview(null, null);
    }
}
