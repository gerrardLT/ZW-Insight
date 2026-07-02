package com.zwinsight.test;

import com.zwinsight.security.interceptor.AuthInterceptor;
import com.zwinsight.security.mapper.SysTenantMapper;
import com.zwinsight.security.service.AuthService;
import com.zwinsight.security.util.JwtUtils;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 测试安全配置 - 禁用 AuthInterceptor，让 @WebMvcTest 不需要认证就能测试 Controller。
 * <p>
 * 原理：
 * <ol>
 *   <li>Mock AuthInterceptor 的三个依赖（JwtUtils / AuthService / SysTenantMapper），
 *       使其可以被 Spring 实例化；</li>
 *   <li>提供一个 @Primary 的 WebMvcConfigurer，覆盖 WebMvcConfig 的拦截器注册行为。
 *       不添加任何拦截器，从而绕过认证。</li>
 * </ol>
 * 使用方式：在 @WebMvcTest 测试类上添加 {@code @Import(TestSecurityConfig.class)}
 * </p>
 */
@TestConfiguration
public class TestSecurityConfig {

    /** Mock AuthInterceptor 依赖 - JwtUtils */
    @MockBean
    private JwtUtils jwtUtils;

    /** Mock AuthInterceptor 依赖 - AuthService */
    @MockBean
    private AuthService authService;

    /** Mock AuthInterceptor 依赖 - SysTenantMapper */
    @MockBean
    private SysTenantMapper sysTenantMapper;

    /**
     * 覆盖 WebMvcConfig 的拦截器注册行为，注册一个空的 WebMvcConfigurer。
     * 不添加任何拦截器，从而绕过认证。
     */
    @Bean
    @Primary
    public WebMvcConfigurer testWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                // 不注册任何拦截器 - 测试环境绕过认证
            }
        };
    }
}
