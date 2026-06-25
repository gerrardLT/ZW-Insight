package com.zwinsight.security.interceptor;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.security.domain.SysTenant;
import com.zwinsight.security.mapper.SysTenantMapper;
import com.zwinsight.security.service.AuthService;
import com.zwinsight.security.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private final JwtUtils jwtUtils;
    private final AuthService authService;
    private final SysTenantMapper tenantMapper;

    /** 租户状态：正常 */
    private static final int TENANT_STATUS_NORMAL = 1;
    /** 租户状态：已停用 */
    private static final int TENANT_STATUS_DISABLED = 2;
    /** 租户状态：已过期 */
    private static final int TENANT_STATUS_EXPIRED = 3;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或Token已过期\"}");
            return false;
        }

        String token = header.substring(7);
        if (!authService.validateToken(token)) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token无效或已过期\"}");
            return false;
        }

        Long userId = jwtUtils.getUserId(token);
        Long tenantId = jwtUtils.getTenantId(token);
        SecurityContextHolder.setUserId(userId);
        SecurityContextHolder.setTenantId(tenantId);

        // 租户状态校验
        if (tenantId != null) {
            SysTenant tenant = tenantMapper.selectById(tenantId);
            if (tenant != null && tenant.getStatus() != null) {
                if (tenant.getStatus() == TENANT_STATUS_DISABLED) {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"租户已被停用，请联系平台管理员\"}");
                    SecurityContextHolder.clear();
                    return false;
                }
                if (tenant.getStatus() == TENANT_STATUS_EXPIRED) {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"租户已过期，请联系管理员续期\"}");
                    SecurityContextHolder.clear();
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        SecurityContextHolder.clear();
    }
}
