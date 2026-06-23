package com.zwinsight.system.aspect;

import java.lang.annotation.*;

/**
 * 操作日志注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {

    /**
     * 模块名称
     */
    String module() default "";

    /**
     * 操作类型（INSERT/UPDATE/DELETE）
     */
    String operType() default "";

    /**
     * 操作描述
     */
    String description() default "";
}
