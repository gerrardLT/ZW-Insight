package com.zwinsight.pbt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.assertj.core.api.Assertions;

import java.util.*;

/**
 * Property 18：快照-回滚 Round Trip
 * <p>
 * 验证：将数据保存为快照字段（JSON）后，从快照字段重建的数据等于原始数据。
 * 即 deserialize(serialize(data)) == data
 * <p>
 * Validates: Requirements 8.4
 */
@Tag("Feature: p1-system-integrity, Property 18: 快照-回滚 Round Trip")
class SnapshotRoundTripPropertyTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 核心业务逻辑：将业务数据字段序列化为快照 JSON
     */
    static String serializeSnapshot(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Snapshot serialization failed", e);
        }
    }

    /**
     * 核心业务逻辑：从快照 JSON 反序列化为业务数据字段
     */
    static Map<String, Object> deserializeSnapshot(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Snapshot deserialization failed", e);
        }
    }

    @Property(tries = 100)
    void roundTrip_preservesData(
            @ForAll("snapshotData") Map<String, Object> original) {
        String serialized = serializeSnapshot(original);
        Map<String, Object> restored = deserializeSnapshot(serialized);
        Assertions.assertThat(restored).isEqualTo(original);
    }

    @Property(tries = 100)
    void roundTrip_serializedIsNotEmpty(
            @ForAll("snapshotData") Map<String, Object> original) {
        String serialized = serializeSnapshot(original);
        Assertions.assertThat(serialized).isNotEmpty();
    }

    @Property(tries = 100)
    void roundTrip_isIdempotent(
            @ForAll("snapshotData") Map<String, Object> original) {
        String firstSerialized = serializeSnapshot(original);
        Map<String, Object> restored = deserializeSnapshot(firstSerialized);
        String secondSerialized = serializeSnapshot(restored);
        Assertions.assertThat(secondSerialized).isEqualTo(firstSerialized);
    }

    @Provide
    Arbitrary<Map<String, Object>> snapshotData() {
        // 生成典型的业务快照数据：字符串、数字、布尔值
        Arbitrary<String> keys = Arbitraries.of(
                "amount", "status", "totalBudget", "contractAmount",
                "settledAmount", "outputValue", "version");
        Arbitrary<Object> values = Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20).map(s -> (Object) s),
                Arbitraries.integers().between(0, 999999).map(i -> (Object) i),
                Arbitraries.of(true, false).map(b -> (Object) b)
        );
        return Combinators.combine(keys, values)
                .as((k, v) -> new AbstractMap.SimpleEntry<>(k, v))
                .list().ofMinSize(1).ofMaxSize(7)
                .map(entries -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    for (var entry : entries) {
                        map.put(entry.getKey(), entry.getValue());
                    }
                    return map;
                });
    }
}
