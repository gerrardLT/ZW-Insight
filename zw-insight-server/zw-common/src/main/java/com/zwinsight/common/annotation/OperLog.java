package com.zwinsight.common.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解
 * <p>标注在 Controller/Service 方法上，由 zw-system 模块的 OperLogAspect
 * 切面旁路落库（sys_oper_log）。注解定义置于 zw-common，使各业务模块
 * （project/contract/finance 等）无需依赖 zw-system 即可标注。</p>
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
