package com.zwinsight.dashboard;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
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
import com.zwinsight.dashboard.dto.MonthlyOutputDTO;
import com.zwinsight.dashboard.dto.OutputTrendDTO;
import com.zwinsight.dashboard.dto.ProgressDTO;
import com.zwinsight.dashboard.dto.SubjectDetailDTO;
import com.zwinsight.dashboard.service.ProjectDashboardService;
import com.zwinsight.finance.domain.BizInvoiceApply;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.domain.BizPaymentReceived;
import com.zwinsight.finance.mapper.BizInvoiceApplyMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.BizPaymentReceivedMapper;
import com.zwinsight.site.domain.BizSchedulePlan;
import com.zwinsight.site.mapper.BizSchedulePlanMapper;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.assertj.core.api.Assertions;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ProjectDashboardPropertyTest — ProjectDashboardService 比率计算逻辑属性测试（Property 1-5）
 *
 * <p>使用 Mockito 内存 store 模拟 8 个跨模块 Mapper，无需真实数据库即可驱动
 * {@code getBudgetExecution / getProgress / getContractReceipt / getOutputTrend}
 * 并验证其比率计算（4 位小数 HALF_UP，分母为 0 返回 0）与产值趋势升序排列契约。</p>
 *
 * <p>测试通过公开方法验证真实行为（approach A），各属性的期望比率由与实现一致的
 * {@link #expectedRate} 复算得到。</p>
 */
class ProjectDashboardPropertyTest {

    private static final long PROJECT_ID = 1001L;

    /**
     * 初始化 MyBatis-Plus 实体元数据（lambda 列缓存），
     * 使 service 内部 LambdaQueryWrapper 的 eq/orderBy(lambda) 在无 Spring 容器时也能解析列名。
     */
    @BeforeContainer
    static void initMybatisPlusTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizBudget.class);
        TableInfoHelper.initTableInfo(assistant, BizBudgetDetail.class);
        TableInfoHelper.initTableInfo(assistant, BizPaymentApply.class);
        TableInfoHelper.initTableInfo(assistant, BizSchedulePlan.class);
        TableInfoHelper.initTableInfo(assistant, BizConstructionContract.class);
        TableInfoHelper.initTableInfo(assistant, BizInvoiceApply.class);
        TableInfoHelper.initTableInfo(assistant, BizPaymentReceived.class);
        TableInfoHelper.initTableInfo(assistant, BizOutputReport.class);
    }

    /** 与实现一致的比率复算：分母 null/0 返回 ZERO，否则 num/den 保留 4 位 HALF_UP。 */
    private static BigDecimal expectedRate(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }

    // ==================== Property 1: 预算使用率计算正确 ====================

    /**
     * Property 1: usageRate = usedAmount / totalBudget（4 位小数 HALF_UP）；totalBudget=0 时为 0。
     * usedAmount 为该项目全部已审批付款金额之和。
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 1: 预算使用率计算正确")
    void property1_budgetUsageRate(
            @ForAll("amount") BigDecimal totalBudget,
            @ForAll("amountList") List<BigDecimal> paymentAmounts) {

        Stores s = new Stores();
        s.budget = budget(totalBudget);
        s.payments = paymentAmounts.stream()
                .map(amt -> payment(amt, "其他"))
                .collect(Collectors.toList());

        ProjectDashboardService service = buildService(s);
        BudgetExecutionDTO dto = service.getBudgetExecution(PROJECT_ID);

        BigDecimal expectedUsed = paymentAmounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expected = expectedRate(expectedUsed, totalBudget);

        Assertions.assertThat(dto.getUsedAmount())
                .as("已使用金额应为已审批付款之和")
                .isEqualByComparingTo(expectedUsed);
        Assertions.assertThat(dto.getUsageRate())
                .as("使用率应为 usedAmount/totalBudget（4 位 HALF_UP，分母 0 返回 0）")
                .isEqualByComparingTo(expected);
        if (totalBudget.compareTo(BigDecimal.ZERO) != 0) {
            Assertions.assertThat(dto.getUsageRate().scale())
                    .as("非零分母时比率应保留 4 位小数")
                    .isEqualTo(4);
        }
    }

    // ==================== Property 2: 回款率计算正确 ====================

    /**
     * Property 2: receiptRate = receivedAmount / contractTotal（4 位小数 HALF_UP）；contractTotal=0 时为 0。
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 2: 回款率计算正确")
    void property2_contractReceiptRate(
            @ForAll("amountList") List<BigDecimal> contractAmounts,
            @ForAll("amountList") List<BigDecimal> receiveAmounts) {

        Stores s = new Stores();
        s.contracts = contractAmounts.stream().map(this::contract).collect(Collectors.toList());
        s.received = receiveAmounts.stream().map(this::received).collect(Collectors.toList());

        ProjectDashboardService service = buildService(s);
        ContractReceiptDTO dto = service.getContractReceipt(PROJECT_ID);

        BigDecimal contractTotal = contractAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal receivedTotal = receiveAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expected = expectedRate(receivedTotal, contractTotal);

        Assertions.assertThat(dto.getContractTotal()).isEqualByComparingTo(contractTotal);
        Assertions.assertThat(dto.getReceivedAmount()).isEqualByComparingTo(receivedTotal);
        Assertions.assertThat(dto.getReceiptRate())
                .as("回款率应为 received/contractTotal（4 位 HALF_UP，分母 0 返回 0）")
                .isEqualByComparingTo(expected);
        if (contractTotal.compareTo(BigDecimal.ZERO) != 0) {
            Assertions.assertThat(dto.getReceiptRate().scale()).isEqualTo(4);
        }
    }

    // ==================== Property 3: 完成百分比计算正确 ====================

    /**
     * Property 3: completionRate = completedTasks / totalTasks（4 位小数 HALF_UP）；totalTasks=0 时为 0。
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 3: 完成百分比计算正确")
    void property3_progressCompletionRate(
            @ForAll("taskStatusList") List<String> taskStatuses) {

        Stores s = new Stores();
        s.plans = taskStatuses.stream().map(this::plan).collect(Collectors.toList());

        ProjectDashboardService service = buildService(s);
        ProgressDTO dto = service.getProgress(PROJECT_ID);

        int total = taskStatuses.size();
        int completed = (int) taskStatuses.stream().filter("COMPLETED"::equals).count();
        BigDecimal expected = expectedRate(new BigDecimal(completed), new BigDecimal(total));

        Assertions.assertThat(dto.getTotalTasks()).isEqualTo(total);
        Assertions.assertThat(dto.getCompletedTasks()).isEqualTo(completed);
        Assertions.assertThat(dto.getCompletionRate())
                .as("完成百分比应为 completed/total（4 位 HALF_UP，分母 0 返回 0）")
                .isEqualByComparingTo(expected);
        if (total != 0) {
            Assertions.assertThat(dto.getCompletionRate().scale()).isEqualTo(4);
        }
    }

    // ==================== Property 4: 月度产值趋势升序排列 ====================

    /**
     * Property 4: 趋势列表按月份升序排列；无任何产值记录时返回空列表。
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 4: 月度产值趋势升序排列")
    void property4_outputTrendAscending(
            @ForAll("outputReports") List<BizOutputReport> reports) {

        Stores s = new Stores();
        s.reports = reports;

        ProjectDashboardService service = buildService(s);
        OutputTrendDTO dto = service.getOutputTrend(PROJECT_ID);

        List<MonthlyOutputDTO> trend = dto.getTrend();

        if (reports.isEmpty()) {
            Assertions.assertThat(trend)
                    .as("无产值记录时趋势应为空列表")
                    .isEmpty();
            return;
        }

        Assertions.assertThat(trend).as("有记录时趋势不应为空").isNotEmpty();

        List<String> months = trend.stream().map(MonthlyOutputDTO::getMonth).collect(Collectors.toList());
        List<String> sorted = months.stream().sorted().collect(Collectors.toList());
        Assertions.assertThat(months)
                .as("趋势列表应按月份（YYYY-MM）升序排列")
                .containsExactlyElementsOf(sorted);
    }

    // ==================== Property 5: 比率结果为非负 BigDecimal ====================

    /**
     * Property 5: 预算使用率、回款率、完成率及各科目占比均为非空 BigDecimal 且 ≥ 0。
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 5: 比率结果为非负 BigDecimal")
    void property5_ratesAreNonNegative(
            @ForAll("amount") BigDecimal totalBudget,
            @ForAll("amountList") List<BigDecimal> paymentAmounts,
            @ForAll("amountList") List<BigDecimal> contractAmounts,
            @ForAll("amountList") List<BigDecimal> receiveAmounts,
            @ForAll("taskStatusList") List<String> taskStatuses) {

        Stores s = new Stores();
        s.budget = budget(totalBudget);
        s.budgetDetails = List.of(budgetDetail("MATERIAL", totalBudget));
        s.payments = paymentAmounts.stream().map(amt -> payment(amt, "MATERIAL")).collect(Collectors.toList());
        s.contracts = contractAmounts.stream().map(this::contract).collect(Collectors.toList());
        s.received = receiveAmounts.stream().map(this::received).collect(Collectors.toList());
        s.plans = taskStatuses.stream().map(this::plan).collect(Collectors.toList());

        ProjectDashboardService service = buildService(s);

        BudgetExecutionDTO budget = service.getBudgetExecution(PROJECT_ID);
        ContractReceiptDTO contract = service.getContractReceipt(PROJECT_ID);
        ProgressDTO progress = service.getProgress(PROJECT_ID);

        assertNonNegative(budget.getUsageRate(), "usageRate");
        assertNonNegative(contract.getReceiptRate(), "receiptRate");
        assertNonNegative(progress.getCompletionRate(), "completionRate");
        for (SubjectDetailDTO subject : budget.getSubjects()) {
            assertNonNegative(subject.getRatio(), "subject.ratio");
        }
    }

    private static void assertNonNegative(BigDecimal value, String name) {
        Assertions.assertThat(value).as(name + " 应为非空 BigDecimal").isNotNull();
        Assertions.assertThat(value.compareTo(BigDecimal.ZERO))
                .as(name + " 应 ≥ 0")
                .isGreaterThanOrEqualTo(0);
    }

    // ==================== Arbitraries ====================

    /** 非负金额（含 0 边界），2 位小数，0 ~ 1,000,000 */
    @Provide
    Arbitrary<BigDecimal> amount() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("1000000"))
                .ofScale(2);
    }

    /** 非负金额列表，长度 0-15 */
    @Provide
    Arbitrary<List<BigDecimal>> amountList() {
        return amount().list().ofMinSize(0).ofMaxSize(15);
    }

    /** 任务状态列表，长度 0-20，状态来自有限集合（COMPLETED 占其一） */
    @Provide
    Arbitrary<List<String>> taskStatusList() {
        return Arbitraries.of("NOT_STARTED", "IN_PROGRESS", "COMPLETED", "DELAYED")
                .list().ofMinSize(0).ofMaxSize(20);
    }

    /** 产值上报记录列表（长度 0-20，含空列表场景） */
    @Provide
    Arbitrary<List<BizOutputReport>> outputReports() {
        Arbitrary<Integer> year = Arbitraries.integers().between(2022, 2026);
        Arbitrary<Integer> month = Arbitraries.integers().between(1, 12);
        Arbitrary<BizOutputReport> one = Combinators.combine(year, month, amount())
                .as((y, m, amt) -> outputReport(String.format("%04d-%02d", y, m), amt));
        return one.list().ofMinSize(0).ofMaxSize(20);
    }

    // ==================== 实体构造辅助 ====================

    private BizBudget budget(BigDecimal totalAmount) {
        BizBudget b = new BizBudget();
        b.setId(1L);
        b.setProjectId(PROJECT_ID);
        b.setStatus("APPROVED");
        b.setTotalAmount(totalAmount);
        return b;
    }

    private BizBudgetDetail budgetDetail(String category, BigDecimal total) {
        BizBudgetDetail d = new BizBudgetDetail();
        d.setBudgetId(1L);
        d.setCostCategory(category);
        d.setBudgetTotalPrice(total);
        return d;
    }

    private BizPaymentApply payment(BigDecimal amount, String category) {
        BizPaymentApply p = new BizPaymentApply();
        p.setProjectId(PROJECT_ID);
        p.setStatus("APPROVED");
        p.setPaymentAmount(amount);
        p.setContractCategory(category);
        return p;
    }

    private BizSchedulePlan plan(String status) {
        BizSchedulePlan p = new BizSchedulePlan();
        p.setProjectId(PROJECT_ID);
        p.setTaskStatus(status);
        return p;
    }

    private BizConstructionContract contract(BigDecimal amount) {
        BizConstructionContract c = new BizConstructionContract();
        c.setProjectId(PROJECT_ID);
        c.setContractAmount(amount);
        return c;
    }

    private BizPaymentReceived received(BigDecimal amount) {
        BizPaymentReceived r = new BizPaymentReceived();
        r.setProjectId(PROJECT_ID);
        r.setStatus("APPROVED");
        r.setReceiveAmount(amount);
        return r;
    }

    private BizOutputReport outputReport(String period, BigDecimal amount) {
        BizOutputReport r = new BizOutputReport();
        r.setProjectId(PROJECT_ID);
        r.setReportPeriod(period);
        r.setCurrentOutput(amount);
        return r;
    }

    // ==================== Mock 基础设施 ====================

    /** 每个属性调用独立的内存数据集 */
    private static class Stores {
        BizBudget budget;
        List<BizBudgetDetail> budgetDetails = new ArrayList<>();
        List<BizPaymentApply> payments = new ArrayList<>();
        List<BizSchedulePlan> plans = new ArrayList<>();
        List<BizConstructionContract> contracts = new ArrayList<>();
        List<BizInvoiceApply> invoices = new ArrayList<>();
        List<BizPaymentReceived> received = new ArrayList<>();
        List<BizOutputReport> reports = new ArrayList<>();
    }

    /**
     * 基于 Stores 构建带内存数据的 ProjectDashboardService。
     * 由于生成的数据均满足 service 内部 status/projectId 过滤条件，mock 直接返回完整列表。
     */
    private ProjectDashboardService buildService(Stores s) {
        BizBudgetMapper budgetMapper = Mockito.mock(BizBudgetMapper.class);
        BizBudgetDetailMapper budgetDetailMapper = Mockito.mock(BizBudgetDetailMapper.class);
        BizPaymentApplyMapper paymentApplyMapper = Mockito.mock(BizPaymentApplyMapper.class);
        BizSchedulePlanMapper schedulePlanMapper = Mockito.mock(BizSchedulePlanMapper.class);
        BizConstructionContractMapper constructionContractMapper = Mockito.mock(BizConstructionContractMapper.class);
        BizInvoiceApplyMapper invoiceApplyMapper = Mockito.mock(BizInvoiceApplyMapper.class);
        BizPaymentReceivedMapper paymentReceivedMapper = Mockito.mock(BizPaymentReceivedMapper.class);
        BizOutputReportMapper outputReportMapper = Mockito.mock(BizOutputReportMapper.class);

        when(budgetMapper.selectOne(any())).thenReturn(s.budget);
        when(budgetDetailMapper.selectList(any())).thenReturn(new ArrayList<>(s.budgetDetails));
        when(paymentApplyMapper.selectList(any())).thenReturn(new ArrayList<>(s.payments));
        when(schedulePlanMapper.selectList(any())).thenReturn(new ArrayList<>(s.plans));
        when(constructionContractMapper.selectList(any())).thenReturn(new ArrayList<>(s.contracts));
        when(invoiceApplyMapper.selectList(any())).thenReturn(new ArrayList<>(s.invoices));
        when(paymentReceivedMapper.selectList(any())).thenReturn(new ArrayList<>(s.received));
        when(outputReportMapper.selectList(any())).thenReturn(new ArrayList<>(s.reports));

        return new ProjectDashboardService(
                budgetMapper, budgetDetailMapper, paymentApplyMapper, schedulePlanMapper,
                constructionContractMapper, invoiceApplyMapper, paymentReceivedMapper, outputReportMapper);
    }
}
