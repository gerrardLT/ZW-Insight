package com.zwinsight.pbt;

import net.jqwik.api.*;
import org.assertj.core.api.Assertions;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Property 9：机械费用计算公式正确性
 * <p>
 * 验证：
 * - 台班计价：cost = shift_count * unit_price，scale=2
 * - 工作量计价：cost = work_volume * unit_price，scale=2
 * <p>
 * Validates: Requirements 4.2
 */
@Tag("Feature: p1-system-integrity, Property 9: 机械费用计算公式正确性")
class MachineCostFormulaPropertyTest {

    enum PricingMode {
        SHIFT,  // 台班计价
        VOLUME  // 工作量计价
    }

    /**
     * 核心业务逻辑：计算机械费用
     */
    static BigDecimal calculateCost(PricingMode mode, BigDecimal shiftCount,
                                     BigDecimal workVolume, BigDecimal unitPrice) {
        BigDecimal result;
        switch (mode) {
            case SHIFT:
                result = shiftCount.multiply(unitPrice);
                break;
            case VOLUME:
                result = workVolume.multiply(unitPrice);
                break;
            default:
                throw new IllegalArgumentException("Unknown pricing mode: " + mode);
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    @Property(tries = 100)
    void shiftPricing_costEqualsShiftCountTimesUnitPrice(
            @ForAll("shiftCounts") BigDecimal shiftCount,
            @ForAll("unitPrices") BigDecimal unitPrice) {
        BigDecimal cost = calculateCost(PricingMode.SHIFT, shiftCount, BigDecimal.ZERO, unitPrice);
        BigDecimal expected = shiftCount.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        Assertions.assertThat(cost).isEqualByComparingTo(expected);
    }

    @Property(tries = 100)
    void volumePricing_costEqualsWorkVolumeTimesUnitPrice(
            @ForAll("workVolumes") BigDecimal workVolume,
            @ForAll("unitPrices") BigDecimal unitPrice) {
        BigDecimal cost = calculateCost(PricingMode.VOLUME, BigDecimal.ZERO, workVolume, unitPrice);
        BigDecimal expected = workVolume.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        Assertions.assertThat(cost).isEqualByComparingTo(expected);
    }

    @Property(tries = 100)
    void cost_alwaysHasScale2(
            @ForAll PricingMode mode,
            @ForAll("shiftCounts") BigDecimal shiftCount,
            @ForAll("workVolumes") BigDecimal workVolume,
            @ForAll("unitPrices") BigDecimal unitPrice) {
        BigDecimal cost = calculateCost(mode, shiftCount, workVolume, unitPrice);
        Assertions.assertThat(cost.scale()).isEqualTo(2);
    }

    @Property(tries = 100)
    void cost_isNonNegative(
            @ForAll PricingMode mode,
            @ForAll("shiftCounts") BigDecimal shiftCount,
            @ForAll("workVolumes") BigDecimal workVolume,
            @ForAll("unitPrices") BigDecimal unitPrice) {
        BigDecimal cost = calculateCost(mode, shiftCount, workVolume, unitPrice);
        Assertions.assertThat(cost).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Provide
    Arbitrary<BigDecimal> shiftCounts() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, BigDecimal.valueOf(1000))
                .ofScale(1);
    }

    @Provide
    Arbitrary<BigDecimal> workVolumes() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, BigDecimal.valueOf(100000))
                .ofScale(2);
    }

    @Provide
    Arbitrary<BigDecimal> unitPrices() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(10000))
                .ofScale(2);
    }
}
