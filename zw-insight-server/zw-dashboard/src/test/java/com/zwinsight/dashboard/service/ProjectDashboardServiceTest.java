package com.zwinsight.dashboard.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.mapper.BizBudgetMapper;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizOutputReport;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizOutputReportMapper;
import com.zwinsight.dashboard.dto.BudgetExecutionDTO;
import com.zwinsight.dashboard.dto.ContractReceiptDTO;
import com.zwinsight.dashboard.dto.OutputTrendDTO;
import com.zwinsight.dashboard.dto.ProgressDTO;
import com.zwinsight.dashboard.dto.ProjectDashboardDTO;
import com.zwinsight.finance.domain.BizInvoiceApply;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.domain.BizPaymentReceived;
import com.zwinsight.finance.mapper.BizInvoiceApplyMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.BizPaymentReceivedMapper;
import com.zwinsight.site.domain.BizSchedulePlan;
import com.zwinsight.site.mapper.BizSchedulePlanMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 项目维度看板服务单元测试（零值分支 + 聚合计算）
 */
@ExtendWith(MockitoExtension.class)
class ProjectDashboardServiceTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BizBudget.class);
        TableInfoHelper.initTableInfo(assistant, BizBudgetDetail.class);
        TableInfoHelper.initTableInfo(assistant, BizPaymentApply.class);
        TableInfoHelper.initTableInfo(assistant, BizSchedulePlan.class);
        TableInfoHelper.initTableInfo(assistant, BizConstructionContract.class);
        TableInfoHelper.initTableInfo(assistant, BizInvoiceApply.class);
        TableInfoHelper.initTableInfo(assistant, BizPaymentReceived.class);
        TableInfoHelper.initTableInfo(assistant, BizOutputReport.class);
    }

    @Mock private BizBudgetMapper budgetMapper;
    @Mock private BizBudgetDetailMapper budgetDetailMapper;
    @Mock private BizPaymentApplyMapper paymentApplyMapper;
    @Mock private BizSchedulePlanMapper schedulePlanMapper;
    @Mock private BizConstructionContractMapper constructionContractMapper;
    @Mock private BizInvoiceApplyMapper invoiceApplyMapper;
    @Mock private BizPaymentReceivedMapper paymentReceivedMapper;
    @Mock private BizOutputReportMapper outputReportMapper;
    @Mock private com.zwinsight.budget.mapper.BizCostAccountMapper costAccountMapper;
    @Mock private com.zwinsight.dashboard.mapper.LaborCostReadMapper laborCostReadMapper;

    @InjectMocks
    private ProjectDashboardService dashboardService;

    @Test
    @DisplayName("预算执行：无审批预算时返回零值")
    void testBudgetExecution_noBudget() {
        when(budgetMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BudgetExecutionDTO dto = dashboardService.getBudgetExecution(1L);

        assertThat(dto.getTotalBudget()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getUsedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getUsageRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getSubjects()).isEmpty();
    }

    @Test
    @DisplayName("预算执行：按科目聚合预算与已付，使用率计算正确")
    void testBudgetExecution_aggregates() {
        BizBudget budget = new BizBudget();
        budget.setId(10L);
        budget.setTotalAmount(new BigDecimal("100000"));
        when(budgetMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(budget);

        BizBudgetDetail labor = new BizBudgetDetail();
        labor.setCostCategory("LABOR");
        labor.setBudgetTotalPrice(new BigDecimal("60000"));
        BizBudgetDetail material = new BizBudgetDetail();
        material.setCostCategory("MATERIAL");
        material.setBudgetTotalPrice(new BigDecimal("40000"));
        when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(labor, material));

        BizPaymentApply pay = new BizPaymentApply();
        pay.setPaymentAmount(new BigDecimal("30000"));
        pay.setContractCategory("LABOR");
        when(paymentApplyMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(pay));

        BudgetExecutionDTO dto = dashboardService.getBudgetExecution(1L);

        assertThat(dto.getTotalBudget()).isEqualByComparingTo("100000");
        assertThat(dto.getUsedAmount()).isEqualByComparingTo("30000");
        assertThat(dto.getUsageRate()).isEqualByComparingTo("0.3");
        assertThat(dto.getSubjects()).hasSize(2);
        assertThat(dto.getSubjects().get(0).getSubjectName()).isEqualTo("LABOR");
        assertThat(dto.getSubjects().get(0).getPaid()).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("进度完成率：统计总任务与已完成任务")
    void testProgress_counts() {
        BizSchedulePlan done = new BizSchedulePlan();
        done.setTaskStatus("COMPLETED");
        BizSchedulePlan doing = new BizSchedulePlan();
        doing.setTaskStatus("IN_PROGRESS");
        when(schedulePlanMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(done, doing));

        ProgressDTO dto = dashboardService.getProgress(1L);

        assertThat(dto.getTotalTasks()).isEqualTo(2);
        assertThat(dto.getCompletedTasks()).isEqualTo(1);
        assertThat(dto.getCompletionRate()).isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("合同回款：合同总额/开票/回款汇总与回款率")
    void testContractReceipt_aggregates() {
        BizConstructionContract contract = new BizConstructionContract();
        contract.setContractAmount(new BigDecimal("100"));
        when(constructionContractMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(contract));

        BizInvoiceApply invoice = new BizInvoiceApply();
        invoice.setInvoiceAmount(new BigDecimal("50"));
        when(invoiceApplyMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(invoice));

        BizPaymentReceived received = new BizPaymentReceived();
        received.setReceiveAmount(new BigDecimal("25"));
        when(paymentReceivedMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(received));

        ContractReceiptDTO dto = dashboardService.getContractReceipt(1L);

        assertThat(dto.getContractTotal()).isEqualByComparingTo("100");
        assertThat(dto.getInvoicedAmount()).isEqualByComparingTo("50");
        assertThat(dto.getReceivedAmount()).isEqualByComparingTo("25");
        assertThat(dto.getReceiptRate()).isEqualByComparingTo("0.25");
    }

    @Test
    @DisplayName("产值趋势：无记录返回空趋势；有记录时本月产值命中")
    void testOutputTrend() {
        when(outputReportMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        OutputTrendDTO empty = dashboardService.getOutputTrend(1L);
        assertThat(empty.getTotalOutput()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(empty.getTrend()).isEmpty();

        BizOutputReport report = new BizOutputReport();
        report.setCurrentOutput(new BigDecimal("888"));
        report.setReportPeriod(YearMonth.now().toString());
        when(outputReportMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(report));
        OutputTrendDTO dto = dashboardService.getOutputTrend(1L);
        assertThat(dto.getTotalOutput()).isEqualByComparingTo("888");
        assertThat(dto.getMonthOutput()).isEqualByComparingTo("888");
        assertThat(dto.getTrend()).hasSize(12);
    }

    @Test
    @DisplayName("项目看板聚合：四个维度全部非空")
    void testProjectOverview_allDimensions() {
        when(budgetMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(schedulePlanMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(constructionContractMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(invoiceApplyMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(paymentReceivedMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(outputReportMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        ProjectDashboardDTO dto = dashboardService.getProjectOverview(1L);

        assertThat(dto.getBudget()).isNotNull();
        assertThat(dto.getProgress()).isNotNull();
        assertThat(dto.getContract()).isNotNull();
        assertThat(dto.getOutput()).isNotNull();
    }

    // ==================== C5 回款率四口径 + 双毛利 + 人工对照 ====================

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("C5 - 四口径回款率带分子分母；零分母明示不可算")
    void getReceiptRatios_fourRatesWithMetadata() {
        com.zwinsight.contract.domain.BizConstructionContract c = new com.zwinsight.contract.domain.BizConstructionContract();
        c.setProjectId(10L); c.setStatus("EFFECTIVE");
        c.setContractAmount(new java.math.BigDecimal("1000000"));
        c.setCumulativeReceivedAmount(new java.math.BigDecimal("300000"));
        c.setCumulativeOutput(new java.math.BigDecimal("500000"));
        c.setCumulativeInvoiceAmount(new java.math.BigDecimal("400000"));
        c.setCumulativeSettlement(new java.math.BigDecimal("0"));
        when(constructionContractMapper.selectList(any())).thenReturn(java.util.List.of(c));
        when(costAccountMapper.selectList(any())).thenReturn(java.util.List.of());

        java.util.Map<String, Object> result = service.getReceiptRatios(10L);

        var byContract = (java.util.Map<String, Object>) result.get("collectionRateByContract");
        assertEquals(true, byContract.get("available"));
        assertEquals(0, ((java.math.BigDecimal) byContract.get("value")).compareTo(new java.math.BigDecimal("0.3")));
        assertEquals(0, ((java.math.BigDecimal) byContract.get("denominator")).compareTo(new java.math.BigDecimal("1000000")));

        var bySettlement = (java.util.Map<String, Object>) result.get("collectionRateBySettlement");
        assertEquals(false, bySettlement.get("available"));
        assertEquals("DENOMINATOR_ZERO", bySettlement.get("unavailableReason"));

        var margin = (java.util.Map<String, Object>) result.get("outputGrossMargin");
        assertEquals(false, margin.get("available"));
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("C5 - CBS 可用时给双毛利；人工对照只读并列")
    void getReceiptRatios_marginsAndLaborCompare() {
        com.zwinsight.contract.domain.BizConstructionContract c = new com.zwinsight.contract.domain.BizConstructionContract();
        c.setProjectId(10L); c.setStatus("EFFECTIVE");
        c.setContractAmount(new java.math.BigDecimal("1000000"));
        c.setCumulativeOutput(new java.math.BigDecimal("800000"));
        c.setCumulativeSettlement(new java.math.BigDecimal("600000"));
        when(constructionContractMapper.selectList(any())).thenReturn(java.util.List.of(c));
        com.zwinsight.budget.domain.BizCostAccount root = new com.zwinsight.budget.domain.BizCostAccount();
        root.setId(1L); root.setParentId(null);
        root.setActualAmount(new java.math.BigDecimal("500000"));
        com.zwinsight.budget.domain.BizCostAccount child = new com.zwinsight.budget.domain.BizCostAccount();
        child.setId(2L); child.setParentId(1L);
        child.setActualAmount(new java.math.BigDecimal("999999")); // 子账户不得重复计入
        when(costAccountMapper.selectList(any())).thenReturn(java.util.List.of(root, child));
        when(laborCostReadMapper.sumApprovedPayroll(10L)).thenReturn(new java.math.BigDecimal("120000"));
        when(laborCostReadMapper.sumApprovedWorkOrders(10L)).thenReturn(new java.math.BigDecimal("30000"));
        when(laborCostReadMapper.sumLaborSettlement(10L)).thenReturn(new java.math.BigDecimal("100000"));

        java.util.Map<String, Object> result = service.getReceiptRatios(10L);

        var outputMargin = (java.util.Map<String, Object>) result.get("outputGrossMargin");
        assertEquals(0, ((java.math.BigDecimal) outputMargin.get("value")).compareTo(new java.math.BigDecimal("300000")));
        var settleMargin = (java.util.Map<String, Object>) result.get("settlementGrossMargin");
        assertEquals(0, ((java.math.BigDecimal) settleMargin.get("value")).compareTo(new java.math.BigDecimal("100000")));

        var labor = (java.util.Map<String, Object>) result.get("laborCostCompare");
        assertEquals(0, ((java.math.BigDecimal) labor.get("wageLikeTotal")).compareTo(new java.math.BigDecimal("150000")));
        assertEquals(0, ((java.math.BigDecimal) labor.get("difference")).compareTo(new java.math.BigDecimal("50000")));
        assertEquals("WAGE_LIKE_NOT_IN_CBS", labor.get("basis"));
    }
}
