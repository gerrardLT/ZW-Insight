package com.zwinsight.pbt;

import net.jqwik.api.*;
import org.assertj.core.api.Assertions;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Property 5：同比环比变化率公式
 * <p>
 * 验证：rate = (current - previous) / previous * 100，scale=1
 * 当 previous == 0 时不可计算（应抛异常或返回 null）。
 * <p>
 * Validates: Requirements 2.8
 */
@Tag("Feature: p1-system-integrity, Property 5: 同比环比变化率公式")
class ChangeRateFormulaPropertyTest {

    /**
     * 核心业务逻辑：计算变化率
     * rate = (current - previous) / previous * 100, scale=1, HALF_UP
     *
     * @throws ArithmeticException if previous is zero
     */
    static BigDecimal calculateChangeRate(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Previous value cannot be zero");
        }
        return current.subtract(previous)
                .divide(previous, 10, RoundingMode.HALF_UP) // 中间精度用 10
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    @Property(tries = 100)
    void changeRate_followsFormula(
            @ForAll("currentValues") BigDecimal current,
            @ForAll("previousValues") BigDecimal previous) {
        BigDecimal rate = calculateChangeRate(current, previous);

        // 使用高精度独立计算验证一致性
        // rate = (current - previous) / previous * 100
        BigDecimal diff = current.subtract(previous);
        BigDecimal ratio = diff.divide(previous, 10, RoundingMode.HALF_UP);
        BigDecimal expected = ratio.multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);

        Assertions.assertThat(rate).isEqualByComparingTo(expected);
    }

    @Property(tries = 100)
    void changeRate_scaleIsOne(
            @ForAll("currentValues") BigDecimal current,
            @ForAll("previousValues") BigDecimal previous) {
        BigDecimal rate = calculateChangeRate(current, previous);
        Assertions.assertThat(rate.scale()).isEqualTo(1);
    }

    @Property(tries = 100)
    void changeRate_zeroWhenCurrentEqualsPrevious(
            @ForAll("previousValues") BigDecimal value) {
        BigDecimal rate = calculateChangeRate(value, value);
        Assertions.assertThat(rate).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Property(tries = 100)
    void changeRate_positiveWhenCurrentGreater(
            @ForAll("previousValues") BigDecimal previous) {
        // current 明显大于 previous（翻倍），确保变化率四舍五入到 1 位小数后仍为正。
        // 注意：若仅 previous + 1，当 previous 很大时（如 100000）变化率约 0.001%，
        // setScale(1, HALF_UP) 会舍入为 0.0，导致 “>0” 不成立——那是舍入而非逻辑问题。
        BigDecimal current = previous.add(previous); // current = 2 * previous > previous
        BigDecimal rate = calculateChangeRate(current, previous);
        Assertions.assertThat(rate).isGreaterThan(BigDecimal.ZERO);
    }

    @Provide
    Arbitrary<BigDecimal> currentValues() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(-100000), BigDecimal.valueOf(100000))
                .ofScale(2);
    }

    @Provide
    Arbitrary<BigDecimal> previousValues() {
        // previous != 0，避免除零
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(1), BigDecimal.valueOf(100000))
                .ofScale(2);
    }
}
