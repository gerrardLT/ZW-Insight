package com.zwinsight.security.interceptor;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.security.Logical;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.security.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 接口级功能权限拦截器。
 * <p>
 * 在 {@code AuthInterceptor} 之后执行（依赖其已注入的 userId），对标注了
 * {@link RequiresPermission} 的 Controller 方法/类做服务端功能权限校验：
 * </p>
 * <ol>
 *   <li>开关 {@code auth.permission-check-enabled=false} 时全局放行（灰度/回滚）。</li>
 *   <li>未标注注解的接口放行（opt-in，按高危优先级增量铺开）。</li>
 *   <li>无 userId 返回 401；{@code SUPER_ADMIN} 角色跳过校验。</li>
 *   <li>按注解的权限标识与 {@link Logical} 逻辑比对登录用户权限集合，不满足返回 403。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    private final SysUserMapper sysUserMapper;

    /** 超级管理员角色编码，跳过功能权限校验 */
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    @Value("${auth.permission-check-enabled:true}")
    private boolean permissionCheckEnabled;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!permissionCheckEnabled) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 方法级注解优先，其次类级注解；均无则放行（opt-in）
        RequiresPermission required = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequiresPermission.class);
        }
        if (required == null) {
            return true;
        }

        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            writeErrorResponse(response, 401, "未登录或Token已过期");
            return false;
        }

        // 超级管理员豁免
        List<String> roleCodes = sysUserMapper.selectRoleCodesByUserId(userId);
        if (roleCodes != null && roleCodes.contains(ROLE_SUPER_ADMIN)) {
            return true;
        }

        List<String> owned = sysUserMapper.selectPermissionsByUserId(userId);
        Set<String> ownedSet = (owned == null) ? Set.of() : new HashSet<>(owned);
        String[] requiredPerms = required.value();

        boolean pass = (required.logical() == Logical.AND)
                ? Arrays.stream(requiredPerms).allMatch(ownedSet::contains)
                : Arrays.stream(requiredPerms).anyMatch(ownedSet::contains);

        if (!pass) {
            log.warn("功能权限不足 [{}] userId={} 需要={} 逻辑={} 拥有={}",
                    request.getRequestURI(), userId, Arrays.toString(requiredPerms), required.logical(), ownedSet);
            writeErrorResponse(response, 403, "无权访问该功能");
            return false;
        }
        return true;
    }

    private void writeErrorResponse(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        // 与 AuthInterceptor 保持一致，直接输出简化 JSON，避免引入 ObjectMapper 依赖
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
