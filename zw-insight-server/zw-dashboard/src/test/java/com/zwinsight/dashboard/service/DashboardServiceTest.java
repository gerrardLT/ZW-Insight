package com.zwinsight.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.mapper.BizBudgetMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.domain.BizPaymentReceived;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.BizPaymentReceivedMapper;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.purchase.mapper.BizPurchaseContractMapper;
import com.zwinsight.site.mapper.BizSchedulePlanMapper;
import com.zwinsight.tender.domain.BizDepositApply;
import com.zwinsight.tender.domain.BizTenderRegister;
import com.zwinsight.tender.mapper.BizDepositApplyMapper;
import com.zwinsight.tender.mapper.BizTenderRegisterMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private BizProjectMapper projectMapper;
    @Mock private BizConstructionContractMapper constructionContractMapper;
    @Mock private BizBudgetMapper budgetMapper;
    @Mock private BizBudgetDetailMapper budgetDetailMapper;
    @Mock private BizPaymentApplyMapper paymentApplyMapper;
    @Mock private BizPaymentReceivedMapper paymentReceivedMapper;
    // V2026_64：利润趋势支出侧对 PARTIAL_PAID 只计已勾稽额，需读流水勾稽聚合
    @Mock private com.zwinsight.finance.mapper.BizBankFlowMapper bankFlowMapper;
    @Mock private BizPurchaseContractMapper purchaseContractMapper;
    @Mock private BizProjectMaterialStockMapper projectMaterialStockMapper;
    @Mock private BizTenderRegisterMapper tenderRegisterMapper;
    @Mock private BizDepositApplyMapper depositApplyMapper;
    @Mock private BizSchedulePlanMapper schedulePlanMapper;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    @DisplayName("公司概览：无项目时总额为0")
    void testGetCompanyOverview_empty() {
        when(projectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        Map<String, Object> result = dashboardService.getCompanyOverview();

        assertThat(result.get("projectTotal")).isEqualTo(0);
        assertThat((BigDecimal) result.get("totalContractAmount")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("公司概览：汇总项目数据")
    void testGetCompanyOverview_withProjects() {
        BizProject p1 = new BizProject();
        p1.setStatus("ACTIVE");
        p1.setContractAmount(new BigDecimal("100000"));
        p1.setSettlementAmount(new BigDecimal("50000"));
        p1.setTotalIncome(new BigDecimal("80000"));
        p1.setTotalExpense(new BigDecimal("60000"));
        when(projectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p1));

        Map<String, Object> result = dashboardService.getCompanyOverview();

        assertThat(result.get("projectTotal")).isEqualTo(1);
        assertThat((BigDecimal) result.get("totalContractAmount")).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat((BigDecimal) result.get("profit")).isEqualByComparingTo(new BigDecimal("20000"));
    }

    @Test
    @DisplayName("投标分析：统计投标数和保证金")
    void testGetTenderAnalysis() {
        BizTenderRegister reg = new BizTenderRegister();
        reg.setStatus("WON");
        when(tenderRegisterMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(reg));

        BizDepositApply deposit = new BizDepositApply();
        deposit.setDepositAmount(new BigDecimal("5000"));
        when(depositApplyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(deposit));

        Map<String, Object> result = dashboardService.getTenderAnalysis();

        assertThat(result.get("totalTenders")).isEqualTo(1);
        assertThat((BigDecimal) result.get("totalDepositAmount")).isEqualByComparingTo(new BigDecimal("5000"));
    }

    @Test
    @DisplayName("应收款监控：无项目时回款率为0")
    void testGetReceivableMonitor_empty() {
        when(projectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        Map<String, Object> result = dashboardService.getReceivableMonitor();

        assertThat((BigDecimal) result.get("totalReceivable")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat((BigDecimal) result.get("receivedRate")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("库存分析：无库存返回空列表")
    void testGetInventoryAnalysis_empty() {
        when(projectMaterialStockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        Map<String, Object> result = dashboardService.getInventoryAnalysis();

        assertThat((List<?>) result.get("projectInventory")).isEmpty();
    }

    // ============ 预算执行/偏差（原零覆盖，2026-08-11 补 NPE 防护钉住） ============

    @Test
    @DisplayName("预算执行：无审批预算时返回空明细")
    void testGetBudgetExecution_noBudget_returnsEmpty() {
        when(budgetMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        Map<String, Object> result = dashboardService.getBudgetExecution(1L, null, null);

        assertThat((List<?>) result.get("budgetDetails")).isEmpty();
    }

    @Test
    @DisplayName("预算执行：totalAmount 为 null 不抛 NPE，余额按 0 计（NPE 防护钉住）")
    void testGetBudgetExecution_totalAmountNull_noNpe() {
        BizBudget budget = new BizBudget();
        budget.setId(10L);
        budget.setTotalAmount(null);
        when(budgetMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(budget);
        when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(paymentApplyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        Map<String, Object> result = dashboardService.getBudgetExecution(1L, null, null);

        // balance = 0 - 0 = 0，不抛 NPE
        assertThat((BigDecimal) result.get("balance")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("预算偏差：明细科目为 null 不抛 NPE，兜底归入未分类（NPE 防护钉住）")
    void testGetBudgetVariance_costCategoryNull_noNpe() {
        BizBudget budget = new BizBudget();
        budget.setId(10L);
        budget.setTotalAmount(new BigDecimal("100000"));
        when(budgetMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(budget);

        BizBudgetDetail detailNullCategory = new BizBudgetDetail();
        detailNullCategory.setCostCategory(null);
        detailNullCategory.setBudgetTotalPrice(new BigDecimal("5000"));
        when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(detailNullCategory));

        Map<String, Object> result = dashboardService.getBudgetVariance(1L);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("利润趋势：收入按回款登记 receive_date 真实分月（钉住废弃年均摊模拟口径，V2026_56）")
    @SuppressWarnings("unchecked")
    void testGetProfitTrend_realReceiptsByMonth() {
        BizPaymentReceived r1 = new BizPaymentReceived();
        r1.setStatus("APPROVED");
        r1.setReceiveDate(LocalDate.of(2026, 3, 15));
        r1.setReceiveAmount(new BigDecimal("100000"));
        BizPaymentReceived r2 = new BizPaymentReceived();
        r2.setStatus("APPROVED");
        r2.setReceiveDate(LocalDate.of(2026, 3, 20));
        r2.setReceiveAmount(new BigDecimal("50000"));
        // 非目标年份，不应计入
        BizPaymentReceived r3 = new BizPaymentReceived();
        r3.setStatus("APPROVED");
        r3.setReceiveDate(LocalDate.of(2025, 12, 1));
        r3.setReceiveAmount(new BigDecimal("999"));
        when(paymentReceivedMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(r1, r2, r3));

        BizPaymentApply p1 = new BizPaymentApply();
        p1.setStatus("APPROVED");
        p1.setPayDate(LocalDate.of(2026, 3, 18));
        p1.setPaymentAmount(new BigDecimal("60000"));
        // 无 pay_date 回退 payment_date（存量单据兼容）
        BizPaymentApply p2 = new BizPaymentApply();
        p2.setStatus("APPROVED");
        p2.setPaymentDate(LocalDate.of(2026, 4, 10));
        p2.setPaymentAmount(new BigDecimal("20000"));
        when(paymentApplyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p1, p2));

        Map<String, Object> result = dashboardService.getProfitTrend(2026);
        List<Map<String, Object>> months = (List<Map<String, Object>>) result.get("months");

        Map<String, Object> march = months.get(2);
        assertThat((BigDecimal) march.get("income")).isEqualByComparingTo("150000");
        assertThat((BigDecimal) march.get("expense")).isEqualByComparingTo("60000");
        assertThat((BigDecimal) march.get("profit")).isEqualByComparingTo("90000");
        Map<String, Object> april = months.get(3);
        assertThat((BigDecimal) april.get("income")).isEqualByComparingTo("0");
        assertThat((BigDecimal) april.get("expense")).isEqualByComparingTo("20000");
        // 年度合计为当年真实单据口径（非项目 totalIncome 均摊）
        assertThat((BigDecimal) result.get("totalIncome")).isEqualByComparingTo("150000");
        assertThat((BigDecimal) result.get("totalExpense")).isEqualByComparingTo("80000");
        assertThat((BigDecimal) result.get("totalProfit")).isEqualByComparingTo("70000");
    }

    @Test
    @DisplayName("利润趋势：无任何单据时 12 个月全 0，不抛异常不伪造数据")
    @SuppressWarnings("unchecked")
    void testGetProfitTrend_emptyAllZero() {
        when(paymentReceivedMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(paymentApplyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        Map<String, Object> result = dashboardService.getProfitTrend(null);
        List<Map<String, Object>> months = (List<Map<String, Object>>) result.get("months");

        assertThat(months).hasSize(12);
        assertThat(months).allSatisfy(m -> {
            assertThat((BigDecimal) m.get("income")).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat((BigDecimal) m.get("expense")).isEqualByComparingTo(BigDecimal.ZERO);
        });
        assertThat((BigDecimal) result.get("totalProfit")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("利润趋势：PARTIAL_PAID 单据只计已勾稽额（不按全额虚增支出，V2026_64）")
    @SuppressWarnings("unchecked")
    void testGetProfitTrend_partialPaidCountsMatchedOnly() {
        when(paymentReceivedMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        BizPaymentApply partial = new BizPaymentApply();
        partial.setId(9L);
        partial.setStatus("APPROVED");
        partial.setPayStatus(BizPaymentApply.PAY_STATUS_PARTIAL);
        partial.setPaymentAmount(new BigDecimal("1000000"));
        partial.setPayDate(LocalDate.of(2026, 5, 20));
        // 已勾稽 60 万（部分支付）：现金实际流出只有这 60 万
        when(bankFlowMapper.matchedAmountByPaymentApply())
                .thenReturn(java.util.Map.of(9L, new BigDecimal("600000")));
        when(paymentApplyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(partial));

        Map<String, Object> result = dashboardService.getProfitTrend(2026);
        List<Map<String, Object>> months = (List<Map<String, Object>>) result.get("months");

        // 5 月支出 = 已勾稽 60 万（而非申请全额 100 万），否则虚增支出、虚减利润
        assertThat((BigDecimal) months.get(4).get("expense")).isEqualByComparingTo("600000");
        assertThat((BigDecimal) result.get("totalExpense")).isEqualByComparingTo("600000");
    }

    @Test
    @DisplayName("利润趋势：PAID/UNPAID 单据仍按全额计（仅 PARTIAL_PAID 扣减已勾稽）")
    @SuppressWarnings("unchecked")
    void testGetProfitTrend_paidAndUnpaidCountFullAmount() {
        when(paymentReceivedMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        BizPaymentApply paid = new BizPaymentApply();
        paid.setId(11L);
        paid.setStatus("APPROVED");
        paid.setPayStatus(BizPaymentApply.PAY_STATUS_PAID);
        paid.setPaymentAmount(new BigDecimal("300000"));
        paid.setPayDate(LocalDate.of(2026, 6, 10));
        BizPaymentApply unpaid = new BizPaymentApply();
        unpaid.setId(12L);
        unpaid.setStatus("APPROVED");
        unpaid.setPayStatus(BizPaymentApply.PAY_STATUS_UNPAID);
        unpaid.setPaymentAmount(new BigDecimal("200000"));
        unpaid.setPaymentDate(LocalDate.of(2026, 6, 20));
        when(bankFlowMapper.matchedAmountByPaymentApply())
                .thenReturn(java.util.Map.of(11L, new BigDecimal("300000")));
        when(paymentApplyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(paid, unpaid));

        Map<String, Object> result = dashboardService.getProfitTrend(2026);
        List<Map<String, Object>> months = (List<Map<String, Object>>) result.get("months");

        // 6 月支出 = 30万（已付全额）+ 20万（审批口径未付）= 50万
        assertThat((BigDecimal) months.get(5).get("expense")).isEqualByComparingTo("500000");
    }
}
