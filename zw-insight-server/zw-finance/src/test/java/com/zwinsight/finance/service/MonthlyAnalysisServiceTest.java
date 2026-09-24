package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizMonthlyOperationAnalysis;
import com.zwinsight.finance.mapper.BizMonthlyOperationAnalysisMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MonthlyAnalysisService 单元测试（V2026_67，资金流转 §9 月度经营分析表）
 * <p>钉住三处「如实为空」的口径纪律：本月发生无上月基期时为 null（不用 0 冒充）、
 * 间接费六类累计支付/应付未付为 null（不拿 0 冒充已付清）、未归类行仅在有账户时写入；
 * 以及同月重跑为覆盖更新（幂等，不重复插行）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MonthlyAnalysisServiceTest {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Long PROJECT_ID = 90001L;

    @Mock
    private BizMonthlyOperationAnalysisMapper analysisMapper;

    @InjectMocks
    private MonthlyAnalysisService service;

    private String thisMonth() {
        return YearMonth.now().format(MONTH_FMT);
    }

    private String lastMonth() {
        return YearMonth.now().minusMonths(1).format(MONTH_FMT);
    }

    /** 构造 CBS 聚合行（sumCbsByCategory 的返回结构） */
    private Map<String, Object> cbs(String code, String budget, String occurred, String forecast, int accounts) {
        Map<String, Object> row = new HashMap<>();
        row.put("categoryCode", code);
        row.put("budgetAmount", new BigDecimal(budget));
        row.put("cumulativeOccurred", new BigDecimal(occurred));
        row.put("forecastFinal", new BigDecimal(forecast));
        row.put("accountCount", accounts);
        return row;
    }

    private Map<String, Object> paid(String code, String amount) {
        Map<String, Object> row = new HashMap<>();
        row.put("categoryCode", code);
        row.put("cumulativePaid", new BigDecimal(amount));
        return row;
    }

    private Map<String, Object> lastOccurred(String code, String amount) {
        Map<String, Object> row = new HashMap<>();
        row.put("categoryCode", code);
        row.put("cumulativeOccurred", new BigDecimal(amount));
        return row;
    }

    /** 直接费四类有 CBS 账户 + 招待费有账户（覆盖两种 paid 口径） */
    private void stubTypicalCbs() {
        when(analysisMapper.sumCbsByCategory(PROJECT_ID)).thenReturn(List.of(
                cbs("LABOR", "5000000", "4800000", "5400000", 2),
                cbs("MATERIAL", "19000000", "9000000", "22500000", 1),
                cbs("MACHINE", "2800000", "2600000", "3200000", 2),
                cbs("SUBCONTRACT", "4000000", "4000000", "4800000", 1),
                cbs("ENTERTAIN", "600000", "40700", "480000", 1)));
        when(analysisMapper.sumContractPaidByCategory(PROJECT_ID)).thenReturn(List.of(
                paid("LABOR", "5000000"), paid("MATERIAL", "1000000"),
                paid("MACHINE", "200000"), paid("SUBCONTRACT", "400000")));
        when(analysisMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
    }

    private BizMonthlyOperationAnalysis captured(int index) {
        ArgumentCaptor<BizMonthlyOperationAnalysis> captor =
                ArgumentCaptor.forClass(BizMonthlyOperationAnalysis.class);
        verify(analysisMapper, times(index + 1)).insert(captor.capture());
        return captor.getAllValues().get(index);
    }

    private BizMonthlyOperationAnalysis findByCode(List<BizMonthlyOperationAnalysis> rows, String code) {
        return rows.stream().filter(r -> code.equals(r.getCategoryCode())).findFirst().orElseThrow();
    }

    private List<BizMonthlyOperationAnalysis> captureAllInserts() {
        ArgumentCaptor<BizMonthlyOperationAnalysis> captor =
                ArgumentCaptor.forClass(BizMonthlyOperationAnalysis.class);
        verify(analysisMapper, org.mockito.Mockito.atLeastOnce()).insert(captor.capture());
        return captor.getAllValues();
    }

    @Nested
    @DisplayName("generate() 生成与口径纪律")
    class GenerateTests {

        @Test
        @DisplayName("正常路径 — 有上月行时本月发生=两时点差值，十类恒定写入")
        void generate_withLastMonth_computesDelta() {
            stubTypicalCbs();
            when(analysisMapper.selectLastMonthOccurred(PROJECT_ID, lastMonth())).thenReturn(List.of(
                    lastOccurred("LABOR", "4500000"),
                    lastOccurred("MATERIAL", "8500000"),
                    lastOccurred("ENTERTAIN", "20000")));

            Map<String, Object> report = service.generate(PROJECT_ID, thisMonth());

            // §9 十类恒定生成（无账户的类金额为 0，保证表格结构完整）
            assertThat(report.get("rows")).isEqualTo(10);
            List<BizMonthlyOperationAnalysis> rows = captureAllInserts();
            assertThat(rows).hasSize(10);

            BizMonthlyOperationAnalysis labor = findByCode(rows, "LABOR");
            // 本月发生 = 4800000 − 4500000 = 300000
            assertThat(labor.getCurrentMonthOccurred()).isEqualByComparingTo("300000");
            assertThat(labor.getOccurredBasis())
                    .isEqualTo(BizMonthlyOperationAnalysis.OCCURRED_VS_LAST_MONTH);
            // 直接费四类：累计支付有权威来源，应付未付 = 累计发生 − 累计支付
            assertThat(labor.getCumulativePaid()).isEqualByComparingTo("5000000");
            assertThat(labor.getPayableOutstanding()).isEqualByComparingTo("-200000");
            assertThat(labor.getPaidBasis())
                    .isEqualTo(BizMonthlyOperationAnalysis.PAID_APPROVAL_WRITEBACK);

            // 材料：本月发生 9000000 − 8500000 = 500000；应付未付 9000000 − 1000000 = 8000000
            BizMonthlyOperationAnalysis material = findByCode(rows, "MATERIAL");
            assertThat(material.getCurrentMonthOccurred()).isEqualByComparingTo("500000");
            assertThat(material.getPayableOutstanding()).isEqualByComparingTo("8000000");
        }

        @Test
        @DisplayName("口径纪律 — 间接费无付款数据源时累计支付/应付未付为 null，不用 0 冒充已付清")
        void generate_indirectCategory_paidIsNull() {
            stubTypicalCbs();
            when(analysisMapper.selectLastMonthOccurred(eq(PROJECT_ID), any())).thenReturn(List.of());

            service.generate(PROJECT_ID, thisMonth());

            BizMonthlyOperationAnalysis entertain = findByCode(captureAllInserts(), "ENTERTAIN");
            assertThat(entertain.getCumulativePaid()).isNull();
            assertThat(entertain.getPayableOutstanding()).isNull();
            assertThat(entertain.getPaidBasis())
                    .isEqualTo(BizMonthlyOperationAnalysis.PAID_NO_PAYMENT_SOURCE);
            // 但预算/累计发生/预计最终仍来自 CBS 真实账户，不为空
            assertThat(entertain.getBudgetAmount()).isEqualByComparingTo("600000");
            assertThat(entertain.getCumulativeOccurred()).isEqualByComparingTo("40700");
            assertThat(entertain.getAccountCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("口径纪律 — 首次生成无上月行时本月发生为 null（NO_BASELINE），不得用 0 冒充")
        void generate_noBaseline_currentMonthNull() {
            stubTypicalCbs();
            when(analysisMapper.selectLastMonthOccurred(eq(PROJECT_ID), any())).thenReturn(List.of());

            service.generate(PROJECT_ID, thisMonth());

            List<BizMonthlyOperationAnalysis> rows = captureAllInserts();
            assertThat(rows).allSatisfy(r -> {
                // 有账户的类：NO_BASELINE + null；无账户的类：NO_DATA_SOURCE + null
                assertThat(r.getCurrentMonthOccurred()).isNull();
                assertThat(r.getOccurredBasis()).isIn(
                        BizMonthlyOperationAnalysis.OCCURRED_NO_BASELINE,
                        BizMonthlyOperationAnalysis.OCCURRED_NO_DATA_SOURCE);
            });
            assertThat(findByCode(rows, "LABOR").getOccurredBasis())
                    .isEqualTo(BizMonthlyOperationAnalysis.OCCURRED_NO_BASELINE);
            assertThat(findByCode(rows, "TAX").getOccurredBasis())
                    .isEqualTo(BizMonthlyOperationAnalysis.OCCURRED_NO_DATA_SOURCE);
            assertThat(findByCode(rows, "TAX").getAccountCount()).isZero();
        }

        @Test
        @DisplayName("未归类行 — 仅在确有未归类账户时写入，否则跳过（避免恒 0 噪音行）")
        void generate_unclassifiedRowOnlyWhenPresent() {
            stubTypicalCbs();
            when(analysisMapper.selectLastMonthOccurred(eq(PROJECT_ID), any())).thenReturn(List.of());

            // 无未归类账户 → 只写十类
            service.generate(PROJECT_ID, thisMonth());
            assertThat(captureAllInserts()).hasSize(10);

            // 有未归类账户 → 追加第 11 行，且不并入其他类
            org.mockito.Mockito.reset(analysisMapper);
            when(analysisMapper.sumCbsByCategory(PROJECT_ID)).thenReturn(List.of(
                    cbs("LABOR", "5000000", "4800000", "5400000", 2),
                    cbs("UNCLASSIFIED", "800000", "300000", "750000", 1)));
            when(analysisMapper.sumContractPaidByCategory(PROJECT_ID)).thenReturn(List.of());
            when(analysisMapper.selectLastMonthOccurred(eq(PROJECT_ID), any())).thenReturn(List.of());
            when(analysisMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            Map<String, Object> report = service.generate(PROJECT_ID, thisMonth());

            assertThat(report.get("skippedUnclassified")).isEqualTo(false);
            assertThat(report.get("rows")).isEqualTo(11);
            BizMonthlyOperationAnalysis unclassified = findByCode(captureAllInserts(), "UNCLASSIFIED");
            assertThat(unclassified.getCategoryName()).isEqualTo("未归类");
            assertThat(unclassified.getCumulativeOccurred()).isEqualByComparingTo("300000");
        }

        @Test
        @DisplayName("幂等 — 同项目同月重跑为覆盖更新（updateById），不重复插行")
        void generate_rerun_updatesInsteadOfInsert() {
            stubTypicalCbs();
            when(analysisMapper.selectLastMonthOccurred(eq(PROJECT_ID), any())).thenReturn(List.of());
            BizMonthlyOperationAnalysis existingRow = new BizMonthlyOperationAnalysis();
            existingRow.setId(777L);
            existingRow.setProjectId(PROJECT_ID);
            existingRow.setAnalysisMonth(thisMonth());
            existingRow.setCategoryCode("LABOR");
            existingRow.setCategoryName("人工");
            // 既有十行（与十类一一对应）
            List<BizMonthlyOperationAnalysis> existingList = new ArrayList<>();
            existingList.add(existingRow);
            for (String code : List.of("MATERIAL", "MACHINE", "SUBCONTRACT", "MEASURE", "ADMIN",
                    "ENTERTAIN", "TRAVEL_VEHICLE", "PROFESSIONAL", "TAX")) {
                BizMonthlyOperationAnalysis r = new BizMonthlyOperationAnalysis();
                r.setId((long) (800 + code.hashCode() % 100));
                r.setProjectId(PROJECT_ID);
                r.setAnalysisMonth(thisMonth());
                r.setCategoryCode(code);
                existingList.add(r);
            }
            when(analysisMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(existingList);

            Map<String, Object> report = service.generate(PROJECT_ID, thisMonth());

            assertThat(report.get("inserted")).isEqualTo(0);
            assertThat(report.get("updated")).isEqualTo(10);
            verify(analysisMapper, never()).insert(any(BizMonthlyOperationAnalysis.class));
            verify(analysisMapper, times(10)).updateById(any(BizMonthlyOperationAnalysis.class));
            // 覆盖更新保留原主键（不新建行）
            ArgumentCaptor<BizMonthlyOperationAnalysis> captor =
                    ArgumentCaptor.forClass(BizMonthlyOperationAnalysis.class);
            verify(analysisMapper, times(10)).updateById(captor.capture());
            assertThat(captor.getAllValues()).extracting(BizMonthlyOperationAnalysis::getId)
                    .doesNotContainNull();
        }

        @Test
        @DisplayName("异常路径 — 缺项目/月份格式非法/未来月份均被拒绝（不静默按当月处理）")
        void generate_invalidArgs_rejected() {
            assertThatThrownBy(() -> service.generate(null, thisMonth()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("项目ID");
            assertThatThrownBy(() -> service.generate(PROJECT_ID, "2026/09"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("yyyy-MM");
            assertThatThrownBy(() -> service.generate(PROJECT_ID, ""))
                    .isInstanceOf(BusinessException.class);
            // 未来月份无发生额可言，必须拒绝而不是产出全 0 的空表
            String nextMonth = YearMonth.now().plusMonths(1).format(MONTH_FMT);
            assertThatThrownBy(() -> service.generate(PROJECT_ID, nextMonth))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未来月份");
            verify(analysisMapper, never()).insert(any(BizMonthlyOperationAnalysis.class));
        }
    }

    @Nested
    @DisplayName("generateAll() 批量生成与失败可见")
    class GenerateAllTests {

        @Test
        @DisplayName("单项目失败不中断其余项目，失败明细随返回（不静默跳过）")
        void generateAll_partialFailure_reported() {
            when(analysisMapper.selectProjectIdsWithAccounts()).thenReturn(List.of(1L, 2L, 3L));
            // 项目 1 与 3 正常，项目 2 抛异常
            when(analysisMapper.sumCbsByCategory(1L)).thenReturn(List.of());
            when(analysisMapper.sumCbsByCategory(3L)).thenReturn(List.of());
            when(analysisMapper.sumCbsByCategory(2L)).thenThrow(new RuntimeException("DB 连接中断"));
            when(analysisMapper.sumContractPaidByCategory(any())).thenReturn(List.of());
            when(analysisMapper.selectLastMonthOccurred(any(), any())).thenReturn(List.of());
            when(analysisMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            Map<String, Object> report = service.generateAll(thisMonth());

            assertThat(report.get("projectCount")).isEqualTo(3);
            assertThat(report.get("successCount")).isEqualTo(2);
            @SuppressWarnings("unchecked")
            List<String> failed = (List<String>) report.get("failedProjects");
            assertThat(failed).hasSize(1);
            assertThat(failed.get(0)).contains("2:").contains("DB 连接中断");
        }

        @Test
        @DisplayName("边界路径 — 无已建 CBS 账户的项目时返回 0 且不写库（不伪造空表数据）")
        void generateAll_noProjects_noWrites() {
            when(analysisMapper.selectProjectIdsWithAccounts()).thenReturn(List.of());

            Map<String, Object> report = service.generateAll(thisMonth());

            assertThat(report.get("projectCount")).isEqualTo(0);
            assertThat(report.get("successCount")).isEqualTo(0);
            verify(analysisMapper, never()).insert(any(BizMonthlyOperationAnalysis.class));
        }
    }

    @Nested
    @DisplayName("query() 矩阵查询与合计口径")
    class QueryTests {

        private BizMonthlyOperationAnalysis row(String code, String name, String occurred,
                                                String paid, String currentMonth) {
            BizMonthlyOperationAnalysis r = new BizMonthlyOperationAnalysis();
            r.setProjectId(PROJECT_ID);
            r.setAnalysisMonth(thisMonth());
            r.setCategoryCode(code);
            r.setCategoryName(name);
            r.setBudgetAmount(new BigDecimal("1000000"));
            r.setCumulativeOccurred(new BigDecimal(occurred));
            r.setCumulativePaid(paid == null ? null : new BigDecimal(paid));
            r.setPayableOutstanding(paid == null ? null
                    : new BigDecimal(occurred).subtract(new BigDecimal(paid)));
            r.setForecastFinal(new BigDecimal("1100000"));
            r.setCurrentMonthOccurred(currentMonth == null ? null : new BigDecimal(currentMonth));
            r.setAccountCount(1);
            return r;
        }

        @Test
        @DisplayName("合计口径 — 应付未付合计仅覆盖有付款数据源的类，且 notes 明示范围")
        @SuppressWarnings("unchecked")
        void query_totalsScopeLimitedToDirectCategories() {
            when(analysisMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                    row("MATERIAL", "材料", "9000000", "1000000", "500000"),
                    row("LABOR", "人工", "4800000", "5000000", "300000"),
                    row("ENTERTAIN", "招待", "40700", null, null)));

            Map<String, Object> result = service.query(PROJECT_ID, thisMonth());

            Map<String, Object> totals = (Map<String, Object>) result.get("totals");
            // 累计发生合计含全部三类
            assertThat((BigDecimal) totals.get("cumulativeOccurred"))
                    .isEqualByComparingTo("13840700");
            // 累计支付合计只含直接费两类
            assertThat((BigDecimal) totals.get("cumulativePaid")).isEqualByComparingTo("6000000");
            // 应付未付合计 = (9000000+4800000) − 6000000 = 7800000，**不含招待的 40700**
            assertThat((BigDecimal) totals.get("directOccurredTotal")).isEqualByComparingTo("13800000");
            assertThat((BigDecimal) totals.get("payableOutstanding")).isEqualByComparingTo("7800000");
            assertThat(totals.get("paidScope")).isEqualTo("DIRECT_FOUR_CATEGORIES");
            // 本月发生有一类为 null → 合计只含已知项，且 complete=false + notes 说明
            assertThat((BigDecimal) totals.get("currentMonthOccurred")).isEqualByComparingTo("800000");
            assertThat(totals.get("currentMonthComplete")).isEqualTo(false);
            List<String> notes = (List<String>) result.get("notes");
            assertThat(notes).anyMatch(n -> n.contains("招待"));
            assertThat((List<String>) result.get("paidNullCategories")).containsExactly("招待");
        }

        @Test
        @DisplayName("行序 — 按 §9 固定十类顺序返回，未归类殿后，未生成的类不出现")
        @SuppressWarnings("unchecked")
        void query_rowsOrderedByDocument() {
            when(analysisMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                    row("TAX", "财税", "0", null, null),
                    row("UNCLASSIFIED", "未归类", "300000", null, null),
                    row("LABOR", "人工", "4800000", "5000000", "300000"),
                    row("MATERIAL", "材料", "9000000", "1000000", "500000")));

            Map<String, Object> result = service.query(PROJECT_ID, thisMonth());

            List<BizMonthlyOperationAnalysis> rows =
                    (List<BizMonthlyOperationAnalysis>) result.get("rows");
            assertThat(rows).extracting(BizMonthlyOperationAnalysis::getCategoryCode)
                    .containsExactly("LABOR", "MATERIAL", "TAX", "UNCLASSIFIED");
        }

        @Test
        @DisplayName("边界路径 — 未生成时行为空且 notes 提示先生成（不返回全 0 假表）")
        @SuppressWarnings("unchecked")
        void query_notGenerated_emptyWithNote() {
            when(analysisMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            Map<String, Object> result = service.query(PROJECT_ID, thisMonth());

            assertThat((List<BizMonthlyOperationAnalysis>) result.get("rows")).isEmpty();
            Map<String, Object> totals = (Map<String, Object>) result.get("totals");
            // 无数据时本月发生与应付未付合计为 null（不是 0）
            assertThat(totals.get("currentMonthOccurred")).isNull();
            assertThat(totals.get("payableOutstanding")).isNull();
            assertThat((List<String>) result.get("notes"))
                    .anyMatch(n -> n.contains("尚未生成"));
        }

        @Test
        @DisplayName("异常路径 — 缺项目ID或月份非法时拒绝查询")
        void query_invalidArgs_rejected() {
            assertThatThrownBy(() -> service.query(null, thisMonth()))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service.query(PROJECT_ID, "bad-month"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("yyyy-MM");
        }

        @Test
        @DisplayName("latestMonth — 无数据时返回 null（前端据此提示先生成，不默认当月）")
        void latestMonth_nullWhenEmpty() {
            when(analysisMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            assertThat(service.latestMonth(PROJECT_ID)).isNull();

            BizMonthlyOperationAnalysis r = row("LABOR", "人工", "1", null, null);
            r.setAnalysisMonth("2026-08");
            when(analysisMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(r));
            assertThat(service.latestMonth(PROJECT_ID)).isEqualTo("2026-08");
        }
    }

    @Test
    @DisplayName("类别清单 — §9 十类中文名与行序固定（前端表头与导出共用，防两处硬编码漂移）")
    void categoryNames_tenInDocumentOrder() {
        Map<String, String> names = service.categoryNames();
        assertThat(names).hasSize(10);
        assertThat(new ArrayList<>(names.keySet())).containsExactly(
                "LABOR", "MATERIAL", "MACHINE", "SUBCONTRACT", "MEASURE",
                "ADMIN", "ENTERTAIN", "TRAVEL_VEHICLE", "PROFESSIONAL", "TAX");
        assertThat(new ArrayList<>(names.values())).containsExactly(
                "人工", "材料", "机械", "分包", "措施",
                "管理", "招待", "差旅车辆", "专业服务", "财税");
    }
}
