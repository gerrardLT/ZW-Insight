package com.zwinsight.contract.service;

import com.zwinsight.contract.dto.ContractExpiryDTO;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.assertj.core.api.Assertions;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Property 8: 合同到期通知级别判断
 * <p>
 * For any endDate and today combination:
 * - If contract status is CLOSED/SETTLED/TERMINATED → no notification (shouldSkip = true)
 * - If remainingDays ≤ 0 or > 30 → null
 * - If remainingDays in (0, 7] → "URGENT"
 * - If remainingDays in (7, 30] → "UPCOMING"
 * </p>
 * <p>
 * <b>Validates: Requirements 5.2, 5.3, 5.7</b>
 * </p>
 */
@Tag("Feature: p1-business-completion, Property 8: 合同到期通知级别判断")
class ContractExpiryLevelPropertyTest {

    private final ContractExpiryService service = new ContractExpiryService(null, null, null, null);

    // ========== determineLevel 属性测试 ==========

    @Property(tries = 100)
    @Label("剩余天数 ≤ 0 时不产生通知（返回 null）")
    void expiredOrTodayContracts_returnNull(
            @ForAll("pastOrTodayEndDates") LocalDate endDate) {
        LocalDate today = endDate; // endDate == today → remainingDays = 0
        String level = service.determineLevel(endDate, today);
        Assertions.assertThat(level).isNull();
    }

    @Property(tries = 100)
    @Label("剩余天数在 (0, 7] 范围内时级别为 URGENT")
    void urgentRange_returnsUrgent(
            @ForAll @IntRange(min = 1, max = 7) int remainingDays) {
        LocalDate today = LocalDate.of(2025, 1, 1);
        LocalDate endDate = today.plusDays(remainingDays);

        String level = service.determineLevel(endDate, today);
        Assertions.assertThat(level).isEqualTo(ContractExpiryService.LEVEL_URGENT);
    }

    @Property(tries = 100)
    @Label("剩余天数在 (7, 30] 范围内时级别为 UPCOMING")
    void upcomingRange_returnsUpcoming(
            @ForAll @IntRange(min = 8, max = 30) int remainingDays) {
        LocalDate today = LocalDate.of(2025, 1, 1);
        LocalDate endDate = today.plusDays(remainingDays);

        String level = service.determineLevel(endDate, today);
        Assertions.assertThat(level).isEqualTo(ContractExpiryService.LEVEL_UPCOMING);
    }

    @Property(tries = 100)
    @Label("剩余天数 > 30 时不产生通知（返回 null）")
    void farFutureContracts_returnNull(
            @ForAll @IntRange(min = 31, max = 3650) int remainingDays) {
        LocalDate today = LocalDate.of(2025, 1, 1);
        LocalDate endDate = today.plusDays(remainingDays);

        String level = service.determineLevel(endDate, today);
        Assertions.assertThat(level).isNull();
    }

    @Property(tries = 100)
    @Label("随机日期组合：determineLevel 结果与剩余天数区间一致")
    void randomDates_levelMatchesRemainingDaysRange(
            @ForAll("randomToday") LocalDate today,
            @ForAll @IntRange(min = -365, max = 365) int offset) {
        LocalDate endDate = today.plusDays(offset);
        long remainingDays = ChronoUnit.DAYS.between(today, endDate);

        String level = service.determineLevel(endDate, today);

        if (remainingDays <= 0) {
            Assertions.assertThat(level).isNull();
        } else if (remainingDays <= 7) {
            Assertions.assertThat(level).isEqualTo(ContractExpiryService.LEVEL_URGENT);
        } else if (remainingDays <= 30) {
            Assertions.assertThat(level).isEqualTo(ContractExpiryService.LEVEL_UPCOMING);
        } else {
            Assertions.assertThat(level).isNull();
        }
    }

    // ========== shouldSkip 属性测试 ==========

    @Property(tries = 100)
    @Label("CLOSED/SETTLED/TERMINATED 状态的合同应跳过（不产生通知）")
    void closedSettledTerminated_shouldSkip(
            @ForAll("skipStatuses") String status) {
        ContractExpiryDTO contract = new ContractExpiryDTO();
        contract.setStatus(status);

        boolean result = service.shouldSkip(contract);
        Assertions.assertThat(result).isTrue();
    }

    @Property(tries = 100)
    @Label("非终止状态的合同不应跳过")
    void activeStatuses_shouldNotSkip(
            @ForAll("activeStatuses") String status) {
        ContractExpiryDTO contract = new ContractExpiryDTO();
        contract.setStatus(status);

        boolean result = service.shouldSkip(contract);
        Assertions.assertThat(result).isFalse();
    }

    // ========== Arbitraries (数据提供器) ==========

    @Provide
    Arbitrary<LocalDate> pastOrTodayEndDates() {
        return Arbitraries.integers()
                .between(0, 3650)
                .map(daysAgo -> LocalDate.of(2025, 6, 15).minusDays(daysAgo));
    }

    @Provide
    Arbitrary<LocalDate> randomToday() {
        return Arbitraries.integers()
                .between(0, 3650)
                .map(offset -> LocalDate.of(2020, 1, 1).plusDays(offset));
    }

    @Provide
    Arbitrary<String> skipStatuses() {
        return Arbitraries.of("CLOSED", "SETTLED", "TERMINATED");
    }

    @Provide
    Arbitrary<String> activeStatuses() {
        return Arbitraries.of("ACTIVE", "EFFECTIVE", "DRAFT", "PENDING", "APPROVED");
    }
}
