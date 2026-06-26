package com.zwinsight.finance.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 财务封账校验注解
 * <p>
 * 标注在财务模块的写入方法（新增、编辑、删除）上，{@code FinanceLockAspect} 切面会
 * 在方法执行前根据单据的业务日期校验封账状态。若业务日期所属年月命中状态为 LOCKED 的
 * 封账期间，则抛出 {@code BusinessException} 阻止操作。
 * </p>
 *
 * @see com.zwinsight.finance.aspect.FinanceLockAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FinanceLockCheck {

    /**
     * 业务日期字段名。
     * <p>
     * 切面通过反射从方法参数对象中读取该字段对应的 getter（如 {@code applyDate} → {@code getApplyDate()}），
     * 提取业务日期值用于封账期间匹配。支持 {@link java.time.LocalDate}、{@link java.time.LocalDateTime}、
     * {@link java.util.Date} 类型。
     * </p>
     */
    String dateField() default "applyDate";

    /**
     * 操作描述，用于封账命中时拼接错误提示信息（"期间{period}已封账，禁止{operation}"）。
     * 默认为 "操作"，可标注为 "新增"、"编辑"、"删除" 等更具体的描述。
     */
    String operation() default "操作";
}
