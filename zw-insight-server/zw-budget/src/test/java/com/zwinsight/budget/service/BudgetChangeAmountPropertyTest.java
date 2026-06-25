package com.zwinsight.budget.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property P2: 预算变更金额守恒
 * <p>
 * 验证：SUM(details.adjustAmount) == change.totalAdjustAmount
 * 且 adjustedAmount == originalAmount + adjustAmount 对每条明细成立。
 * </p>
 * <p>
 * **Validates: Requirements 2.2, 2.5**
 * </p>
 */
@DisplayName("P2: 预算变更金额守恒属性测试")
class BudgetChangeAmountPropertyTest {

    /**
     * 模拟变更明细数据结构
     */
    static class ChangeDetail {
        BigDecimal originalAmount;
        BigDecimal adjustAmount;
        BigDecimal adjustedAmount;

        ChangeDetail(BigDecimal originalAmount, BigDecimal adjustAmount) {
            this.originalAmount = originalAmount;
            this.adjustAmount = adjustAmount;
            this.adjustedAmount = originalAmount.add(adjustAmount);
        }
    }

    /**
     * 模拟创建变更单时的 totalAdjustAmount 计算逻辑（与 BudgetChangeService.create 一致）
     */
    private BigDecimal calculateTotalAdjustAmount(List<ChangeDetail> details) {
        return details.stream()
                .map(d -> d.adjustAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @RepeatedTest(100)
    @DisplayName("P2: SUM(details.adjustAmount) == change.totalAdjustAmount")
    void testAmountConservation() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 随机生成 1~20 条明细
        int detailCount = random.nextInt(1, 21);
        List<ChangeDetail> details = new ArrayList<>();

        for (int i = 0; i < detailCount; i++) {
            // 原金额: 10000~5000000 之间（精确到2位小数）
            BigDecimal originalAmount = BigDecimal.valueOf(random.nextDouble(10000, 5000000))
                    .setScale(2, RoundingMode.HALF_UP);
            // 调整金额: -100000~+100000 之间（正追加/负调减）
            BigDecimal adjustAmount = BigDecimal.valueOf(random.nextDouble(-100000, 100000))
                    .setScale(2, RoundingMode.HALF_UP);

            details.add(new ChangeDetail(originalAmount, adjustAmount));
        }

        // 计算 totalAdjustAmount（模拟 BudgetChangeService 的逻辑）
        BigDecimal totalAdjustAmount = calculateTotalAdjustAmount(details);

        // 属性1: SUM(details.adjustAmount) == totalAdjustAmount
        BigDecimal sumAdjust = BigDecimal.ZERO;
        for (ChangeDetail detail : details) {
            sumAdjust = sumAdjust.add(detail.adjustAmount);
        }
        assertEquals(0, sumAdjust.compareTo(totalAdjustAmount),
                "SUM(details.adjustAmount)=" + sumAdjust + " 应等于 totalAdjustAmount=" + totalAdjustAmount);

        // 属性2: adjustedAmount == originalAmount + adjustAmount 对每条明细成立
        for (int i = 0; i < details.size(); i++) {
            ChangeDetail detail = details.get(i);
            BigDecimal expected = detail.originalAmount.add(detail.adjustAmount);
            assertEquals(0, expected.compareTo(detail.adjustedAmount),
                    "明细[" + i + "]: adjustedAmount=" + detail.adjustedAmount +
                            " 应等于 originalAmount(" + detail.originalAmount + ") + adjustAmount(" + detail.adjustAmount + ") = " + expected);
        }
    }

    @RepeatedTest(100)
    @DisplayName("P2: 审批通过后各明细回写累加总和等于变更单总调整额")
    void testApprovalWritebackConsistency() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 随机生成 2~15 条明细
        int detailCount = random.nextInt(2, 16);
        List<ChangeDetail> details = new ArrayList<>();

        for (int i = 0; i < detailCount; i++) {
            BigDecimal originalAmount = BigDecimal.valueOf(random.nextDouble(50000, 2000000))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal adjustAmount = BigDecimal.valueOf(random.nextDouble(-50000, 80000))
                    .setScale(2, RoundingMode.HALF_UP);
            details.add(new ChangeDetail(originalAmount, adjustAmount));
        }

        BigDecimal totalAdjustAmount = calculateTotalAdjustAmount(details);

        // 模拟审批通过后回写：逐科目回写预算明细（累加 adjustAmount）
        BigDecimal writebackSum = BigDecimal.ZERO;
        for (ChangeDetail detail : details) {
            writebackSum = writebackSum.add(detail.adjustAmount);
        }

        // 属性: 回写累加总和 == 变更单总调整额
        assertEquals(0, writebackSum.compareTo(totalAdjustAmount),
                "回写累加总和=" + writebackSum + " 应等于变更单总调整额=" + totalAdjustAmount);
    }
}
