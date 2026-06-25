package com.zwinsight.common.reference;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 引用校验注解
 * <p>
 * 标注在删除方法上，在删除执行前由 AOP 切面自动校验是否存在引用关系。
 * 如果被引用则抛出 {@link ReferenceExistsException} 阻止删除。
 * </p>
 *
 * <pre>
 * {@code
 * @ReferenceCheck({
 *     @ReferenceRelation(tableName = "biz_labor_roster", column = "team_id",
 *         displayName = "花名册", codeColumn = "roster_code"),
 *     @ReferenceRelation(tableName = "biz_work_order", column = "team_id",
 *         displayName = "用工单", codeColumn = "order_code")
 * })
 * public void delete(Long id) { ... }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReferenceCheck {

    /**
     * 引用关系列表
     */
    ReferenceRelation[] value();
}
