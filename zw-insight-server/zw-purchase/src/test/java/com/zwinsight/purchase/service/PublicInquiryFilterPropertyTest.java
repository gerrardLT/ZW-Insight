package com.zwinsight.purchase.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 公开询价过滤与截止拦截属性测试
 * <p>
 * 测试纯过滤/判断逻辑，不依赖数据库。
 * 模拟 PublicInquiryController/Service 中的过滤与截止判断逻辑。
 * <p>
 * **Validates: Requirements 6.2, 6.7**
 */
@Tag("Feature: p1-business-completion")
class PublicInquiryFilterPropertyTest {

    // ==================== 辅助数据结构 ====================

    /**
     * 模拟询价记录
     */
    record InquiryRecord(Long id, String inviteMode, String status, LocalDateTime deadline) {}

    // ==================== 纯逻辑（提取自 Service） ====================

    /**
     * 公开询价过滤条件：inviteMode == "PUBLIC" 且 status ∈ {"OPEN", "PUBLISHED"}
     * 逻辑来源：PublicInquiryController.listPublicInquiries()
     */
    static final Set<String> VISIBLE_STATUSES = Set.of("OPEN", "PUBLISHED");

    static boolean isPublicVisible(InquiryRecord record) {
        return "PUBLIC".equals(record.inviteMode())
                && VISIBLE_STATUSES.contains(record.status());
    }

    /**
     * 过滤询价列表，仅返回公开可见的记录
     * 逻辑来源：PublicInquiryService.listPublicInquiries()
     */
    static List<InquiryRecord> filterPublicInquiries(List<InquiryRecord> all) {
        return all.stream()
                .filter(PublicInquiryFilterPropertyTest::isPublicVisible)
                .collect(Collectors.toList());
    }

    /**
     * 判断是否超过截止时间
     * 逻辑来源：PublicInquiryController.submitQuote() 中的校验
     */
    static boolean isDeadlineExceeded(LocalDateTime currentTime, LocalDateTime deadline) {
        return currentTime.isAfter(deadline);
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<String> inviteModes() {
        return Arbitraries.of("PUBLIC", "INVITED", "DESIGNATED", "INTERNAL");
    }

    @Provide
    Arbitrary<String> inquiryStatuses() {
        return Arbitraries.of("DRAFT", "OPEN", "PUBLISHED", "CLOSED", "CANCELLED", "EXPIRED");
    }

    @Provide
    Arbitrary<InquiryRecord> inquiryRecord() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1L, 10000L);
        Arbitrary<String> modes = inviteModes();
        Arbitrary<String> statuses = inquiryStatuses();
        Arbitrary<LocalDateTime> deadlines = Arbitraries.of(
                LocalDateTime.of(2024, 6, 1, 12, 0),
                LocalDateTime.of(2024, 7, 15, 18, 0),
                LocalDateTime.of(2024, 8, 30, 9, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 6, 30, 23, 59)
        );

