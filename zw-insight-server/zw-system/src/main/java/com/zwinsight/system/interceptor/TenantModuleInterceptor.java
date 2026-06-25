package com.zwinsight.system.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.R;
import com.zwinsight.security.domain.SysTenant;
import com.zwinsight.security.mapper.SysTenantMapper;
import com.zwinsight.system.domain.enums.TenantModuleEnum;
import com.zwinsight.system.domain.enums.TenantStatusEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 租户模块权限拦截器
 * 检查当前租户是否有访问该 API 所属模块的权限
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantModuleInterceptor implements HandlerInterceptor {

    private final SysTenantMapper tenantMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            // 无租户上下文的请求（如平台管理接口）不做模块校验
            return true;
        }

        String requestPath = request.getRequestURI();

        // 匹配请求路径对应的模块编码
        String requiredModule = TenantModuleEnum.getModuleCodeByPath(requestPath);
        if (requiredModule == null) {
            // 该路径不属于任何业务模块（如系统管理、认证等），直接放行
            return true;
        }

        // 查询租户信息
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            writeErrorResponse(response, 403, "租户不存在");
            return false;
        }

        // 检查租户状态
        if (tenant.getStatus() != TenantStatusEnum.NORMAL.getCode()) {
            writeErrorResponse(response, 403, "租户已被停用或已过期，无法访问");
            return false;
        }

        // 检查模块权限
        List<String> modules = tenant.getModules();
        if (modules == null || !modules.contains(requiredModule)) {
            log.warn("租户 {} 未授权模块 {}，拒绝访问: {}", tenantId, requiredModule, requestPath);
            writeErrorResponse(response, 403, "您的租户未开通该功能模块，请联系管理员");
            return false;
        }

        return true;
    }

    private void writeErrorResponse(HttpServletResponse response, int httpStatus, String message) throws Exception {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        R<Void> result = R.fail(httpStatus, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
