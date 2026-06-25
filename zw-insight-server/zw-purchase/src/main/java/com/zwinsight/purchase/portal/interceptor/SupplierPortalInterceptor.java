package com.zwinsight.purchase.portal.interceptor;

import com.zwinsight.purchase.portal.context.SupplierSecurityContext;
import com.zwinsight.purchase.portal.util.SupplierJwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 供应商门户专用 JWT 拦截器
 * <p>
 * 独立于主系统认证链，仅拦截 /api/v1/supplier-portal/**（排除 /auth/** 路径）。
 * 验证供应商专用 JWT，解析后将供应商信息存入 SupplierSecurityContext。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupplierPortalInterceptor implements HandlerInterceptor {

    private final SupplierJwtUtils supplierJwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            sendUnauthorized(response, "未登录或Token已过期");
            return false;
        }

        String token = header.substring(7);
        if (!supplierJwtUtils.validateToken(token)) {
            sendUnauthorized(response, "Token无效或已过期");
            return false;
        }

        // 解析供应商信息并存入上下文
        Long supplierId = supplierJwtUtils.getSupplierId(token);
        String phone = supplierJwtUtils.getPhone(token);
        String supplierName = supplierJwtUtils.getSupplierName(token);

        SupplierSecurityContext.setSupplierId(supplierId);
        SupplierSecurityContext.setPhone(phone);
        SupplierSecurityContext.setSupplierName(supplierName);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        SupplierSecurityContext.clear();
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }
}