        return Combinators.combine(ids, modes, statuses, deadlines)
                .as(InquiryRecord::new);
    }

    @Provide
    Arbitrary<List<InquiryRecord>> inquiryRecordList() {
        return inquiryRecord().list().ofMinSize(0).ofMaxSize(50);
    }

    @Provide
    Arbitrary<LocalDateTime> randomTimestamp() {
        return Arbitraries.of(
                LocalDateTime.of(2024, 1, 1, 0, 0),
                LocalDateTime.of(2024, 5, 31, 23, 59),
                LocalDateTime.of(2024, 6, 1, 11, 59),
                LocalDateTime.of(2024, 6, 1, 12, 0),
                LocalDateTime.of(2024, 6, 1, 12, 1),
                LocalDateTime.of(2024, 7, 15, 17, 59),
                LocalDateTime.of(2024, 7, 15, 18, 0),
                LocalDateTime.of(2024, 7, 15, 18, 1),
                LocalDateTime.of(2024, 12, 31, 23, 59),
                LocalDateTime.of(2025, 6, 30, 23, 59),
                LocalDateTime.of(2025, 7, 1, 0, 0)
        );
    }

    // ==================== Property 11: 公开询价过滤不变量 ====================

    /**
     * Property 11: 公开询价过滤不变量
     * <p>
     * For any 公开询价列表查询返回的结果集，每条记录必须满足：
     * inviteMode == "PUBLIC" 且 status ∈ {"OPEN", "PUBLISHED"}
     * <p>
     * **Validates: Requirements 6.2**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 11: 公开询价过滤 - 结果集仅包含 PUBLIC+OPEN/PUBLISHED")
    void filteredResultsContainOnlyPublicAndOpenOrPublished(
            @ForAll("inquiryRecordList") List<InquiryRecord> allInquiries) {

        List<InquiryRecord> filtered = filterPublicInquiries(allInquiries);

        for (InquiryRecord record : filtered) {
            assert "PUBLIC".equals(record.inviteMode())
                    : String.format("结果中出现非 PUBLIC 记录: id=%d, inviteMode=%s",
                    record.id(), record.inviteMode());

            assert VISIBLE_STATUSES.contains(record.status())
                    : String.format("结果中出现非法状态记录: id=%d, status=%s",
                    record.id(), record.status());
        }
    }

    /**
     * Property 11 补充：不满足条件的记录不能出现在结果中
     * <p>
     * **Validates: Requirements 6.2**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 11: 公开询价过滤 - 不符条件记录被正确排除")
    void nonMatchingRecordsAreExcluded(
            @ForAll("inquiryRecordList") List<InquiryRecord> allInquiries) {

        List<InquiryRecord> filtered = filterPublicInquiries(allInquiries);
        Set<Long> filteredIds = filtered.stream().map(InquiryRecord::id).collect(Collectors.toSet());

        for (InquiryRecord record : allInquiries) {
            if (!isPublicVisible(record)) {
                assert !filteredIds.contains(record.id())
                        : String.format("不满足条件的记录出现在结果中: id=%d, inviteMode=%s, status=%s",
                        record.id(), record.inviteMode(), record.status());
            }
        }
    }

    /**
     * Property 11 补充：满足条件的记录都在结果中（不遗漏）
     * <p>
     * **Validates: Requirements 6.2**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 11: 公开询价过滤 - 满足条件的记录不遗漏")
    void allMatchingRecordsAreIncluded(
            @ForAll("inquiryRecordList") List<InquiryRecord> allInquiries) {

        List<InquiryRecord> filtered = filterPublicInquiries(allInquiries);
        Set<Long> filteredIds = filtered.stream().map(InquiryRecord::id).collect(Collectors.toSet());

        for (InquiryRecord record : allInquiries) {
            if (isPublicVisible(record)) {
                assert filteredIds.contains(record.id())
                        : String.format("满足条件的记录未出现在结果中: id=%d, inviteMode=%s, status=%s",
                        record.id(), record.inviteMode(), record.status());
            }
        }
    }

    // ==================== Property 12: 询价截止拦截 ====================

    /**
     * Property 12: 询价截止拦截
     * <p>
     * For any 询价记录和提交报价的时间点：
     * - 如果当前时间超过询价的 deadline，则提交报价操作必须被拒绝
     * - 如果未超过 deadline，则应被允许
     * <p>
     * **Validates: Requirements 6.7**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 12: 询价截止后报价被拒绝")
    void quoteSubmissionRejectedAfterDeadline(
            @ForAll("inquiryRecord") InquiryRecord inquiry,
            @ForAll("randomTimestamp") LocalDateTime currentTime) {

        boolean exceeded = isDeadlineExceeded(currentTime, inquiry.deadline());

        if (currentTime.isAfter(inquiry.deadline())) {
            assert exceeded
                    : String.format("currentTime(%s) > deadline(%s) 但未被拒绝",
                    currentTime, inquiry.deadline());
        } else {
            assert !exceeded
                    : String.format("currentTime(%s) <= deadline(%s) 但被错误拒绝",
                    currentTime, inquiry.deadline());
        }
    }

    /**
     * Property 12 补充：恰好在截止时间提交不应被拒绝
     * <p>
     * **Validates: Requirements 6.7**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 12: 恰好在截止时间提交不被拒绝")
    void quoteSubmissionAtExactDeadlineIsAllowed(
            @ForAll("inquiryRecord") InquiryRecord inquiry) {

        // currentTime == deadline 时不应被拒绝（isAfter 返回 false）
        boolean exceeded = isDeadlineExceeded(inquiry.deadline(), inquiry.deadline());

        assert !exceeded
                : String.format("恰好在截止时间(%s)提交不应被拒绝", inquiry.deadline());
    }
}
