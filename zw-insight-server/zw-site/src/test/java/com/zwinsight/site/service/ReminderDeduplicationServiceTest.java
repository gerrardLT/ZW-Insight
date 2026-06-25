package com.zwinsight.site.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.site.domain.BizReminderLog;
import com.zwinsight.site.mapper.BizReminderLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Feature: p0-data-permission-overdue
/**
 * ReminderDeduplicationService 单元测试
 * <p>
 * 测试 Redis 正常场景：shouldSend 判断、markSent 写入、clearMarks 删除
 * 测试 Redis 不可用场景：降级为 DB 查询
 * </p>
 * <p>
 * Validates: Requirements 9.1, 9.2, 9.5
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReminderDeduplicationService 单元测试")
class ReminderDeduplicationServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private BizReminderLogMapper reminderLogMapper;

    private ReminderDeduplicationServiceImpl service;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String KEY_PREFIX = "rectification:reminder:last:";

    @BeforeEach
    void setUp() {
        service = new ReminderDeduplicationServiceImpl(stringRedisTemplate, reminderLogMapper);
    }

    @Nested
    @DisplayName("Redis 正常场景")
    class RedisNormalScenario {

        @Test
        @DisplayName("shouldSend: Redis 有记录且未达间隔 → 返回 false")
        void shouldSend_notReachedInterval_returnsFalse() {
            Long inspectionId = 1001L;
            LocalDate today = LocalDate.of(2025, 6, 25);
            int intervalDays = 3;
            LocalDate lastSentDate = LocalDate.of(2025, 6, 24); // 1天前

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(KEY_PREFIX + inspectionId))
                    .thenReturn(lastSentDate.format(DATE_FORMATTER));

            boolean result = service.shouldSend(inspectionId, today, intervalDays);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("shouldSend: Redis 有记录且已达间隔 → 返回 true")
        void shouldSend_reachedInterval_returnsTrue() {
            Long inspectionId = 1001L;
            LocalDate today = LocalDate.of(2025, 6, 25);
            int intervalDays = 3;
            LocalDate lastSentDate = LocalDate.of(2025, 6, 22); // 3天前

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(KEY_PREFIX + inspectionId))
                    .thenReturn(lastSentDate.format(DATE_FORMATTER));

            boolean result = service.shouldSend(inspectionId, today, intervalDays);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("shouldSend: Redis 无记录（从未催办）→ 返回 true")
        void shouldSend_neverSent_returnsTrue() {
            Long inspectionId = 1001L;
            LocalDate today = LocalDate.of(2025, 6, 25);
            int intervalDays = 3;

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(KEY_PREFIX + inspectionId)).thenReturn(null);

            boolean result = service.shouldSend(inspectionId, today, intervalDays);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("shouldSend: 恰好达到间隔天数（边界值）→ 返回 true")
        void shouldSend_exactlyAtInterval_returnsTrue() {
            Long inspectionId = 2002L;
            LocalDate today = LocalDate.of(2025, 6, 25);
            int intervalDays = 5;
            LocalDate lastSentDate = LocalDate.of(2025, 6, 20); // 恰好5天前

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(KEY_PREFIX + inspectionId))
                    .thenReturn(lastSentDate.format(DATE_FORMATTER));

            boolean result = service.shouldSend(inspectionId, today, intervalDays);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("markSent: 正确写入 Redis Key 和 TTL")
        void markSent_writesToRedisWithCorrectKeyAndTtl() {
            Long inspectionId = 1001L;
            LocalDate today = LocalDate.of(2025, 6, 25);

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

            service.markSent(inspectionId, today);

            verify(valueOperations).set(
                    eq(KEY_PREFIX + inspectionId),
                    eq(today.format(DATE_FORMATTER)),
                    eq(90L),
                    eq(TimeUnit.DAYS)
            );
        }

        @Test
        @DisplayName("clearMarks: 删除对应的 Redis Key")
        void clearMarks_deletesRedisKey() {
            Long inspectionId = 1001L;

            when(stringRedisTemplate.delete(KEY_PREFIX + inspectionId)).thenReturn(true);

            service.clearMarks(inspectionId);

            verify(stringRedisTemplate).delete(KEY_PREFIX + inspectionId);
        }
    }

    @Nested
    @DisplayName("Redis 不可用降级场景")
    class RedisFallbackScenario {

        @Test
        @DisplayName("shouldSend: Redis 异常时降级查询 DB，DB 有记录且未达间隔 → 返回 false")
        void shouldSend_redisFails_fallbackToDb_notReachedInterval() {
            Long inspectionId = 3003L;
            LocalDate today = LocalDate.of(2025, 6, 25);
            int intervalDays = 3;

            // Redis 抛出异常
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString()))
                    .thenThrow(new RedisConnectionFailureException("Connection refused"));

            // DB 返回最近一次催办记录（2天前）
            BizReminderLog latestLog = new BizReminderLog();
            latestLog.setSentAt(LocalDateTime.of(2025, 6, 23, 8, 0, 0)); // 2天前
            latestLog.setSendStatus("SENT");
            when(reminderLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(latestLog);

            boolean result = service.shouldSend(inspectionId, today, intervalDays);

            assertThat(result).isFalse();
            verify(reminderLogMapper).selectOne(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("shouldSend: Redis 异常时降级查询 DB，DB 有记录且已达间隔 → 返回 true")
        void shouldSend_redisFails_fallbackToDb_reachedInterval() {
            Long inspectionId = 3003L;
            LocalDate today = LocalDate.of(2025, 6, 25);
            int intervalDays = 3;

            // Redis 抛出异常
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString()))
                    .thenThrow(new RedisConnectionFailureException("Connection refused"));

            // DB 返回最近一次催办记录（5天前）
            BizReminderLog latestLog = new BizReminderLog();
            latestLog.setSentAt(LocalDateTime.of(2025, 6, 20, 8, 0, 0)); // 5天前
            latestLog.setSendStatus("SENT");
            when(reminderLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(latestLog);

            boolean result = service.shouldSend(inspectionId, today, intervalDays);

            assertThat(result).isTrue();
            verify(reminderLogMapper).selectOne(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("shouldSend: Redis 异常时降级查询 DB，DB 无记录 → 返回 true")
        void shouldSend_redisFails_fallbackToDb_noRecords() {
            Long inspectionId = 3003L;
            LocalDate today = LocalDate.of(2025, 6, 25);
            int intervalDays = 3;

            // Redis 抛出异常
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString()))
                    .thenThrow(new RedisConnectionFailureException("Connection refused"));

            // DB 无催办记录
            when(reminderLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            boolean result = service.shouldSend(inspectionId, today, intervalDays);

            assertThat(result).isTrue();
            verify(reminderLogMapper).selectOne(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("markSent: Redis 异常时不抛出异常（仅记录日志）")
        void markSent_redisFails_doesNotThrow() {
            Long inspectionId = 3003L;
            LocalDate today = LocalDate.of(2025, 6, 25);

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            doThrow(new RedisConnectionFailureException("Connection refused"))
                    .when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

            // 不应抛出异常
            service.markSent(inspectionId, today);

            // 验证 Redis 确实被调用了（只是失败了）
            verify(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("clearMarks: Redis 异常时不抛出异常（仅记录日志）")
        void clearMarks_redisFails_doesNotThrow() {
            Long inspectionId = 3003L;

            when(stringRedisTemplate.delete(anyString()))
                    .thenThrow(new RedisConnectionFailureException("Connection refused"));

            // 不应抛出异常
            service.clearMarks(inspectionId);

            verify(stringRedisTemplate).delete(KEY_PREFIX + inspectionId);
        }
    }
}
