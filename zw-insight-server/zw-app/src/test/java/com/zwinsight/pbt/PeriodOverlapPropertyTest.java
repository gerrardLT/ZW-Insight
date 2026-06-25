package com.zwinsight.pbt;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.assertj.core.api.Assertions;

import java.time.LocalDate;

/**
 * Property 10：结算周期重叠检测
 * <p>
 * 验证：两个日期范围重叠 iff start1 <= end2 AND start2 <= end1
 * <p>
 * Validates: Requirements 4.6
 */
@Tag("Feature: p1-system-integrity, Property 10: 结算周期重叠检测")
class PeriodOverlapPropertyTest {

    /**
     * 核心业务逻辑：检测两个周期是否重叠
     * 重叠条件：start1 <= end2 AND start2 <= end1
     */
    static boolean isOverlapping(LocalDate start1, LocalDate end1,
                                  LocalDate start2, LocalDate end2) {
        return !start1.isAfter(end2) && !start2.isAfter(end1);
    }

    @Property(tries = 100)
    void samePeriod_alwaysOverlaps(
            @ForAll("validDates") LocalDate start,
            @ForAll @IntRange(min = 0, max = 365) int daysToAdd) {
        LocalDate end = start.plusDays(daysToAdd);
        Assertions.assertThat(isOverlapping(start, end, start, end)).isTrue();
    }

    @Property(tries = 100)
    void overlap_isSymmetric(
            @ForAll("dateRange") LocalDate[] range1,
            @ForAll("dateRange") LocalDate[] range2) {
        boolean overlap12 = isOverlapping(range1[0], range1[1], range2[0], range2[1]);
        boolean overlap21 = isOverlapping(range2[0], range2[1], range1[0], range1[1]);
        Assertions.assertThat(overlap12).isEqualTo(overlap21);
    }

    @Property(tries = 100)
    void nonOverlapping_whenFirstEndsBeforeSecondStarts(
            @ForAll("validDates") LocalDate start1,
            @ForAll @IntRange(min = 1, max = 30) int duration1,
            @ForAll @IntRange(min = 1, max = 30) int gap,
            @ForAll @IntRange(min = 1, max = 30) int duration2) {
        LocalDate end1 = start1.plusDays(duration1);
        LocalDate start2 = end1.plusDays(gap); // gap > 0 确保不重叠
        LocalDate end2 = start2.plusDays(duration2);

        Assertions.assertThat(isOverlapping(start1, end1, start2, end2)).isFalse();
    }

    @Property(tries = 100)
    void overlapping_whenPeriodsShareADay(
            @ForAll("validDates") LocalDate start1,
            @ForAll @IntRange(min = 1, max = 30) int duration1) {
        LocalDate end1 = start1.plusDays(duration1);
        // start2 = end1，即恰好共享一天边界
        LocalDate start2 = end1;
        LocalDate end2 = start2.plusDays(10);

        Assertions.assertThat(isOverlapping(start1, end1, start2, end2)).isTrue();
    }

    @Provide
    Arbitrary<LocalDate> validDates() {
        return Arbitraries.integers().between(2020, 2030)
                .flatMap(year -> Arbitraries.integers().between(1, 12)
                        .flatMap(month -> Arbitraries.integers().between(1, 28)
                                .map(day -> LocalDate.of(year, month, day))));
    }

    @Provide
    Arbitrary<LocalDate[]> dateRange() {
        return validDates().flatMap(start ->
                Arbitraries.integers().between(1, 90)
                        .map(days -> new LocalDate[]{start, start.plusDays(days)}));
    }
}
