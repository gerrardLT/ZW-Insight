package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.mapper.ProjectOnebookMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目一本账只读聚合服务（V2026_117，spec finance-clarity-terminology-ui 阶段三）。
 * <p>
 * 单项目六条线一次取全：①收入（六段链+应收未收）②支出（五类×四段+在途+费用类）
 * ③成本三算（CBS 目标/实际/实付）④利润三口径（预计/已实现/落袋）⑤资金（垫资+四项占用）
 * ⑥税务（销项−进项=应纳增值税）。<b>只读不落库</b>——所有数字可溯源到单据表
 * 既有累计列/台账行，规避 R7「累计值必须有单据支撑」风险。</p>
 * <p>
 * 口径纪律（与 docs/资金流转全景图.md 一致）：
 * 已实现利润 = total_income − total_expense（审批口径字段）；
 * 落袋利润 = 银行实收 − 银行实付（现金口径，流水勾稽为事实源）；
 * 预计利润 = 合同额+签证 − CBS 完工预测 EAC（无 CBS 时 null 不伪造）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectOnebookService {

    private final ProjectOnebookMapper onebookMapper;
    private final BizProjectMapper projectMapper;

    public Map<String, Object> onebook(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        BizProject project = projectMapper.selectById(projectId);
        if (project == null || project.getDeleted() != null && project.getDeleted() == 1) {
            throw new BusinessException("项目不存在：" + projectId);
        }

        Map<String, Object> income = incomeSection(projectId, project);
        Map<String, Object> expense = expenseSection(projectId, project);
        Map<String, Object> cost = costSection(projectId);
        Map<String, Object> profit = profitSection(project, income, cost, projectId);
        Map<String, Object> fund = fundSection(projectId);
        Map<String, Object> tax = taxSection(projectId);

        // 现金三项与利润段共享一次查询；垫资 = 现金流出 − 现金流入（下限 0）
        BigDecimal cashIn = (BigDecimal) profit.get("cashIn");
        BigDecimal cashOut = (BigDecimal) profit.get("cashOut");
        fund.put("cashIn", cashIn);
        fund.put("cashOut", cashOut);
        fund.put("advance", cashOut.subtract(cashIn).max(BigDecimal.ZERO));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("projectName", project.getProjectName());
        result.put("status", project.getStatus());
        result.put("income", income);
        result.put("expense", expense);
        result.put("cost", cost);
        result.put("profit", profit);
        result.put("fund", fund);
        result.put("tax", tax);
        return result;
    }

    // ==================== ① 收入 ====================

    private Map<String, Object> incomeSection(Long projectId, BizProject project) {
        Map<String, Object> chain = onebookMapper.sumIncomeChain(projectId);
        BigDecimal contract = num(chain.get("contractAmount"));
        BigDecimal visa = num(chain.get("visaAmount"));
        BigDecimal received = num(chain.get("receivedAmount"));
        BigDecimal unreceived = contract.add(visa).subtract(received).max(BigDecimal.ZERO);
        BigDecimal receivableOpen = num0(onebookMapper.sumReceivableOpen(projectId));

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("contractAmount", contract);
        section.put("visaAmount", visa);
        section.put("outputAmount", num(chain.get("outputAmount")));
        section.put("settlementAmount", num(chain.get("settlementAmount")));
        section.put("invoicedAmount", num(chain.get("invoicedAmount")));
        section.put("receivedAmount", received);
        // 未收 =（合同额+签证）− 累计回款，下限 0（合同列表同式）；与应收台账 OPEN 余额分列（口径不同：前者含未结算敞口）
        section.put("unreceived", unreceived);
        section.put("receivableOpen", receivableOpen);
        return section;
    }

    // ==================== ② 支出 ====================

    private Map<String, Object> expenseSection(Long projectId, BizProject project) {
        List<Map<String, Object>> byCategory = onebookMapper.sumExpenseByCategory(projectId);
        BigDecimal applying = num0(onebookMapper.sumApplying(projectId));
        BigDecimal reserveOutstanding = num0(onebookMapper.sumReserveOutstanding(projectId));
        BigDecimal wageUnpaid = num0(onebookMapper.sumWageUnpaid(projectId));
        BigDecimal reimbursement = num0(onebookMapper.sumReimbursementApproved(projectId));

        BigDecimal totalSettled = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        List<Map<String, Object>> categories = new ArrayList<>();
        for (Map<String, Object> row : byCategory) {
            BigDecimal settled = num(row.get("settled"));
            BigDecimal paid = num(row.get("paid"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", row.get("category"));
            item.put("contractAmount", num(row.get("contractAmount")));
            item.put("settled", settled);
            item.put("paid", paid);
            item.put("outstanding", settled.subtract(paid).max(BigDecimal.ZERO));
            item.put("invoiceReceived", num(row.get("invoiceReceived")));
            categories.add(item);
            totalSettled = totalSettled.add(settled);
            totalPaid = totalPaid.add(paid);
        }

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("categories", categories);
        section.put("totalSettled", totalSettled);
        section.put("totalPaid", totalPaid);
        section.put("totalOutstanding", totalSettled.subtract(totalPaid).max(BigDecimal.ZERO));
        section.put("applying", applying);
        section.put("reimbursementApproved", reimbursement);
        section.put("reserveOutstanding", reserveOutstanding);
        section.put("wageUnpaid", wageUnpaid);
        section.put("otherPayment", num0(otherPayment(project)));
        return section;
    }

    /** 其他支出（审批口径单列字段 biz_project.total_other_payment，不入 total_expense；复用主流程已载实体） */
    private BigDecimal otherPayment(BizProject project) {
        return project != null && project.getTotalOtherPayment() != null ? project.getTotalOtherPayment() : BigDecimal.ZERO;
    }

    // ==================== ③ 成本 ====================

    private Map<String, Object> costSection(Long projectId) {
        Map<String, Object> cbs = onebookMapper.sumCost(projectId);
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("baseline", num(cbs.get("baseline")));
        section.put("currentBudget", num(cbs.get("currentBudget")));
        section.put("commitment", num(cbs.get("commitment")));
        section.put("actual", num(cbs.get("actual")));
        section.put("forecast", num(cbs.get("forecast")));
        section.put("hasCbs", num(cbs.get("currentBudget")).signum() > 0
                || num(cbs.get("actual")).signum() > 0);
        return section;
    }

    // ==================== ④ 利润 ====================

    private Map<String, Object> profitSection(BizProject project, Map<String, Object> income,
                                              Map<String, Object> cost, Long projectId) {
        BigDecimal incomeBasis = ((BigDecimal) income.get("contractAmount"))
                .add((BigDecimal) income.get("visaAmount"));
        BigDecimal cashIn = num0(onebookMapper.sumCashIn(projectId));
        BigDecimal cashOut = num0(onebookMapper.sumCashOut(projectId));

        BigDecimal totalIncome = project.getTotalIncome() != null ? project.getTotalIncome() : BigDecimal.ZERO;
        BigDecimal totalExpense = project.getTotalExpense() != null ? project.getTotalExpense() : BigDecimal.ZERO;

        Map<String, Object> section = new LinkedHashMap<>();
        // 预计利润 =（合同额+签证）− CBS 完工预测；无 CBS（hasCbs=false）时如实 null，不退化冒充
        if (Boolean.TRUE.equals(cost.get("hasCbs"))) {
            section.put("forecastProfit", incomeBasis.subtract((BigDecimal) cost.get("forecast")));
            section.put("forecastBasis", "CONTRACT_VISA_MINUS_CBS_FORECAST");
        } else {
            section.put("forecastProfit", null);
            section.put("forecastBasis", "CBS_UNAVAILABLE");
        }
        // 已实现利润 = total_income − total_expense（既有审批口径字段，与驾驶舱一致）
        section.put("realizedProfit", totalIncome.subtract(totalExpense));
        section.put("realizedBasis", "INCOME_MINUS_EXPENSE_APPROVAL");
        // 落袋利润 = 银行实收 − 银行实付（现金口径）
        section.put("pocketProfit", cashIn.subtract(cashOut));
        section.put("pocketBasis", "BANK_CASH");
        section.put("cashIn", cashIn);
        section.put("cashOut", cashOut);
        return section;
    }

    // ==================== ⑤ 资金 ====================

    private Map<String, Object> fundSection(Long projectId) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("retentionOutstanding", num0(onebookMapper.sumRetentionOutstanding(projectId)));
        section.put("bondOccupied", num0(onebookMapper.sumBondOccupied(projectId)));
        section.put("billHeld", num0(onebookMapper.sumBillHeld(projectId)));
        section.put("payableOpen", num0(onebookMapper.sumPayableOpen(projectId)));
        section.put("receivableOpen", num0(onebookMapper.sumReceivableOpen(projectId)));
        // cashIn/cashOut/advance 由 onebook 主流程回填（与利润段共享一次查询）
        return section;
    }

    // ==================== ⑥ 税务 ====================

    private Map<String, Object> taxSection(Long projectId) {
        Map<String, Object> tax = onebookMapper.sumTax(projectId);
        BigDecimal saleTax = num(tax.get("saleTax"));
        BigDecimal inputTax = num(tax.get("inputTax"));
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("saleTax", saleTax);
        section.put("inputTax", inputTax);
        section.put("vatPayable", saleTax.subtract(inputTax));
        return section;
    }

    // ==================== 私有 ====================

    private BigDecimal num(Object v) {
        return v instanceof BigDecimal ? (BigDecimal) v : BigDecimal.ZERO;
    }

    private BigDecimal num0(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
