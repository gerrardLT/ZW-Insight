package com.zwinsight.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.mapper.BizBudgetMapper;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.finance.domain.BizPaymentApply;
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
}
