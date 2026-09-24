package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizEntertainmentDetail;
import com.zwinsight.finance.domain.BizFundCategory;
import com.zwinsight.finance.domain.BizReimbursementDetail;
import com.zwinsight.finance.mapper.BizEntertainmentDetailMapper;
import com.zwinsight.finance.mapper.BizReimbursementDetailMapper;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReimbursementDetailService 单元测试（V2026_60 报销明细激活 + 招待费专控）
 * <p>核心断言：明细合计与总额强一致（不静默丢弃）、科目必须为支出向、
 * 招待费专项字段必填（对象/事由/日期）、替换式保存先清旧明细及其专项记录、
 * 分析仅罗列异常项（正常费用隐藏）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReimbursementDetailServiceTest {

    @Mock private BizReimbursementDetailMapper detailMapper;
    @Mock private BizEntertainmentDetailMapper entertainmentMapper;
    @Mock private FundCategoryService fundCategoryService;

    @InjectMocks
    private ReimbursementDetailService service;

    private BizReimbursementDetail detail(String categoryCode, String amount) {
        BizReimbursementDetail d = new BizReimbursementDetail();
        d.setCategoryCode(categoryCode);
        d.setExpenseType("测试费用");
        d.setAmount(new BigDecimal(amount));
        return d;
    }

    private BizEntertainmentDetail entertainment() {
        BizEntertainmentDetail e = new BizEntertainmentDetail();
        e.setEntertainDate(LocalDate.of(2026, 9, 20));
        e.setHostCompany("某建设单位");
        e.setEntertainReason("工程进度协调会用餐");
        e.setHostCount(3);
        e.setGuestCount(4);
        e.setPreApproved(1);
        e.setHasInvoice(1);
        return e;
    }

    private void stubCategoryValid() {
        BizFundCategory category = new BizFundCategory();
        category.setCode("EXP-INDIRECT-OFFICE");
        when(fundCategoryService.getByCode(anyString(), eq("EXPENSE"))).thenReturn(category);
    }

    @Nested
    @DisplayName("saveDetails() 明细级联保存与校验")
    class SaveTests {

        @Test
        @DisplayName("正常路径 — 明细合计等于总额时逐行落库并绑定来源与主表ID")
        void save_consistentDetails_persisted() {
            stubCategoryValid();
            when(detailMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            List<BizReimbursementDetail> details = new ArrayList<>();
            details.add(detail("EXP-INDIRECT-OFFICE", "60000"));
            details.add(detail("EXP-INDIRECT-TRAVEL", "40000"));

            service.saveDetails(BizReimbursementDetail.SOURCE_PROJECT, 700L, 10L,
                    details, new BigDecimal("100000"));

            ArgumentCaptor<BizReimbursementDetail> captor =
                    ArgumentCaptor.forClass(BizReimbursementDetail.class);
            verify(detailMapper, times(2)).insert(captor.capture());
            assertThat(captor.getAllValues()).allSatisfy(d -> {
                assertThat(d.getSourceType()).isEqualTo(BizReimbursementDetail.SOURCE_PROJECT);
                assertThat(d.getReimbursementId()).isEqualTo(700L);
            });
        }

        @Test
        @DisplayName("异常路径 — 明细合计与报销总额不一致被拦截（不静默按总额入账）")
        void save_inconsistentSum_rejected() {
            stubCategoryValid();
            List<BizReimbursementDetail> details = new ArrayList<>();
            details.add(detail("EXP-INDIRECT-OFFICE", "60000"));

            assertThatThrownBy(() -> service.saveDetails(
                    BizReimbursementDetail.SOURCE_PROJECT, 700L, 10L, details, new BigDecimal("100000")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不一致");
            verify(detailMapper, never()).insert(any(BizReimbursementDetail.class));
        }

        @Test
        @DisplayName("异常路径 — 明细未选费用科目被拦截（科目维度不可缺失）")
        void save_missingCategory_rejected() {
            List<BizReimbursementDetail> details = new ArrayList<>();
            details.add(detail(null, "10000"));

            assertThatThrownBy(() -> service.saveDetails(
                    BizReimbursementDetail.SOURCE_PROJECT, 700L, 10L, details, new BigDecimal("10000")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("必须选择费用科目");
        }

        @Test
        @DisplayName("异常路径 — 非支出向科目由 FundCategoryService 拒绝（方向校验不绕过）")
        void save_incomeCategory_rejected() {
            when(fundCategoryService.getByCode(anyString(), eq("EXPENSE")))
                    .thenThrow(new BusinessException(400, "科目方向不符"));
            List<BizReimbursementDetail> details = new ArrayList<>();
            details.add(detail("IN-CONTRACT", "10000"));

            assertThatThrownBy(() -> service.saveDetails(
                    BizReimbursementDetail.SOURCE_PROJECT, 700L, 10L, details, new BigDecimal("10000")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("方向不符");
        }

        @Test
        @DisplayName("异常路径 — 明细金额非正数被拦截")
        void save_nonPositiveAmount_rejected() {
            List<BizReimbursementDetail> details = new ArrayList<>();
            details.add(detail("EXP-INDIRECT-OFFICE", "0"));

            assertThatThrownBy(() -> service.saveDetails(
                    BizReimbursementDetail.SOURCE_PROJECT, 700L, 10L, details, BigDecimal.ZERO))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("金额必须大于0");
        }

        @Test
        @DisplayName("异常路径 — 非法报销来源被拦截（不得默认按 PROJECT 处理）")
        void save_invalidSourceType_rejected() {
            assertThatThrownBy(() -> service.saveDetails("UNKNOWN", 700L, 10L,
                    new ArrayList<>(), BigDecimal.TEN))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("报销来源不合法");
        }

        @Test
        @DisplayName("兼容路径 — details 为 null 时不写明细（存量单据与简易报销向后兼容）")
        void save_nullDetails_skipped() {
            service.saveDetails(BizReimbursementDetail.SOURCE_PROJECT, 700L, 10L,
                    null, new BigDecimal("10000"));

            verify(detailMapper, never()).insert(any(BizReimbursementDetail.class));
            verify(detailMapper, never()).delete(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("替换式保存 — 先删旧明细及其招待费专项记录，防孤儿数据残留")
        void save_replacesOldDetailsAndEntertainment() {
            stubCategoryValid();
            BizReimbursementDetail old = detail("EXP-INDIRECT-ENTERTAIN", "5000");
            old.setId(8801L);
            when(detailMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(old));
            List<BizReimbursementDetail> details = new ArrayList<>();
            details.add(detail("EXP-INDIRECT-OFFICE", "10000"));

            service.saveDetails(BizReimbursementDetail.SOURCE_PROJECT, 700L, 10L,
                    details, new BigDecimal("10000"));

            verify(entertainmentMapper).delete(any(LambdaQueryWrapper.class));
            verify(detailMapper).delete(any(LambdaQueryWrapper.class));
            verify(detailMapper).insert(any(BizReimbursementDetail.class));
        }
    }

    @Nested
    @DisplayName("招待费专项字段强校验（资金流转文档 §6.3）")
    class EntertainmentTests {

        @Test
        @DisplayName("正常路径 — 招待费明细携带完整专项信息时落库并绑定明细ID")
        void entertainment_complete_persisted() {
            stubCategoryValid();
            when(detailMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            BizReimbursementDetail d = detail(BizReimbursementDetail.CATEGORY_ENTERTAINMENT, "8600");
            d.setEntertainment(entertainment());

            service.saveDetails(BizReimbursementDetail.SOURCE_PROJECT, 701L, 10L,
                    new ArrayList<>(List.of(d)), new BigDecimal("8600"));

            ArgumentCaptor<BizEntertainmentDetail> captor =
                    ArgumentCaptor.forClass(BizEntertainmentDetail.class);
            verify(entertainmentMapper).insert(captor.capture());
            // 项目归属由主表 projectId 回填（明细未显式指定时）
            assertThat(captor.getValue().getProjectId()).isEqualTo(10L);
            assertThat(captor.getValue().getHostCompany()).isEqualTo("某建设单位");
        }

        @Test
        @DisplayName("异常路径 — 招待费缺专项信息被拦截（不允许只记金额不记对象事由）")
        void entertainment_missing_rejected() {
            stubCategoryValid();
            BizReimbursementDetail d = detail(BizReimbursementDetail.CATEGORY_ENTERTAINMENT, "8600");

            assertThatThrownBy(() -> service.saveDetails(
                    BizReimbursementDetail.SOURCE_PROJECT, 701L, 10L,
                    new ArrayList<>(List.of(d)), new BigDecimal("8600")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("必须填写专项信息");
        }

        @Test
        @DisplayName("异常路径 — 招待对象/事由/日期任一缺失均被拦截")
        void entertainment_partialFields_rejected() {
            stubCategoryValid();

            BizEntertainmentDetail noHost = entertainment();
            noHost.setHostCompany("  ");
            assertThatThrownBy(() -> saveWith(noHost, "8600"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("招待对象单位");

            BizEntertainmentDetail noReason = entertainment();
            noReason.setEntertainReason(null);
            assertThatThrownBy(() -> saveWith(noReason, "8600"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("招待事由");

            BizEntertainmentDetail noDate = entertainment();
            noDate.setEntertainDate(null);
            assertThatThrownBy(() -> saveWith(noDate, "8600"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("招待日期");
        }

        private void saveWith(BizEntertainmentDetail entertainment, String amount) {
            BizReimbursementDetail d = detail(BizReimbursementDetail.CATEGORY_ENTERTAINMENT, amount);
            d.setEntertainment(entertainment);
            when(detailMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            service.saveDetails(BizReimbursementDetail.SOURCE_PROJECT, 701L, 10L,
                    new ArrayList<>(List.of(d)), new BigDecimal(amount));
        }
    }

    @Nested
    @DisplayName("analyzeEntertainment() 招待费分析")
    class AnalyzeTests {

        private Map<String, Object> summary(String total, long count, String maxSingle,
                                           long noReason, long noHost, long noPreApproval, long noInvoice) {
            Map<String, Object> m = new HashMap<>();
            m.put("totalAmount", new BigDecimal(total));
            m.put("totalCount", count);
            m.put("maxSingleAmount", new BigDecimal(maxSingle));
            m.put("avgPerCapita", new BigDecimal("1200"));
            m.put("noReasonCount", noReason);
            m.put("noHostCount", noHost);
            m.put("noPreApprovalCount", noPreApproval);
            m.put("noInvoiceCount", noInvoice);
            return m;
        }

        @Test
        @DisplayName("正常路径 — 异常项按命中罗列并附处置建议，正常项不出现")
        @SuppressWarnings("unchecked")
        void analyze_listsOnlyAnomalies() {
            when(entertainmentMapper.analyzeEntertainment(eq(10L), any()))
                    .thenReturn(summary("86000", 12, "5800", 2, 0, 3, 0));
            when(entertainmentMapper.sumByHandler(10L)).thenReturn(List.of());
            when(entertainmentMapper.countSameHandlerSameDay(10L)).thenReturn(4L);

            Map<String, Object> result = service.analyzeEntertainment(10L, null);

            List<Map<String, Object>> anomalies = (List<Map<String, Object>>) result.get("anomalies");
            // 命中：无事由(2)、无事前审批(3)、同人同日多笔(4)；未命中：无对象、发票不完整
            assertThat(anomalies).extracting(a -> a.get("name"))
                    .containsExactly("无招待事由", "无事前审批", "同人同日多笔");
            assertThat(anomalies).allSatisfy(a -> assertThat(a.get("suggestion")).isNotNull());
            Map<String, Object> summaryResult = (Map<String, Object>) result.get("summary");
            assertThat((BigDecimal) summaryResult.get("totalAmount")).isEqualByComparingTo("86000");
        }

        @Test
        @DisplayName("边界路径 — 全部正常时异常清单为空（正常费用隐藏，不制造噪音）")
        @SuppressWarnings("unchecked")
        void analyze_allNormal_emptyAnomalies() {
            when(entertainmentMapper.analyzeEntertainment(eq(10L), any()))
                    .thenReturn(summary("20000", 5, "2800", 0, 0, 0, 0));
            when(entertainmentMapper.sumByHandler(10L)).thenReturn(List.of());
            when(entertainmentMapper.countSameHandlerSameDay(10L)).thenReturn(0L);

            Map<String, Object> result = service.analyzeEntertainment(10L, null);

            assertThat((List<Map<String, Object>>) result.get("anomalies")).isEmpty();
        }

        @Test
        @DisplayName("边界路径 — 聚合返回 null 时不抛 NPE（无招待费记录场景）")
        void analyze_nullSummary_noNpe() {
            when(entertainmentMapper.analyzeEntertainment(any(), any())).thenReturn(null);
            when(entertainmentMapper.sumByHandler(any())).thenReturn(List.of());
            when(entertainmentMapper.countSameHandlerSameDay(any())).thenReturn(null);

            Map<String, Object> result = service.analyzeEntertainment(null, null);

            assertThat(result.get("summary")).isNotNull();
            assertThat(result.get("anomalies")).isNotNull();
            // monthlyTrend 查询返回 null（Mapper 空结果）时不得透传 null，前端可直接遍历
            assertThat(result.get("monthlyTrend")).isNotNull();
        }

        private List<Map<String, Object>> trend(String month, String amount) {
            Map<String, Object> row = new HashMap<>();
            row.put("month", month);
            row.put("amount", new BigDecimal(amount));
            row.put("count", 3L);
            return List.of(row);
        }

        private String currentMonth() {
            return java.time.YearMonth.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        }

        @Test
        @DisplayName("§6.3 月度变化 — 近 6 月趋势透传，预算执行率按计划额计算")
        @SuppressWarnings("unchecked")
        void analyze_monthlyTrendAndBudgetRate() {
            when(entertainmentMapper.analyzeEntertainment(eq(10L), any()))
                    .thenReturn(summary("86000", 12, "5800", 0, 0, 0, 0));
            when(entertainmentMapper.sumByHandler(10L)).thenReturn(List.of());
            when(entertainmentMapper.countSameHandlerSameDay(10L)).thenReturn(0L);
            when(entertainmentMapper.monthlyTrend(eq(10L), any()))
                    .thenReturn(trend(currentMonth(), "50000"));
            when(entertainmentMapper.sumEntertainmentPlanLimit(eq(10L), anyInt(), anyInt()))
                    .thenReturn(new BigDecimal("100000"));

            Map<String, Object> result = service.analyzeEntertainment(10L, null);

            assertThat((List<Map<String, Object>>) result.get("monthlyTrend")).hasSize(1);
            Map<String, Object> budget = (Map<String, Object>) result.get("budgetExecution");
            assertThat(budget.get("month")).isEqualTo(currentMonth());
            // 实际额取趋势中的当月行（不重复查库），计划额取资金计划科目明细
            assertThat((BigDecimal) budget.get("actual")).isEqualByComparingTo("50000");
            assertThat((BigDecimal) budget.get("planned")).isEqualByComparingTo("100000");
            assertThat((Boolean) budget.get("hasBaseline")).isTrue();
            assertThat((BigDecimal) budget.get("rate")).isEqualByComparingTo("0.5");
            assertThat((Boolean) budget.get("overLimit")).isFalse();
            // 未超限不得混入异常清单（老板视角默认看异常，噪音即失真）
            assertThat((List<Map<String, Object>>) result.get("anomalies"))
                    .extracting(a -> a.get("name")).doesNotContain("超月度限额");
        }

        @Test
        @DisplayName("§11 第 8 类 — 当月实际超计划时产出「超月度限额」异常项（旧实现仅 7/8 类）")
        @SuppressWarnings("unchecked")
        void analyze_overLimit_addsAnomaly() {
            when(entertainmentMapper.analyzeEntertainment(eq(10L), any()))
                    .thenReturn(summary("120000", 20, "9000", 0, 0, 0, 0));
            when(entertainmentMapper.sumByHandler(10L)).thenReturn(List.of());
            when(entertainmentMapper.countSameHandlerSameDay(10L)).thenReturn(0L);
            when(entertainmentMapper.monthlyTrend(eq(10L), any()))
                    .thenReturn(trend(currentMonth(), "120000"));
            when(entertainmentMapper.sumEntertainmentPlanLimit(eq(10L), anyInt(), anyInt()))
                    .thenReturn(new BigDecimal("100000"));

            Map<String, Object> result = service.analyzeEntertainment(10L, null);

            Map<String, Object> budget = (Map<String, Object>) result.get("budgetExecution");
            assertThat((Boolean) budget.get("overLimit")).isTrue();
            assertThat((BigDecimal) budget.get("rate")).isEqualByComparingTo("1.2");
            List<Map<String, Object>> anomalies = (List<Map<String, Object>>) result.get("anomalies");
            assertThat(anomalies).extracting(a -> a.get("name")).contains("超月度限额");
            // 处置建议须给出实际额与限额，便于直接问责
            assertThat(anomalies.stream()
                    .filter(a -> "超月度限额".equals(a.get("name")))
                    .findFirst().orElseThrow().get("suggestion").toString())
                    .contains("120000").contains("100000");
        }

        @Test
        @DisplayName("边界路径 — 未编月度计划时不判定超限（不得把「无基准」当「限额 0 元」误报）")
        @SuppressWarnings("unchecked")
        void analyze_noPlanBaseline_notOverLimit() {
            when(entertainmentMapper.analyzeEntertainment(eq(10L), any()))
                    .thenReturn(summary("300000", 30, "9000", 0, 0, 0, 0));
            when(entertainmentMapper.sumByHandler(10L)).thenReturn(List.of());
            when(entertainmentMapper.countSameHandlerSameDay(10L)).thenReturn(0L);
            when(entertainmentMapper.monthlyTrend(eq(10L), any()))
                    .thenReturn(trend(currentMonth(), "300000"));
            // 未编计划：Mapper 故意返回 null（非 COALESCE 0），null 即「无基准」
            when(entertainmentMapper.sumEntertainmentPlanLimit(eq(10L), anyInt(), anyInt()))
                    .thenReturn(null);

            Map<String, Object> result = service.analyzeEntertainment(10L, null);

            Map<String, Object> budget = (Map<String, Object>) result.get("budgetExecution");
            assertThat(budget.get("planned")).isNull();
            assertThat((Boolean) budget.get("hasBaseline")).isFalse();
            assertThat(budget.get("rate")).isNull();
            assertThat((Boolean) budget.get("overLimit")).isFalse();
            assertThat((List<Map<String, Object>>) result.get("anomalies"))
                    .extracting(a -> a.get("name")).doesNotContain("超月度限额");
        }
    }

    @Nested
    @DisplayName("listDetails() 明细查询")
    class ListTests {

        @Test
        @DisplayName("正常路径 — 招待费明细行回填专项信息，非招待费行不查专项表")
        void list_fillsEntertainmentOnlyForEntertainRows() {
            BizReimbursementDetail entertain = detail(BizReimbursementDetail.CATEGORY_ENTERTAINMENT, "5000");
            entertain.setId(1L);
            BizReimbursementDetail office = detail("EXP-INDIRECT-OFFICE", "3000");
            office.setId(2L);
            when(detailMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(entertain, office));
            when(entertainmentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entertainment());

            List<BizReimbursementDetail> result =
                    service.listDetails(BizReimbursementDetail.SOURCE_PROJECT, 700L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getEntertainment()).isNotNull();
            assertThat(result.get(1).getEntertainment()).isNull();
            verify(entertainmentMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
        }
    }
}
