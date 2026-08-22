package com.zwinsight.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.material.domain.BizStockWarningConfig;
import com.zwinsight.material.mapper.BizStockWarningConfigMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * StockWarningConfigService 单元测试
 * <p>P0 Req4.6：安全库存配置 upsert（projectId+materialId 维度）、参数校验、删除。</p>
 */
@ExtendWith(MockitoExtension.class)
class StockWarningConfigServiceTest {

    @Mock
    private BizStockWarningConfigMapper configMapper;

    @InjectMocks
    private StockWarningConfigService service;

    private BizStockWarningConfig config(Long projectId, Long materialId, String safety) {
        BizStockWarningConfig c = new BizStockWarningConfig();
        c.setProjectId(projectId);
        c.setMaterialId(materialId);
        c.setMaterialName("螺纹钢");
        c.setSafetyStock(new BigDecimal(safety));
        return c;
    }

    @Test
    @DisplayName("save - 新增（无同键配置）：insert 且默认启用")
    void save_new_inserts() {
        BizStockWarningConfig c = config(1L, 10L, "20");
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        service.save(c);

        verify(configMapper).insert(c);
        assertThat(c.getEnabled()).isEqualTo(1);
        verify(configMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("save - 同 projectId+materialId 已存在：更新而非重复插入")
    void save_existing_updates() {
        BizStockWarningConfig existing = config(1L, 10L, "20");
        existing.setId(5L);
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        BizStockWarningConfig input = config(1L, 10L, "35");
        input.setEnabled(0);
        service.save(input);

        ArgumentCaptor<BizStockWarningConfig> captor = ArgumentCaptor.forClass(BizStockWarningConfig.class);
        verify(configMapper).updateById(captor.capture());
        verify(configMapper, never()).insert(any());
        assertThat(captor.getValue().getId()).isEqualTo(5L);
        assertThat(captor.getValue().getSafetyStock()).isEqualByComparingTo("35");
        assertThat(captor.getValue().getEnabled()).isEqualTo(0);
        assertThat(input.getId()).isEqualTo(5L); // 回写已存在记录ID
    }

    @Test
    @DisplayName("save - 全局配置（projectId=null）：按 isNull 匹配同键")
    void save_globalConfig() {
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        service.save(config(null, 10L, "30"));

        verify(configMapper).insert(any(BizStockWarningConfig.class));
    }

    @Test
    @DisplayName("save - 缺少材料/负数安全库存：拒绝")
    void save_invalid_rejected() {
        BizStockWarningConfig noMaterial = config(1L, null, "10");
        assertThatThrownBy(() -> service.save(noMaterial))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择材料");

        BizStockWarningConfig negative = config(1L, 10L, "-1");
        assertThatThrownBy(() -> service.save(negative))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("安全库存必须为非负数");

        BizStockWarningConfig nullSafety = config(1L, 10L, "0");
        nullSafety.setSafetyStock(null);
        assertThatThrownBy(() -> service.save(nullSafety))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("安全库存必须为非负数");

        verify(configMapper, never()).insert(any());
        verify(configMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("delete - 存在则删除；不存在抛异常")
    void delete_success_andNotFound() {
        BizStockWarningConfig existing = config(1L, 10L, "20");
        existing.setId(5L);
        when(configMapper.selectById(5L)).thenReturn(existing);
        service.delete(5L);
        verify(configMapper).deleteById(5L);

        when(configMapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("预警配置不存在");
    }
}
