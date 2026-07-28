package com.zwinsight.security.config;

import com.zwinsight.security.interceptor.AuthInterceptor;
import com.zwinsight.security.interceptor.PermissionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    private final PermissionInterceptor permissionInterceptor;

    /** 认证相关免登录放行路径（AuthInterceptor 与 PermissionInterceptor 共用） */
    private static final String[] EXCLUDE_PATHS = {
            "/api/v1/auth/login",
            "/api/v1/auth/captcha",
            // 新版图形验证码 / 短信验证码接口（CaptchaController），登录前必须免登录可达
            "/api/v1/captcha/**",
            // 忘记密码全流程（发送验证码/校验验证码/重置密码）必须免登录可达
            "/api/v1/auth/password-reset/**",
            "/api/v1/public/**",
            // 供应商门户全路径放行（含 /public/** 免登录公开询价接口）
            "/api/v1/supplier-portal/**"
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(EXCLUDE_PATHS)
                .order(0);
        // 接口级功能权限校验，在 AuthInterceptor 之后执行（依赖已注入的 userId）
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(EXCLUDE_PATHS)
                .order(1);
    }
}
