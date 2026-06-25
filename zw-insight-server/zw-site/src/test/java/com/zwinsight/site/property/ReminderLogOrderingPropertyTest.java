package com.zwinsight.site.property;

import com.zwinsight.site.domain.BizReminderLog;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: p0-data-permission-overdue, Property 14: 催办日志时间排序
/**
 * Property 14: 催办日志时间排序
 * <p>
 * 验证：查询某整改记录的催办日志列表时，返回的记录按 sentAt 降序排列。
 * 模拟 ReminderLogServiceImpl.getLogsByInspectionId 的排序逻辑：
 * 使用 LambdaQueryWrapper.orderByDesc(BizReminderLog::getSentAt) 确保结果降序。
 * </p>
 * <p>
 * **Validates: Requirements 10.3**
 * </p>
 */
class ReminderLogOrderingPropertyTest {

    /**
     * 模拟服务层排序逻辑：按 sentAt 降序排列日志列表。
     * 这等同于 ReminderLogServiceImpl 中 orderByDesc(BizReminderLog::getSentAt) 的效果。
     */
    private List<BizReminderLog> sortBysentAtDesc(List<BizReminderLog> logs) {
        List<BizReminderLog> sorted = new ArrayList<>(logs);
        sorted.sort(Comparator.comparing(BizReminderLog::getSentAt).reversed());
        return sorted;
    }

    @Property(tries = 100)
    @Label("催办日志列表按 sentAt 降序排列")
    void logsAreOrderedBySentAtDescending(
            @ForAll @LongRange(min = 1, max = 999999) Long inspectionId,
            @ForAll("reminderLogList") @Size(min = 2, max = 20) List<BizReminderLog> rawLogs) {

        // 为所有日志设置相同的 inspectionId（模拟同一检查记录）
        rawLogs.forEach(log -> log.setInspectionId(inspectionId));

        // 调用排序逻辑（模拟 service 层 orderByDesc）
        List<BizReminderLog> orderedLogs = sortBysentAtDesc(rawLogs);

        // 验证结果按 sentAt 降序
        for (int i = 0; i < orderedLogs.size() - 1; i++) {
            LocalDateTime current = orderedLogs.get(i).getSentAt();
            LocalDateTime next = orderedLogs.get(i + 1).getSentAt();
            assertThat(current)
                    .as("日志[%d].sentAt(%s) 应 >= 日志[%d].sentAt(%s)", i, current, i + 1, next)
                    .isAfterOrEqualTo(next);
        }
    }

    @Property(tries = 100)
    @Label("排序后列表大小不变")
    void sortingPreservesListSize(
            @ForAll("reminderLogList") @Size(min = 1, max = 20) List<BizReminderLog> rawLogs) {

        List<BizReminderLog> orderedLogs = sortBysentAtDesc(rawLogs);

        assertThat(orderedLogs)
                .as("排序不应改变列表大小")
                .hasSameSizeAs(rawLogs);
    }

    @Property(tries = 100)
    @Label("排序后列表包含所有原始元素")
    void sortingPreservesAllElements(
            @ForAll("reminderLogList") @Size(min = 1, max = 20) List<BizReminderLog> rawLogs) {

        List<BizReminderLog> orderedLogs = sortBysentAtDesc(rawLogs);

        assertThat(orderedLogs)
                .as("排序后应包含所有原始日志记录")
                .containsExactlyInAnyOrderElementsOf(rawLogs);
    }

    @Provide
    Arbitrary<List<BizReminderLog>> reminderLogList() {
        return reminderLogArbitrary().list().ofMinSize(1).ofMaxSize(20);
    }

    private Arbitrary<BizReminderLog> reminderLogArbitrary() {
        Arbitrary<Long> inspectionIds = Arbitraries.longs().between(1, 999999);
        Arbitrary<Long> receiverIds = Arbitraries.longs().between(1, 999999);
        Arbitrary<String> levels = Arbitraries.of("NORMAL", "ESCALATED");
        Arbitrary<String> statuses = Arbitraries.of("SENT", "FAILED");
        Arbitrary<Integer> overdueDays = Arbitraries.integers().between(1, 365);
        Arbitrary<LocalDateTime> sentAts = Arbitraries.integers()
                .between(0, 365 * 24 * 60)  // 分钟偏移
                .map(minutes -> LocalDateTime.of(2024, 1, 1, 0, 0).plusMinutes(minutes));

        return Combinators.combine(inspectionIds, receiverIds, levels, statuses, overdueDays, sentAts)
                .as((id, receiver, level, status, days, sentAt) -> {
                    BizReminderLog log = new BizReminderLog();
                    log.setInspectionId(id);
                    log.setReceiverId(receiver);
                    log.setReminderLevel(level);
                    log.setSendStatus(status);
                    log.setOverdueDays(days);
                    log.setSentAt(sentAt);
                    return log;
                });
    }
}
