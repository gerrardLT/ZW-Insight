package com.zwinsight.purchase.portal.config;

import com.zwinsight.purchase.portal.interceptor.SupplierPortalInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 供应商门户 WebMvc 配置
 * <p>
 * 注册供应商专用 JWT 拦截器，仅拦截 /api/v1/supplier-portal/** 路径，
 * 排除认证相关的 /auth/** 路径。
 */
@Configuration
@RequiredArgsConstructor
public class SupplierPortalWebMvcConfig implements WebMvcConfigurer {

    private final SupplierPortalInterceptor supplierPortalInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(supplierPortalInterceptor)
                .addPathPatterns("/api/v1/supplier-portal/**")
                .excludePathPatterns(
                        "/api/v1/supplier-portal/auth/**",
                        "/api/v1/supplier-portal/public/**"
                );
    }
}
