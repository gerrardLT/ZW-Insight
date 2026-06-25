package com.zwinsight.budget.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 预算控制校验注解
 * <p>
 * 标注在业务单据提交方法上，AOP 切面会自动根据配置规则进行预算执行率校验。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BudgetCheck {

    /**
     * 成本科目（如 MATERIAL/LABOR/MACHINE/SUBCONTRACT）
     * <p>
     * 若为空字符串，切面将尝试从方法参数中自动识别科目。
     * </p>
     */
    String category() default "";
}
