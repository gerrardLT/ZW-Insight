package com.zwinsight.common.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;

/**
 * 全局 Jackson 配置：将 Long / long / BigInteger 序列化为 String。
 * <p>
 * 背景：实体主键采用雪花算法（19 位 Long，见 BaseEntity），JS 端 JSON.parse 超过
 *      2^53 会丢失精度，导致前端从列表取到的 id 与真实值不一致，按 id 做详情/编辑/删除失败。
 * </p>
 * <p>
 * 方案：响应序列化时把 Long/BigInteger 转为字符串，前后端（含移动端）天然无精度问题。
 *      仅影响出参序列化，不影响 @PathVariable/@RequestParam/@RequestBody 的入参解析
 *      （字符串 "123" 仍可正常反序列化为 Long）。
 * </p>
 * <p>
 * 采用 Jackson2ObjectMapperBuilderCustomizer 追加序列化器，不覆盖 Spring Boot 默认
 *      ObjectMapper，保留 JavaTimeModule 等既有配置。
 * </p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringSerializerCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
            builder.serializerByType(BigInteger.class, ToStringSerializer.instance);
        };
    }
}
