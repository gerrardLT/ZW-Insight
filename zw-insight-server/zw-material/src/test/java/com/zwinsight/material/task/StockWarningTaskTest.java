package com.zwinsight.material.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.domain.BizStockWarningConfig;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import com.zwinsight.material.mapper.BizStockWarningConfigMapper;
import com.zwinsight.message.service.MessageService;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.domain.BizProjectMember;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.mapper.BizProjectMemberMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * StockWarningTask 单元测试
 * <p>库存预警：零库存/低库存分级、项目级阈值、Redis 去重、无配置默认阈值 10；
 * 站内信发送（材料员优先/项目经理兜底）、发送失败记录不静默降级（P0 Req4.7）。</p>
 */
@ExtendWith(MockitoExtension.class)
class StockWarningTaskTest {

    @Mock
    private BizProjectMaterialStockMapper stockMapper;

    @Mock
    private BizStockWarningConfigMapper configMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @Mock
    private BizProjectMemberMapper projectMemberMapper;

    @Mock
    private MessageService messageService;

    @Mock
    private RedisUtils redisUtils;

    @InjectMocks
    private StockWarningTask task;

    @org.mockito.Mock
    private com.zwinsight.security.service.TenantTaskRunner tenantTaskRunner;

    @org.junit.jupiter.api.BeforeEach
    void stubTenantRunner() {
        // 逐租户执行器透传：直接执行单租户逻辑
        org.mockito.Mockito.lenient().doAnswer(inv -> {
            ((java.util.function.LongConsumer) inv.getArgument(1)).accept(9999L);
            return null;
        }).when(tenantTaskRunner).runForActiveTenants(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    private BizProjectMaterialStock stock(Long projectId, Long materialId, String qty) {
        BizProjectMaterialStock s = new BizProjectMaterialStock();
        s.setProjectId(projectId);
        s.setMaterialId(materialId);
        s.setMaterialName("螺纹钢");
        s.setUnit("吨");
        s.setStockQuantity(qty == null ? null : new BigDecimal(qty));
        return s;
    }

    private BizStockWarningConfig config(Long projectId, Long materialId, String safety) {
        BizStockWarningConfig c = new BizStockWarningConfig();
        c.setProjectId(projectId);
        c.setMaterialId(materialId);
        c.setSafetyStock(new BigDecimal(safety));
        return c;
    }

    private BizProjectMember member(Long userId) {
        BizProjectMember m = new BizProjectMember();
        m.setUserId(userId);
        return m;
    }

    /** 默认收件人桩：首次查材料员返回指定成员（非空则不再查项目经理） */
    private void stubReceivers(Long... userIds) {
        when(projectMemberMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.stream(userIds).map(this::member).toList());
    }

    @Test
    @DisplayName("零库存 - ZERO_STOCK 级别发送预警并标记去重键")
    void zeroStock_sendsZeroLevelWarning() {
        when(configMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(config(1L, 10L, "5")));
        when(stockMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(stock(1L, 10L, "0")));
        when(redisUtils.hasKey("stock:warning:1:10:ZERO_STOCK")).thenReturn(false);
        BizProject project = new BizProject();
        project.setProjectName("滨江花园");
        when(projectMapper.selectById(1L)).thenReturn(project);
        stubReceivers(5L);

        task.execute();

        verify(messageService).sendMessage(eq(5L), anyString(), anyString(),
                eq("WARNING"), eq("STOCK_WARNING"), any());
        verify(redisUtils).set(eq("stock:warning:1:10:ZERO_STOCK"), eq("1"),
                eq(7L * 24 * 60 * 60), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("低库存（≤安全库存）- LOW_STOCK 级别发送")
    void lowStock_sendsLowLevelWarning() {
        when(configMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(config(1L, 10L, "20")));
        when(stockMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(stock(1L, 10L, "15")));
        when(redisUtils.hasKey("stock:warning:1:10:LOW_STOCK")).thenReturn(false);
        when(projectMapper.selectById(1L)).thenReturn(null); // 项目不存在 → 未知项目
        stubReceivers(5L);

        task.execute();

        verify(messageService).sendMessage(eq(5L), anyString(), anyString(),
                eq("WARNING"), eq("STOCK_WARNING"), any());
        verify(redisUtils).set(eq("stock:warning:1:10:LOW_STOCK"), eq("1"), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("库存正常 - 不发送")
    void normalStock_noWarning() {
        when(configMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(config(1L, 10L, "5")));
        when(stockMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(stock(1L, 10L, "100")));

        task.execute();

        verify(redisUtils, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("Redis 去重 - 7 天内已通知过则跳过")
    void alreadyNotified_skips() {
        when(configMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(config(1L, 10L, "5")));
        when(stockMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(stock(1L, 10L, "0")));
        when(redisUtils.hasKey("stock:warning:1:10:ZERO_STOCK")).thenReturn(true);

        task.execute();

        verify(redisUtils, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("无专属配置 - 使用全局默认阈值 10；null 库存按 0 计")
    void noConfig_usesDefaultThreshold() {
        when(configMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(stockMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(
                        stock(1L, 10L, "8"),   // 8 <= 默认10 → LOW
                        stock(1L, 11L, null))); // null → 0 → ZERO
        when(redisUtils.hasKey(anyString())).thenReturn(false);
        when(projectMapper.selectById(anyLong())).thenReturn(null);
        stubReceivers(5L);

        task.execute();

        verify(redisUtils).set(eq("stock:warning:1:10:LOW_STOCK"), eq("1"), anyLong(), eq(TimeUnit.SECONDS));
        verify(redisUtils).set(eq("stock:warning:1:11:ZERO_STOCK"), eq("1"), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("收件人解析 - 材料员优先，不再查项目经理")
    void receivers_materialOfficerFirst() {
        BizProjectMaterialStock s = stock(1L, 10L, "0");
        s.setId(77L);
        when(configMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(config(1L, 10L, "5")));
        when(stockMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(s));
        when(redisUtils.hasKey(anyString())).thenReturn(false);
        when(projectMapper.selectById(1L)).thenReturn(null);
        stubReceivers(7L);

        task.doExecute();

        // 材料员非空 → 只查一次成员表，只给材料员发信
        verify(projectMemberMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
        verify(messageService).sendMessage(eq(7L), anyString(), anyString(),
                eq("WARNING"), eq("STOCK_WARNING"), eq(77L));
    }

    @Test
    @DisplayName("收件人解析 - 无材料员时兜底项目经理")
    void receivers_fallbackToProjectManager() {
        when(configMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(config(1L, 10L, "5")));
        when(stockMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(stock(1L, 10L, "0")));
        when(redisUtils.hasKey(anyString())).thenReturn(false);
        when(projectMapper.selectById(1L)).thenReturn(null);
        // 首次查材料员空 → 二次查项目经理返回 9L
        when(projectMemberMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.singletonList(member(9L)));

        task.doExecute();

        verify(messageService).sendMessage(eq(9L), anyString(), anyString(),
                eq("WARNING"), eq("STOCK_WARNING"), any());
        verify(redisUtils).set(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("无收件人 - sendWarning 抛异常且不标记去重键（可重试）")
    void noReceiver_throwsAndNoDedupKey() {
        BizProjectMaterialStock s = stock(1L, 10L, "0");
        when(projectMapper.selectById(1L)).thenReturn(null);
        when(projectMemberMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> task.sendWarning(s, StockWarningTask.LEVEL_ZERO, new BigDecimal("5")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置材料员或项目经理");
        verify(messageService, never()).sendMessage(anyLong(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("消息服务不可用 - 抛异常向上冒泡，调用方记录失败不静默降级")
    void messageServiceFailure_propagates() {
        when(projectMemberMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(member(5L)));
        when(projectMapper.selectById(1L)).thenReturn(null);
        doThrow(new RuntimeException("消息服务不可用")).when(messageService)
                .sendMessage(anyLong(), anyString(), anyString(), anyString(), anyString(), any());

        assertThatThrownBy(() -> task.sendWarning(stock(1L, 10L, "0"),
                StockWarningTask.LEVEL_LOW, new BigDecimal("5")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("消息服务不可用");
    }

    @Test
    @DisplayName("发送失败 - 整体扫描不中断且不标记去重键，其余记录继续处理")
    void sendFailure_continuesScanAndSkipsDedup() {
        when(configMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        // 材料10 发送失败，材料11 正常发送
        when(stockMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(stock(1L, 10L, "0"), stock(1L, 11L, "0")));
        when(redisUtils.hasKey(anyString())).thenReturn(false);
        when(projectMapper.selectById(anyLong())).thenReturn(null);
        when(projectMemberMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(member(5L)));
        doThrow(new RuntimeException("消息服务不可用")).when(messageService)
                .sendMessage(anyLong(), anyString(), anyString(), anyString(), anyString(), any());

        task.doExecute();

        // 两条都发送失败 → 都不标记去重键（下次扫描重试）
        verify(redisUtils, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
        verify(messageService, times(2)).sendMessage(anyLong(), anyString(), anyString(),
                anyString(), anyString(), any());
    }
}
