package com.zwinsight.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizBudgetConfig;
import com.zwinsight.budget.mapper.BizBudgetConfigMapper;
import com.zwinsight.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BudgetConfigService 单元测试
 * <p>预算管控配置：项目级优先、全局兜底的获取策略。</p>
 */
@ExtendWith(MockitoExtension.class)
class BudgetConfigServiceTest {

    @Mock
    private BizBudgetConfigMapper budgetConfigMapper;

    @InjectMocks
    private BudgetConfigService service;

    private BizBudgetConfig config(Long id, Long projectId) {
        BizBudgetConfig c = new BizBudgetConfig();
        c.setId(id);
        c.setProjectId(projectId);
        return c;
    }

    @Test
    @DisplayName("listAll - 查询全部配置")
    void listAll_delegates() {
        when(budgetConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(config(1L, null)));

        assertThat(service.listAll()).hasSize(1);
    }

    @Test
    @DisplayName("getConfig - 项目级配置优先返回")
    void getConfig_projectLevelFirst() {
        BizBudgetConfig projectConfig = config(1L, 10L);
        when(budgetConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(projectConfig);

        BizBudgetConfig result = service.getConfig(10L);

        assertThat(result.getProjectId()).isEqualTo(10L);
        // 命中项目级后不再查全局（selectOne 只调用一次）
        verify(budgetConfigMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("getConfig - 项目级缺失时回退全局配置")
    void getConfig_fallbackToGlobal() {
        when(budgetConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null)                       // 项目级无
                .thenReturn(config(2L, null));          // 全局有

        BizBudgetConfig result = service.getConfig(10L);

        assertThat(result.getId()).isEqualTo(2L);
        verify(budgetConfigMapper, times(2)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("getConfig - projectId 为 null 时直接查全局")
    void getConfig_nullProjectId_queriesGlobalDirectly() {
        when(budgetConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(config(2L, null));

        BizBudgetConfig result = service.getConfig(null);

        assertThat(result).isNotNull();
        verify(budgetConfigMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("save/update/delete - 委托 mapper，update 不存在抛异常")
    void crud_delegates() {
        BizBudgetConfig c = config(1L, null);
        service.save(c);
        verify(budgetConfigMapper).insert(c);

        when(budgetConfigMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.update(config(99L, null)))
                .hasMessageContaining("配置不存在");

        when(budgetConfigMapper.selectById(1L)).thenReturn(c);
        service.update(c);
        verify(budgetConfigMapper).updateById(c);

        service.delete(1L);
        verify(budgetConfigMapper).deleteById(1L);
    }
}
