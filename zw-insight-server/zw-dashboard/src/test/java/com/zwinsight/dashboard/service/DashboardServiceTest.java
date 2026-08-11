package com.zwinsight.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.mapper.BizBudgetMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
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
}
