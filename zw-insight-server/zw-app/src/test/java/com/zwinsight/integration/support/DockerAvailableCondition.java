package com.zwinsight.integration.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * JUnit 5 执行条件：仅当本机 Docker 可用时才启用被标注的测试类。
 * <p>
 * 集成测试依赖 Testcontainers 启动真实 MySQL/Redis 容器。在没有 Docker 的
 * 环境（如开发者本机未安装/未运行 Docker、或受限 CI）下，应当将这些测试
 * 标记为「跳过(SKIPPED)」而非「失败(FAILED)」，从而保证 {@code mvn test}
 * 在缺少基础设施时仍为绿色，同时在具备 Docker 的 CI 上完整执行真实集成测试。
 * <p>
 * 该条件在任何扩展(SpringExtension/Testcontainers)的 beforeAll 之前求值，
 * 因此 Docker 缺失时不会触发容器启动或 Spring 上下文加载。
 */
public class DockerAvailableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        try {
            boolean available = DockerClientFactory.instance().isDockerAvailable();
            if (available) {
                return ConditionEvaluationResult.enabled("Docker 可用，执行集成测试");
            }
            return ConditionEvaluationResult.disabled("Docker 不可用，跳过需要 Testcontainers 的集成测试");
        } catch (Throwable t) {
            // isDockerAvailable 在某些环境下可能抛出而非返回 false，统一按不可用处理并跳过。
            return ConditionEvaluationResult.disabled("Docker 不可用（检测异常：" + t.getClass().getSimpleName() + "），跳过集成测试");
        }
    }
}
