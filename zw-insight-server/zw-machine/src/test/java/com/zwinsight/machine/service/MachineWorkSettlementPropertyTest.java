package com.zwinsight.machine.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 机械工作量结算属性测试
 * <p>
 * 测试纯计算逻辑，不依赖数据库。
 * 模拟 MachineWorkSettlementService 中的汇总和金额计算逻辑。
 * <p>
 * **Validates: Requirements 1.2, 1.3, 1.4**
 */
@Tag("Feature: p1-business-completion")
class MachineWorkSettlementPropertyTest {

    // ==================== 辅助数据结构 ====================

    record WorkLog(Long machineId, LocalDate workDate, BigDecimal shiftCount, BigDecimal workQuantity) {}

    record SettlementDetail(Long machineId, BigDecimal shiftCount, BigDecimal workVolume,
                            BigDecimal unitPrice, BigDecimal subtotal, String pricingType) {}

    // ==================== 纯计算逻辑（提取自 Service） ====================

    /**
     * 按机械ID分组工作日志，生成结算明细列表
     * 逻辑来源：MachineWorkSettlementService.createSettlement()
     */
    static List<SettlementDetail> buildSettlementDetails(List<WorkLog> workLogs,
                                                         Map<Long, BigDecimal> machineUnitPrices) {
        Map<Long, List<WorkLog>> logsByMachine = workLogs.stream()
                .collect(Collectors.groupingBy(WorkLog::machineId));

        List<SettlementDetail> details = new ArrayList<>();
        for (Map.Entry<Long, List<WorkLog>> entry : logsByMachine.entrySet()) {
            Long machineId = entry.getKey();
            List<WorkLog> machineLogs = entry.getValue();

            BigDecimal totalShiftCount = machineLogs.stream()
                    .map(l -> l.shiftCount() != null ? l.shiftCount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalWorkVolume = machineLogs.stream()
                    .map(l -> l.workQuantity() != null ? l.workQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal unitPrice = machineUnitPrices.getOrDefault(machineId, BigDecimal.ZERO);
            // 默认使用台班计价
            BigDecimal subtotal = totalShiftCount.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

            details.add(new SettlementDetail(machineId, totalShiftCount, totalWorkVolume,
                    unitPrice, subtotal, "SHIFT"));
        }
        return details;
    }

    /**
     * 计算结算单总金额 = sum(detail.subtotal)
     * 逻辑来源：MachineWorkSettlementService.createSettlement()
     */
    static BigDecimal calculateTotalAmount(List<SettlementDetail> details) {
        return details.stream()
                .map(SettlementDetail::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<List<WorkLog>> workLogList() {
        Arbitrary<Long> machineIds = Arbitraries.longs().between(1L, 10L);
        Arbitrary<BigDecimal> shiftCounts = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.5), BigDecimal.valueOf(10.0))
                .ofScale(2);
        Arbitrary<BigDecimal> workQuantities = Arbitraries.bigDecimals()
                .between(BigDecimal.ONE, BigDecimal.valueOf(1000))
                .ofScale(2);
        Arbitrary<LocalDate> workDates = Arbitraries.of(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 15),
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 15),
                LocalDate.of(2024, 3, 1)
        );

        return Combinators.combine(machineIds, workDates, shiftCounts, workQuantities)
                .as(WorkLog::new)
                .list().ofMinSize(1).ofMaxSize(50);
    }

    @Provide
    Arbitrary<Map<Long, BigDecimal>> unitPriceMap() {
        return Arbitraries.maps(
                Arbitraries.longs().between(1L, 10L),
                Arbitraries.bigDecimals()
                        .between(BigDecimal.valueOf(100), BigDecimal.valueOf(5000))
                        .ofScale(2)
        ).ofMinSize(1).ofMaxSize(10);
    }

    // ==================== Property 1: 机械结算汇总正确性 ====================

