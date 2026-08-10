package com.zwinsight.security.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API 文档端点拦截器（生产收敛，2026-08-10，tasks.md 2.3.3 豁免条件落地）。
 *
 * <p>背景：拦截器体系仅保护 /api/**，knife4j 的 /doc.html 与 /webjars/** 为 webjar
 * 静态资源且 knife4j 自注册 ResourceHandler，配置级关闭（knife4j.enable=false、
 * static-locations=[]）实测仍返回 200（CI run 31368061537/31370597288 断言实证），
 * 故在 handler 执行前直接拒绝，返回 404 不暴露端点存在性。
 *
 * <p>由 {@code security.docs-block-enabled} 开关控制（默认 false，不影响本地/联调），
 * 生产经 deploy compose 环境变量 SECURITY_DOCS_BLOCK_ENABLED=true 启用。
 */
public class DocsBlockInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        response.setStatus(404);
        return false;
    }
}
