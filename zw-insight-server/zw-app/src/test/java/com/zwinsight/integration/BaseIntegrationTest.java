package com.zwinsight.integration;

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
 * <b>注意：全部集成测试应当在执行 {@code mvn test} 时通过。</b>
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
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
}
