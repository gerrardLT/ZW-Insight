package com.zwinsight.security.config;

import com.zwinsight.security.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/captcha",
                        // 忘记密码全流程（发送验证码/校验验证码/重置密码）必须免登录可达
                        "/api/v1/auth/password-reset/**",
                        "/api/v1/public/**",
                        // 供应商门户全路径放行（含 /public/** 免登录公开询价接口）
                        "/api/v1/supplier-portal/**"
                );
    }
}
