package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.mapper.ProjectOnebookMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProjectOnebookService 单元测试（V2026_117）
 * <p>覆盖：六条线装配、未收派生式、三口径利润、CBS 缺失不伪造预计利润、
 * 垫资下限截断、项目不存在异常。纯 Service 层（mapper 全 mock）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectOnebookServiceTest {

    @Mock private ProjectOnebookMapper onebookMapper;
    @Mock private BizProjectMapper projectMapper;

    private ProjectOnebookService onebookService;

    private BizProject project;

    @BeforeEach
    void setUp() {
        onebookService = new ProjectOnebookService(onebookMapper, projectMapper);

        project = new BizProject();
        project.setId(10L);
        project.setProjectName("滨江花园一期");
        project.setStatus("CONSTRUCTION");
        project.setTotalIncome(new BigDecimal("8000000"));
        project.setTotalExpense(new BigDecimal("6500000"));
        project.setTotalOtherPayment(new BigDecimal("50000"));
        lenient().when(projectMapper.selectById(10L)).thenReturn(project);

        Map<String, Object> income = new HashMap<>();
        income.put("contractAmount", new BigDecimal("10000000"));
        income.put("visaAmount", new BigDecimal("500000"));
        income.put("outputAmount", new BigDecimal("6000000"));
        income.put("settlementAmount", new BigDecimal("5500000"));
        income.put("invoicedAmount", new BigDecimal("5000000"));
        income.put("receivedAmount", new BigDecimal("4000000"));
        lenient().when(onebookMapper.sumIncomeChain(10L)).thenReturn(income);

        Map<String, Object> cbs = new HashMap<>();
        cbs.put("baseline", new BigDecimal("9000000"));
        cbs.put("currentBudget", new BigDecimal("9200000"));
        cbs.put("commitment", new BigDecimal("3000000"));
        cbs.put("actual", new BigDecimal("5200000"));
        cbs.put("forecast", new BigDecimal("8800000"));
        lenient().when(onebookMapper.sumCost(10L)).thenReturn(cbs);

        lenient().when(onebookMapper.sumCashIn(10L)).thenReturn(new BigDecimal("3900000"));
        lenient().when(onebookMapper.sumCashOut(10L)).thenReturn(new BigDecimal("4200000"));
        lenient().when(onebookMapper.sumApplying(10L)).thenReturn(new BigDecimal("200000"));
        lenient().when(onebookMapper.sumReceivableOpen(10L)).thenReturn(new BigDecimal("1500000"));
        lenient().when(onebookMapper.sumPayableOpen(10L)).thenReturn(new BigDecimal("900000"));
        lenient().when(onebookMapper.sumTax(10L)).thenReturn(Map.of(
                "saleTax", new BigDecimal("300000"), "inputTax", new BigDecimal("180000")));
        lenient().when(onebookMapper.sumRetentionOutstanding(10L)).thenReturn(new BigDecimal("250000"));
        lenient().when(onebookMapper.sumBondOccupied(10L)).thenReturn(new BigDecimal("100000"));
        lenient().when(onebookMapper.sumBillHeld(10L)).thenReturn(BigDecimal.ZERO);
        lenient().when(onebookMapper.sumReserveOutstanding(10L)).thenReturn(new BigDecimal("20000"));
        lenient().when(onebookMapper.sumWageUnpaid(10L)).thenReturn(new BigDecimal("80000"));
        lenient().when(onebookMapper.sumReimbursementApproved(10L)).thenReturn(new BigDecimal("60000"));
    }

    @Test
    @DisplayName("收入线：六段 + 未收 =（合同额+签证）− 回款，与应收 OPEN 分列")
    void onebook_income_unreceivedFormula() {
        Map<String, Object> r = onebookService.onebook(10L);
        Map<String, Object> income = (Map<String, Object>) r.get("income");

        assertThat(income.get("unreceived")).isEqualTo(new BigDecimal("6500000"));
        assertThat(income.get("receivableOpen")).isEqualTo(new BigDecimal("1500000"));
    }

    @Test
    @DisplayName("利润三口径：预计=合同+签证−EAC；已实现=收入−支出（审批口径）；落袋=现金口径")
    void onebook_profit_threeBasises() {
        Map<String, Object> r = onebookService.onebook(10L);
        Map<String, Object> profit = (Map<String, Object>) r.get("profit");

        assertThat(profit.get("forecastProfit")).isEqualTo(new BigDecimal("1700000"));
        assertThat(profit.get("forecastBasis")).isEqualTo("CONTRACT_VISA_MINUS_CBS_FORECAST");
        assertThat(profit.get("realizedProfit")).isEqualTo(new BigDecimal("1500000"));
        assertThat(profit.get("pocketProfit")).isEqualTo(new BigDecimal("-300000"));
        assertThat(profit.get("pocketBasis")).isEqualTo("BANK_CASH");
    }

    @Test
    @DisplayName("资金线：垫资 = 现金流出 − 流入，且为正时如实呈现")
    void onebook_fund_advancePositive() {
        Map<String, Object> r = onebookService.onebook(10L);
        Map<String, Object> fund = (Map<String, Object>) r.get("fund");

        assertThat(fund.get("advance")).isEqualTo(new BigDecimal("300000"));
        assertThat(fund.get("retentionOutstanding")).isEqualTo(new BigDecimal("250000"));
        assertThat(fund.get("bondOccupied")).isEqualTo(new BigDecimal("100000"));
    }

    @Test
    @DisplayName("无 CBS 成本账户：预计利润如实 null（不退化冒充），hasCbs=false")
    void onebook_noCbs_forecastNull() {
        when(onebookMapper.sumCost(10L)).thenReturn(Map.of(
                "baseline", BigDecimal.ZERO, "currentBudget", BigDecimal.ZERO,
                "commitment", BigDecimal.ZERO, "actual", BigDecimal.ZERO, "forecast", BigDecimal.ZERO));

        Map<String, Object> r = onebookService.onebook(10L);
        Map<String, Object> profit = (Map<String, Object>) r.get("profit");
        Map<String, Object> cost = (Map<String, Object>) r.get("cost");

        assertThat(cost.get("hasCbs")).isEqualTo(false);
        assertThat(profit.get("forecastProfit")).isNull();
        assertThat(profit.get("forecastBasis")).isEqualTo("CBS_UNAVAILABLE");
    }

    @Test
    @DisplayName("支出线：分类聚合与应付未付派生")
    void onebook_expense_categories() {
        Map<String, Object> row = new HashMap<>();
        row.put("category", "SUBCONTRACT");
        row.put("contractAmount", new BigDecimal("4000000"));
        row.put("settled", new BigDecimal("3000000"));
        row.put("paid", new BigDecimal("2500000"));
        row.put("invoiceReceived", new BigDecimal("2000000"));
        lenient().when(onebookMapper.sumExpenseByCategory(10L)).thenReturn(List.of(row));

        Map<String, Object> r = onebookService.onebook(10L);
        Map<String, Object> expense = (Map<String, Object>) r.get("expense");
        List<Map<String, Object>> cats = (List<Map<String, Object>>) expense.get("categories");

        assertThat(cats).hasSize(1);
        assertThat(cats.get(0).get("outstanding")).isEqualTo(new BigDecimal("500000"));
        assertThat(expense.get("applying")).isEqualTo(new BigDecimal("200000"));
        assertThat(expense.get("otherPayment")).isEqualTo(new BigDecimal("50000"));
    }

    @Test
    @DisplayName("税务线：应纳增值税 = 销项 − 进项")
    void onebook_tax_vat() {
        Map<String, Object> r = onebookService.onebook(10L);
        Map<String, Object> tax = (Map<String, Object>) r.get("tax");

        assertThat(tax.get("vatPayable")).isEqualTo(new BigDecimal("120000"));
    }

    @Test
    @DisplayName("项目不存在抛业务异常")
    void onebook_missingProject_throws() {
        when(projectMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> onebookService.onebook(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
        verify(onebookMapper, never()).sumIncomeChain(anyLong());
    }
}
