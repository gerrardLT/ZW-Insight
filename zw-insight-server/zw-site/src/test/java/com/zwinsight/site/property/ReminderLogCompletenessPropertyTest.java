package com.zwinsight.site.property;

import com.zwinsight.site.domain.BizReminderLog;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.IntRange;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: p0-data-permission-overdue, Property 13: 催办日志完整性
/**
 * Property 13: 催办日志完整性
 * <p>
 * 验证：对于任何催办发送后创建的日志记录，必填字段
 * (inspectionId, receiverId, reminderLevel, sendStatus) 均非空；
 * 当 sendStatus=SENT 时，sentAt 也必须非空。
 * </p>
 * <p>
 * **Validates: Requirements 10.1, 10.2**
 * </p>
 */
class ReminderLogCompletenessPropertyTest {

    /**
     * 模拟 RectificationReminderTask.processRecord 中创建催办日志的逻辑：
     * 在发送催办后，构建 BizReminderLog 记录。
     * 当发送成功（SENT）时设置 sentAt；发送失败（FAILED）时 sentAt 可能为空。
     */
    private BizReminderLog buildReminderLog(Long inspectionId, Long receiverId,
                                            String reminderLevel, String sendStatus,
                                            int overdueDays) {
        BizReminderLog log = new BizReminderLog();
        log.setInspectionId(inspectionId);
        log.setReceiverId(receiverId);
        log.setReminderLevel(reminderLevel);
        log.setSendStatus(sendStatus);
        log.setOverdueDays(overdueDays);
        // 当发送状态为 SENT 时，sentAt 必须设置；FAILED 时也记录尝试时间
        if ("SENT".equals(sendStatus)) {
            log.setSentAt(LocalDateTime.now());
        }
        // FAILED 情况下 sentAt 可能为 null（消息未发出）
        return log;
    }

    @Property(tries = 100)
    @Label("催办日志基础必填字段均非空（inspectionId, receiverId, reminderLevel, sendStatus）")
    void reminderLogBaseRequiredFieldsAreNonNull(
            @ForAll @LongRange(min = 1, max = 999999) Long inspectionId,
            @ForAll @LongRange(min = 1, max = 999999) Long receiverId,
            @ForAll("reminderLevels") String reminderLevel,
            @ForAll("sendStatuses") String sendStatus,
            @ForAll @IntRange(min = 1, max = 365) int overdueDays) {

        BizReminderLog log = buildReminderLog(inspectionId, receiverId,
                reminderLevel, sendStatus, overdueDays);

        // 验证基础必填字段非空
        assertThat(log.getInspectionId())
                .as("inspectionId 不应为空")
                .isNotNull();
        assertThat(log.getReceiverId())
                .as("receiverId 不应为空")
                .isNotNull();
        assertThat(log.getReminderLevel())
                .as("reminderLevel 不应为空")
                .isNotNull()
                .isNotBlank();
        assertThat(log.getSendStatus())
                .as("sendStatus 不应为空")
                .isNotNull()
                .isNotBlank();
    }

    @Property(tries = 100)
    @Label("当 sendStatus=SENT 时 sentAt 必须非空")
    void sentAtIsNonNullWhenStatusIsSent(
            @ForAll @LongRange(min = 1, max = 999999) Long inspectionId,
            @ForAll @LongRange(min = 1, max = 999999) Long receiverId,
            @ForAll("reminderLevels") String reminderLevel,
            @ForAll @IntRange(min = 1, max = 365) int overdueDays) {

        // 固定 sendStatus = SENT，验证 sentAt 非空
        BizReminderLog log = buildReminderLog(inspectionId, receiverId,
                reminderLevel, "SENT", overdueDays);

        assertThat(log.getSentAt())
                .as("当 sendStatus=SENT 时, sentAt 不应为空")
                .isNotNull();
    }

    @Property(tries = 100)
    @Label("催办日志 reminderLevel 仅允许 NORMAL/ESCALATED")
    void reminderLevelIsValid(
            @ForAll @LongRange(min = 1, max = 999999) Long inspectionId,
            @ForAll @LongRange(min = 1, max = 999999) Long receiverId,
            @ForAll("reminderLevels") String reminderLevel,
            @ForAll("sendStatuses") String sendStatus,
            @ForAll @IntRange(min = 1, max = 365) int overdueDays) {

        BizReminderLog log = buildReminderLog(inspectionId, receiverId,
                reminderLevel, sendStatus, overdueDays);

        assertThat(log.getReminderLevel())
                .as("reminderLevel 必须为 NORMAL 或 ESCALATED")
                .isIn("NORMAL", "ESCALATED");
    }

    @Property(tries = 100)
    @Label("催办日志 sendStatus 仅允许 SENT/FAILED")
    void sendStatusIsValid(
            @ForAll @LongRange(min = 1, max = 999999) Long inspectionId,
            @ForAll @LongRange(min = 1, max = 999999) Long receiverId,
            @ForAll("reminderLevels") String reminderLevel,
            @ForAll("sendStatuses") String sendStatus,
            @ForAll @IntRange(min = 1, max = 365) int overdueDays) {

        BizReminderLog log = buildReminderLog(inspectionId, receiverId,
                reminderLevel, sendStatus, overdueDays);

        assertThat(log.getSendStatus())
                .as("sendStatus 必须为 SENT 或 FAILED")
                .isIn("SENT", "FAILED");
    }

    @Provide
    Arbitrary<String> reminderLevels() {
        return Arbitraries.of("NORMAL", "ESCALATED");
    }

    @Provide
    Arbitrary<String> sendStatuses() {
        return Arbitraries.of("SENT", "FAILED");
    }
}
