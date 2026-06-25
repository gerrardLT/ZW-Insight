package com.zwinsight.site.service;

import java.time.LocalDate;

/**
 * 催办去重服务
 * <p>
 * 基于 Redis 频率控制，防止对同一整改记录重复发送催办通知。
 * Redis 不可用时降级为查询 biz_reminder_log 表最新记录时间。
 * </p>
 */
public interface ReminderDeduplicationService {

    /**
     * 判断是否应该发送催办
     *
     * @param inspectionId 检查记录ID
     * @param today        当前日期
     * @param intervalDays 催办间隔天数
     * @return true-应发送, false-应跳过（未达间隔）
     */
    boolean shouldSend(Long inspectionId, LocalDate today, int intervalDays);

    /**
     * 标记已发送催办
     *
     * @param inspectionId 检查记录ID
     * @param today        发送日期
     */
    void markSent(Long inspectionId, LocalDate today);

    /**
     * 清除催办标记（整改完成时调用）
     *
     * @param inspectionId 检查记录ID
     */
    void clearMarks(Long inspectionId);
}
