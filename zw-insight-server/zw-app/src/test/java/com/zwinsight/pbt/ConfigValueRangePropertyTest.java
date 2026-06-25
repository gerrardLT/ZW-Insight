package com.zwinsight.pbt;

import net.jqwik.api.*;
import org.assertj.core.api.Assertions;

/**
 * Property 12：系统参数值范围校验
 * <p>
 * 验证：数值在 [min, max] 范围内为有效，范围外为无效。
 * <p>
 * Validates: Requirements 5.7
 */
@Tag("Feature: p1-system-integrity, Property 12: 系统参数值范围校验")
class ConfigValueRangePropertyTest {

    /**
     * 核心业务逻辑：校验数值是否在指定范围内
     *
     * @param value 待校验的值
     * @param min   最小值（含）
     * @param max   最大值（含）
     * @return true 表示有效
     */
    static boolean isValueInRange(int value, int min, int max) {
        return value >= min && value <= max;
    }

    /**
     * 解析 "min-max" 格式的范围字符串
     */
    static int[] parseRange(String rangeStr) {
        String[] parts = rangeStr.split("-");
        if (parts.length == 2) {
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        }
        throw new IllegalArgumentException("Invalid range format: " + rangeStr);
    }

    @Property(tries = 100)
    void valueWithinRange_isValid(
            @ForAll("ranges") int[] range,
            @ForAll("valuesInRange") Integer offset) {
        int min = range[0];
        int max = range[1];
        // 确保 value 在 [min, max] 之间
        int value = min + Math.abs(offset) % (max - min + 1);

        Assertions.assertThat(isValueInRange(value, min, max)).isTrue();
    }

    @Property(tries = 100)
    void valueAboveMax_isInvalid(
            @ForAll("ranges") int[] range,
            @ForAll("positiveOffsets") int offset) {
        int min = range[0];
        int max = range[1];
        int value = max + offset; // value > max

        Assertions.assertThat(isValueInRange(value, min, max)).isFalse();
    }

    @Property(tries = 100)
    void valueBelowMin_isInvalid(
            @ForAll("ranges") int[] range,
            @ForAll("positiveOffsets") int offset) {
        int min = range[0];
        int max = range[1];
        int value = min - offset; // value < min

        Assertions.assertThat(isValueInRange(value, min, max)).isFalse();
    }

    @Property(tries = 100)
    void boundaryValues_areValid(@ForAll("ranges") int[] range) {
        int min = range[0];
        int max = range[1];

        Assertions.assertThat(isValueInRange(min, min, max)).isTrue();
        Assertions.assertThat(isValueInRange(max, min, max)).isTrue();
    }

    @Provide
    Arbitrary<int[]> ranges() {
        return Arbitraries.integers().between(1, 100)
                .flatMap(min -> Arbitraries.integers().between(min + 1, min + 200)
                        .map(max -> new int[]{min, max}));
    }

    @Provide
    Arbitrary<Integer> valuesInRange() {
        return Arbitraries.integers().between(0, 10000);
    }

    @Provide
    Arbitrary<Integer> positiveOffsets() {
        return Arbitraries.integers().between(1, 1000);
    }
}
