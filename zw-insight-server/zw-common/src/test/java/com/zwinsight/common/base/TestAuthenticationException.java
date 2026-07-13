package com.zwinsight.common.base;

/**
 * 测试认证异常。
 * <p>
 * 当集成测试基类 {@link IntegrationTestBase} 获取 JWT token 失败时抛出，
 * 包含失败原因的详细描述，便于快速定位认证问题（如验证码获取失败、登录接口异常等）。
 * </p>
 */
public class TestAuthenticationException extends RuntimeException {

    public TestAuthenticationException(String message) {
        super(message);
    }

    public TestAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
