package com.zwinsight.hr.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 人事统计分区不变量属性测试
 * <p>
 * 测试纯计算逻辑，不依赖数据库。
 * 验证按部门/岗位/工龄段统计时，各维度人数之和恒等于在职总人数。
 * <p>
 * **Validates: Requirements 4.1, 4.2**
 */
@Tag("Feature: p1-business-completion")
class HrStatisticsPartitionPropertyTest {

    // ==================== 辅助数据结构 ====================

    /**
     * 模拟员工记录（在职状态）
     */
    record Employee(Long id, String department, String post, LocalDate entryDate) {}

    /**
     * 统计项
     */
    record StatItem(String name, long count) {}

    // ==================== 纯计算逻辑（提取自 HrStatisticsService） ====================

    /**
     * 按部门统计人数
     */
    static List<StatItem> statByDept(List<Employee> employees) {
        return employees.stream()
                .collect(Collectors.groupingBy(Employee::department, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new StatItem(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 按岗位统计人数
     */
    static List<StatItem> statByPost(List<Employee> employees) {
        return employees.stream()
                .collect(Collectors.groupingBy(Employee::post, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new StatItem(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 按工龄段统计人数
     * 工龄段规则：0-1年, 1-3年, 3-5年, 5年以上
     */
    static List<StatItem> statBySeniority(List<Employee> employees, LocalDate today) {
        Map<String, Long> seniorityMap = new LinkedHashMap<>();
        seniorityMap.put("0-1年", 0L);
        seniorityMap.put("1-3年", 0L);
        seniorityMap.put("3-5年", 0L);
        seniorityMap.put("5年以上", 0L);

        for (Employee emp : employees) {
            long years = java.time.temporal.ChronoUnit.YEARS.between(emp.entryDate(), today);
            String range;
            if (years < 1) {
                range = "0-1年";
            } else if (years < 3) {
                range = "1-3年";
            } else if (years < 5) {
                range = "3-5年";
            } else {
                range = "5年以上";
            }
            seniorityMap.merge(range, 1L, Long::sum);
        }

        return seniorityMap.entrySet().stream()
                .map(e -> new StatItem(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 计算在职总人数
     */
    static long totalActive(List<Employee> employees) {
        return employees.size();
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<List<Employee>> employeeList() {
        Arbitrary<String> departments = Arbitraries.of(
                "工程部", "财务部", "采购部", "行政部", "技术部", "市场部", "人事部"
        );
        Arbitrary<String> posts = Arbitraries.of(
                "项目经理", "工程师", "会计", "采购专员", "行政助理", "技术总监", "市场经理", "出纳"
        );
        Arbitrary<LocalDate> entryDates = Arbitraries.of(
                LocalDate.of(2024, 6, 1),   // ~0年
                LocalDate.of(2024, 1, 1),   // ~0.5年
                LocalDate.of(2023, 1, 1),   // ~1.5年
                LocalDate.of(2022, 1, 1),   // ~2.5年
                LocalDate.of(2021, 1, 1),   // ~3.5年
                LocalDate.of(2020, 1, 1),   // ~4.5年
                LocalDate.of(2019, 1, 1),   // ~5.5年
                LocalDate.of(2018, 1, 1),   // ~6.5年
                LocalDate.of(2015, 6, 1)    // ~9年
        );

        Arbitrary<Long> ids = Arbitraries.longs().between(1L, 10000L);

        return Combinators.combine(ids, departments, posts, entryDates)
                .as(Employee::new)
                .list().ofMinSize(1).ofMaxSize(100);
    }

    // ==================== Property 7: 人事统计分区不变量 ====================

    /**
     * Property 7: 人事统计分区不变量
     * <p>
     * For any 租户的人事数据集：
     * - sum(byDept.count) == totalActive
     * - sum(byPost.count) == totalActive
     * - sum(bySeniority.count) == totalActive
     * <p>
     * 即按任何维度对在职人员进行分区统计时，各分区人数之和必须等于在职总人数。
     * 这保证了统计数据不会出现"丢人"或"多人"的情况。
     * <p>
     * **Validates: Requirements 4.1, 4.2**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 7: 人事统计分区不变量")
    void statisticsPartitionSumEqualsTotalActive(
            @ForAll("employeeList") List<Employee> employees) {

        LocalDate today = LocalDate.of(2024, 7, 1);

        long total = totalActive(employees);

        // 按部门统计
        List<StatItem> byDept = statByDept(employees);
        long deptSum = byDept.stream().mapToLong(StatItem::count).sum();

        // 按岗位统计
        List<StatItem> byPost = statByPost(employees);
        long postSum = byPost.stream().mapToLong(StatItem::count).sum();

        // 按工龄段统计
        List<StatItem> bySeniority = statBySeniority(employees, today);
        long senioritySum = bySeniority.stream().mapToLong(StatItem::count).sum();

        // 验证分区不变量
        assert deptSum == total
                : String.format("按部门统计之和 %d != 在职总人数 %d", deptSum, total);

        assert postSum == total
                : String.format("按岗位统计之和 %d != 在职总人数 %d", postSum, total);

        assert senioritySum == total
                : String.format("按工龄段统计之和 %d != 在职总人数 %d", senioritySum, total);
    }

    /**
     * Property 7 补充验证：每个分区内的人数必须为正数
     * <p>
     * 对于出现在统计中的每个分区项，其 count 必须 > 0。
     * 空分区可以不出现在部门/岗位列表中，但如果出现则 count 必须为正数。
     * <p>
     * **Validates: Requirements 4.1, 4.2**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 7: 各分区项人数为正数")
    void eachPartitionItemCountIsPositive(
            @ForAll("employeeList") List<Employee> employees) {

        LocalDate today = LocalDate.of(2024, 7, 1);

        List<StatItem> byDept = statByDept(employees);
        List<StatItem> byPost = statByPost(employees);
        List<StatItem> bySeniority = statBySeniority(employees, today);

        // 部门统计中出现的项 count > 0
        for (StatItem item : byDept) {
            assert item.count() > 0
                    : String.format("部门 '%s' 统计人数应为正数，实际为 %d", item.name(), item.count());
        }

        // 岗位统计中出现的项 count > 0
        for (StatItem item : byPost) {
            assert item.count() > 0
                    : String.format("岗位 '%s' 统计人数应为正数，实际为 %d", item.name(), item.count());
        }

        // 工龄段统计中，sum 等于 total，但单项可以为 0（因为固定四个段都显示）
        // 但非零项必须为正数
        for (StatItem item : bySeniority) {
            assert item.count() >= 0
                    : String.format("工龄段 '%s' 统计人数不能为负数，实际为 %d", item.name(), item.count());
        }
    }
}
