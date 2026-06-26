package com.zwinsight.basedata.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 供应商黑名单校验注解
 * <p>
 * 标注在合同创建/更新方法上，在方法执行前由 AOP 切面自动校验
 * 供应商是否在黑名单中。如果在黑名单中则抛出 BusinessException 阻止操作。
 * </p>
 *
 * <pre>
 * {@code
 * @BlacklistCheck
 * public void save(BizPurchaseContract contract) { ... }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BlacklistCheck {
}
