package h2test;

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
 * <p>
 * ⚠ 位置警告：本基类刻意放在 com.zwinsight 包之外，禁止移回。
 * 其嵌套 @SpringBootApplication 配置类若位于 com.zwinsight.* 下，会被全量上下文的
 * 组件扫描（scanBasePackages=com.zwinsight）扫入，导致其 @MapperScan 以短名重复注册
 * 全部 Mapper，引发 NoUniqueBeanDefinitionException（2026-08-05 CI 首跑暴露，
 * 原位置 com.zwinsight.test.BaseH2MapperTest 已迁移至此）。
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
