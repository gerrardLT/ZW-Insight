package com.zwinsight.integration;

import com.zwinsight.app.ZwInsightApplication;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.integration.support.EnabledIfDockerAvailable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成测试基类
 * <p>
 * 使用 Testcontainers 启动 MySQL 8.0 和 Redis 7 容器，
 * 所有集成测试类继承此基类即可获得真实数据库和缓存支持。
 * </p>
 * <p>
 * 运行方式：开发者在本地执行 {@code mvn test -Dtest="com.zwinsight.integration.*"} 即可。
 * 需要 Docker 环境支持。
 * </p>
 *
 * <b>注意：全部集成测试应当在执行 {@code mvn test} 时通过；在缺少 Docker 的环境下将被自动跳过。</b>
 * <p>
 * 租户上下文：无登录态时 MyBatis-Plus 租户插件注入 tenant_id=0 条件，
 * 故每个测试前将上下文租户设为 9999（自动化测试租户），测试数据 INSERT 时
 * 必须显式带 tenant_id=9999 才能被 Mapper 查询命中（2026-08-05 CI 首跑暴露）。
 * </p>
 */
@SpringBootTest(classes = ZwInsightApplication.class)
@Testcontainers
@ActiveProfiles("test")
@EnabledIfDockerAvailable
public abstract class BaseIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("zw_test")
            .withUsername("root")
            .withPassword("test")
            .withInitScript("db/init-test-schema.sql");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    /**
     * 每个测试方法前设置租户上下文为 9999（自动化测试租户），
     * 使租户插件注入 tenant_id=9999 条件，与测试数据匹配。
     */
    @BeforeEach
    void setUpTenantContext() {
        SecurityContextHolder.setTenantId(TEST_TENANT_ID);
        SecurityContextHolder.setUserId(TEST_USER_ID);
    }

    @AfterEach
    void clearTenantContext() {
        SecurityContextHolder.clear();
    }

    /** 自动化测试租户 ID（与 AGENTS.md 测试规则一致） */
    protected static final Long TEST_TENANT_ID = 9999L;

    /** 测试用户 ID */
    protected static final Long TEST_USER_ID = 999901L;
}
