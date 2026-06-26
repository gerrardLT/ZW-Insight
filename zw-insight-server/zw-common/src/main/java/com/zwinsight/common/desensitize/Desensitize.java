package com.zwinsight.common.desensitize;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.*;

/**
 * 数据脱敏注解
 * <p>
 * 标注在实体类的 String 类型字段上，API 响应经过 Jackson 序列化时
 * 会自动按指定脱敏类型对字段值进行掩码处理。
 * </p>
 *
 * <pre>
 * 使用示例：
 * {@code @Desensitize(type = DesensitizeType.PHONE)}
 * private String phone;
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonSerialize(using = DesensitizeSerializer.class)
public @interface Desensitize {

    /**
     * 脱敏类型
     */
    DesensitizeType type();
}
