package com.zwinsight.test;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * H2 Mapper 层轻量测试基类
 * <p>
 * 使用 H2 内存数据库（MySQL 兼容模式），仅加载 MyBatis-Plus 相关配置，
 * 适用于不包含 MySQL 专有函数（如 JSON_CONTAINS、DATE_FORMAT、FIELD）的 Mapper 测试。
 * </p>
 * <p>
 * 对于使用 MySQL 专有语法的 Mapper 测试，请继承
 * {@link com.zwinsight.integration.BaseIntegrationTest} 使用 Testcontainers。
 * </p>
 *
 * @see com.zwinsight.integration.BaseIntegrationTest
 */
@SpringBootTest(
        classes = BaseH2MapperTest.H2TestConfig.class,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration,"
                        + "org.flowable.spring.boot.FlowableSecurityAutoConfiguration"
        }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("h2-test")
@Transactional
public abstract class BaseH2MapperTest {

    /**
     * 最小化 Spring 配置，仅加载 MyBatis-Plus 和数据源
     */
    @SpringBootApplication(exclude = {
            RabbitAutoConfiguration.class
    })
    @MapperScan("com.zwinsight.**.mapper")
    public static class H2TestConfig {
    }
}
