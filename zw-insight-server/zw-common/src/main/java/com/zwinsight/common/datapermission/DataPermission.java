package com.zwinsight.common.datapermission;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解
 * <p>
 * 标注在 Mapper 接口方法上，表示该查询需要进行数据权限过滤。
 * 配合 {@link DataColumn} 指定需要过滤的表和列。
 * </p>
 *
 * 使用示例：
 * <pre>
 * &#64;DataPermission(value = {
 *     &#64;DataColumn(alias = "p", projectColumn = "id"),
 *     &#64;DataColumn(alias = "m", userColumn = "created_by")
 * })
 * List&lt;BizProject&gt; selectProjectList(IPage&lt;BizProject&gt; page);
 * </pre>
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataPermission {

    /**
     * 数据权限列配置
     * <p>可配置多个表的数据过滤规则</p>
     */
    DataColumn[] value() default {};
}
