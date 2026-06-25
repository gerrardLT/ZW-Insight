package com.zwinsight.pbt;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.assertj.core.api.Assertions;

import java.time.LocalDate;

/**
 * Property 15：租户续期日期计算
 * <p>
 * 验证：newEndDate = originalEndDate.plusDays(days)
 * 续期天数范围为 1-1095。
 * <p>
 * Validates: Requirements 7.4
 */
@Tag("Feature: p1-system-integrity, Property 15: 租户续期日期计算")
class TenantRenewalPropertyTest {

    /**
     * 核心业务逻辑：计算续期后的到期日期
     *
     * @param originalEndDate 原始到期日期
     * @param renewalDays     续期天数（1-1095）
     * @return 新的到期日期
     */
    static LocalDate calculateRenewalEndDate(LocalDate originalEndDate, int renewalDays) {
        if (renewalDays < 1 || renewalDays > 1095) {
            throw new IllegalArgumentException("续期天数必须在 1-1095 之间");
        }
        return originalEndDate.plusDays(renewalDays);
    }

    @Property(tries = 100)
    void renewalEndDate_equalsOriginalPlusDays(
            @ForAll("validDates") LocalDate originalEndDate,
            @ForAll @IntRange(min = 1, max = 1095) int renewalDays) {
        LocalDate newEndDate = calculateRenewalEndDate(originalEndDate, renewalDays);
        LocalDate expected = originalEndDate.plusDays(renewalDays);
        Assertions.assertThat(newEndDate).isEqualTo(expected);
    }

    @Property(tries = 100)
    void renewalEndDate_isAlwaysAfterOriginal(
            @ForAll("validDates") LocalDate originalEndDate,
            @ForAll @IntRange(min = 1, max = 1095) int renewalDays) {
        LocalDate newEndDate = calculateRenewalEndDate(originalEndDate, renewalDays);
        Assertions.assertThat(newEndDate).isAfter(originalEndDate);
    }

    @Property(tries = 100)
    void renewalEndDate_differenceDaysMatchesInput(
            @ForAll("validDates") LocalDate originalEndDate,
            @ForAll @IntRange(min = 1, max = 1095) int renewalDays) {
        LocalDate newEndDate = calculateRenewalEndDate(originalEndDate, renewalDays);
        long diff = java.time.temporal.ChronoUnit.DAYS.between(originalEndDate, newEndDate);
        Assertions.assertThat(diff).isEqualTo(renewalDays);
    }

    @Property(tries = 100)
    void multipleRenewals_areAdditive(
            @ForAll("validDates") LocalDate originalEndDate,
            @ForAll @IntRange(min = 1, max = 500) int days1,
            @ForAll @IntRange(min = 1, max = 500) int days2) {
        LocalDate afterFirst = calculateRenewalEndDate(originalEndDate, days1);
        LocalDate afterSecond = calculateRenewalEndDate(afterFirst, days2);
        LocalDate combined = originalEndDate.plusDays(days1 + days2);
        Assertions.assertThat(afterSecond).isEqualTo(combined);
    }

    @Provide
    Arbitrary<LocalDate> validDates() {
        return Arbitraries.integers().between(2020, 2030)
                .flatMap(year -> Arbitraries.integers().between(1, 12)
                        .flatMap(month -> Arbitraries.integers().between(1, 28)
                                .map(day -> LocalDate.of(year, month, day))));
    }
}
