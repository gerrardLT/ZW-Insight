package com.zwinsight.common.base;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注需要远程服务器可用的测试类或方法。
 * <p>
 * 当测试类或方法标注此注解时，JUnit 5 将在执行前通过
 * {@link ServerAvailabilityCondition} 检查服务器 MySQL（3306）和 Redis（6379）
 * 是否可达。若任一服务不可达，测试将被自动跳过，CI 输出中显示
 * "Server unreachable: ..." 跳过原因。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @RequiresServer
 * @SpringBootTest
 * class ProjectIntegrationTest extends IntegrationTestBase {
 *     // 仅当服务器可达时执行
 * }
 * }</pre>
 *
 * @see ServerAvailabilityCondition
 * @see IntegrationTestBase
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(ServerAvailabilityCondition.class)
public @interface RequiresServer {
}
