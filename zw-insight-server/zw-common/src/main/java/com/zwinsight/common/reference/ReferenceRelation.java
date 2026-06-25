package com.zwinsight.common.reference;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 引用关系定义注解
 * <p>
 * 描述一个引用表与被删除实体的关联关系，用于删除前校验。
 * </p>
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface ReferenceRelation {

    /**
     * 引用表名（必须为合法表名：仅字母、数字、下划线）
     */
    String tableName();

    /**
     * 引用字段名（外键字段，必须为合法列名）
     */
    String column();

    /**
     * 引用实体中文名，用于前端展示
     */
    String displayName();

    /**
     * 单据编号字段名，用于查询引用详情。为空则不查询单据编号。
     */
    String codeColumn() default "";
}
