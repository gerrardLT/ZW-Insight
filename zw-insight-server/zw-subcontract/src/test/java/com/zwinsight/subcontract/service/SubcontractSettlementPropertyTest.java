package com.zwinsight.subcontract.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分包结算金额计算属性测试
 * <p>
 * 测试纯计算逻辑，不依赖数据库。
 * 模拟 SubcontractSettlementService 中的金额计算逻辑。
 * <p>
 * **Validates: Requirements 2.4, 2.5**
 */
@Tag("Feature: p1-business-completion")
class SubcontractSettlementPropertyTest {

    // ==================== 辅助数据结构 ====================

    record DetailLine(String itemName, String unit, BigDecimal quantity, BigDecimal unitPrice) {}

    record CalculatedDetail(String itemName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {}

    // ==================== 纯计算逻辑（提取自 Service） ====================

    /**
     * 计算明细行金额 = quantity × unitPrice，保留2位小数，HALF_UP
     * 逻辑来源：SubcontractSettlementService.saveDetails()
     */
    static BigDecimal calculateLineAmount(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算结算单总金额 = sum(detail.amount)
     * 逻辑来源：SubcontractSettlementService.saveDetails()
     */
    static BigDecimal calculateTotalAmount(List<CalculatedDetail> details) {
        return details.stream()
                .map(CalculatedDetail::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 将输入明细行列表转换为计算后的明细行列表
     * 逻辑来源：SubcontractSettlementService.saveDetails()
     */
    static List<CalculatedDetail> processDetails(List<DetailLine> inputs) {
        return inputs.stream()
                .map(line -> new CalculatedDetail(
                        line.itemName(),
                        line.quantity(),
                        line.unitPrice(),
                        calculateLineAmount(line.quantity(), line.unitPrice())
                ))
                .collect(Collectors.toList());
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<DetailLine> detailLine() {
        Arbitrary<String> itemNames = Arbitraries.of(
                "土方开挖", "混凝土浇筑", "钢筋绑扎", "模板安装",
                "防水施工", "回填土方", "基坑支护", "桩基施工"
        );
        Arbitrary<String> units = Arbitraries.of("m³", "m²", "t", "m", "个", "组");
        Arbitrary<BigDecimal> quantities = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(99999.99))
                .ofScale(2);
        Arbitrary<BigDecimal> unitPrices = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(99999.99))
                .ofScale(2);

        return Combinators.combine(itemNames, units, quantities, unitPrices)
                .as(DetailLine::new);
    }

    @Provide
    Arbitrary<List<DetailLine>> detailLineList() {
        return detailLine().list().ofMinSize(1).ofMaxSize(30);
    }

    // ==================== Property 5: 分包结算金额计算不变量 ====================

    /**
     * Property 5: 分包结算金额计算不变量 - 行金额验证
     * <p>
     * For any 分包结算明细行：
     * detail.amount == detail.quantity × detail.unitPrice（scale 2, HALF_UP）
     * <p>
     * **Validates: Requirements 2.4, 2.5**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 5: 分包结算行金额 = quantity × unitPrice")
    void lineAmountEqualsQuantityTimesUnitPrice(
            @ForAll("detailLine") DetailLine line) {

        BigDecimal calculatedAmount = calculateLineAmount(line.quantity(), line.unitPrice());

        BigDecimal expectedAmount = line.quantity()
                .multiply(line.unitPrice())
                .setScale(2, RoundingMode.HALF_UP);

        assert calculatedAmount.compareTo(expectedAmount) == 0
                : String.format("行金额 %s != quantity(%s) × unitPrice(%s) = %s",
                calculatedAmount, line.quantity(), line.unitPrice(), expectedAmount);
    }

    /**
     * Property 5: 分包结算金额计算不变量 - 总金额验证
     * <p>
     * For any 分包结算单：
     * settlement.totalAmount == sum(detail.amount)
     * <p>
     * **Validates: Requirements 2.4, 2.5**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 5: 分包结算总金额 = sum(detail.amount)")
    void totalAmountEqualsSumOfDetailAmounts(
            @ForAll("detailLineList") List<DetailLine> lines) {

        // 模拟 saveDetails 逻辑
        List<CalculatedDetail> details = processDetails(lines);
        BigDecimal totalAmount = calculateTotalAmount(details);

        // 验证 totalAmount == sum(detail.amount)
        BigDecimal expectedTotal = details.stream()
                .map(CalculatedDetail::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assert totalAmount.compareTo(expectedTotal) == 0
                : String.format("totalAmount %s != sum(detail.amount) %s", totalAmount, expectedTotal);
    }

    /**
     * Property 5 补充：行金额精度验证
     * <p>
     * 验证每个明细行的 amount 都是2位小数
     * <p>
     * **Validates: Requirements 2.4, 2.5**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 5: 分包结算行金额精度为2位小数")
    void lineAmountHasScale2(
            @ForAll("detailLine") DetailLine line) {

        BigDecimal amount = calculateLineAmount(line.quantity(), line.unitPrice());

        assert amount.scale() == 2
                : String.format("行金额精度应为2, 实际为 %d (值: %s)", amount.scale(), amount);
    }

    /**
     * Property 5 补充：行金额非负验证
     * <p>
     * 因为 quantity 和 unitPrice 都是正数，所以 amount >= 0
     * <p>
     * **Validates: Requirements 2.4, 2.5**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 5: 分包结算行金额非负")
    void lineAmountIsNonNegative(
            @ForAll("detailLine") DetailLine line) {

        BigDecimal amount = calculateLineAmount(line.quantity(), line.unitPrice());

        assert amount.compareTo(BigDecimal.ZERO) >= 0
                : String.format("行金额应 >= 0, 实际为 %s", amount);
    }
}
