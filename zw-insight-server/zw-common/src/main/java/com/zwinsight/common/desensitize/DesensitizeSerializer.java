package com.zwinsight.common.desensitize;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

/**
 * 数据脱敏 Jackson 序列化器
 * <p>
 * 配合 {@link Desensitize} 注解使用，在序列化阶段对标注字段执行掩码处理。
 * 完整实现将在后续任务中补充。
 * </p>
 */
public class DesensitizeSerializer extends StdSerializer<String> implements ContextualSerializer {

    private DesensitizeType type;

    protected DesensitizeSerializer() {
        super(String.class);
    }

    protected DesensitizeSerializer(DesensitizeType type) {
        super(String.class);
        this.type = type;
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
            throws JsonMappingException {
        if (property != null) {
            Desensitize ann = property.getAnnotation(Desensitize.class);
            if (ann == null) {
                ann = property.getContextAnnotation(Desensitize.class);
            }
            if (ann != null) {
                return new DesensitizeSerializer(ann.type());
            }
        }
        return prov.findValueSerializer(property.getType(), property);
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        if (type == null) {
            gen.writeString(value);
        } else {
            gen.writeString(DesensitizeUtil.desensitize(value, type));
        }
    }
}
