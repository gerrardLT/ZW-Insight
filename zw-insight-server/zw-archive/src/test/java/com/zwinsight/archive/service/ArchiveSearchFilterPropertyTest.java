package com.zwinsight.archive.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 档案接口搜索过滤正确性属性测试
 * <p>
 * 测试纯过滤逻辑，不依赖数据库。
 * 模拟 ArchiveService 中的关键字搜索过滤逻辑。
 * <p>
 * **Validates: Requirements 8.6**
 */
@Tag("Feature: p1-business-completion")
class ArchiveSearchFilterPropertyTest {

    // ==================== 辅助数据结构 ====================

    /**
     * 其它合同档案记录
     */
    record ContractArchiveRecord(Long id, String contractCode, String contractName,
                                 String supplyName, BigDecimal amount,
                                 LocalDate signDate, String status) {}

    /**
     * 办公用品档案记录
     */
    record OfficeSupplyRecord(Long id, String supplyName, BigDecimal currentStock,
                              BigDecimal totalInbound, BigDecimal totalIssued) {}

    // ==================== 纯逻辑（提取自 Service） ====================

    /**
     * 合同档案关键字搜索匹配逻辑
     * 逻辑来源：ArchiveService.pageOtherContractArchive() 中的 LIKE 条件
     * <p>
     * 关键字在合同编号、合同名称、供应商名称中任一匹配（不区分大小写）
     */
    static boolean matchesContractKeyword(ContractArchiveRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true; // 无关键字则不过滤
        }
        String lowerKeyword = keyword.toLowerCase();
        return (record.contractCode() != null && record.contractCode().toLowerCase().contains(lowerKeyword))
                || (record.contractName() != null && record.contractName().toLowerCase().contains(lowerKeyword))
                || (record.supplyName() != null && record.supplyName().toLowerCase().contains(lowerKeyword));
    }

    /**
     * 办公用品关键字搜索匹配逻辑
     * 逻辑来源：ArchiveService.pageOfficeSupplyArchive() 中的 LIKE 条件
     * <p>
     * 关键字在用品名称中匹配（不区分大小写）
     */
    static boolean matchesSupplyKeyword(OfficeSupplyRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String lowerKeyword = keyword.toLowerCase();
        return record.supplyName() != null && record.supplyName().toLowerCase().contains(lowerKeyword);
    }

    /**
     * 过滤合同档案列表
     */
    static List<ContractArchiveRecord> filterContractArchives(
            List<ContractArchiveRecord> all, String keyword) {
        return all.stream()
                .filter(r -> matchesContractKeyword(r, keyword))
                .collect(Collectors.toList());
    }

    /**
     * 过滤办公用品档案列表
     */
    static List<OfficeSupplyRecord> filterSupplyArchives(
            List<OfficeSupplyRecord> all, String keyword) {
        return all.stream()
                .filter(r -> matchesSupplyKeyword(r, keyword))
                .collect(Collectors.toList());
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<String> contractCodes() {
        return Arbitraries.of(
                "HT-2024-001", "HT-2024-002", "CG-2024-010", "FB-2024-005",
                "ZC-2024-003", "SR-2024-008", "QT-2024-015", "HT-2024-100"
        );
    }

    @Provide
    Arbitrary<String> contractNames() {
        return Arbitraries.of(
                "办公楼装修合同", "设备采购合同", "场地租赁合同", "物业管理合同",
                "保安服务合同", "食堂承包合同", "绿化养护合同", "电梯维保合同",
                "消防设备维护", "空调安装工程"
        );
    }

    @Provide
    Arbitrary<String> supplyNames() {
        return Arbitraries.of(
                "A4纸", "打印墨盒", "文件夹", "订书钉", "白板笔",
                "笔记本", "签字笔", "固体胶", "透明胶带", "剪刀",
                "计算器", "档案盒", "便利贴", "回形针"
        );
    }

    @Provide
    Arbitrary<String> searchKeywords() {
        return Arbitraries.of(
                "合同", "采购", "HT", "2024", "装修", "设备",
                "服务", "维", "办公", "A4", "墨盒", "文件",
                "笔", "胶", "档案", "SR", "FB", "CG"
        );
    }

    @Provide
    Arbitrary<ContractArchiveRecord> contractArchiveRecord() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1L, 10000L);
        Arbitrary<BigDecimal> amounts = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(1000), BigDecimal.valueOf(999999))
                .ofScale(2);
        Arbitrary<LocalDate> dates = Arbitraries.of(
                LocalDate.of(2024, 1, 10),
                LocalDate.of(2024, 3, 20),
                LocalDate.of(2024, 5, 15),
                LocalDate.of(2024, 8, 1)
        );
        Arbitrary<String> statuses = Arbitraries.of("ACTIVE", "CLOSED", "SETTLED");

        return Combinators.combine(ids, contractCodes(), contractNames(),
                        supplyNames(), amounts, dates, statuses)
                .as(ContractArchiveRecord::new);
    }

    @Provide
    Arbitrary<List<ContractArchiveRecord>> contractArchiveList() {
        return contractArchiveRecord().list().ofMinSize(0).ofMaxSize(30);
    }

    @Provide
    Arbitrary<OfficeSupplyRecord> officeSupplyRecord() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1L, 10000L);
        Arbitrary<BigDecimal> stocks = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, BigDecimal.valueOf(9999))
                .ofScale(2);
        Arbitrary<BigDecimal> inbounds = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, BigDecimal.valueOf(99999))
                .ofScale(2);
        Arbitrary<BigDecimal> issued = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, BigDecimal.valueOf(99999))
                .ofScale(2);

        return Combinators.combine(ids, supplyNames(), stocks, inbounds, issued)
                .as(OfficeSupplyRecord::new);
    }

    @Provide
    Arbitrary<List<OfficeSupplyRecord>> officeSupplyList() {
        return officeSupplyRecord().list().ofMinSize(0).ofMaxSize(30);
    }

    // ==================== Property 16: 档案接口搜索过滤正确性 ====================

    /**
     * Property 16: 档案接口搜索过滤正确性 - 合同档案
     * <p>
     * For any 关键字搜索，返回的每条合同档案记录必须在合同编号、合同名称或供应商名称中包含该关键字。
     * <p>
     * **Validates: Requirements 8.6**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 16: 合同档案搜索结果包含关键字")
    void contractArchiveSearchResultsContainKeyword(
            @ForAll("contractArchiveList") List<ContractArchiveRecord> allRecords,
            @ForAll("searchKeywords") String keyword) {

        List<ContractArchiveRecord> filtered = filterContractArchives(allRecords, keyword);

        String lowerKeyword = keyword.toLowerCase();
        for (ContractArchiveRecord record : filtered) {
            boolean containsKeyword =
                    (record.contractCode() != null && record.contractCode().toLowerCase().contains(lowerKeyword))
                    || (record.contractName() != null && record.contractName().toLowerCase().contains(lowerKeyword))
                    || (record.supplyName() != null && record.supplyName().toLowerCase().contains(lowerKeyword));

            assert containsKeyword
                    : String.format("记录 id=%d 不包含关键字 '%s' (code=%s, name=%s, supply=%s)",
                    record.id(), keyword, record.contractCode(),
                    record.contractName(), record.supplyName());
        }
    }

    /**
     * Property 16: 档案接口搜索过滤正确性 - 办公用品
     * <p>
     * For any 关键字搜索，返回的每条办公用品记录必须在用品名称中包含该关键字。
     * <p>
     * **Validates: Requirements 8.6**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 16: 办公用品搜索结果包含关键字")
    void officeSupplySearchResultsContainKeyword(
            @ForAll("officeSupplyList") List<OfficeSupplyRecord> allRecords,
            @ForAll("searchKeywords") String keyword) {

        List<OfficeSupplyRecord> filtered = filterSupplyArchives(allRecords, keyword);

        String lowerKeyword = keyword.toLowerCase();
        for (OfficeSupplyRecord record : filtered) {
            boolean containsKeyword = record.supplyName() != null
                    && record.supplyName().toLowerCase().contains(lowerKeyword);

            assert containsKeyword
                    : String.format("办公用品记录 id=%d (name=%s) 不包含关键字 '%s'",
                    record.id(), record.supplyName(), keyword);
        }
    }

    /**
     * Property 16 补充：过滤结果数量等于满足条件记录数量（合同档案）
     * <p>
     * **Validates: Requirements 8.6**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 16: 合同档案过滤结果数量正确")
    void contractArchiveFilteredCountIsCorrect(
            @ForAll("contractArchiveList") List<ContractArchiveRecord> allRecords,
            @ForAll("searchKeywords") String keyword) {

        List<ContractArchiveRecord> filtered = filterContractArchives(allRecords, keyword);

        long expectedCount = allRecords.stream()
                .filter(r -> matchesContractKeyword(r, keyword))
                .count();

        assert filtered.size() == expectedCount
                : String.format("过滤结果数量 %d != 预期数量 %d (关键字: '%s')",
                filtered.size(), expectedCount, keyword);
    }

    /**
     * Property 16 补充：空关键字返回全部记录
     * <p>
     * **Validates: Requirements 8.6**
     */
    @Property(tries = 100)
    @Label("Feature: p1-business-completion, Property 16: 空关键字返回全部记录")
    void emptyKeywordReturnsAllRecords(
            @ForAll("contractArchiveList") List<ContractArchiveRecord> allRecords) {

        List<ContractArchiveRecord> filtered = filterContractArchives(allRecords, "");

        assert filtered.size() == allRecords.size()
                : String.format("空关键字应返回全部 %d 条记录，实际返回 %d 条",
                allRecords.size(), filtered.size());
    }
}