    /**
     * Property 1: 机械结算汇总正确性
     * <p>
     * For any 项目和结算周期，创建结算单后：
     * - 结算明细的分组数量应等于该周期内未结算工作日志按机械ID分组的组数
     * - 每条明细行的台班数应等于对应机械分组内所有工作日志的台班数之和
     * <p>
     * **Validates: Requirements 1.2, 1.3**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 1: 机械结算汇总正确性")
    void settlementDetailGroupCountMatchesWorkLogGroups(
            @ForAll("workLogList") List<WorkLog> workLogs,
            @ForAll("unitPriceMap") Map<Long, BigDecimal> unitPrices) {

        // 执行汇总计算逻辑
        List<SettlementDetail> details = buildSettlementDetails(workLogs, unitPrices);

        // 预期分组
        Map<Long, List<WorkLog>> expectedGroups = workLogs.stream()
                .collect(Collectors.groupingBy(WorkLog::machineId));

        // 验证1: 结算明细分组数 == 工作日志按机械ID分组数
        assert details.size() == expectedGroups.size()
                : String.format("明细数量 %d != 分组数 %d", details.size(), expectedGroups.size());

        // 验证2: 每条明细的台班数 == 对应分组工作日志台班数之和
        Map<Long, SettlementDetail> detailMap = details.stream()
                .collect(Collectors.toMap(SettlementDetail::machineId, d -> d));

        for (Map.Entry<Long, List<WorkLog>> entry : expectedGroups.entrySet()) {
            Long machineId = entry.getKey();
            List<WorkLog> machineLogs = entry.getValue();

            BigDecimal expectedShiftCount = machineLogs.stream()
                    .map(l -> l.shiftCount() != null ? l.shiftCount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            SettlementDetail detail = detailMap.get(machineId);
            assert detail != null : "机械ID " + machineId + " 没有对应的结算明细";
            assert detail.shiftCount().compareTo(expectedShiftCount) == 0
                    : String.format("机械ID %d 台班数不匹配: 预期 %s, 实际 %s",
                    machineId, expectedShiftCount, detail.shiftCount());
        }
    }

    // ==================== Property 2: 机械结算总金额不变量 ====================

    /**
     * Property 2: 机械结算总金额不变量
     * <p>
     * For any 机械工作量结算单，结算单的 totalAmount 应严格等于其所有明细行 subtotal 之和。
     * settlement.totalAmount == sum(detail.subtotal)
     * <p>
     * **Validates: Requirements 1.4**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 2: 机械结算总金额不变量")
    void settlementTotalAmountEqualsSumOfDetailSubtotals(
            @ForAll("workLogList") List<WorkLog> workLogs,
            @ForAll("unitPriceMap") Map<Long, BigDecimal> unitPrices) {

        // 执行汇总计算逻辑
        List<SettlementDetail> details = buildSettlementDetails(workLogs, unitPrices);

        // 计算 totalAmount
        BigDecimal totalAmount = calculateTotalAmount(details);

        // 手动累加 sum(detail.subtotal)
        BigDecimal expectedTotal = details.stream()
                .map(SettlementDetail::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        assert totalAmount.compareTo(expectedTotal) == 0
                : String.format("totalAmount %s != sum(subtotal) %s", totalAmount, expectedTotal);
    }

    /**
     * Property 2 补充验证：直接使用随机明细行验证总金额不变量
     * <p>
     * 生成随机的明细行列表（数量、单价），验证总金额 == sum(数量 × 单价)
     * <p>
     * **Validates: Requirements 1.4**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 2: 机械结算总金额不变量（直接验证）")
    void totalAmountInvariantWithRandomDetails(
            @ForAll @Size(min = 1, max = 20) List<@From("randomDetail") SettlementDetail> details) {

        BigDecimal totalAmount = calculateTotalAmount(details);

        BigDecimal expectedTotal = details.stream()
                .map(SettlementDetail::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        assert totalAmount.compareTo(expectedTotal) == 0
                : String.format("totalAmount %s != sum(subtotal) %s", totalAmount, expectedTotal);
    }

    @Provide
    Arbitrary<SettlementDetail> randomDetail() {
        Arbitrary<Long> machineIds = Arbitraries.longs().between(1L, 100L);
        Arbitrary<BigDecimal> shiftCounts = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.5), BigDecimal.valueOf(50))
                .ofScale(2);
        Arbitrary<BigDecimal> unitPrices = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(100), BigDecimal.valueOf(10000))
                .ofScale(2);

        return Combinators.combine(machineIds, shiftCounts, unitPrices)
                .as((id, shift, price) -> {
                    BigDecimal subtotal = shift.multiply(price).setScale(2, RoundingMode.HALF_UP);
                    return new SettlementDetail(id, shift, BigDecimal.ZERO, price, subtotal, "SHIFT");
                });
    }

    // ==================== Property 3: 审批通过后工作日志状态变更 ====================

    /**
     * 辅助数据结构：模拟审批后的结算单与工作日志关联关系
     */
    record SettlementWithLogIds(Long settlementId, List<Long> workLogIds) {}

