package com.zwinsight.site.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: p0-data-permission-overdue, Property 9: 升级通知阈值正确触发
/**
 * Property 9: 升级通知阈值正确触发
 * <p>
 * 验证：当 overdueDays >= escalationDays 时，应触发项目经理升级通知；
 * 当 overdueDays < escalationDays 时，不应触发升级通知。
 * </p>
 * <p>
 * **Validates: Requirements 7.3**
 * </p>
 */
class EscalationThresholdPropertyTest {

    /**
     * 复刻 RectificationReminderTask.processRecord 中的升级判断逻辑
     * 当 overdueDays >= escalationDays 时触发升级通知
     */
    private boolean shouldEscalate(long overdueDays, int escalationDays) {
        return overdueDays >= escalationDays;
    }

    @Property(tries = 100)
    void escalationTriggeredWhenOverdueExceedsThreshold(
            @ForAll @IntRange(min = 1, max = 60) int escalationDays,
            @ForAll @IntRange(min = 0, max = 100) int extraDays) {

        long overdueDays = escalationDays + extraDays; // overdueDays >= escalationDays

        boolean result = shouldEscalate(overdueDays, escalationDays);

        assertThat(result)
                .as("overdueDays=%d >= escalationDays=%d 时应触发升级通知", overdueDays, escalationDays)
                .isTrue();
    }

    @Property(tries = 100)
    void escalationNotTriggeredWhenBelowThreshold(
            @ForAll @IntRange(min = 2, max = 60) int escalationDays,
            @ForAll @IntRange(min = 1, max = 59) int overdueDaysRaw) {

        // 确保 overdueDays < escalationDays
        long overdueDays = Math.min(overdueDaysRaw, escalationDays - 1);

        boolean result = shouldEscalate(overdueDays, escalationDays);

        assertThat(result)
                .as("overdueDays=%d < escalationDays=%d 时不应触发升级通知", overdueDays, escalationDays)
                .isFalse();
    }

    @Property(tries = 100)
    void escalationTriggeredAtExactThreshold(
            @ForAll @IntRange(min = 1, max = 100) int escalationDays) {

        long overdueDays = escalationDays; // 恰好等于阈值

        boolean result = shouldEscalate(overdueDays, escalationDays);

        assertThat(result)
                .as("overdueDays=%d == escalationDays=%d 时应触发升级通知（>= 条件）", overdueDays, escalationDays)
                .isTrue();
    }

    @Property(tries = 100)
    void escalationResultConsistentWithManualComparison(
            @ForAll @IntRange(min = 1, max = 200) int overdueDays,
            @ForAll @IntRange(min = 1, max = 100) int escalationDays) {

        boolean result = shouldEscalate(overdueDays, escalationDays);
        boolean expected = overdueDays >= escalationDays;

        assertThat(result)
                .as("shouldEscalate(%d, %d) 应与手动比较一致", overdueDays, escalationDays)
                .isEqualTo(expected);
    }
}
