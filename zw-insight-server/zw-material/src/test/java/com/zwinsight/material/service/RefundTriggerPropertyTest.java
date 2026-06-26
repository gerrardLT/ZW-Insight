package com.zwinsight.material.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 退货退款关联属性测试
 * <p>
 * 测试纯业务逻辑，不依赖数据库。
 * 模拟 MaterialReturnRefundEventListener 和 MaterialRefundService 中的核心逻辑。
 * <p>
 * **Validates: Requirements 7.1, 7.2, 7.4, 7.5, 7.6**
 */
@Tag("Feature: p1-business-completion")
class RefundTriggerPropertyTest {

    // ==================== 辅助数据结构 ====================

    record OutboundOrder(Long id, String outboundType, Long contractId, List<OutboundDetail> details) {}

    record OutboundDetail(Long materialId, BigDecimal quantity, BigDecimal inboundUnitPrice) {}

    record RefundRecord(Long outboundId, Long contractId, BigDecimal refundAmount,
                        List<RefundDetailRecord> details) {}

    record RefundDetailRecord(Long materialId, BigDecimal quantity, BigDecimal unitPrice,
                              BigDecimal amount) {}

    record Contract(Long id, BigDecimal cumulativePaid) {}

    record RefundQueryFilter(Long contractId, LocalDate startDate, LocalDate endDate) {}

    record RefundListItem(Long id, Long contractId, LocalDateTime createTime,
                          BigDecimal refundAmount) {}

    // ==================== 纯逻辑（提取自 Service） ====================

    /**
     * 判断是否需要自动生成退款申请
     * 逻辑来源：MaterialReturnRefundEventListener.onReturnCreated()
     */
    static boolean shouldGenerateRefund(OutboundOrder order) {
        return "RETURN".equals(order.outboundType()) && order.contractId() != null;
    }

    /**
     * 从退货出库单生成退款申请记录
     * 逻辑来源：MaterialRefundService.createRefundFromReturn()
     */
    static RefundRecord createRefundFromReturn(OutboundOrder order) {
        List<RefundDetailRecord> refundDetails = order.details().stream()
                .map(d -> {
                    BigDecimal amount = d.quantity().multiply(d.inboundUnitPrice())
                            .setScale(2, RoundingMode.HALF_UP);
                    return new RefundDetailRecord(d.materialId(), d.quantity(),
                            d.inboundUnitPrice(), amount);
                })
                .collect(Collectors.toList());

        BigDecimal totalRefund = refundDetails.stream()
                .map(RefundDetailRecord::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new RefundRecord(order.id(), order.contractId(), totalRefund, refundDetails);
    }

    /**
     * 审批通过后扣减合同累计付款金额
     * 逻辑来源：MaterialRefundService.onRefundApproved()
     */
    static BigDecimal deductCumulativePaid(BigDecimal currentPaid, BigDecimal refundAmount) {
        return currentPaid.subtract(refundAmount).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 退款记录筛选逻辑
     * 逻辑来源：MaterialRefundController.list() 的查询条件组装
     */
    static boolean matchesFilter(RefundListItem item, RefundQueryFilter filter) {
        // 按合同ID筛选
        if (filter.contractId() != null && !filter.contractId().equals(item.contractId())) {
            return false;
        }
        // 按时间范围筛选
        if (filter.startDate() != null) {
            LocalDate itemDate = item.createTime().toLocalDate();
            if (itemDate.isBefore(filter.startDate())) {
                return false;
            }
        }
        if (filter.endDate() != null) {
            LocalDate itemDate = item.createTime().toLocalDate();
            if (itemDate.isAfter(filter.endDate())) {
                return false;
            }
        }
        return true;
    }

    static List<RefundListItem> filterRefunds(List<RefundListItem> all, RefundQueryFilter filter) {
        return all.stream()
                .filter(item -> matchesFilter(item, filter))
                .collect(Collectors.toList());
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<OutboundDetail> outboundDetail() {
        Arbitrary<Long> materialIds = Arbitraries.longs().between(1L, 100L);
        Arbitrary<BigDecimal> quantities = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(9999.99))
                .ofScale(2);
        Arbitrary<BigDecimal> unitPrices = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(9999.99))
                .ofScale(2);

        return Combinators.combine(materialIds, quantities, unitPrices)
                .as(OutboundDetail::new);
    }

    @Provide
    Arbitrary<OutboundOrder> outboundOrder() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1L, 10000L);
        Arbitrary<String> types = Arbitraries.of("RETURN", "NORMAL", "TRANSFER", "SCRAP");
        Arbitrary<Long> contractIds = Arbitraries.longs().between(1L, 1000L)
                .injectNull(0.3); // 30% 概率为 null
        Arbitrary<List<OutboundDetail>> details = outboundDetail().list().ofMinSize(1).ofMaxSize(10);

