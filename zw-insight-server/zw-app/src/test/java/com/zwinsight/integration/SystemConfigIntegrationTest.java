package com.zwinsight.integration;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.system.service.SystemConfigService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 系统配置集成测试
 * <p>
 * 验证：系统配置更新 → Redis 缓存清除 → 读取最新值
 * </p>
 *
 * 对应需求：R5 (AC 7-8)
 */
@DisplayName("系统配置集成测试")
class SystemConfigIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long TENANT_ID = 1000L;
    private static final Long USER_ID = 2001L;
    private static final String CONFIG_KEY = "security.password.min_length";
    private static final String REDIS_CACHE_KEY = "sys:config:" + CONFIG_KEY;

    @BeforeEach
    void setupTestData() {
        // 模拟用户上下文
        SecurityContextHolder.setUserId(USER_ID);
        SecurityContextHolder.setTenantId(TENANT_ID);

        // 清除残留数据
        jdbcTemplate.update("DELETE FROM sys_config");
        jdbcTemplate.update("DELETE FROM sys_config_change_log");

        // 清除 Redis
        redisTemplate.delete(REDIS_CACHE_KEY);

        // 插入测试配置数据
        jdbcTemplate.update(
                "INSERT INTO sys_config (id, tenant_id, config_key, config_value, config_name, config_group, " +
                        "value_type, default_value, value_range) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                1L, TENANT_ID, CONFIG_KEY, "8", "密码最小长度", "security",
                "NUMBER", "8", "6-20");
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clear();
        redisTemplate.delete(REDIS_CACHE_KEY);
    }

    @Test
    @DisplayName("读取配置值 - 首次从DB加载并缓存到Redis")
    void testGetConfigValue_firstReadFromDbThenCached() {
        // 确保 Redis 中无缓存
        assertThat(redisTemplate.opsForValue().get(REDIS_CACHE_KEY)).isNull();

        // 读取配置值
        String value = systemConfigService.getConfigValue(CONFIG_KEY);
        assertThat(value).isEqualTo("8");

        // 验证 Redis 中已缓存
        String cachedValue = redisTemplate.opsForValue().get(REDIS_CACHE_KEY);
        assertThat(cachedValue).isEqualTo("8");
    }

    @Test
    @DisplayName("更新配置 → Redis缓存清除 → 读取最新值")
    void testUpdateConfig_clearsCacheAndReadsNewValue() {
        // 先读取一次，让值被缓存
        systemConfigService.getConfigValue(CONFIG_KEY);
        assertThat(redisTemplate.opsForValue().get(REDIS_CACHE_KEY)).isEqualTo("8");

        // 更新配置值
        systemConfigService.updateConfig(CONFIG_KEY, "10");

        // 验证 Redis 缓存已被清除
        String cachedAfterUpdate = redisTemplate.opsForValue().get(REDIS_CACHE_KEY);
        assertThat(cachedAfterUpdate).isNull();

        // 重新读取，应返回最新值
        String newValue = systemConfigService.getConfigValue(CONFIG_KEY);
        assertThat(newValue).isEqualTo("10");
    }

    @Test
    @DisplayName("更新配置 - 记录变更日志")
    void testUpdateConfig_writesChangeLog() {
        systemConfigService.updateConfig(CONFIG_KEY, "12");

        // 验证变更日志
        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_config_change_log WHERE config_key = ?",
                Integer.class, CONFIG_KEY);
        assertThat(logCount).isGreaterThanOrEqualTo(1);

        // 验证变更内容
        String oldValue = jdbcTemplate.queryForObject(
                "SELECT old_value FROM sys_config_change_log WHERE config_key = ? ORDER BY created_at DESC LIMIT 1",
                String.class, CONFIG_KEY);
        assertThat(oldValue).isEqualTo("8");
    }

    @Test
    @DisplayName("恢复默认值 → 配置值恢复为默认 → 缓存清除")
    void testResetToDefault_restoresDefaultAndClearsCache() {
        // 先修改为非默认值
        systemConfigService.updateConfig(CONFIG_KEY, "15");
        systemConfigService.getConfigValue(CONFIG_KEY); // 触发缓存

        // 恢复默认值
        systemConfigService.resetToDefault(CONFIG_KEY);

        // 验证缓存被清除
        assertThat(redisTemplate.opsForValue().get(REDIS_CACHE_KEY)).isNull();

        // 读取应为默认值 "8"
        String value = systemConfigService.getConfigValue(CONFIG_KEY);
        assertThat(value).isEqualTo("8");
    }
}
