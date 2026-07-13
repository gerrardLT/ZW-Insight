package com.zwinsight.common.base;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * JUnit 5 ExecutionCondition 实现：检测服务器 MySQL 和 Redis 是否可达。
 * <p>
 * 通过 Socket 连接测试远程服务器的 MySQL（3306）和 Redis（6379）端口。
 * 连接超时设为 3 秒。若任一服务不可达，返回 disabled 结果，
 * 测试将被跳过并在 CI 输出中显示明确的跳过原因。
 * </p>
 * <p>
 * 通过 {@link RequiresServer} 注解使用，无需手动注册此扩展。
 * </p>
 *
 * @see RequiresServer
 */
public class ServerAvailabilityCondition implements ExecutionCondition {

    private static final Logger log = LoggerFactory.getLogger(ServerAvailabilityCondition.class);

    /** 服务器地址 */
    private static final String SERVER_HOST = TestConstants.SERVER_HOST;

    /** MySQL 端口 */
    private static final int MYSQL_PORT = 3306;

    /** Redis 端口 */
    private static final int REDIS_PORT = 6379;

    /** Socket 连接超时时间（毫秒） */
    private static final int CONNECT_TIMEOUT_MS = 3000;

    /**
     * 缓存检测结果，避免同一测试运行中重复探测。
     * 使用 volatile 确保多线程可见性。
     */
    private static volatile ConditionEvaluationResult cachedResult;

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (cachedResult != null) {
            return cachedResult;
        }

        synchronized (ServerAvailabilityCondition.class) {
            if (cachedResult != null) {
                return cachedResult;
            }
            cachedResult = doCheck();
            return cachedResult;
        }
    }

    /**
     * 执行实际的服务器可达性检查。
     *
     * @return 检查结果
     */
    private ConditionEvaluationResult doCheck() {
        // 检查 MySQL
        if (!isPortReachable(SERVER_HOST, MYSQL_PORT)) {
            String reason = String.format(
                    "Server unreachable: MySQL at %s:%d is not reachable (timeout %dms)",
                    SERVER_HOST, MYSQL_PORT, CONNECT_TIMEOUT_MS);
            log.warn("[ServerAvailabilityCondition] {}", reason);
            return ConditionEvaluationResult.disabled(reason);
        }

        // 检查 Redis
        if (!isPortReachable(SERVER_HOST, REDIS_PORT)) {
            String reason = String.format(
                    "Server unreachable: Redis at %s:%d is not reachable (timeout %dms)",
                    SERVER_HOST, REDIS_PORT, CONNECT_TIMEOUT_MS);
            log.warn("[ServerAvailabilityCondition] {}", reason);
            return ConditionEvaluationResult.disabled(reason);
        }

        log.info("[ServerAvailabilityCondition] Server available: MySQL({}:{}) and Redis({}:{}) are reachable",
                SERVER_HOST, MYSQL_PORT, SERVER_HOST, REDIS_PORT);
        return ConditionEvaluationResult.enabled("Server available");
    }

    /**
     * 使用 Socket 测试指定主机和端口是否可达。
     *
     * @param host 目标主机
     * @param port 目标端口
     * @return true 如果连接成功，false 如果超时或连接被拒绝
     */
    private boolean isPortReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            log.debug("[ServerAvailabilityCondition] Connection to {}:{} failed: {}",
                    host, port, e.getMessage());
            return false;
        }
    }
}
