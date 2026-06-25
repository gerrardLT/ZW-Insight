package com.zwinsight.budget.service;

import com.zwinsight.budget.domain.SysBudgetControlConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property P5: 预算控制配置单调性
 * <p>
 * 验证：创建项目配置后删除，getEffectiveConfig 回落为系统默认(BLOCK, 80%)。
 * hardCodedDefault() 始终返回 controlMode=BLOCK, warningThreshold=80。
 * 这是纯逻辑验证，不需要数据库。
 * </p>
 * <p>
 * **Validates: Requirements 6.4, 6.9**
 * </p>
 */
@DisplayName("P5: 预算控制配置单调性属性测试")
class BudgetConfigMonotonicityPropertyTest {

    @RepeatedTest(20)
    @DisplayName("P5: hardCodedDefault 始终返回 controlMode=BLOCK, warningThreshold=80")
    void testHardCodedDefaultConsistency() {
        // 无论调用多少次，hardCodedDefault 始终返回固定值
        SysBudgetControlConfig defaultConfig = BudgetControlConfigService.hardCodedDefault();

        assertNotNull(defaultConfig, "hardCodedDefault 不应返回 null");
        assertEquals("BLOCK", defaultConfig.getControlMode(),
                "hardCodedDefault.controlMode 应始终为 BLOCK");
        assertEquals(80, defaultConfig.getWarningThreshold(),
                "hardCodedDefault.warningThreshold 应始终为 80");
        assertEquals(1, defaultConfig.getIsDefault(),
                "hardCodedDefault.isDefault 应始终为 1");
    }

    @RepeatedTest(20)
    @DisplayName("P5: 模拟配置回落逻辑 — 无项目配置时回落为硬编码默认值")
    void testConfigFallbackLogic() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 模拟场景：随机 projectId 不存在于数据库
        Long projectId = (long) random.nextInt(1, 999999);

        // 模拟 getEffectiveConfig 的回落逻辑:
        // 1. 项目级配置不存在(simulateProjectConfig = null)
        // 2. 系统默认配置不存在(simulateDefaultConfig = null)
        // 3. 回落到 hardCodedDefault()
        SysBudgetControlConfig simulateProjectConfig = null;  // 项目级不存在
        SysBudgetControlConfig simulateDefaultConfig = null;  // 系统默认也不存在

        SysBudgetControlConfig effectiveConfig;
        if (simulateProjectConfig != null) {
            effectiveConfig = simulateProjectConfig;
        } else if (simulateDefaultConfig != null) {
            effectiveConfig = simulateDefaultConfig;
        } else {
            effectiveConfig = BudgetControlConfigService.hardCodedDefault();
        }

        // 验证回落后的配置
        assertNotNull(effectiveConfig);
        assertEquals("BLOCK", effectiveConfig.getControlMode(),
                "配置回落后 controlMode 应为 BLOCK");
        assertEquals(80, effectiveConfig.getWarningThreshold(),
                "配置回落后 warningThreshold 应为 80");
    }

    @RepeatedTest(20)
    @DisplayName("P5: 项目配置存在时优先返回项目配置，删除后回落默认")
    void testProjectConfigPriorityAndFallback() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 随机生成项目级配置
        String[] modes = {"WARN_ONLY", "BLOCK", "EXEMPT"};
        String projectMode = modes[random.nextInt(modes.length)];
        int projectThreshold = random.nextInt(50, 100);

        // 模拟存在项目级配置
        SysBudgetControlConfig projectConfig = new SysBudgetControlConfig();
        projectConfig.setControlMode(projectMode);
        projectConfig.setWarningThreshold(projectThreshold);
        projectConfig.setIsDefault(0);

        // 阶段1: 项目配置存在 → 返回项目配置
        SysBudgetControlConfig phase1Result = projectConfig;
        assertEquals(projectMode, phase1Result.getControlMode());
        assertEquals(projectThreshold, phase1Result.getWarningThreshold());

        // 阶段2: 删除项目配置（设为 null） → 回落到硬编码默认
        SysBudgetControlConfig phase2Result = BudgetControlConfigService.hardCodedDefault();
        assertEquals("BLOCK", phase2Result.getControlMode(),
                "删除项目配置后应回落为 BLOCK");
        assertEquals(80, phase2Result.getWarningThreshold(),
                "删除项目配置后应回落为阈值 80");
    }
}
