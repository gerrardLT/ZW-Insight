package com.zwinsight.system.config;

import com.zwinsight.system.interceptor.TenantModuleInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 租户模块权限拦截器注册配置
 */
@Configuration
@RequiredArgsConstructor
public class TenantModuleWebMvcConfig implements WebMvcConfigurer {

    private final TenantModuleInterceptor tenantModuleInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantModuleInterceptor)
                .addPathPatterns(
                        "/api/v1/tender/**",
                        "/api/v1/budget/**",
                        "/api/v1/purchase/**",
                        "/api/v1/labor/**",
                        "/api/v1/material/**",
                        "/api/v1/machine/**",
                        "/api/v1/subcontract/**",
                        "/api/v1/site/**",
                        "/api/v1/finance/**",
                        "/api/v1/hr/**",
                        "/api/v1/price-compare/**",
                        "/api/v1/dashboard/**"
                )
                .order(2); // AuthInterceptor 之后执行
    }
}
