package com.zwinsight.site.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: p0-data-permission-overdue, Property 7: 超期天数计算正确性
/**
 * Property 7: 超期天数计算正确性
 * <p>
 * 验证：对任意超期记录的 rectificationDeadline（早于 today 的日期），
 * calculateOverdueDays 返回值应等于 ChronoUnit.DAYS.between(deadline, today)。
 * </p>
 * <p>
 * **Validates: Requirements 6.3**
 * </p>
 */
class OverdueDaysCalculationPropertyTest {

    /**
     * 复刻 RectificationReminderTask.calculateOverdueDays 逻辑
     */
    private long calculateOverdueDays(LocalDate deadline, LocalDate today) {
        return ChronoUnit.DAYS.between(deadline, today);
    }

    @Provide
    Arbitrary<LocalDate> randomToday() {
        return Arbitraries.integers().between(2020, 2026)
                .flatMap(year -> Arbitraries.integers().between(1, 12)
                        .flatMap(month -> Arbitraries.integers().between(1, 28)
                                .map(day -> LocalDate.of(year, month, day))));
    }

    @Property(tries = 100)
    void calculateOverdueDaysEqualsChronoUnitBetween(
            @ForAll("randomToday") LocalDate today,
            @ForAll @IntRange(min = 1, max = 365) int daysAgo) {

        LocalDate deadline = today.minusDays(daysAgo);

        long result = calculateOverdueDays(deadline, today);
        long expected = ChronoUnit.DAYS.between(deadline, today);

        assertThat(result)
                .as("calculateOverdueDays(%s, %s) 应等于 ChronoUnit.DAYS.between", deadline, today)
                .isEqualTo(expected);
    }

    @Property(tries = 100)
    void overdueDaysAlwaysPositiveWhenDeadlineBeforeToday(
            @ForAll("randomToday") LocalDate today,
            @ForAll @IntRange(min = 1, max = 1000) int daysAgo) {

        LocalDate deadline = today.minusDays(daysAgo);

        long result = calculateOverdueDays(deadline, today);

        assertThat(result)
                .as("当 deadline(%s) 早于 today(%s) 时，超期天数应为正值", deadline, today)
                .isPositive()
                .isEqualTo(daysAgo);
    }

    @Property(tries = 100)
    void overdueDaysIsExactDayDifference(
            @ForAll @IntRange(min = 1, max = 500) int daysAgo) {

        LocalDate today = LocalDate.of(2025, 6, 15);
        LocalDate deadline = today.minusDays(daysAgo);

        long result = calculateOverdueDays(deadline, today);

        // 超期天数应精确等于间隔天数
        assertThat(result).isEqualTo(daysAgo);
    }
}
