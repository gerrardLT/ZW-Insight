package com.zwinsight.material.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.domain.BizStockWarningConfig;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import com.zwinsight.material.mapper.BizStockWarningConfigMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * StockWarningTask 单元测试
 * <p>库存预警：零库存/低库存分级、项目级阈值、Redis 去重、无配置默认阈值 10。</p>
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
    private RedisUtils redisUtils;

    @InjectMocks
    private StockWarningTask task;

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

        task.execute();

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

        task.execute();

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

        task.execute();

        verify(redisUtils).set(eq("stock:warning:1:10:LOW_STOCK"), eq("1"), anyLong(), eq(TimeUnit.SECONDS));
        verify(redisUtils).set(eq("stock:warning:1:11:ZERO_STOCK"), eq("1"), anyLong(), eq(TimeUnit.SECONDS));
    }
}
