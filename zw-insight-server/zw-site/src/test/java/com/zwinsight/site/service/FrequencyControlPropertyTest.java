package com.zwinsight.site.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.assertj.core.api.Assertions;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

// Feature: p0-data-permission-overdue, Property 12: 催办频率控制
/**
 * Property 12: 催办频率控制
 * <p>
 * For any 整改记录和 lastSentDate，当 today - lastSentDate < intervalDays 时，
 * shouldSend 返回 false（跳过）；当 today - lastSentDate >= intervalDays 时返回 true（发送）。
 * </p>
 * <p>
 * **Validates: Requirements 9.2**
 * </p>
 */
@Tag("Feature: p0-data-permission-overdue, Property 12: 催办频率控制")
class FrequencyControlPropertyTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * 构建一个使用 Mock Redis 的 ReminderDeduplicationServiceImpl 实例，
     * Redis 中存储了指定的 lastSentDate。
     */
    private ReminderDeduplicationServiceImpl buildServiceWithLastSentDate(Long inspectionId, LocalDate lastSentDate) {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);

        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);

        String key = "rectification:reminder:last:" + inspectionId;
        if (lastSentDate != null) {
            Mockito.when(valueOps.get(key)).thenReturn(lastSentDate.format(DATE_FORMATTER));
        } else {
            Mockito.when(valueOps.get(key)).thenReturn(null);
        }

        // BizReminderLogMapper 不会被调用（Redis 正常场景）
        return new ReminderDeduplicationServiceImpl(redisTemplate, null);
    }

    @Property(tries = 100)
    void shouldSend_returnsFalse_whenIntervalNotReached(
            @ForAll("inspectionIds") Long inspectionId,
            @ForAll @IntRange(min = 1, max = 30) int intervalDays,
            @ForAll @IntRange(min = 0, max = 29) int daysSinceLastSent) {

        // 保证 daysSinceLastSent < intervalDays
        int actualDaysSinceLastSent = daysSinceLastSent % intervalDays; // [0, intervalDays-1]

        LocalDate today = LocalDate.of(2025, 6, 25);
        LocalDate lastSentDate = today.minusDays(actualDaysSinceLastSent);

        ReminderDeduplicationServiceImpl service = buildServiceWithLastSentDate(inspectionId, lastSentDate);

        boolean result = service.shouldSend(inspectionId, today, intervalDays);

        Assertions.assertThat(result)
                .as("距离上次催办 %d 天 < intervalDays %d，应跳过", actualDaysSinceLastSent, intervalDays)
                .isFalse();
    }

    @Property(tries = 100)
    void shouldSend_returnsTrue_whenIntervalReached(
            @ForAll("inspectionIds") Long inspectionId,
            @ForAll @IntRange(min = 1, max = 30) int intervalDays,
            @ForAll @IntRange(min = 0, max = 60) int extraDays) {

        // daysSinceLastSent = intervalDays + extraDays >= intervalDays
        int daysSinceLastSent = intervalDays + extraDays;

        LocalDate today = LocalDate.of(2025, 6, 25);
        LocalDate lastSentDate = today.minusDays(daysSinceLastSent);

        ReminderDeduplicationServiceImpl service = buildServiceWithLastSentDate(inspectionId, lastSentDate);

        boolean result = service.shouldSend(inspectionId, today, intervalDays);

        Assertions.assertThat(result)
                .as("距离上次催办 %d 天 >= intervalDays %d，应发送", daysSinceLastSent, intervalDays)
                .isTrue();
    }

    @Property(tries = 100)
    void shouldSend_returnsTrue_whenNeverSentBefore(
            @ForAll("inspectionIds") Long inspectionId,
            @ForAll @IntRange(min = 1, max = 30) int intervalDays) {

        LocalDate today = LocalDate.of(2025, 6, 25);

        // lastSentDate 为 null 表示从未发送过
        ReminderDeduplicationServiceImpl service = buildServiceWithLastSentDate(inspectionId, null);

        boolean result = service.shouldSend(inspectionId, today, intervalDays);

        Assertions.assertThat(result)
                .as("从未发送过催办，应返回 true")
                .isTrue();
    }

    @Property(tries = 100)
    void shouldSend_exactlyAtBoundary_returnsTrue(
            @ForAll("inspectionIds") Long inspectionId,
            @ForAll @IntRange(min = 1, max = 30) int intervalDays) {

        // 恰好等于 intervalDays 天时应该发送
        LocalDate today = LocalDate.of(2025, 6, 25);
        LocalDate lastSentDate = today.minusDays(intervalDays);

        ReminderDeduplicationServiceImpl service = buildServiceWithLastSentDate(inspectionId, lastSentDate);

        boolean result = service.shouldSend(inspectionId, today, intervalDays);

        // ChronoUnit.DAYS.between(lastSentDate, today) == intervalDays, 应返回 true
        long actualDays = ChronoUnit.DAYS.between(lastSentDate, today);
        Assertions.assertThat(actualDays).isEqualTo(intervalDays);
        Assertions.assertThat(result)
                .as("恰好达到间隔 %d 天，应返回 true", intervalDays)
                .isTrue();
    }

    @Provide
    Arbitrary<Long> inspectionIds() {
        return Arbitraries.longs().between(1L, 999999L);
    }
}
