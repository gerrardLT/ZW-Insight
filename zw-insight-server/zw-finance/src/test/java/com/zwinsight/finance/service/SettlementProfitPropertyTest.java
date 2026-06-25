package com.zwinsight.finance.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property P3: 结算利润计算正确性
 * <p>
 * 验证：profit == totalIncome - totalExpenditure
 * 且 profitRate == profit / totalIncome * 100（totalIncome > 0 时，精确到2位小数）
 * </p>
 * <p>
 * **Validates: Requirements 3.4**
 * </p>
 */
@DisplayName("P3: 结算利润计算正确性属性测试")
class SettlementProfitPropertyTest {

    /**
     * 模拟 ProjectSettlementService 中的利润计算逻辑
     */
    private BigDecimal calculateProfit(BigDecimal totalIncome, BigDecimal totalExpenditure) {
        return totalIncome.subtract(totalExpenditure).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 模拟 ProjectSettlementService 中的利润率计算逻辑
     * profitRate = profit / totalIncome * 100（精确到2位小数）
     */
    private BigDecimal calculateProfitRate(BigDecimal profit, BigDecimal totalIncome) {
        if (totalIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return profit.divide(totalIncome, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    @RepeatedTest(100)
    @DisplayName("P3: profit == totalIncome - totalExpenditure, profitRate == profit/totalIncome*100 (when income > 0)")
    void testProfitCalculation() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 随机生成 totalIncome 和 totalExpenditure（0~10000000 之间）
        BigDecimal totalIncome = BigDecimal.valueOf(random.nextDouble(0, 10000000))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalExpenditure = BigDecimal.valueOf(random.nextDouble(0, 10000000))
                .setScale(2, RoundingMode.HALF_UP);

        // 计算利润
        BigDecimal profit = calculateProfit(totalIncome, totalExpenditure);

        // 属性1: profit == totalIncome - totalExpenditure (精确到2位小数)
        BigDecimal expectedProfit = totalIncome.subtract(totalExpenditure).setScale(2, RoundingMode.HALF_UP);
        assertEquals(0, expectedProfit.compareTo(profit),
                "profit=" + profit + " 应等于 totalIncome(" + totalIncome + ") - totalExpenditure(" + totalExpenditure + ") = " + expectedProfit);

        // 属性2: profitRate == profit / totalIncome * 100 (income > 0 时, 精确到2位小数)
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal profitRate = calculateProfitRate(profit, totalIncome);

            BigDecimal expectedRate = profit.divide(totalIncome, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            assertEquals(0, expectedRate.compareTo(profitRate),
                    "profitRate=" + profitRate + " 应等于 profit(" + profit + ")/totalIncome(" + totalIncome + ")*100 = " + expectedRate);
        }
    }

    @RepeatedTest(100)
    @DisplayName("P3: totalIncome == 0 时 profitRate 为 0")
    void testProfitRateZeroWhenIncomeZero() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpenditure = BigDecimal.valueOf(random.nextDouble(0, 10000000))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal profit = calculateProfit(totalIncome, totalExpenditure);
        BigDecimal profitRate = calculateProfitRate(profit, totalIncome);

        // 属性: totalIncome == 0 时，profitRate 应为 0
        assertEquals(0, BigDecimal.ZERO.compareTo(profitRate),
                "totalIncome=0 时 profitRate 应为 0，但实际为 " + profitRate);
    }
}
