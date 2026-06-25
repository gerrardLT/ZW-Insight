package com.zwinsight.site.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.site.domain.BizReminderLog;
import com.zwinsight.site.mapper.BizReminderLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * 催办去重服务实现
 * <p>
 * 使用 Redis 记录上次催办时间，通过比较当前日期与上次催办日期的间隔来判断是否应发送。
 * Redis 不可用时降级为查询 biz_reminder_log 表最新记录时间。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderDeduplicationServiceImpl implements ReminderDeduplicationService {

    private final StringRedisTemplate stringRedisTemplate;
    private final BizReminderLogMapper reminderLogMapper;

    /**
     * Redis Key 前缀
     */
    private static final String REDIS_KEY_PREFIX = "rectification:reminder:last:";

    /**
     * Redis TTL：90天（超过 longOverdueDays 自然过期）
     */
    private static final long REDIS_TTL_DAYS = 90;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public boolean shouldSend(Long inspectionId, LocalDate today, int intervalDays) {
        LocalDate lastSentDate = getLastSentDate(inspectionId);
        if (lastSentDate == null) {
            // 从未发送过催办，应该发送
            return true;
        }
        long daysSinceLastSent = ChronoUnit.DAYS.between(lastSentDate, today);
        return daysSinceLastSent >= intervalDays;
    }

    @Override
    public void markSent(Long inspectionId, LocalDate today) {
        String key = buildRedisKey(inspectionId);
        String value = today.format(DATE_FORMATTER);
        try {
            stringRedisTemplate.opsForValue().set(key, value, REDIS_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("Redis 写入催办标记失败, inspectionId={}, 将依赖 DB 日志记录", inspectionId, e);
        }
    }

    @Override
    public void clearMarks(Long inspectionId) {
        String key = buildRedisKey(inspectionId);
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Redis 清除催办标记失败, inspectionId={}", inspectionId, e);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 获取上次催办日期
     * <p>优先从 Redis 获取，Redis 不可用时降级查询 biz_reminder_log 表</p>
     */
    private LocalDate getLastSentDate(Long inspectionId) {
        try {
            String key = buildRedisKey(inspectionId);
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                return LocalDate.parse(value, DATE_FORMATTER);
            }
            // Redis 中无记录，可能是首次或 key 已过期
            return null;
        } catch (Exception e) {
            log.warn("Redis 不可用, 降级查询 DB 获取上次催办时间, inspectionId={}", inspectionId, e);
            return getLastSentDateFromDb(inspectionId);
        }
    }

    /**
     * 降级：从 biz_reminder_log 表查询最新发送时间
     */
    private LocalDate getLastSentDateFromDb(Long inspectionId) {
        LambdaQueryWrapper<BizReminderLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizReminderLog::getInspectionId, inspectionId)
                .eq(BizReminderLog::getSendStatus, "SENT")
                .orderByDesc(BizReminderLog::getSentAt)
                .last("LIMIT 1");
        BizReminderLog latestLog = reminderLogMapper.selectOne(wrapper);
        if (latestLog != null && latestLog.getSentAt() != null) {
            return latestLog.getSentAt().toLocalDate();
        }
        return null;
    }

    /**
     * 构建 Redis Key
     */
    private String buildRedisKey(Long inspectionId) {
        return REDIS_KEY_PREFIX + inspectionId;
    }
}
