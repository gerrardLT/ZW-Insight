package com.zwinsight.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作二次确认注解。
 * <p>
 * 标注在高风险的 Controller 方法（如删除、审批终止等）上，要求请求方在请求头
 * {@code X-Confirm-Password} 中携带当前登录用户的登录密码进行二次确认。
 * 由 {@code SecondaryConfirmAspect} 切面拦截校验：
 * </p>
 * <ul>
 *   <li>请求头缺失/为空/纯空白 → HTTP 449（需要二次确认）</li>
 *   <li>密码错误 → HTTP 403，并累计失败次数；15 分钟内连续 5 次失败 → 锁定 15 分钟（HTTP 423）</li>
 *   <li>密码正确 → 重置失败计数并放行目标方法</li>
 * </ul>
 *
 * @see com.zwinsight.security.aspect.SecondaryConfirmAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SecondaryConfirm {

    /**
     * 需要二次确认时返回给前端的提示信息。
     */
    String message() default "此操作需要二次确认";
}
