package com.zwinsight.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.mapper.BizBudgetMapper;
import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizOutputReport;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizOutputReportMapper;
import com.zwinsight.dashboard.dto.BudgetExecutionDTO;
import com.zwinsight.dashboard.dto.ContractReceiptDTO;
import com.zwinsight.dashboard.dto.MonthlyOutputDTO;
import com.zwinsight.dashboard.dto.OutputTrendDTO;
import com.zwinsight.dashboard.dto.ProgressDTO;
import com.zwinsight.dashboard.dto.ProjectDashboardDTO;
import com.zwinsight.dashboard.dto.SubjectDetailDTO;
import com.zwinsight.finance.domain.BizInvoiceApply;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.domain.BizPaymentReceived;
import com.zwinsight.finance.mapper.BizInvoiceApplyMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.BizPaymentReceivedMapper;
import com.zwinsight.site.domain.BizSchedulePlan;
import com.zwinsight.site.mapper.BizSchedulePlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;

/**
 * 项目维度数据看板服务
 * <p>
 * 聚合单项目的预算执行、进度完成率、合同回款、产值上报等数据。
 * 通过跨模块 Mapper 注入（zw-app 模块依赖打通）实现数据查询。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class ProjectDashboardService {

    private final BizBudgetMapper budgetMapper;
    private final BizBudgetDetailMapper budgetDetailMapper;
    private final BizPaymentApplyMapper paymentApplyMapper;
    private final BizSchedulePlanMapper schedulePlanMapper;
    private final BizConstructionContractMapper constructionContractMapper;
    private final BizInvoiceApplyMapper invoiceApplyMapper;
    private final BizPaymentReceivedMapper paymentReceivedMapper;
    private final BizOutputReportMapper outputReportMapper;
    private final com.zwinsight.budget.mapper.BizCostAccountMapper costAccountMapper;
    private final com.zwinsight.dashboard.mapper.LaborCostReadMapper laborCostReadMapper;

    /**
     * 获取项目预算执行数据
     * <p>
     * 查询该项目最新已审批预算，按一级成本科目分组聚合预算金额，
     * 并计算各科目已付金额和整体使用率。
     * </p>
     *
     * @param projectId 项目ID
     * @return 预算执行DTO（预算总额、已使用金额、使用率、各科目明细）
     */
    public BudgetExecutionDTO getBudgetExecution(Long projectId) {
        BudgetExecutionDTO dto = new BudgetExecutionDTO();

        // 查询最新已审批预算
        BizBudget budget = budgetMapper.selectOne(
                new LambdaQueryWrapper<BizBudget>()
                        .eq(BizBudget::getProjectId, projectId)
                        .eq(BizBudget::getStatus, "APPROVED")
                        .orderByDesc(BizBudget::getCreatedAt)
                        .last("LIMIT 1"));

        if (budget == null) {
            dto.setTotalBudget(BigDecimal.ZERO);
            dto.setUsedAmount(BigDecimal.ZERO);
            dto.setUsageRate(BigDecimal.ZERO);
            dto.setSubjects(Collections.emptyList());
            return dto;
        }

        BigDecimal totalBudget = budget.getTotalAmount() != null ? budget.getTotalAmount() : BigDecimal.ZERO;
        dto.setTotalBudget(totalBudget);

        // 查询预算明细，按一级成本科目分组
        List<BizBudgetDetail> details = budgetDetailMapper.selectList(
                new LambdaQueryWrapper<BizBudgetDetail>()
                        .eq(BizBudgetDetail::getBudgetId, budget.getId()));

        Map<String, BigDecimal> categoryBudgetMap = new LinkedHashMap<>();
        for (BizBudgetDetail detail : details) {
            String category = detail.getCostCategory();
            if (category == null) {
                category = "OTHER";
            }
            BigDecimal amount = detail.getBudgetTotalPrice() != null ? detail.getBudgetTotalPrice() : BigDecimal.ZERO;
            categoryBudgetMap.merge(category, amount, BigDecimal::add);
        }

        // 查询该项目所有已审批付款记录，按合同分类（即成本科目）分组
        List<BizPaymentApply> payments = paymentApplyMapper.selectList(
                new LambdaQueryWrapper<BizPaymentApply>()
                        .eq(BizPaymentApply::getProjectId, projectId)
                        .eq(BizPaymentApply::getStatus, "APPROVED"));

        Map<String, BigDecimal> categoryPaidMap = new HashMap<>();
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (BizPaymentApply payment : payments) {
            BigDecimal paymentAmount = payment.getPaymentAmount() != null ? payment.getPaymentAmount() : BigDecimal.ZERO;
            totalPaid = totalPaid.add(paymentAmount);
            String category = payment.getContractCategory();
            if (category == null) {
                category = "OTHER";
            }
            categoryPaidMap.merge(category, paymentAmount, BigDecimal::add);
        }

        dto.setUsedAmount(totalPaid);
        dto.setUsageRate(calculateRate(totalPaid, totalBudget));

        // 构建各科目明细
        List<SubjectDetailDTO> subjects = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : categoryBudgetMap.entrySet()) {
            SubjectDetailDTO subject = new SubjectDetailDTO();
            subject.setSubjectName(entry.getKey());
            subject.setBudget(entry.getValue());
            subject.setPaid(categoryPaidMap.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            subject.setRatio(calculateRate(entry.getValue(), totalBudget));
            subjects.add(subject);
        }
        dto.setSubjects(subjects);

        return dto;
    }

    /**
     * 获取项目进度完成率数据
     * <p>
     * 查询该项目的所有进度计划任务，统计总任务数和已完成任务数，
     * 计算完成百分比。
     * </p>
     *
     * @param projectId 项目ID
     * @return 进度DTO（总任务数、已完成数、完成百分比）
     */
    public ProgressDTO getProgress(Long projectId) {
        ProgressDTO dto = new ProgressDTO();

        // 查询该项目的所有进度计划任务
        List<BizSchedulePlan> plans = schedulePlanMapper.selectList(
                new LambdaQueryWrapper<BizSchedulePlan>()
                        .eq(BizSchedulePlan::getProjectId, projectId));

        int totalTasks = plans.size();
        int completedTasks = (int) plans.stream()
                .filter(p -> "COMPLETED".equals(p.getTaskStatus()))
                .count();

        dto.setTotalTasks(totalTasks);
        dto.setCompletedTasks(completedTasks);
        dto.setCompletionRate(calculateRate(new BigDecimal(completedTasks), new BigDecimal(totalTasks)));

        return dto;
    }

    /**
     * 获取项目合同与回款数据
     * <p>
     * 汇总该项目的施工合同总额、累计开票金额（已审批）、累计回款金额（已审批），
     * 并计算回款率（累计回款÷施工合同总额，保留4位小数，合同总额为0时返回0）。
     * </p>
     *
     * @param projectId 项目ID
     * @return 合同回款DTO（合同总额、累计开票、累计回款、回款率）
     */
    public ContractReceiptDTO getContractReceipt(Long projectId) {
        ContractReceiptDTO dto = new ContractReceiptDTO();

        // 施工合同总额：累加该项目所有施工合同金额
        List<BizConstructionContract> contracts = constructionContractMapper.selectList(
                new LambdaQueryWrapper<BizConstructionContract>()
                        .eq(BizConstructionContract::getProjectId, projectId));
        BigDecimal contractTotal = contracts.stream()
                .map(c -> c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 累计开票金额：该项目所有已审批开票申请的开票金额
        List<BizInvoiceApply> invoices = invoiceApplyMapper.selectList(
                new LambdaQueryWrapper<BizInvoiceApply>()
                        .eq(BizInvoiceApply::getProjectId, projectId)
                        .eq(BizInvoiceApply::getStatus, "APPROVED"));
        BigDecimal invoicedAmount = invoices.stream()
                .map(i -> i.getInvoiceAmount() != null ? i.getInvoiceAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 累计回款金额：该项目所有已审批收款登记的收款金额
        List<BizPaymentReceived> receivedList = paymentReceivedMapper.selectList(
                new LambdaQueryWrapper<BizPaymentReceived>()
                        .eq(BizPaymentReceived::getProjectId, projectId)
                        .eq(BizPaymentReceived::getStatus, "APPROVED"));
        BigDecimal receivedAmount = receivedList.stream()
                .map(p -> p.getReceiveAmount() != null ? p.getReceiveAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        dto.setContractTotal(contractTotal);
        dto.setInvoicedAmount(invoicedAmount);
        dto.setReceivedAmount(receivedAmount);
        // 回款率 = 累计回款 ÷ 施工合同总额（合同总额为0时返回0）
        dto.setReceiptRate(calculateRate(receivedAmount, contractTotal));

        return dto;
    }

    /**
     * 获取项目产值上报汇总数据
     * <p>
     * 汇总该项目的累计上报产值、本月产值，并生成近12个月的产值趋势列表
     * （按月份升序排列，缺失月份补 0）。若该项目无任何产值上报记录，趋势列表返回空列表。
     * </p>
     *
     * @param projectId 项目ID
     * @return 产值趋势DTO（累计产值、本月产值、近12月趋势）
     */
    public OutputTrendDTO getOutputTrend(Long projectId) {
        OutputTrendDTO dto = new OutputTrendDTO();

        // 查询该项目所有产值上报记录
        List<BizOutputReport> reports = outputReportMapper.selectList(
                new LambdaQueryWrapper<BizOutputReport>()
                        .eq(BizOutputReport::getProjectId, projectId));

        // 按报告期间（YYYY-MM）汇总本期产值，并统计累计产值
        Map<String, BigDecimal> periodOutputMap = new HashMap<>();
        BigDecimal totalOutput = BigDecimal.ZERO;
        for (BizOutputReport report : reports) {
            BigDecimal amount = report.getCurrentOutput() != null ? report.getCurrentOutput() : BigDecimal.ZERO;
            totalOutput = totalOutput.add(amount);
            String period = report.getReportPeriod();
            if (period != null) {
                periodOutputMap.merge(period, amount, BigDecimal::add);
            }
        }

        String currentMonth = YearMonth.now().toString();
        dto.setTotalOutput(totalOutput);
        dto.setMonthOutput(periodOutputMap.getOrDefault(currentMonth, BigDecimal.ZERO));

        // 无任何产值上报记录时返回空趋势列表
        if (reports.isEmpty()) {
            dto.setTrend(Collections.emptyList());
            return dto;
        }

        // 生成近12个月趋势（按月份升序，缺失月份补 BigDecimal.ZERO）
        List<MonthlyOutputDTO> trend = new ArrayList<>();
        YearMonth current = YearMonth.now();
        for (int i = 11; i >= 0; i--) {
            String month = current.minusMonths(i).toString();
            MonthlyOutputDTO monthly = new MonthlyOutputDTO();
            monthly.setMonth(month);
            monthly.setAmount(periodOutputMap.getOrDefault(month, BigDecimal.ZERO));
            trend.add(monthly);
        }
        dto.setTrend(trend);

        return dto;
    }

    /**
     * 获取项目看板聚合数据
     * <p>
     * 聚合调用预算执行、进度完成率、合同回款、产值上报四个维度的数据，
     * 组装为统一的项目看板响应对象。
     * </p>
     *
     * @param projectId 项目ID
     * @return 项目看板聚合DTO（预算、进度、合同、产值）
     */
    public ProjectDashboardDTO getProjectOverview(Long projectId) {
        ProjectDashboardDTO dto = new ProjectDashboardDTO();
        dto.setBudget(getBudgetExecution(projectId));
        dto.setProgress(getProgress(projectId));
        dto.setContract(getContractReceipt(projectId));
        dto.setOutput(getOutputTrend(projectId));
        return dto;
    }

    /**
     * 计算比率（分子÷分母，保留4位小数，HALF_UP 舍入）
     * <p>
     * 分母为 0 或 null 时返回 BigDecimal.ZERO。
     * </p>
     *
     * @param numerator   分子
     * @param denominator 分母
     * @return 比率值
     */
    private BigDecimal calculateRate(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }

    /**
     * 回款率四口径 + 双毛利 + 人工成本对照（C5，口径治理：分子分母随值下发，前端不自算）。
     * <p>
     * 每个指标统一为 {@code value/numerator/denominator/basis/available/unavailableReason}：
     * 分母为 0 时 available=false 并给出原因，不用 0% 冒充；毛利仅在 CBS 可用时提供
     * （无账户回退 total_expense 会混审批口径，明示不可算而非降级）。
     * 人工对照：工资性支出（工资单+点工，审批口径）未入 CBS 权责成本，只读并列呈现。
     * </p>
     */
    public Map<String, Object> getReceiptRatios(Long projectId) {
        List<BizConstructionContract> contracts = constructionContractMapper.selectList(
                new LambdaQueryWrapper<BizConstructionContract>()
                        .eq(BizConstructionContract::getProjectId, projectId)
                        .in(BizConstructionContract::getStatus, "EFFECTIVE", "SETTLED"));
        BigDecimal contractTotal = BigDecimal.ZERO;
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal outputTotal = BigDecimal.ZERO;
        BigDecimal invoiced = BigDecimal.ZERO;
        BigDecimal settled = BigDecimal.ZERO;
        for (BizConstructionContract c : contracts) {
            contractTotal = contractTotal.add(nvl(c.getContractAmount()));
            received = received.add(nvl(c.getCumulativeReceivedAmount()));
            outputTotal = outputTotal.add(nvl(c.getCumulativeOutput()));
            invoiced = invoiced.add(nvl(c.getCumulativeInvoiceAmount()));
            settled = settled.add(nvl(c.getCumulativeSettlement()));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("asOf", java.time.LocalDate.now().toString());
        result.put("collectionRateByContract", ratio(received, contractTotal, "RECEIVED_DIV_CONTRACT_AMOUNT"));
        result.put("collectionRateByOutput", ratio(received, outputTotal, "RECEIVED_DIV_CUMULATIVE_OUTPUT"));
        result.put("collectionRateByInvoice", ratio(received, invoiced, "RECEIVED_DIV_CUMULATIVE_INVOICE"));
        result.put("collectionRateBySettlement", ratio(received, settled, "RECEIVED_DIV_CUMULATIVE_SETTLEMENT"));

        // 双毛利：仅 CBS 可用（根账户防层级重复计数，与 ProfitSnapshotService 同口径）
        List<com.zwinsight.budget.domain.BizCostAccount> accounts = costAccountMapper.selectList(
                new LambdaQueryWrapper<com.zwinsight.budget.domain.BizCostAccount>()
                        .eq(com.zwinsight.budget.domain.BizCostAccount::getProjectId, projectId));
        if (accounts.isEmpty()) {
            result.put("outputGrossMargin", unavailable("CBS_UNAVAILABLE",
                    "项目未建成本账户，毛利不可算（不回退审批口径支出冒充成本）"));
            result.put("settlementGrossMargin", unavailable("CBS_UNAVAILABLE",
                    "项目未建成本账户，毛利不可算（不回退审批口径支出冒充成本）"));
        } else {
            Set<Long> ids = new HashSet<>();
            for (com.zwinsight.budget.domain.BizCostAccount a : accounts) {
                ids.add(a.getId());
            }
            BigDecimal actual = BigDecimal.ZERO;
            for (com.zwinsight.budget.domain.BizCostAccount a : accounts) {
                boolean root = a.getParentId() == null || !ids.contains(a.getParentId());
                if (root) {
                    actual = actual.add(nvl(a.getActualAmount()));
                }
            }
            result.put("outputGrossMargin", grossMargin(outputTotal, actual, "CUMULATIVE_OUTPUT_MINUS_CBS_ACTUAL"));
            result.put("settlementGrossMargin", grossMargin(settled, actual, "CUMULATIVE_SETTLEMENT_MINUS_CBS_ACTUAL"));
        }

        // 人工成本对照（只读，不写 CBS）
        Map<String, Object> laborCompare = new LinkedHashMap<>();
        BigDecimal payroll = nvl(laborCostReadMapper.sumApprovedPayroll(projectId));
        BigDecimal workOrders = nvl(laborCostReadMapper.sumApprovedWorkOrders(projectId));
        BigDecimal laborSettlement = nvl(laborCostReadMapper.sumLaborSettlement(projectId));
        laborCompare.put("wagePayrollApproved", payroll);
        laborCompare.put("pointWorkApproved", workOrders);
        laborCompare.put("wageLikeTotal", payroll.add(workOrders));
        laborCompare.put("laborSettlementTotal", laborSettlement);
        laborCompare.put("difference", payroll.add(workOrders).subtract(laborSettlement));
        laborCompare.put("basis", "WAGE_LIKE_NOT_IN_CBS");
        laborCompare.put("note", "工资性支出（工资单+点工，审批口径）未入 CBS 权责成本，会计政策待定；此处只读对照，不写成本账户");
        result.put("laborCostCompare", laborCompare);
        return result;
    }

    private Map<String, Object> ratio(BigDecimal numerator, BigDecimal denominator, String basis) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numerator", numerator);
        m.put("denominator", denominator);
        m.put("basis", basis);
        if (denominator.signum() == 0) {
            m.put("available", false);
            m.put("unavailableReason", "DENOMINATOR_ZERO");
            m.put("value", null);
        } else {
            m.put("available", true);
            m.put("value", numerator.divide(denominator, 4, RoundingMode.HALF_UP));
        }
        return m;
    }

    private Map<String, Object> grossMargin(BigDecimal basisAmount, BigDecimal actualCost, String basisLabel) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numerator", basisAmount.subtract(actualCost));
        m.put("denominator", basisAmount);
        m.put("basis", basisLabel);
        m.put("available", true);
        m.put("value", basisAmount.subtract(actualCost));
        return m;
    }

    private Map<String, Object> unavailable(String reason, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", false);
        m.put("unavailableReason", reason);
        m.put("note", note);
        m.put("value", null);
        return m;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
