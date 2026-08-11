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
}
