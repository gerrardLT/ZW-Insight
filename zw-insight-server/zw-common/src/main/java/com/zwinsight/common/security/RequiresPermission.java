package com.zwinsight.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级功能权限校验注解。
 * <p>
 * 标注在 Controller 方法或类上，声明访问该接口所需的功能权限标识（形如
 * {@code system:user:add}）。由 {@code PermissionInterceptor} 在请求进入时校验：
 * 当前登录用户的权限集合（登录时由 {@code sys_menu.permission} 下发）需满足声明的权限，
 * 否则返回 403。{@code SUPER_ADMIN} 角色跳过校验。
 * </p>
 * <p>
 * 采用 opt-in 语义：未标注该注解的接口不做功能权限校验，便于按高危优先级增量铺开。
 * 方法级注解优先于类级注解。
 * </p>
 *
 * <pre>
 * &#64;RequiresPermission("system:user:reset-pwd")
 * &#64;PutMapping("/{id}/reset-password")
 * public R&lt;Void&gt; resetPassword(...) { ... }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * 所需权限标识列表，命名规范 {@code {模块}:{资源}:{动作}}。
     */
    String[] value();

    /**
     * 多个权限标识之间的逻辑关系，默认 {@link Logical#OR}（满足其一即可）。
     */
    Logical logical() default Logical.OR;
}
