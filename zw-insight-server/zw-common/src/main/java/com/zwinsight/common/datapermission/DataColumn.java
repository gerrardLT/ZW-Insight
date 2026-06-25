package com.zwinsight.common.datapermission;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限列定义
 * <p>
 * 配合 {@link DataPermission} 注解使用，指定表的别名和需要过滤的字段名
 * </p>
 *
 * 示例：@DataColumn(alias = "p", name = "project_id")
 * 表示对表别名为 p 的表，基于 project_id 列进行数据过滤
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface DataColumn {

    /**
     * 表别名（SQL 中 FROM/JOIN 后的别名）
     * <p>若 SQL 无别名，留空</p>
     */
    String alias() default "";

    /**
     * 过滤列名
     * <p>
     * 对于 SELF 范围默认使用 created_by 列；
     * 对于 PROJECT 范围默认使用 project_id 列；
     * 对于 DEPT/DEPT_AND_CHILDREN 默认使用 dept_id 列；
     * 通过此字段可覆盖默认列名
     * </p>
     */
    String name() default "";

    /**
     * 用户ID列名（用于 SELF 范围的过滤条件）
     * <p>默认为 created_by</p>
     */
    String userColumn() default "created_by";

    /**
     * 部门ID列名（用于 DEPT/DEPT_AND_CHILDREN 范围的过滤条件）
     * <p>默认为 dept_id</p>
     */
    String deptColumn() default "dept_id";

    /**
     * 项目ID列名（用于 PROJECT 范围的过滤条件）
     * <p>默认为 project_id</p>
     */
    String projectColumn() default "project_id";
}
