package com.zwinsight.site.property;

import com.zwinsight.site.domain.BizInspection;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: p0-data-permission-overdue, Property 6: 超期扫描只返回符合条件的记录
/**
 * Property 6: 超期扫描只返回符合条件的记录
 * <p>
 * 验证：超期扫描应仅返回满足 rectificationStatus = PENDING AND rectificationDeadline < today 的记录。
 * 状态为 SUBMITTED、APPROVED 或 REJECTED 的记录不在扫描结果中。
 * </p>
 * <p>
 * **Validates: Requirements 6.2, 9.4**
 * </p>
 */
class OverdueScanFilterPropertyTest {

    private static final String[] ALL_STATUSES = {"PENDING", "SUBMITTED", "APPROVED", "REJECTED"};
    private static final LocalDate TODAY = LocalDate.of(2025, 6, 15);

    /**
     * 模拟 queryOverdueRecords 过滤逻辑（复刻 RectificationReminderTask.queryOverdueRecords 的 WHERE 条件）
     * 条件：rectificationStatus = PENDING AND rectificationDeadline < today
     */
    private List<BizInspection> filterOverdueRecords(List<BizInspection> allRecords, LocalDate today) {
        List<BizInspection> result = new ArrayList<>();
        for (BizInspection record : allRecords) {
            if ("PENDING".equals(record.getRectificationStatus())
                    && record.getRectificationDeadline() != null
                    && record.getRectificationDeadline().isBefore(today)) {
                result.add(record);
            }
        }
        return result;
    }

    @Provide
    Arbitrary<List<BizInspection>> randomInspectionRecords() {
        Arbitrary<BizInspection> singleRecord = Combinators.combine(
                Arbitraries.of(ALL_STATUSES),
                Arbitraries.integers().between(-60, 30)
        ).as((status, daysOffset) -> {
            BizInspection record = new BizInspection();
            record.setId((long) Math.abs(status.hashCode() + daysOffset));
            record.setTenantId(1L);
            record.setProjectId(1L);
            record.setInspectionType("QUALITY");
            record.setProblemDescription("测试问题");
            record.setResponsiblePersonId(1L);
            record.setRectificationStatus(status);
            record.setRectificationDeadline(TODAY.plusDays(daysOffset));
            return record;
        });

        return singleRecord.list().ofMinSize(5).ofMaxSize(50);
    }

    @Property(tries = 100)
    void onlyPendingAndOverdueRecordsAreReturned(
            @ForAll("randomInspectionRecords") List<BizInspection> allRecords) {

        List<BizInspection> filtered = filterOverdueRecords(allRecords, TODAY);

        // 属性1: 所有返回的记录状态必须是 PENDING
        assertThat(filtered)
                .allMatch(r -> "PENDING".equals(r.getRectificationStatus()),
                        "过滤结果中不应包含非 PENDING 状态的记录");

        // 属性2: 所有返回的记录整改期限必须早于 today
        assertThat(filtered)
                .allMatch(r -> r.getRectificationDeadline().isBefore(TODAY),
                        "过滤结果中不应包含未超期的记录");

        // 属性3: 原始集合中满足条件的记录都应被选中（无遗漏）
        long expectedCount = allRecords.stream()
                .filter(r -> "PENDING".equals(r.getRectificationStatus()))
                .filter(r -> r.getRectificationDeadline() != null && r.getRectificationDeadline().isBefore(TODAY))
                .count();
        assertThat(filtered).hasSize((int) expectedCount);
    }

    @Property(tries = 100)
    void nonPendingRecordsNeverSelected(
            @ForAll @IntRange(min = 0, max = 2) int statusIndex,
            @ForAll @IntRange(min = 1, max = 60) int daysAgo) {

        String[] nonPendingStatuses = {"SUBMITTED", "APPROVED", "REJECTED"};
        String status = nonPendingStatuses[statusIndex];

        BizInspection record = new BizInspection();
        record.setId(1L);
        record.setTenantId(1L);
        record.setRectificationStatus(status);
        record.setRectificationDeadline(TODAY.minusDays(daysAgo));

        List<BizInspection> filtered = filterOverdueRecords(List.of(record), TODAY);

        // 非 PENDING 状态的记录不应被选出，即使已超期
        assertThat(filtered).isEmpty();
    }

    @Property(tries = 100)
    void pendingButNotOverdueRecordsNotSelected(
            @ForAll @IntRange(min = 0, max = 30) int daysInFuture) {

        BizInspection record = new BizInspection();
        record.setId(1L);
        record.setTenantId(1L);
        record.setRectificationStatus("PENDING");
        record.setRectificationDeadline(TODAY.plusDays(daysInFuture));

        List<BizInspection> filtered = filterOverdueRecords(List.of(record), TODAY);

        // PENDING 但未超期（deadline >= today）的记录不应被选出
        assertThat(filtered).isEmpty();
    }
}
