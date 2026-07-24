package com.zwinsight.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.SysBudgetControlConfig;
import com.zwinsight.budget.dto.BudgetCheckResult;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.mapper.BudgetOccupiedMapper;
import com.zwinsight.budget.mapper.SysBudgetControlConfigMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BudgetControlConfigService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class BudgetControlConfigServiceTest {

    @Mock
    private SysBudgetControlConfigMapper configMapper;

    @Mock
    private BizBudgetDetailMapper budgetDetailMapper;

    @Mock
    private BudgetOccupiedMapper budgetOccupiedMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @InjectMocks
    private BudgetControlConfigService budgetControlConfigService;

    private static final Long PROJECT_ID = 1001L;
    private static final String COST_CATEGORY = "MATERIAL";

    // ======================== checkBudget 测试 ========================

    @Test
    @DisplayName("testCheckBudget_passWhenExemptMode — EXEMPT 模式直接放行")
    void testCheckBudget_passWhenExemptMode() {
        // 准备：项目配置为 EXEMPT 模式
        SysBudgetControlConfig exemptConfig = buildConfig(PROJECT_ID, "EXEMPT", 80, 0);
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(exemptConfig);

        // 执行
        BudgetCheckResult result = budgetControlConfigService.checkBudget(
                PROJECT_ID, COST_CATEGORY, new BigDecimal("999999"));

        // 验证：EXEMPT 模式下无论金额多大都直接放行
        assertEquals(BudgetCheckResult.Status.PASS, result.getStatus());
        assertNull(result.getMessage());

        // 验证不应查询预算数据
        verifyNoInteractions(budgetDetailMapper);
        verifyNoInteractions(budgetOccupiedMapper);
    }

    @Test
    @DisplayName("testCheckBudget_blockWhenOver100Percent — BLOCK 模式超100%返回BLOCK")
    void testCheckBudget_blockWhenOver100Percent() {
        // 准备：项目配置为 BLOCK 模式，预警阈值80%
        SysBudgetControlConfig blockConfig = buildConfig(PROJECT_ID, "BLOCK", 80, 0);
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(blockConfig);

        // 预算额度 100000
        when(budgetDetailMapper.sumBudgetByProjectAndCategory(eq(PROJECT_ID), eq(COST_CATEGORY)))
                .thenReturn(new BigDecimal("100000"));

        // 已签合同金额 80000
        when(budgetOccupiedMapper.sumContractAmountForMaterial(eq(PROJECT_ID)))
                .thenReturn(new BigDecimal("80000"));

        // 已审批付款 10000
        when(budgetOccupiedMapper.sumApprovedPaymentByCategory(eq(PROJECT_ID), eq(COST_CATEGORY)))
                .thenReturn(new BigDecimal("10000"));

        // 本次新增 20000 → 总发生额 = 80000 + 10000 + 20000 = 110000 → 执行率 110% > 100%
        BudgetCheckResult result = budgetControlConfigService.checkBudget(
                PROJECT_ID, COST_CATEGORY, new BigDecimal("20000"));

        // 验证：超100%应返回 BLOCK
        assertEquals(BudgetCheckResult.Status.BLOCK, result.getStatus());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("超预算"));
    }

    @Test
    @DisplayName("testCheckBudget_warnWhenOver100PercentWarnOnly — WARN_ONLY 模式超100%返回WARN")
    void testCheckBudget_warnWhenOver100PercentWarnOnly() {
        // 准备：项目配置为 WARN_ONLY 模式
        SysBudgetControlConfig warnConfig = buildConfig(PROJECT_ID, "WARN_ONLY", 80, 0);
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(warnConfig);

        // 预算额度 50000
        when(budgetDetailMapper.sumBudgetByProjectAndCategory(eq(PROJECT_ID), eq(COST_CATEGORY)))
                .thenReturn(new BigDecimal("50000"));

        // 已签合同金额 40000
        when(budgetOccupiedMapper.sumContractAmountForMaterial(eq(PROJECT_ID)))
                .thenReturn(new BigDecimal("40000"));

        // 已审批付款 5000
        when(budgetOccupiedMapper.sumApprovedPaymentByCategory(eq(PROJECT_ID), eq(COST_CATEGORY)))
                .thenReturn(new BigDecimal("5000"));

        // 本次新增 10000 → 总发生额 = 40000 + 5000 + 10000 = 55000 → 执行率 110% > 100%
        BudgetCheckResult result = budgetControlConfigService.checkBudget(
                PROJECT_ID, COST_CATEGORY, new BigDecimal("10000"));

        // 验证：WARN_ONLY 模式超100%应返回 WARN 而非 BLOCK
        assertEquals(BudgetCheckResult.Status.WARN, result.getStatus());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("超预算"));
    }

    // ======================== getEffectiveConfig 测试 ========================

    @Test
    @DisplayName("testGetEffectiveConfig_returnsProjectConfig — 项目级配置优先返回")
    void testGetEffectiveConfig_returnsProjectConfig() {
        // 准备：项目级配置存在
        SysBudgetControlConfig projectConfig = buildConfig(PROJECT_ID, "WARN_ONLY", 90, 0);
        projectConfig.setId(100L);

        // selectOne 第一次调用（项目级查询）返回项目配置
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(projectConfig);

        // 执行
        SysBudgetControlConfig result = budgetControlConfigService.getEffectiveConfig(PROJECT_ID);

        // 验证：返回项目级配置
        assertNotNull(result);
        assertEquals("WARN_ONLY", result.getControlMode());
        assertEquals(90, result.getWarningThreshold());
        assertEquals(PROJECT_ID, result.getProjectId());
    }

    @Test
    @DisplayName("testGetEffectiveConfig_fallbackToDefault — 无项目配置回落默认配置")
    void testGetEffectiveConfig_fallbackToDefault() {
        // 准备：系统默认配置
        SysBudgetControlConfig defaultConfig = buildConfig(null, "BLOCK", 80, 1);
        defaultConfig.setId(1L);

        // selectOne 第一次调用（项目级查询）返回null，第二次调用（默认配置查询）返回默认配置
        when(configMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null)       // 项目级：未找到
                .thenReturn(defaultConfig);  // 默认配置：找到

        // 执行
        SysBudgetControlConfig result = budgetControlConfigService.getEffectiveConfig(PROJECT_ID);

        // 验证：返回默认配置
        assertNotNull(result);
        assertEquals("BLOCK", result.getControlMode());
        assertEquals(80, result.getWarningThreshold());
        assertEquals(Integer.valueOf(1), result.getIsDefault());

        // 验证 selectOne 被调用了两次
        verify(configMapper, times(2)).selectOne(any(LambdaQueryWrapper.class));
    }

    // ======================== delete 测试 ========================

    @Test
    @DisplayName("testDelete_rejectsDefaultConfig — 不允许删除系统默认配置")
    void testDelete_rejectsDefaultConfig() {
        // 准备：默认配置 isDefault=1
        SysBudgetControlConfig defaultConfig = buildConfig(null, "BLOCK", 80, 1);
        defaultConfig.setId(1L);
        when(configMapper.selectById(eq(1L))).thenReturn(defaultConfig);

        // 执行 & 验证：抛出 BusinessException
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            budgetControlConfigService.delete(1L);
        });

        assertEquals("系统默认配置不允许删除", exception.getMessage());

        // 验证不应执行删除
        verify(configMapper, never()).deleteById(any());
    }

    // ======================== 辅助方法 ========================

    /**
     * 构建测试用预算控制配置
     */
    private SysBudgetControlConfig buildConfig(Long projectId, String controlMode,
                                               int warningThreshold, int isDefault) {
        SysBudgetControlConfig config = new SysBudgetControlConfig();
        config.setProjectId(projectId);
        config.setControlMode(controlMode);
        config.setWarningThreshold(warningThreshold);
        config.setIsDefault(isDefault);
        return config;
    }
}