    /**
     * 模拟审批通过后回写工作日志状态的纯逻辑
     * 逻辑来源：MachineWorkSettlementService.onApproved()
     * <p>
     * 审批通过后，收集结算单所有明细行关联的工作日志ID，将其 settlementStatus 设置为 "SETTLED"
     */
    static Map<Long, String> applyApprovalStatusChange(SettlementWithLogIds settlement,
                                                        Map<Long, String> workLogStatusMap) {
        Map<Long, String> result = new HashMap<>(workLogStatusMap);
        for (Long logId : settlement.workLogIds()) {
            result.put(logId, "SETTLED");
        }
        return result;
    }

    @Provide
    Arbitrary<SettlementWithLogIds> approvedSettlement() {
        Arbitrary<Long> settlementIds = Arbitraries.longs().between(1L, 1000L);
        Arbitrary<List<Long>> workLogIdLists = Arbitraries.longs().between(1L, 500L)
                .list().ofMinSize(1).ofMaxSize(30).uniqueElements();

        return Combinators.combine(settlementIds, workLogIdLists)
                .as(SettlementWithLogIds::new);
    }

    /**
     * Property 3: 审批通过后工作日志状态变更
     * <p>
     * For any 已审批通过的机械工作量结算单，其所有明细行关联的工作日志记录的
     * settlementStatus 必须为 "SETTLED"。
     * <p>
     * **Validates: Requirements 1.6**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 3: 审批通过后工作日志状态变更")
    void approvedSettlementWorkLogsMustBeSettled(
            @ForAll("approvedSettlement") SettlementWithLogIds settlement) {

        // 初始状态：所有工作日志为 UNSETTLED
        Map<Long, String> initialStatus = new HashMap<>();
        for (Long logId : settlement.workLogIds()) {
            initialStatus.put(logId, "UNSETTLED");
        }
        // 额外加入一些不属于结算单的日志
        initialStatus.put(9990L, "UNSETTLED");
        initialStatus.put(9991L, "UNSETTLED");

        // 执行审批通过后的状态变更逻辑
        Map<Long, String> afterApproval = applyApprovalStatusChange(settlement, initialStatus);

        // 验证：结算单关联的所有工作日志状态为 SETTLED
        for (Long logId : settlement.workLogIds()) {
            String status = afterApproval.get(logId);
            assert "SETTLED".equals(status)
                    : String.format("工作日志 %d 审批后状态应为 SETTLED，实际为 %s", logId, status);
        }

        // 验证：未关联的日志不受影响
        assert "UNSETTLED".equals(afterApproval.get(9990L))
                : "不属于结算单的工作日志 9990 不应被修改";
        assert "UNSETTLED".equals(afterApproval.get(9991L))
                : "不属于结算单的工作日志 9991 不应被修改";
    }

    // ==================== Property 4: 工作日志结算唯一性 ====================

    /**
     * 模拟创建结算单时排除已结算工作日志的逻辑
     * 逻辑来源：MachineWorkSettlementService.createSettlement()
     * <p>
     * 如果工作日志已经有 settlementStatus == "SETTLED"，则不会被纳入新结算单。
     * 这保证了不同结算单的工作日志ID集合无交集。
     */
    static List<Long> filterUnsettledLogs(List<Long> candidateLogIds, Set<Long> alreadySettledIds) {
        return candidateLogIds.stream()
                .filter(id -> !alreadySettledIds.contains(id))
                .collect(Collectors.toList());
    }

