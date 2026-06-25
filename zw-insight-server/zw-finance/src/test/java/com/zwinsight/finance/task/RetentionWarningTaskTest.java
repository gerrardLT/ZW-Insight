package com.zwinsight.finance.task;

import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.finance.domain.BizRetentionMoney;
import com.zwinsight.finance.mapper.BizRetentionMoneyMapper;
import com.zwinsight.finance.mapper.BizRetentionWarningLogMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RetentionWarningTask 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RetentionWarningTaskTest {

    @Mock
    private BizRetentionMoneyMapper retentionMoneyMapper;

    @Mock
    private BizRetentionWarningLogMapper warningLogMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @Mock
    private BizConstructionContractMapper contractMapper;

    @Mock
    private RedisUtils redisUtils;

    @InjectMocks
    private RetentionWarningTask retentionWarningTask;

    private BizRetentionMoney buildRecord(Long id, LocalDate expireDate) {
        BizRetentionMoney record = new BizRetentionMoney();
        record.setId(id);
        record.setProjectId(100L);
        record.setContractId(200L);
        record.setRetentionAmount(new BigDecimal("50000.00"));
        record.setExpireDate(expireDate);
        record.setStatus("ACTIVE");
        return record;
    }

    // ============ processRecord 级别判定测试 ============

    @Test
    @DisplayName("testProcessRecord_upcomingWhen20DaysLeft — 剩余20天返回 UPCOMING")
    void testProcessRecord_upcomingWhen20DaysLeft() {
        LocalDate today = LocalDate.of(2025, 7, 1);
        // 到期日 = today + 20天 → 剩余20天，应返回 UPCOMING（8~30天范围）
        BizRetentionMoney record = buildRecord(1L, today.plusDays(20));

        String level = retentionWarningTask.processRecord(record, today);

        assertEquals(RetentionWarningTask.LEVEL_UPCOMING, level);
    }

    @Test
    @DisplayName("testProcessRecord_urgentWhen5DaysLeft — 剩余5天返回 URGENT")
    void testProcessRecord_urgentWhen5DaysLeft() {
        LocalDate today = LocalDate.of(2025, 7, 1);
        // 到期日 = today + 5天 → 剩余5天，应返回 URGENT（1~7天范围）
        BizRetentionMoney record = buildRecord(2L, today.plusDays(5));

        String level = retentionWarningTask.processRecord(record, today);

        assertEquals(RetentionWarningTask.LEVEL_URGENT, level);
    }

    @Test
    @DisplayName("testProcessRecord_overdueWhenExpired — 已过期返回 OVERDUE")
    void testProcessRecord_overdueWhenExpired() {
        LocalDate today = LocalDate.of(2025, 7, 1);
        // 到期日 = today - 10天 → 已逾期10天（<180天），应返回 OVERDUE
        BizRetentionMoney record = buildRecord(3L, today.minusDays(10));

        String level = retentionWarningTask.processRecord(record, today);

        assertEquals(RetentionWarningTask.LEVEL_OVERDUE, level);
    }

    @Test
    @DisplayName("testProcessRecord_nullWhenOverdue200Days — 逾期超180天返回 null")
    void testProcessRecord_nullWhenOverdue200Days() {
        LocalDate today = LocalDate.of(2025, 7, 1);
        // 到期日 = today - 200天 → 逾期200天(>180天)，应返回 null（停止催办）
        BizRetentionMoney record = buildRecord(4L, today.minusDays(200));

        String level = retentionWarningTask.processRecord(record, today);

        assertNull(level);
    }

    @Test
    @DisplayName("testProcessRecord_upcomingBoundary8Days — 边界：8天=UPCOMING")
    void testProcessRecord_upcomingBoundary8Days() {
        LocalDate today = LocalDate.of(2025, 7, 1);
        // 到期日 = today + 8天 → 剩余恰好8天，属于 UPCOMING 范围（8~30天）
        BizRetentionMoney record = buildRecord(5L, today.plusDays(8));

        String level = retentionWarningTask.processRecord(record, today);

        assertEquals(RetentionWarningTask.LEVEL_UPCOMING, level);
    }

    @Test
    @DisplayName("testProcessRecord_urgentBoundary7Days — 边界：7天=URGENT")
    void testProcessRecord_urgentBoundary7Days() {
        LocalDate today = LocalDate.of(2025, 7, 1);
        // 到期日 = today + 7天 → 剩余恰好7天，属于 URGENT 范围（1~7天）
        BizRetentionMoney record = buildRecord(6L, today.plusDays(7));

        String level = retentionWarningTask.processRecord(record, today);

        assertEquals(RetentionWarningTask.LEVEL_URGENT, level);
    }

    // ============ shouldSendNotification 去重测试 ============

    @Test
    @DisplayName("testShouldSendNotification_skipWhenAlreadyWarned — 已发送过则跳过")
    void testShouldSendNotification_skipWhenAlreadyWarned() {
        Long retentionId = 10L;
        String level = RetentionWarningTask.LEVEL_UPCOMING;
        LocalDate today = LocalDate.of(2025, 7, 1);

        // 模拟 Redis 中已存在去重 key
        String warnedKey = retentionWarningTask.buildWarnedKey(retentionId, level);
        when(redisUtils.hasKey(warnedKey)).thenReturn(true);

        boolean result = retentionWarningTask.shouldSendNotification(retentionId, level, today);

        assertFalse(result, "已发送过同级别通知应跳过");
        verify(redisUtils).hasKey(warnedKey);
    }

    // ============ onRetentionReturned 清除Redis测试 ============

    @Test
    @DisplayName("testOnRetentionReturned_clearsAllKeys — 退还后清除 Redis keys")
    void testOnRetentionReturned_clearsAllKeys() {
        Long retentionId = 20L;

        retentionWarningTask.onRetentionReturned(retentionId);

        // 验证删除了所有级别的去重key + 逾期催办key
        verify(redisUtils).delete("retention:warned:" + retentionId + ":UPCOMING");
        verify(redisUtils).delete("retention:warned:" + retentionId + ":URGENT");
        verify(redisUtils).delete("retention:warned:" + retentionId + ":OVERDUE");
        verify(redisUtils).delete("retention:overdue:last:" + retentionId);
        verifyNoMoreInteractions(redisUtils);
    }
}
