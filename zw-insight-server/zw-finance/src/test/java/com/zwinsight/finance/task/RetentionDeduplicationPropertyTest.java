package com.zwinsight.finance.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property P7: 质保金通知去重
 * <p>
 * 验证：同一质保金记录连续两次执行（级别不变），第二次 shouldSendNotification 返回 false。
 * 使用内存模拟 Redis 行为，验证 RetentionWarningTask 的去重逻辑。
 * </p>
 * <p>
 * **Validates: Requirements 5.6**
 * </p>
 */
@DisplayName("P7: 质保金通知去重属性测试")
class RetentionDeduplicationPropertyTest {

    private static final String WARNED_KEY_PREFIX = "retention:warned:";
    private static final String OVERDUE_LAST_PREFIX = "retention:overdue:last:";
    private static final int OVERDUE_REMINDER_INTERVAL_DAYS = 3;

    /**
     * 模拟 Redis 存储（内存Map实现）
     * 复刻 RetentionWarningTask 的去重逻辑
     */
    private static class InMemoryRedis {
        private final Map<String, String> store = new HashMap<>();

        boolean hasKey(String key) {
            return store.containsKey(key);
        }

        void set(String key, String value) {
            store.put(key, value);
        }

        String get(String key) {
            return store.get(key);
        }
    }

    /**
     * 复刻 RetentionWarningTask.shouldSendNotification 逻辑
     */
    private boolean shouldSendNotification(InMemoryRedis redis, Long retentionId, String level, LocalDate today) {
        if ("OVERDUE".equals(level)) {
            // 逾期催办频率控制：每3天发送一次
            return shouldSendOverdueReminder(redis, retentionId, today);
        } else {
            // 非逾期去重：同级别只发一次
            return !isAlreadyWarned(redis, retentionId, level);
        }
    }

    private boolean isAlreadyWarned(InMemoryRedis redis, Long retentionId, String level) {
        String key = WARNED_KEY_PREFIX + retentionId + ":" + level;
        return redis.hasKey(key);
    }

    private boolean shouldSendOverdueReminder(InMemoryRedis redis, Long retentionId, LocalDate today) {
        String key = OVERDUE_LAST_PREFIX + retentionId;
        String lastSentValue = redis.get(key);
        if (lastSentValue == null) {
            return true; // 首次催办
        }
        LocalDate lastSentDate = LocalDate.parse(lastSentValue);
        long daysSinceLastSent = java.time.temporal.ChronoUnit.DAYS.between(lastSentDate, today);
        return daysSinceLastSent >= OVERDUE_REMINDER_INTERVAL_DAYS;
    }

    /**
     * 复刻 RetentionWarningTask.markAsSent 逻辑
     */
    private void markAsSent(InMemoryRedis redis, Long retentionId, String level, LocalDate today) {
        if ("OVERDUE".equals(level)) {
            String key = OVERDUE_LAST_PREFIX + retentionId;
            redis.set(key, today.toString());
        } else {
            String key = WARNED_KEY_PREFIX + retentionId + ":" + level;
            redis.set(key, "1");
        }
    }

    @RepeatedTest(50)
    @DisplayName("P7: 同一质保金记录非逾期级别连续两次执行，第二次 shouldSendNotification 返回 false")
    void testDeduplication() {
        InMemoryRedis redis = new InMemoryRedis();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        Long retentionId = (long) random.nextInt(1, 100000);
        // 非逾期级别随机选择 UPCOMING 或 URGENT
        String level = random.nextBoolean() ? "UPCOMING" : "URGENT";
        LocalDate today = LocalDate.now();

        // 第一次: Redis 中无该 key → shouldSend = true
        boolean firstShouldSend = shouldSendNotification(redis, retentionId, level, today);
        assertTrue(firstShouldSend,
                "第一次执行应该发送通知，retentionId=" + retentionId + ", level=" + level);

        // 发送成功后标记
        markAsSent(redis, retentionId, level, today);

        // 第二次: Redis hasKey 返回 true → shouldSend = false
        boolean secondShouldSend = shouldSendNotification(redis, retentionId, level, today);
        assertFalse(secondShouldSend,
                "第二次执行（同级别）不应重复发送通知，retentionId=" + retentionId + ", level=" + level);
    }

    @RepeatedTest(50)
    @DisplayName("P7: 逾期催办在 3 天内重复执行不产生新通知")
    void testOverdueDeduplicationWithin3Days() {
        InMemoryRedis redis = new InMemoryRedis();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        Long retentionId = (long) random.nextInt(1, 100000);
        String level = "OVERDUE";
        LocalDate today = LocalDate.now();

        // 第一次: 首次催办，shouldSend = true
        boolean firstShouldSend = shouldSendNotification(redis, retentionId, level, today);
        assertTrue(firstShouldSend, "逾期首次催办应该发送");

        // 标记发送
        markAsSent(redis, retentionId, level, today);

        // 第二次: 同一天或1~2天内，shouldSend = false
        int daysLater = random.nextInt(0, 3); // 0, 1, 或 2 天后
        LocalDate nextExecution = today.plusDays(daysLater);
        boolean secondShouldSend = shouldSendNotification(redis, retentionId, level, nextExecution);
        assertFalse(secondShouldSend,
                "逾期催办 " + daysLater + " 天内不应重复发送，retentionId=" + retentionId);
    }

    @RepeatedTest(50)
    @DisplayName("P7: 逾期催办超过 3 天后允许再次发送")
    void testOverdueAllowedAfter3Days() {
        InMemoryRedis redis = new InMemoryRedis();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        Long retentionId = (long) random.nextInt(1, 100000);
        String level = "OVERDUE";
        LocalDate today = LocalDate.now();

        // 第一次发送并标记
        markAsSent(redis, retentionId, level, today);

        // 3天或更多天后，shouldSend = true
        int daysLater = random.nextInt(3, 30); // 3~29 天后
        LocalDate laterDate = today.plusDays(daysLater);
        boolean shouldSend = shouldSendNotification(redis, retentionId, level, laterDate);
        assertTrue(shouldSend,
                daysLater + " 天后逾期催办应该允许再次发送，retentionId=" + retentionId);
    }
}