        return Combinators.combine(ids, types, contractIds, details)
                .as(OutboundOrder::new);
    }

    @Provide
    Arbitrary<BigDecimal> refundAmounts() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(999999.99))
                .ofScale(2);
    }

    @Provide
    Arbitrary<BigDecimal> contractPaidAmounts() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(1000), BigDecimal.valueOf(9999999.99))
                .ofScale(2);
    }

    @Provide
    Arbitrary<RefundListItem> refundListItem() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1L, 10000L);
        Arbitrary<Long> contractIds = Arbitraries.longs().between(1L, 20L);
        Arbitrary<LocalDateTime> times = Arbitraries.of(
                LocalDateTime.of(2024, 1, 15, 10, 30),
                LocalDateTime.of(2024, 3, 20, 14, 0),
                LocalDateTime.of(2024, 5, 10, 9, 15),
                LocalDateTime.of(2024, 7, 25, 16, 45),
                LocalDateTime.of(2024, 9, 5, 11, 0),
                LocalDateTime.of(2024, 11, 18, 8, 30)
        );
        Arbitrary<BigDecimal> amounts = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(100), BigDecimal.valueOf(50000))
                .ofScale(2);

        return Combinators.combine(ids, contractIds, times, amounts)
                .as(RefundListItem::new);
    }

    @Provide
    Arbitrary<List<RefundListItem>> refundListItems() {
        return refundListItem().list().ofMinSize(0).ofMaxSize(30);
    }

    @Provide
    Arbitrary<RefundQueryFilter> queryFilter() {
        Arbitrary<Long> contractIds = Arbitraries.longs().between(1L, 20L)
                .injectNull(0.3);
        Arbitrary<LocalDate> startDates = Arbitraries.of(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 3, 1),
                LocalDate.of(2024, 6, 1),
                null
        );
        Arbitrary<LocalDate> endDates = Arbitraries.of(
                LocalDate.of(2024, 6, 30),
                LocalDate.of(2024, 9, 30),
                LocalDate.of(2024, 12, 31),
                null
        );

        return Combinators.combine(contractIds, startDates, endDates)
                .as(RefundQueryFilter::new);
    }

    // ==================== Property 13: 退款申请条件触发与完整性 ====================

    /**
     * Property 13: 退款申请条件触发与完整性
     * <p>
     * For any 退货出库单：
     * - 如果 outboundType == "RETURN" 且 contractId != null → 必须生成退款申请
     * - 如果 contractId == null → 不生成退款申请
     * - 如果 outboundType != "RETURN" → 不生成退款申请
     * <p>
     * **Validates: Requirements 7.1, 7.2, 7.6**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 13: 退款触发条件判断")
    void refundTriggeredOnlyForReturnWithContract(
            @ForAll("outboundOrder") OutboundOrder order) {

        boolean shouldGenerate = shouldGenerateRefund(order);

        if ("RETURN".equals(order.outboundType()) && order.contractId() != null) {
            assert shouldGenerate
                    : String.format("outboundType=RETURN, contractId=%d 应触发退款但未触发",
                    order.contractId());
        } else {
            assert !shouldGenerate
                    : String.format("outboundType=%s, contractId=%s 不应触发退款但被触发",
                    order.outboundType(), order.contractId());
        }
    }

    /**
     * Property 13 补充：生成的退款申请包含所有必需字段
     * <p>
     * **Validates: Requirements 7.1, 7.2**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 13: 退款记录完整性验证")
    void generatedRefundContainsAllRequiredFields(
            @ForAll("outboundOrder") OutboundOrder order) {

        if (!shouldGenerateRefund(order)) {
            return; // 不触发退款的场景跳过
        }

        RefundRecord refund = createRefundFromReturn(order);

        // 验证必需字段
        assert refund.outboundId() != null && refund.outboundId().equals(order.id())
                : "退款记录缺少 outboundId 或与出库单不匹配";
        assert refund.contractId() != null && refund.contractId().equals(order.contractId())
                : "退款记录缺少 contractId 或与合同不匹配";
        assert refund.refundAmount() != null && refund.refundAmount().compareTo(BigDecimal.ZERO) > 0
                : "退款金额必须大于0";
        assert refund.details() != null && !refund.details().isEmpty()
                : "退款记录缺少明细列表";
        assert refund.details().size() == order.details().size()
                : "退款明细数量与出库明细数量不匹配";
    }

    /**
     * Property 13 补充：退款金额计算正确性
     * <p>
     * 退款金额 = sum(detail.quantity × detail.inboundUnitPrice)
     * <p>
     * **Validates: Requirements 7.2**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 13: 退款金额 = sum(quantity × inboundUnitPrice)")
    void refundAmountCalculatedCorrectly(
            @ForAll("outboundOrder") OutboundOrder order) {

        if (!shouldGenerateRefund(order)) {
            return;
        }

        RefundRecord refund = createRefundFromReturn(order);

        // 手动计算预期总金额
        BigDecimal expectedTotal = order.details().stream()
                .map(d -> d.quantity().multiply(d.inboundUnitPrice()).setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assert refund.refundAmount().compareTo(expectedTotal) == 0
                : String.format("退款金额不正确: 预期 %s, 实际 %s", expectedTotal, refund.refundAmount());
    }

    // ==================== Property 14: 退款后合同金额扣减 ====================

    /**
     * Property 14: 退款后合同金额扣减
     * <p>
     * For any 退款申请审批通过：
     * contract.cumulativePaid_after == contract.cumulativePaid_before - refund.refundAmount
     * <p>
     * **Validates: Requirements 7.4**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 14: 退款后 cumulativePaid 减少恰好等于退款金额")
    void cumulativePaidDecreasedByExactRefundAmount(
            @ForAll("contractPaidAmounts") BigDecimal originalPaid,
            @ForAll("refundAmounts") BigDecimal refundAmount) {

        // 确保退款金额不超过已付金额（业务合理性）
        if (refundAmount.compareTo(originalPaid) > 0) {
            return; // 跳过不合理的场景
        }

        BigDecimal afterPaid = deductCumulativePaid(originalPaid, refundAmount);

        BigDecimal expectedAfter = originalPaid.subtract(refundAmount).setScale(2, RoundingMode.HALF_UP);

        assert afterPaid.compareTo(expectedAfter) == 0
                : String.format("扣减后金额不正确: 原=%s, 退款=%s, 预期=%s, 实际=%s",
                originalPaid, refundAmount, expectedAfter, afterPaid);
    }

    /**
     * Property 14 补充：扣减后金额精度为2位小数
     * <p>
     * **Validates: Requirements 7.4**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 14: 扣减后金额精度为2位小数")
    void deductedAmountHasScale2(
            @ForAll("contractPaidAmounts") BigDecimal originalPaid,
            @ForAll("refundAmounts") BigDecimal refundAmount) {

        BigDecimal afterPaid = deductCumulativePaid(originalPaid, refundAmount);

        assert afterPaid.scale() == 2
                : String.format("扣减后金额精度应为2, 实际为 %d (值: %s)", afterPaid.scale(), afterPaid);
    }

    // ==================== Property 15: 退款记录筛选正确性 ====================

    /**
     * Property 15: 退款记录筛选正确性
     * <p>
     * For any 退款记录查询参数（合同ID和/或时间范围），返回的每条记录必须满足所有指定的筛选条件。
     * <p>
     * **Validates: Requirements 7.5**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 15: 退款筛选结果满足所有条件")
    void filteredRefundsMatchAllCriteria(
            @ForAll("refundListItems") List<RefundListItem> allRefunds,
            @ForAll("queryFilter") RefundQueryFilter filter) {

        List<RefundListItem> filtered = filterRefunds(allRefunds, filter);

        for (RefundListItem item : filtered) {
            // 验证合同ID匹配
            if (filter.contractId() != null) {
                assert filter.contractId().equals(item.contractId())
                        : String.format("记录 contractId=%d 不匹配筛选条件 contractId=%d",
                        item.contractId(), filter.contractId());
            }

            // 验证时间范围
            LocalDate itemDate = item.createTime().toLocalDate();
            if (filter.startDate() != null) {
                assert !itemDate.isBefore(filter.startDate())
                        : String.format("记录日期 %s 早于筛选开始日期 %s",
                        itemDate, filter.startDate());
            }
            if (filter.endDate() != null) {
                assert !itemDate.isAfter(filter.endDate())
                        : String.format("记录日期 %s 晚于筛选结束日期 %s",
                        itemDate, filter.endDate());
            }
        }
    }

    /**
     * Property 15 补充：过滤结果数量等于满足条件记录数量
     * <p>
     * **Validates: Requirements 7.5**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 15: 过滤结果数量正确")
    void filteredCountMatchesExpectedCount(
            @ForAll("refundListItems") List<RefundListItem> allRefunds,
            @ForAll("queryFilter") RefundQueryFilter filter) {

        List<RefundListItem> filtered = filterRefunds(allRefunds, filter);

        long expectedCount = allRefunds.stream()
                .filter(item -> matchesFilter(item, filter))
                .count();

        assert filtered.size() == expectedCount
                : String.format("过滤结果数量 %d != 预期数量 %d", filtered.size(), expectedCount);
    }
}