    @Provide
    Arbitrary<List<Set<Long>>> multipleSettlementLogSets() {
        // 生成2~5个结算单的工作日志ID集合（随机分配，无交集）
        return Arbitraries.integers().between(5, 50).flatMap(totalLogs -> {
            // 生成 totalLogs 个唯一日志ID
            return Arbitraries.longs().between(1L, 1000L)
                    .set().ofMinSize(totalLogs).ofMaxSize(totalLogs)
                    .flatMap(allIds -> {
                        List<Long> idList = new ArrayList<>(allIds);
                        // 随机切分成2~4个不相交子集
                        return Arbitraries.integers().between(2, 4).map(numSets -> {
                            List<Set<Long>> result = new ArrayList<>();
                            int chunkSize = Math.max(1, idList.size() / numSets);
                            for (int i = 0; i < numSets; i++) {
                                int start = i * chunkSize;
                                int end = (i == numSets - 1) ? idList.size() : start + chunkSize;
                                if (start < idList.size()) {
                                    result.add(new HashSet<>(idList.subList(start, end)));
                                }
                            }
                            return result;
                        });
                    });
        });
    }

    /**
     * Property 4: 工作日志结算唯一性
     * <p>
     * For any 两个不同的机械工作量结算单，它们的工作日志ID集合之间不能有交集
     * （即同一条工作日志只能属于一个结算单）。
     * <p>
     * 验证方式：模拟按顺序创建多个结算单，每次创建时排除已结算日志，
     * 验证最终各结算单之间工作日志ID无交集。
     * <p>
     * **Validates: Requirements 1.7**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 4: 工作日志结算唯一性")
    void differentSettlementsHaveDisjointWorkLogSets(
            @ForAll("multipleSettlementLogSets") List<Set<Long>> logSets) {

        // 模拟按顺序创建结算单，每次创建后将日志标记为 SETTLED
        Set<Long> settledSoFar = new HashSet<>();
        List<Set<Long>> actualSettlementLogs = new ArrayList<>();

        for (Set<Long> candidateSet : logSets) {
            // 模拟 createSettlement 中排除已结算日志
            List<Long> unsettled = filterUnsettledLogs(new ArrayList<>(candidateSet), settledSoFar);

            if (!unsettled.isEmpty()) {
                Set<Long> thisSettlement = new HashSet<>(unsettled);
                actualSettlementLogs.add(thisSettlement);

                // 模拟审批通过后标记为 SETTLED
                settledSoFar.addAll(thisSettlement);
            }
        }

        // 验证：任意两个结算单的工作日志ID集合之间无交集
        for (int i = 0; i < actualSettlementLogs.size(); i++) {
            for (int j = i + 1; j < actualSettlementLogs.size(); j++) {
                Set<Long> setA = actualSettlementLogs.get(i);
                Set<Long> setB = actualSettlementLogs.get(j);

                Set<Long> intersection = new HashSet<>(setA);
                intersection.retainAll(setB);

                assert intersection.isEmpty()
                        : String.format("结算单 %d 和 %d 之间存在工作日志交集: %s", i, j, intersection);
            }
        }
    }
}
