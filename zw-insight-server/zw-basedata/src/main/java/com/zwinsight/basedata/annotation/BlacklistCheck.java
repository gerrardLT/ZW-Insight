package com.zwinsight.basedata.annotation;

import java.lang.annotation.*;

/**
 * 供应商黑名单校验注解
 * <p>
 * 标注在 Service 方法上，方法执行前自动校验涉及的供应商是否在黑名单中。
 * 需配合 AOP 切面使用。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BlacklistCheck {
}
