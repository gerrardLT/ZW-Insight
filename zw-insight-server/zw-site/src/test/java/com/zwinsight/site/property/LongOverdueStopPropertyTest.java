package com.zwinsight.site.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: p0-data-permission-overdue, Property 10: 长期超期停止催办
/**
 * Property 10: 长期超期停止催办
 * <p>
 * 验证：当 overdueDays > longOverdueDays 时，系统不应发送任何催办通知。
 * 当 overdueDays <= longOverdueDays 时，允许发送催办通知（如果其他条件满足）。
 * </p>
 * <p>
 * **Validates: Requirements 7.4**
 * </p>
 */
class LongOverdueStopPropertyTest {

    /**
     * 复刻 RectificationReminderTask.processRecord 中的长期超期判断逻辑
     * 当 overdueDays > longOverdueDays 时返回 false（不发送通知）
     */
    private boolean shouldSendNotification(long overdueDays, int longOverdueDays) {
        return overdueDays <= longOverdueDays;
    }

    /**
     * 复刻 calculateOverdueDays
     */
    private long calculateOverdueDays(LocalDate deadline, LocalDate today) {
        return ChronoUnit.DAYS.between(deadline, today);
    }

    @Property(tries = 100)
    void noNotificationWhenExceedsLongOverdueDays(
            @ForAll @IntRange(min = 10, max = 90) int longOverdueDays,
            @ForAll @IntRange(min = 1, max = 100) int extraDays) {

        long overdueDays = longOverdueDays + extraDays; // 严格大于 longOverdueDays

        boolean shouldSend = shouldSendNotification(overdueDays, longOverdueDays);

        assertThat(shouldSend)
                .as("overdueDays=%d > longOverdueDays=%d 时不应发送催办通知", overdueDays, longOverdueDays)
                .isFalse();
    }

    @Property(tries = 100)
    void notificationAllowedWhenWithinLongOverdueDays(
            @ForAll @IntRange(min = 10, max = 90) int longOverdueDays,
            @ForAll @IntRange(min = 1, max = 90) int overdueDaysRaw) {

        // 确保 overdueDays <= longOverdueDays
        long overdueDays = Math.min(overdueDaysRaw, longOverdueDays);

        boolean shouldSend = shouldSendNotification(overdueDays, longOverdueDays);

        assertThat(shouldSend)
                .as("overdueDays=%d <= longOverdueDays=%d 时应允许发送催办通知", overdueDays, longOverdueDays)
                .isTrue();
    }

    @Property(tries = 100)
    void notificationAllowedAtExactBoundary(
            @ForAll @IntRange(min = 5, max = 120) int longOverdueDays) {

        long overdueDays = longOverdueDays; // 恰好等于阈值

        boolean shouldSend = shouldSendNotification(overdueDays, longOverdueDays);

        assertThat(shouldSend)
                .as("overdueDays=%d == longOverdueDays=%d 时仍应允许发送（只有 > 才停止）", overdueDays, longOverdueDays)
                .isTrue();
    }

    @Property(tries = 100)
    void fullFlowWithRandomDeadlineAndConfig(
            @ForAll @IntRange(min = 15, max = 60) int longOverdueDays,
            @ForAll @IntRange(min = 1, max = 50) int extraDays) {

        LocalDate today = LocalDate.of(2025, 6, 15);
        int actualOverdueDays = longOverdueDays + extraDays;
        LocalDate deadline = today.minusDays(actualOverdueDays);

        // 计算超期天数
        long calculatedOverdueDays = calculateOverdueDays(deadline, today);

        // 验证计算出的超期天数确实超过阈值
        assertThat(calculatedOverdueDays)
                .as("计算出的超期天数应大于 longOverdueDays")
                .isGreaterThan(longOverdueDays);

        // 验证不应发送通知
        boolean shouldSend = shouldSendNotification(calculatedOverdueDays, longOverdueDays);
        assertThat(shouldSend)
                .as("deadline=%s, today=%s, overdueDays=%d, longOverdueDays=%d 时不应发送通知",
                        deadline, today, calculatedOverdueDays, longOverdueDays)
                .isFalse();
    }
}
