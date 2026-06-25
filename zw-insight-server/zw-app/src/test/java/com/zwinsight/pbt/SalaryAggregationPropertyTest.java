package com.zwinsight.pbt;

import net.jqwik.api.*;
import net.jqwik.api.constraints.Size;
import org.assertj.core.api.Assertions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Property 3：薪资汇总聚合精度
 * <p>
 * 验证：分组汇总后各组金额之和等于全部金额总和。
 * 所有金额保持 scale=2。
 * <p>
 * Validates: Requirements 2.1
 */
@Tag("Feature: p1-system-integrity, Property 3: 薪资汇总聚合精度")
class SalaryAggregationPropertyTest {

    /**
     * 模拟薪资记录
     */
    record SalaryEntry(String teamName, BigDecimal amount) {}

    /**
     * 核心业务逻辑：按班组分组汇总薪资金额
     */
    static Map<String, BigDecimal> aggregateByTeam(List<SalaryEntry> entries) {
        return entries.stream()
                .collect(Collectors.groupingBy(
                        SalaryEntry::teamName,
                        Collectors.reducing(BigDecimal.ZERO,
                                SalaryEntry::amount,
                                BigDecimal::add)
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().setScale(2, RoundingMode.HALF_UP)
                ));
    }

    /**
     * 计算总额
     */
    static BigDecimal totalAmount(List<SalaryEntry> entries) {
        return entries.stream()
                .map(SalaryEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Property(tries = 100)
    void sumOfGroupedAmounts_equalsTotal(
            @ForAll("salaryEntries") List<SalaryEntry> entries) {
        Map<String, BigDecimal> grouped = aggregateByTeam(entries);
        BigDecimal groupedTotal = grouped.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal directTotal = totalAmount(entries);

        Assertions.assertThat(groupedTotal).isEqualByComparingTo(directTotal);
    }

    @Property(tries = 100)
    void allGroupedAmounts_haveScale2(
            @ForAll("salaryEntries") List<SalaryEntry> entries) {
        Map<String, BigDecimal> grouped = aggregateByTeam(entries);
        for (BigDecimal amount : grouped.values()) {
            Assertions.assertThat(amount.scale()).isLessThanOrEqualTo(2);
        }
    }

    @Provide
    Arbitrary<List<SalaryEntry>> salaryEntries() {
        Arbitrary<String> teams = Arbitraries.of("班组A", "班组B", "班组C", "班组D");
        Arbitrary<BigDecimal> amounts = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(100), BigDecimal.valueOf(50000))
                .ofScale(2);
        return Combinators.combine(teams, amounts)
                .as(SalaryEntry::new)
                .list().ofMinSize(1).ofMaxSize(50);
    }
}
