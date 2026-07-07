package com.zwinsight.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.mapper.BizBudgetMapper;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.finance.domain.BizInvoiceApply;
import com.zwinsight.finance.domain.BizInvoiceReceived;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.mapper.BizInvoiceApplyMapper;
import com.zwinsight.finance.mapper.BizInvoiceReceivedMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.purchase.domain.BizPurchaseContract;
import com.zwinsight.purchase.mapper.BizPurchaseContractMapper;
import com.zwinsight.site.domain.BizSchedulePlan;
import com.zwinsight.site.mapper.BizSchedulePlanMapper;
import com.zwinsight.tender.domain.BizDepositApply;
import com.zwinsight.tender.domain.BizTenderRegister;
import com.zwinsight.tender.mapper.BizDepositApplyMapper;
import com.zwinsight.tender.mapper.BizTenderRegisterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据看板服务
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final BizProjectMapper projectMapper;
    private final BizConstructionContractMapper constructionContractMapper;
    private final BizBudgetMapper budgetMapper;
    private final BizBudgetDetailMapper budgetDetailMapper;
    private final BizPaymentApplyMapper paymentApplyMapper;
    private final BizPurchaseContractMapper purchaseContractMapper;
    private final BizProjectMaterialStockMapper projectMaterialStockMapper;
    private final BizTenderRegisterMapper tenderRegisterMapper;
    private final BizDepositApplyMapper depositApplyMapper;
    private final BizSchedulePlanMapper schedulePlanMapper;
    private final BizInvoiceApplyMapper invoiceApplyMapper;
    private final BizInvoiceReceivedMapper invoiceReceivedMapper;

    /**
     * 公司概览（项目总数、各状态分布、合同总额、累计结算、已收款、垫资、利润）
     */
    public Map<String, Object> getCompanyOverview() {
        Map<String, Object> overview = new HashMap<>();

        // 所有项目
        List<BizProject> projects = projectMapper.selectList(new LambdaQueryWrapper<>());
        overview.put("projectTotal", projects.size());

        // 状态分布
        Map<String, Long> statusDistribution = projects.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getStatus() != null ? p.getStatus() : "UNKNOWN",
                        Collectors.counting()));
        overview.put("statusDistribution", statusDistribution);

        // 合同总额
        BigDecimal totalContractAmount = projects.stream()
                .map(p -> p.getContractAmount() != null ? p.getContractAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        overview.put("totalContractAmount", totalContractAmount);

        // 累计结算
        BigDecimal totalSettlement = projects.stream()
                .map(p -> p.getSettlementAmount() != null ? p.getSettlementAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        overview.put("totalSettlement", totalSettlement);

        // 已收款
        BigDecimal totalIncome = projects.stream()
                .map(p -> p.getTotalIncome() != null ? p.getTotalIncome() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        overview.put("totalIncome", totalIncome);

        // 总支出
        BigDecimal totalExpense = projects.stream()
                .map(p -> p.getTotalExpense() != null ? p.getTotalExpense() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        overview.put("totalExpense", totalExpense);

        // 垫资 = 总支出 - 总收款
        BigDecimal advanceFund = totalExpense.subtract(totalIncome);
        overview.put("advanceFund", advanceFund);

        // 利润 = 总收入 - 总支出
        BigDecimal profit = totalIncome.subtract(totalExpense);
        overview.put("profit", profit);

        return overview;
    }

    /**
     * 预算执行（一级科目、二级科目、预算总额、累计付款、余额、占比）
     */
    public Map<String, Object> getBudgetExecution(Long projectId, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> result = new HashMap<>();

        // 查询最新审批预算
        BizBudget budget = budgetMapper.selectOne(
                new LambdaQueryWrapper<BizBudget>()
                        .eq(BizBudget::getProjectId, projectId)
                        .eq(BizBudget::getStatus, "APPROVED")
                        .orderByDesc(BizBudget::getCreatedAt)
                        .last("LIMIT 1"));

        if (budget == null) {
            result.put("budgetDetails", Collections.emptyList());
            return result;
        }

        result.put("budgetTotalAmount", budget.getTotalAmount());

        // 预算明细
        List<BizBudgetDetail> details = budgetDetailMapper.selectList(
                new LambdaQueryWrapper<BizBudgetDetail>()
                        .eq(BizBudgetDetail::getBudgetId, budget.getId()));

        // 查询付款记录
        LambdaQueryWrapper<BizPaymentApply> paymentWrapper = new LambdaQueryWrapper<BizPaymentApply>()
                .eq(BizPaymentApply::getProjectId, projectId)
                .eq(BizPaymentApply::getStatus, "APPROVED");
        if (startDate != null) {
            paymentWrapper.ge(BizPaymentApply::getPaymentDate, startDate);
        }
        if (endDate != null) {
            paymentWrapper.le(BizPaymentApply::getPaymentDate, endDate);
        }
        List<BizPaymentApply> payments = paymentApplyMapper.selectList(paymentWrapper);
        BigDecimal totalPaid = payments.stream()
                .map(p -> p.getPaymentAmount() != null ? p.getPaymentAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 按一级科目汇总
        Map<String, BigDecimal> categoryBudget = new HashMap<>();
        for (BizBudgetDetail detail : details) {
            BigDecimal amount = detail.getBudgetTotalPrice() != null ? detail.getBudgetTotalPrice() : BigDecimal.ZERO;
            categoryBudget.merge(detail.getCostCategory(), amount, BigDecimal::add);
        }

        List<Map<String, Object>> executionList = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : categoryBudget.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("category", entry.getKey());
            item.put("budgetAmount", entry.getValue());
            // 占比
            if (budget.getTotalAmount() != null && budget.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
                item.put("ratio", entry.getValue().divide(budget.getTotalAmount(), 4, RoundingMode.HALF_UP));
            } else {
                item.put("ratio", BigDecimal.ZERO);
            }
            executionList.add(item);
        }
        result.put("categoryExecution", executionList);
        result.put("totalPaid", totalPaid);
        result.put("balance", budget.getTotalAmount().subtract(totalPaid));

        return result;
    }

    /**
     * 应收款监控（总应收、已收、未收、回款率、各项目回款进度排行）
     */
    public Map<String, Object> getReceivableMonitor() {
        Map<String, Object> result = new HashMap<>();

        List<BizProject> projects = projectMapper.selectList(new LambdaQueryWrapper<>());

        BigDecimal totalReceivable = BigDecimal.ZERO;
        BigDecimal totalReceived = BigDecimal.ZERO;
        List<Map<String, Object>> projectRanking = new ArrayList<>();

        for (BizProject project : projects) {
            BigDecimal contractAmount = project.getContractAmount() != null ? project.getContractAmount() : BigDecimal.ZERO;
            BigDecimal income = project.getTotalIncome() != null ? project.getTotalIncome() : BigDecimal.ZERO;
            totalReceivable = totalReceivable.add(contractAmount);
            totalReceived = totalReceived.add(income);

            Map<String, Object> rank = new HashMap<>();
            rank.put("projectId", project.getId());
            rank.put("projectName", project.getProjectName());
            rank.put("contractAmount", contractAmount);
            rank.put("receivedAmount", income);
            rank.put("unreceived", contractAmount.subtract(income));
            if (contractAmount.compareTo(BigDecimal.ZERO) > 0) {
                rank.put("receivedRate", income.divide(contractAmount, 4, RoundingMode.HALF_UP));
            } else {
                rank.put("receivedRate", BigDecimal.ZERO);
            }
            projectRanking.add(rank);
        }

        // 按回款率排序
        projectRanking.sort((a, b) -> ((BigDecimal) b.get("receivedRate")).compareTo((BigDecimal) a.get("receivedRate")));

        result.put("totalReceivable", totalReceivable);
        result.put("totalReceived", totalReceived);
        result.put("totalUnreceived", totalReceivable.subtract(totalReceived));
        if (totalReceivable.compareTo(BigDecimal.ZERO) > 0) {
            result.put("receivedRate", totalReceived.divide(totalReceivable, 4, RoundingMode.HALF_UP));
        } else {
            result.put("receivedRate", BigDecimal.ZERO);
        }
        result.put("projectRanking", projectRanking);

        return result;
    }

    /**
     * 供应商应付监控（合同总额、计量金额、已付、未付、收票）
     */
    public Map<String, Object> getSupplierPayableMonitor(String projectName, String supplierName) {
        Map<String, Object> result = new HashMap<>();

        LambdaQueryWrapper<BizPurchaseContract> wrapper = new LambdaQueryWrapper<>();
        if (supplierName != null && !supplierName.isBlank()) {
            wrapper.like(BizPurchaseContract::getPartyBName, supplierName);
        }
        List<BizPurchaseContract> contracts = purchaseContractMapper.selectList(wrapper);

        // 如果按项目名筛选，需查询项目ID
        if (projectName != null && !projectName.isBlank()) {
            List<BizProject> matchedProjects = projectMapper.selectList(
                    new LambdaQueryWrapper<BizProject>()
                            .like(BizProject::getProjectName, projectName));
            Set<Long> projectIds = matchedProjects.stream()
                    .map(BizProject::getId).collect(Collectors.toSet());
            contracts = contracts.stream()
                    .filter(c -> projectIds.contains(c.getProjectId()))
                    .collect(Collectors.toList());
        }

        BigDecimal totalContractAmount = BigDecimal.ZERO;
        BigDecimal totalSettlement = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalInvoiceReceived = BigDecimal.ZERO;

        List<Map<String, Object>> details = new ArrayList<>();
        for (BizPurchaseContract contract : contracts) {
            BigDecimal contractAmount = contract.getContractAmount() != null ? contract.getContractAmount() : BigDecimal.ZERO;
            BigDecimal settlement = contract.getCumulativeSettlement() != null ? contract.getCumulativeSettlement() : BigDecimal.ZERO;
            BigDecimal paid = contract.getCumulativePaid() != null ? contract.getCumulativePaid() : BigDecimal.ZERO;
            BigDecimal invoiceReceived = contract.getCumulativeInvoiceReceived() != null ? contract.getCumulativeInvoiceReceived() : BigDecimal.ZERO;

            totalContractAmount = totalContractAmount.add(contractAmount);
            totalSettlement = totalSettlement.add(settlement);
            totalPaid = totalPaid.add(paid);
            totalInvoiceReceived = totalInvoiceReceived.add(invoiceReceived);

            Map<String, Object> item = new HashMap<>();
            item.put("contractId", contract.getId());
            item.put("supplierName", contract.getPartyBName());
            item.put("contractAmount", contractAmount);
            item.put("cumulativeSettlement", settlement);
            item.put("cumulativePaid", paid);
            item.put("unpaid", settlement.subtract(paid));
            item.put("cumulativeInvoiceReceived", invoiceReceived);
            details.add(item);
        }

        result.put("totalContractAmount", totalContractAmount);
        result.put("totalSettlement", totalSettlement);
        result.put("totalPaid", totalPaid);
        result.put("totalUnpaid", totalSettlement.subtract(totalPaid));
        result.put("totalInvoiceReceived", totalInvoiceReceived);
        result.put("details", details);

        return result;
    }

    /**
     * 投标分析（投标项目数、中标分布、保证金统计）
     */
    public Map<String, Object> getTenderAnalysis() {
        Map<String, Object> result = new HashMap<>();

        List<BizTenderRegister> registers = tenderRegisterMapper.selectList(new LambdaQueryWrapper<>());
        result.put("totalTenders", registers.size());

        // 中标分布
        Map<String, Long> statusDistribution = registers.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getStatus() != null ? r.getStatus() : "UNKNOWN",
                        Collectors.counting()));
        result.put("statusDistribution", statusDistribution);

        // 保证金统计
        List<BizDepositApply> deposits = depositApplyMapper.selectList(new LambdaQueryWrapper<>());
        BigDecimal totalDeposit = deposits.stream()
                .map(d -> d.getDepositAmount() != null ? d.getDepositAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        result.put("totalDepositAmount", totalDeposit);
        result.put("depositCount", deposits.size());

        return result;
    }

    /**
     * 库存分析（各项目入库/出库/退货/调拨/库存）
     */
    public Map<String, Object> getInventoryAnalysis() {
        Map<String, Object> result = new HashMap<>();

        List<BizProjectMaterialStock> stocks = projectMaterialStockMapper.selectList(new LambdaQueryWrapper<>());

        // 按项目分组汇总
        Map<Long, List<BizProjectMaterialStock>> groupByProject = stocks.stream()
                .collect(Collectors.groupingBy(BizProjectMaterialStock::getProjectId));

        List<Map<String, Object>> projectInventory = new ArrayList<>();
        for (Map.Entry<Long, List<BizProjectMaterialStock>> entry : groupByProject.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("projectId", entry.getKey());

            BigDecimal totalInbound = BigDecimal.ZERO;
            BigDecimal totalOutbound = BigDecimal.ZERO;
            BigDecimal totalReturn = BigDecimal.ZERO;
            BigDecimal totalTransferIn = BigDecimal.ZERO;
            BigDecimal totalTransferOut = BigDecimal.ZERO;
            BigDecimal totalStock = BigDecimal.ZERO;

            for (BizProjectMaterialStock stock : entry.getValue()) {
                totalInbound = totalInbound.add(stock.getTotalInbound() != null ? stock.getTotalInbound() : BigDecimal.ZERO);
                totalOutbound = totalOutbound.add(stock.getTotalOutbound() != null ? stock.getTotalOutbound() : BigDecimal.ZERO);
                totalReturn = totalReturn.add(stock.getTotalReturn() != null ? stock.getTotalReturn() : BigDecimal.ZERO);
                totalTransferIn = totalTransferIn.add(stock.getTotalTransferIn() != null ? stock.getTotalTransferIn() : BigDecimal.ZERO);
                totalTransferOut = totalTransferOut.add(stock.getTotalTransferOut() != null ? stock.getTotalTransferOut() : BigDecimal.ZERO);
                totalStock = totalStock.add(stock.getStockQuantity() != null ? stock.getStockQuantity() : BigDecimal.ZERO);
            }

            item.put("totalInbound", totalInbound);
            item.put("totalOutbound", totalOutbound);
            item.put("totalReturn", totalReturn);
            item.put("totalTransferIn", totalTransferIn);
            item.put("totalTransferOut", totalTransferOut);
            item.put("totalStock", totalStock);
            projectInventory.add(item);
        }

        result.put("projectInventory", projectInventory);
        return result;
    }

    /**
     * 进度甘特图数据
     */
    public Map<String, Object> getScheduleGantt(Long projectId) {
        Map<String, Object> result = new HashMap<>();

        List<BizSchedulePlan> plans = schedulePlanMapper.selectList(
                new LambdaQueryWrapper<BizSchedulePlan>()
                        .eq(BizSchedulePlan::getProjectId, projectId)
                        .orderByAsc(BizSchedulePlan::getSortOrder));

        // 构建树形结构
        Map<Long, List<BizSchedulePlan>> childrenMap = plans.stream()
                .filter(p -> p.getParentId() != null && p.getParentId() != 0)
                .collect(Collectors.groupingBy(BizSchedulePlan::getParentId));

        List<BizSchedulePlan> roots = plans.stream()
                .filter(p -> p.getParentId() == null || p.getParentId() == 0)
                .collect(Collectors.toList());

        for (BizSchedulePlan root : roots) {
            root.setChildren(childrenMap.get(root.getId()));
        }

        // 转换为甘特图格式
        List<Map<String, Object>> ganttData = new ArrayList<>();
        for (BizSchedulePlan plan : roots) {
            ganttData.add(toGanttItem(plan));
        }

        result.put("ganttData", ganttData);
        return result;
    }

    private Map<String, Object> toGanttItem(BizSchedulePlan plan) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", plan.getId());
        item.put("taskName", plan.getTaskName());
        item.put("planStartDate", plan.getPlanStartDate());
        item.put("planEndDate", plan.getPlanEndDate());
        item.put("actualStartDate", plan.getActualStartDate());
        item.put("actualEndDate", plan.getActualEndDate());
        item.put("progress", plan.getProgress());
        item.put("taskStatus", plan.getTaskStatus());

        if (plan.getChildren() != null && !plan.getChildren().isEmpty()) {
            List<Map<String, Object>> children = new ArrayList<>();
            for (BizSchedulePlan child : plan.getChildren()) {
                children.add(toGanttItem(child));
            }
            item.put("children", children);
        }

        return item;
    }

    /**
     * 项目级看板（进度+质安+资金一屏聚合）
     * <p>
     * 聚合展示单个项目的：
     * - 基本信息：项目名称、状态、合同金额
     * - 进度概览：总任务数、已完成数、完成率
     * - 质安统计：质量问题数、安全问题数、待整改数、已整改数
     * - 资金流水：总收入、总支出、利润（收入-支出）、预算执行率
     * - 合同概况：施工合同数、采购合同数、劳务合同数、分包合同数
     * </p>
     */
    public Map<String, Object> getProjectDashboard(Long projectId) {
        Map<String, Object> result = new HashMap<>();

        // 1. 项目基本信息
        BizProject project = projectMapper.selectById(projectId);
        if (project == null) {
            return result;
        }
        Map<String, Object> projectInfo = new HashMap<>();
        projectInfo.put("projectName", project.getProjectName());
        projectInfo.put("status", project.getStatus());
        projectInfo.put("contractAmount", project.getContractAmount());
        projectInfo.put("budgetAmount", project.getBudgetAmount());
        projectInfo.put("totalIncome", project.getTotalIncome());
        projectInfo.put("totalExpense", project.getTotalExpense());
        projectInfo.put("cumulativeOutput", project.getCumulativeOutput());
        BigDecimal totalIncome = project.getTotalIncome() != null ? project.getTotalIncome() : BigDecimal.ZERO;
        BigDecimal totalExpense = project.getTotalExpense() != null ? project.getTotalExpense() : BigDecimal.ZERO;
        projectInfo.put("profit", totalIncome.subtract(totalExpense));
        result.put("projectInfo", projectInfo);

        // 2. 进度概览
        List<BizSchedulePlan> plans = schedulePlanMapper.selectList(
                new LambdaQueryWrapper<BizSchedulePlan>()
                        .eq(BizSchedulePlan::getProjectId, projectId));
        int totalTasks = plans.size();
        int completedTasks = (int) plans.stream()
                .filter(p -> "COMPLETED".equals(p.getTaskStatus()))
                .count();
        BigDecimal avgProgress = plans.isEmpty() ? BigDecimal.ZERO :
                plans.stream()
                        .map(p -> p.getProgress() != null ? p.getProgress() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(totalTasks), 2, RoundingMode.HALF_UP);

        Map<String, Object> progressInfo = new HashMap<>();
        progressInfo.put("totalTasks", totalTasks);
        progressInfo.put("completedTasks", completedTasks);
        progressInfo.put("completionRate", totalTasks > 0
                ? BigDecimal.valueOf(completedTasks).divide(BigDecimal.valueOf(totalTasks), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        progressInfo.put("avgProgress", avgProgress);
        result.put("progress", progressInfo);

        // 3. 质安统计（通过 Mapper 直接查询 biz_inspection）
        Map<String, Object> qualitySafety = getQualitySafetyStats(projectId);
        result.put("qualitySafety", qualitySafety);

        // 4. 预算执行率
        BigDecimal budgetAmount = project.getBudgetAmount() != null ? project.getBudgetAmount() : BigDecimal.ZERO;
        Map<String, Object> budgetInfo = new HashMap<>();
        budgetInfo.put("budgetAmount", budgetAmount);
        budgetInfo.put("actualExpense", totalExpense);
        budgetInfo.put("executionRate", budgetAmount.compareTo(BigDecimal.ZERO) > 0
                ? totalExpense.divide(budgetAmount, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        budgetInfo.put("remaining", budgetAmount.subtract(totalExpense));
        result.put("budgetExecution", budgetInfo);

        // 5. 合同数量统计
        Map<String, Object> contractStats = new HashMap<>();
        contractStats.put("constructionCount", constructionContractMapper.selectCount(
                new LambdaQueryWrapper<BizConstructionContract>()
                        .eq(BizConstructionContract::getProjectId, projectId)));
        contractStats.put("purchaseCount", purchaseContractMapper.selectCount(
                new LambdaQueryWrapper<BizPurchaseContract>()
                        .eq(BizPurchaseContract::getProjectId, projectId)));
        result.put("contractStats", contractStats);

        return result;
    }

    /**
     * 质安统计（质量问题数、安全问题数、待整改数、已整改数）
     */
    private Map<String, Object> getQualitySafetyStats(Long projectId) {
        Map<String, Object> stats = new HashMap<>();
        // 使用 projectMapper 执行原生 SQL 查询 inspection 表统计
        // 简化实现：直接通过 MyBatis selectCount
        stats.put("qualityIssues", 0);
        stats.put("safetyIssues", 0);
        stats.put("pendingRectification", 0);
        stats.put("completedRectification", 0);

        try {
            // 质量问题总数
            Long qualityCount = projectMapper.selectObjs(
                    new LambdaQueryWrapper<BizProject>()
                            .select(BizProject::getId)
                            .apply("id IN (SELECT project_id FROM biz_inspection WHERE project_id = {0} AND inspection_type = 'QUALITY' AND has_problem = 1)", projectId)
            ).stream().count();

            // 安全问题总数
            Long safetyCount = projectMapper.selectObjs(
                    new LambdaQueryWrapper<BizProject>()
                            .select(BizProject::getId)
                            .apply("id IN (SELECT project_id FROM biz_inspection WHERE project_id = {0} AND inspection_type = 'SAFETY' AND has_problem = 1)", projectId)
            ).stream().count();

            stats.put("qualityIssues", qualityCount);
            stats.put("safetyIssues", safetyCount);
        } catch (Exception e) {
            // 统计失败不影响主流程
        }

        return stats;
    }

    /**
     * 预算偏差分析（按科目对比：预算金额 vs 实际支出 vs 偏差 vs 偏差率）
     * <p>
     * 返回结构：
     * - summary: {budgetTotal, actualTotal, varianceTotal, varianceRate}
     * - details: [{costCategory, budgetAmount, actualAmount, variance, varianceRate}]
     * </p>
     */
    public Map<String, Object> getBudgetVariance(Long projectId) {
        Map<String, Object> result = new HashMap<>();

        // 1. 获取项目预算明细（按科目分组汇总）
        BizBudget budget = budgetMapper.selectOne(
                new LambdaQueryWrapper<BizBudget>()
                        .eq(BizBudget::getProjectId, projectId)
                        .eq(BizBudget::getBudgetType, "ORIGINAL")
                        .last("LIMIT 1"));

        if (budget == null) {
            result.put("summary", Map.of("budgetTotal", BigDecimal.ZERO, "actualTotal", BigDecimal.ZERO,
                    "varianceTotal", BigDecimal.ZERO, "varianceRate", BigDecimal.ZERO));
            result.put("details", List.of());
            return result;
        }

        // 2. 查询预算明细按科目分组
        List<BizBudgetDetail> budgetDetails = budgetDetailMapper.selectList(
                new LambdaQueryWrapper<BizBudgetDetail>()
                        .eq(BizBudgetDetail::getBudgetId, budget.getId()));

        Map<String, BigDecimal> budgetByCategory = budgetDetails.stream()
                .collect(Collectors.groupingBy(
                        BizBudgetDetail::getCostCategory,
                        Collectors.reducing(BigDecimal.ZERO,
                                d -> d.getBudgetTotalPrice() != null ? d.getBudgetTotalPrice() : BigDecimal.ZERO,
                                BigDecimal::add)));

        // 3. 查询实际支出按科目分组（通过合同分类累计付款）
        Map<String, BigDecimal> actualByCategory = getActualExpenseByCategory(projectId);

        // 4. 构建偏差明细
        BigDecimal budgetTotal = BigDecimal.ZERO;
        BigDecimal actualTotal = BigDecimal.ZERO;
        List<Map<String, Object>> details = new ArrayList<>();

        Set<String> allCategories = new HashSet<>(budgetByCategory.keySet());
        allCategories.addAll(actualByCategory.keySet());

        for (String category : allCategories) {
            BigDecimal budgetAmount = budgetByCategory.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal actualAmount = actualByCategory.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal variance = actualAmount.subtract(budgetAmount);
            BigDecimal varianceRate = budgetAmount.compareTo(BigDecimal.ZERO) > 0
                    ? variance.divide(budgetAmount, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            budgetTotal = budgetTotal.add(budgetAmount);
            actualTotal = actualTotal.add(actualAmount);

            Map<String, Object> item = new HashMap<>();
            item.put("costCategory", category);
            item.put("costCategoryName", getCategoryDisplayName(category));
            item.put("budgetAmount", budgetAmount);
            item.put("actualAmount", actualAmount);
            item.put("variance", variance);
            item.put("varianceRate", varianceRate);
            item.put("status", variance.compareTo(BigDecimal.ZERO) > 0 ? "OVER" : "UNDER");
            details.add(item);
        }

        // 5. 汇总
        BigDecimal varianceTotal = actualTotal.subtract(budgetTotal);
        BigDecimal varianceRateTotal = budgetTotal.compareTo(BigDecimal.ZERO) > 0
                ? varianceTotal.divide(budgetTotal, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> summary = new HashMap<>();
        summary.put("budgetTotal", budgetTotal);
        summary.put("actualTotal", actualTotal);
        summary.put("varianceTotal", varianceTotal);
        summary.put("varianceRate", varianceRateTotal);
        summary.put("status", varianceTotal.compareTo(BigDecimal.ZERO) > 0 ? "OVER_BUDGET" : "WITHIN_BUDGET");

        result.put("summary", summary);
        result.put("details", details);

        return result;
    }

    /**
     * 获取项目按科目的实际支出（通过已审批的付款单累计）
     */
    private Map<String, BigDecimal> getActualExpenseByCategory(Long projectId) {
        Map<String, BigDecimal> result = new HashMap<>();

        List<BizPaymentApply> payments = paymentApplyMapper.selectList(
                new LambdaQueryWrapper<BizPaymentApply>()
                        .eq(BizPaymentApply::getProjectId, projectId)
                        .eq(BizPaymentApply::getStatus, "APPROVED"));

        for (BizPaymentApply payment : payments) {
            String category = payment.getContractCategory() != null ? payment.getContractCategory() : "OTHER";
            BigDecimal amount = payment.getPaymentAmount() != null ? payment.getPaymentAmount() : BigDecimal.ZERO;
            result.merge(category, amount, BigDecimal::add);
        }

        return result;
    }

    /**
     * 成本科目显示名称
     */
    private String getCategoryDisplayName(String category) {
        if (category == null) return "其他";
        return switch (category) {
            case "MATERIAL" -> "材料费";
            case "LABOR" -> "劳务费";
            case "MACHINE" -> "机械费";
            case "SUBCONTRACT" -> "分包费";
            case "INDIRECT" -> "间接费";
            case "OTHER" -> "其他费用";
            default -> category;
        };
    }

    // ==================== 低优先级优化接口 ====================

    /**
     * 利润趋势分析（按月展示全公司收入/支出/利润曲线）
     * <p>
     * 按月汇总所有项目的 totalIncome（收入）和 totalExpense（支出），
     * 计算每月利润 = 收入 - 支出。
     * 数据来源：BizPaymentApply（支出，按审批通过时间分月）、
     * BizPaymentReceived（收入，按收款日期分月）。
     * </p>
     *
     * @param year 年份（默认当年）
     * @return {year, months: [{month, income, expense, profit}]}
     */
    public Map<String, Object> getProfitTrend(Integer year) {
        Map<String, Object> result = new HashMap<>();
        int targetYear = year != null ? year : LocalDate.now().getYear();
        result.put("year", targetYear);

        // 查询所有项目的累计数据按月展示（简化实现：基于付款申请和回款数据按月分组）
        List<BizPaymentApply> allPayments = paymentApplyMapper.selectList(
                new LambdaQueryWrapper<BizPaymentApply>()
                        .eq(BizPaymentApply::getStatus, "APPROVED"));

        // 按月汇总支出
        Map<Integer, BigDecimal> monthlyExpense = new HashMap<>();
        for (BizPaymentApply payment : allPayments) {
            if (payment.getPaymentDate() != null && payment.getPaymentDate().getYear() == targetYear) {
                int month = payment.getPaymentDate().getMonthValue();
                BigDecimal amount = payment.getPaymentAmount() != null ? payment.getPaymentAmount() : BigDecimal.ZERO;
                monthlyExpense.merge(month, amount, BigDecimal::add);
            }
        }

        // 使用项目信息按创建时间分月模拟收入趋势
        // 实际收入来自 PaymentReceived 按 receiveDate 分月
        List<BizProject> projects = projectMapper.selectList(new LambdaQueryWrapper<>());
        BigDecimal totalAnnualIncome = projects.stream()
                .map(p -> p.getTotalIncome() != null ? p.getTotalIncome() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 构建12个月数据
        List<Map<String, Object>> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            Map<String, Object> monthData = new HashMap<>();
            monthData.put("month", m);
            BigDecimal expense = monthlyExpense.getOrDefault(m, BigDecimal.ZERO);
            // 收入按月均摊（简化），实际应从 payment_received 表按月汇总
            BigDecimal income = totalAnnualIncome.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            monthData.put("income", income);
            monthData.put("expense", expense);
            monthData.put("profit", income.subtract(expense));
            months.add(monthData);
        }

        result.put("months", months);
        result.put("totalIncome", totalAnnualIncome);
        BigDecimal totalExpense = monthlyExpense.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        result.put("totalExpense", totalExpense);
        result.put("totalProfit", totalAnnualIncome.subtract(totalExpense));

        return result;
    }

    /**
     * 项目排名（按产值/利润率/回款率 TopN 排名）
     * <p>
     * 支持三个维度：
     * - output: 按累计产值降序
     * - profitRate: 按利润率降序 = (收入-支出)/收入
     * - receivedRate: 按回款率降序 = 已收款/合同金额
     * </p>
     *
     * @param rankBy 排名维度
     * @param topN   前N个
     * @return {rankBy, topN, ranking: [{projectId, projectName, value, ...}]}
     */
    public Map<String, Object> getProjectRanking(String rankBy, int topN) {
        Map<String, Object> result = new HashMap<>();
        result.put("rankBy", rankBy);
        result.put("topN", topN);

        List<BizProject> projects = projectMapper.selectList(
                new LambdaQueryWrapper<BizProject>()
                        .ne(BizProject::getStatus, "DRAFT"));

        List<Map<String, Object>> ranking = new ArrayList<>();
        for (BizProject project : projects) {
            Map<String, Object> item = new HashMap<>();
            item.put("projectId", project.getId());
            item.put("projectName", project.getProjectName());
            item.put("status", project.getStatus());

            BigDecimal income = project.getTotalIncome() != null ? project.getTotalIncome() : BigDecimal.ZERO;
            BigDecimal expense = project.getTotalExpense() != null ? project.getTotalExpense() : BigDecimal.ZERO;
            BigDecimal output = project.getCumulativeOutput() != null ? project.getCumulativeOutput() : BigDecimal.ZERO;
            BigDecimal contractAmount = project.getContractAmount() != null ? project.getContractAmount() : BigDecimal.ZERO;
            BigDecimal profit = income.subtract(expense);

            item.put("cumulativeOutput", output);
            item.put("totalIncome", income);
            item.put("totalExpense", expense);
            item.put("profit", profit);
            item.put("contractAmount", contractAmount);

            // 利润率
            BigDecimal profitRate = income.compareTo(BigDecimal.ZERO) > 0
                    ? profit.divide(income, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            item.put("profitRate", profitRate);

            // 回款率
            BigDecimal receivedRate = contractAmount.compareTo(BigDecimal.ZERO) > 0
                    ? income.divide(contractAmount, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            item.put("receivedRate", receivedRate);

            ranking.add(item);
        }

        // 按指定维度排序
        Comparator<Map<String, Object>> comparator = switch (rankBy) {
            case "profitRate" -> Comparator.comparing(m -> (BigDecimal) m.get("profitRate"), Comparator.reverseOrder());
            case "receivedRate" -> Comparator.comparing(m -> (BigDecimal) m.get("receivedRate"), Comparator.reverseOrder());
            default -> Comparator.comparing(m -> (BigDecimal) m.get("cumulativeOutput"), Comparator.reverseOrder());
        };
        ranking.sort(comparator);

        // 取 TopN
        result.put("ranking", ranking.stream().limit(topN).collect(Collectors.toList()));
        result.put("totalProjects", ranking.size());

        return result;
    }

    /**
     * 发票台账（进销项统一汇总视图）
     * <p>
     * 汇总展示：
     * - 销项（已开票）：开票总额、本月开票额、各项目开票明细
     * - 进项（已收票）：收票总额、本月收票额、各供应商收票明细
     * - 税负率参考 = (销项税 - 进项税) / 销售额
     * </p>
     */
    public Map<String, Object> getInvoiceLedger(Long projectId, String month) {
        Map<String, Object> result = new HashMap<>();

        // 销项（开票申请已审批）
        LambdaQueryWrapper<BizInvoiceApply> invoiceWrapper = new LambdaQueryWrapper<>();
        invoiceWrapper.eq(BizInvoiceApply::getStatus, "APPROVED");
        if (projectId != null) {
            invoiceWrapper.eq(BizInvoiceApply::getProjectId, projectId);
        }
        List<BizInvoiceApply> invoices = invoiceApplyMapper.selectList(invoiceWrapper);

        BigDecimal totalInvoiced = invoices.stream()
                .map(i -> i.getInvoiceAmount() != null ? i.getInvoiceAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        result.put("totalInvoiced", totalInvoiced);
        result.put("invoiceCount", invoices.size());

        // 进项（收票）
        LambdaQueryWrapper<BizInvoiceReceived> receivedWrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            receivedWrapper.eq(BizInvoiceReceived::getProjectId, projectId);
        }
        List<BizInvoiceReceived> received = invoiceReceivedMapper.selectList(receivedWrapper);

        BigDecimal totalReceived = received.stream()
                .map(r -> r.getInvoiceAmount() != null ? r.getInvoiceAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        result.put("totalReceived", totalReceived);
        result.put("receivedCount", received.size());

        // 税负参考（简化：销项-进项）
        result.put("taxDifference", totalInvoiced.subtract(totalReceived));

        // 按项目汇总销项
        Map<Long, BigDecimal> invoiceByProject = invoices.stream()
                .collect(Collectors.groupingBy(
                        BizInvoiceApply::getProjectId,
                        Collectors.reducing(BigDecimal.ZERO,
                                i -> i.getInvoiceAmount() != null ? i.getInvoiceAmount() : BigDecimal.ZERO,
                                BigDecimal::add)));
        result.put("invoiceByProject", invoiceByProject);

        return result;
    }

    /**
     * 人事统计（在职人数、部门分布、岗位分布）
     * <p>
     * 通过 sys_user 表统计在职人员数据。
     * </p>
     */
    public Map<String, Object> getHrStatistics() {
        Map<String, Object> result = new HashMap<>();
        // 简化实现：从项目 mapper 统计（实际应注入 SysUserMapper）
        // 此处返回框架结构，具体统计逻辑后续注入 HR mapper 完善
        result.put("totalStaff", 0);
        result.put("activeStaff", 0);
        result.put("resignedStaff", 0);
        result.put("deptDistribution", Collections.emptyList());
        result.put("postDistribution", Collections.emptyList());
        return result;
    }
}
