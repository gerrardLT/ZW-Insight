package com.zwinsight.common.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 测试数据物理清理组件。
 * <p>
 * 按 {@link TestConstants#TABLE_DELETE_ORDER} 拓扑逆序执行 DELETE，
 * 确保外键约束不冲突。仅允许清理 tenant_id={@value TestConstants#TEST_TENANT_ID} 的数据，
 * 防止误删生产数据。
 * <p>
 * 使用 {@code @Component} 注入，可在集成测试的 {@code @AfterAll} 中调用。
 */
@Component
public class TestDataCleaner {

    private static final Logger log = LoggerFactory.getLogger(TestDataCleaner.class);

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    public TestDataCleaner(JdbcTemplate jdbcTemplate, RedisTemplate<String, Object> redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 按拓扑逆序物理删除指定租户的所有测试数据。
     *
     * @param tenantId 必须为 {@link TestConstants#TEST_TENANT_ID}（9999），否则抛出异常
     * @return 所有表累计删除的行数
     * @throws IllegalArgumentException 如果 tenantId 不是测试租户 ID
     */
    public int cleanByTenantId(Long tenantId) {
        if (tenantId == null || !tenantId.equals(TestConstants.TEST_TENANT_ID)) {
            throw new IllegalArgumentException(
                    "拒绝执行：tenantId 必须为测试租户 ID（" + TestConstants.TEST_TENANT_ID
                            + "），实际值：" + tenantId + "。此安全检查防止误删生产数据。");
        }

        log.info("====== TestDataCleaner 开始清理 tenant_id={} 的测试数据 ======", tenantId);
        int totalDeleted = 0;

        for (String table : TestConstants.TABLE_DELETE_ORDER) {
            try {
                String sql = "DELETE FROM " + table + " WHERE tenant_id = ?";
                int rows = jdbcTemplate.update(sql, tenantId);
                if (rows > 0) {
                    log.info("  [清理] {} : 删除 {} 行", table, rows);
                } else {
                    log.debug("  [跳过] {} : 无匹配数据", table);
                }
                totalDeleted += rows;
            } catch (DataAccessException e) {
                // 容错：表可能不存在（模块未部署），记录日志后继续下一张表
                log.warn("  [容错] {} : 删除失败（表可能不存在），原因：{}", table, e.getMessage());
            }
        }

        log.info("====== TestDataCleaner 清理完成：共删除 {} 行 ======", totalDeleted);
        return totalDeleted;
    }

    /**
     * 清理 Redis 中匹配指定模式的所有键。
     * <p>
     * 默认模式为 {@code test:t9999:*}，清理测试过程中产生的缓存键。
     *
     * @param pattern Redis key 匹配模式，如 {@code test:t9999:*}
     * @return 已删除的键数量
     */
    public long cleanRedisKeys(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            pattern = TestConstants.REDIS_TEST_PREFIX + "*";
        }

        log.info("====== TestDataCleaner 清理 Redis 键，模式：{} ======", pattern);

        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            log.info("  [Redis] 无匹配键");
            return 0;
        }

        Long deletedCount = redisTemplate.delete(keys);
        long result = (deletedCount != null) ? deletedCount : 0;
        log.info("  [Redis] 已删除 {} 个键", result);
        return result;
    }

    /**
     * 执行完整清理：先清理数据库，再清理 Redis。
     *
     * @return 数据库删除行数 + Redis 删除键数
     */
    public long cleanAll() {
        int dbRows = cleanByTenantId(TestConstants.TEST_TENANT_ID);
        long redisKeys = cleanRedisKeys(TestConstants.REDIS_TEST_PREFIX + "*");
        log.info("====== 全量清理完成：数据库 {} 行 + Redis {} 键 ======", dbRows, redisKeys);
        return dbRows + redisKeys;
    }
}
