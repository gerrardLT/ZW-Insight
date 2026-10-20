package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zwinsight.finance.domain.BizInvoiceReceived;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.domain.BizReceivable;
import com.zwinsight.finance.mapper.BizBankFlowMapper;
import com.zwinsight.finance.mapper.BizInvoiceReceivedMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.BizReceivableMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 财务待办首屏聚合（S3.1）。
 *
 * <p>一次返回待审批付款、已批未付、逾期未付、应收逾期与 TOP5 清单。
 * 所有查询走 finance 侧带 {@code @DataPermission} 的 Mapper；金额统一 BigDecimal，
 * 空结果返回 0，不返回 null。</p>
 */
@Service
@RequiredArgsConstructor
public class FinanceWorkbenchService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final BizPaymentApplyMapper paymentApplyMapper;
    private final BizReceivableMapper receivableMapper;
    private final BizInvoiceReceivedMapper invoiceReceivedMapper;
    private final BizBankFlowMapper bankFlowMapper;
    private final BizProjectMapper projectMapper;

    public Map<String, Object> summary() {
        LocalDate today = LocalDate.now();

        // 待审批：尚未付款，按申请全额统计
        Map<String, Object> pendingApproval = first(paymentApplyMapper.selectMaps(
                new QueryWrapper<BizPaymentApply>()
                        .select("COUNT(*) AS cnt", "COALESCE(SUM(payment_amount), 0) AS amt")
                        .eq("status", "SUBMITTED")));
        // 已批未付 / 逾期未付：按「剩余未付额」口径（申请额扣减银行已勾稽），与
        // BizPaymentApplyMapper#sumApprovedUnpaidRemaining 同源，避免部分付款重复计入资金压力
        Map<String, Object> approvedUnpaid = row(paymentApplyMapper.sumApprovedUnpaidAgg(null));
        Map<String, Object> overduePayment = row(paymentApplyMapper.sumApprovedUnpaidAgg(today));
        Map<String, Object> receivableOpen = first(receivableMapper.selectMaps(
                new QueryWrapper<BizReceivable>()
                        .select("COUNT(*) AS cnt",
                                "COALESCE(SUM(receivable_amount - COALESCE(written_off_amount, 0)), 0) AS amt")
                        .eq("status", "OPEN")));
        Map<String, Object> receivableOverdue = first(receivableMapper.selectMaps(
                new QueryWrapper<BizReceivable>()
                        .select("COUNT(*) AS cnt",
                                "COALESCE(SUM(receivable_amount - COALESCE(written_off_amount, 0)), 0) AS amt")
                        .eq("status", "OPEN")
                        .lt("due_date", today)
                        .apply("(receivable_amount - COALESCE(written_off_amount, 0)) > 0")));
        Map<String, Object> unclaimedInvoice = first(invoiceReceivedMapper.selectMaps(
                new QueryWrapper<BizInvoiceReceived>()
                        .select("COUNT(*) AS cnt", "COALESCE(SUM(invoice_amount), 0) AS amt")
                        .isNull("contract_id")));

        Map<String, Object> payable = new LinkedHashMap<>();
        payable.put("pendingApprovalCount", count(pendingApproval));
        payable.put("pendingApprovalAmount", amount(pendingApproval));
        payable.put("approvedUnpaidCount", count(approvedUnpaid));
        payable.put("approvedUnpaidAmount", amount(approvedUnpaid));
        payable.put("overdueCount", count(overduePayment));
        payable.put("overdueAmount", amount(overduePayment));
        payable.put("unclaimedInvoiceCount", count(unclaimedInvoice));
        payable.put("unclaimedInvoiceAmount", amount(unclaimedInvoice));

        Map<String, Object> receivable = new LinkedHashMap<>();
        receivable.put("openCount", count(receivableOpen));
        receivable.put("openAmount", amount(receivableOpen));
        receivable.put("overdueCount", count(receivableOverdue));
        receivable.put("overdueAmount", amount(receivableOverdue));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("payable", payable);
        result.put("receivable", receivable);
        result.put("topOverduePayments", topOverduePayments(today));
        result.put("topOverdueReceivables", topOverdueReceivables(today));
        return result;
    }

    private List<Map<String, Object>> topOverduePayments(LocalDate today) {
        List<BizPaymentApply> rows = paymentApplyMapper.selectList(
                new QueryWrapper<BizPaymentApply>()
                        .eq("status", "APPROVED")
                        .and(w -> w.isNull("pay_status").or().ne("pay_status", "PAID"))
                        .lt("payment_date", today)
                        .orderByDesc("payment_amount")
                        .last("LIMIT 5"));
        Map<Long, BizProject> projects = loadProjects(rows.stream()
                .map(BizPaymentApply::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        // TOP5 金额同样按剩余未付额展示：申请额扣减银行已勾稽，避免与卡片口径打架
        Map<Long, BigDecimal> matchedByApply = bankFlowMapper.matchedAmountByPaymentApply();
        return rows.stream().map(row -> {
            BizProject project = projects.get(row.getProjectId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("projectName", project != null ? project.getProjectName() : row.getProjectName());
            item.put("supplierName", row.getSupplierName());
            item.put("amount", remainingAmount(row, matchedByApply));
            item.put("paymentDate", row.getPaymentDate() == null ? null : row.getPaymentDate().toString());
            item.put("overdueDays", overdueDays(row.getPaymentDate(), today));
            return item;
        }).toList();
    }

    private List<Map<String, Object>> topOverdueReceivables(LocalDate today) {
        List<BizReceivable> rows = receivableMapper.selectList(
                new QueryWrapper<BizReceivable>()
                        .eq("status", "OPEN")
                        .lt("due_date", today)
                        .apply("(receivable_amount - COALESCE(written_off_amount, 0)) > 0")
                        .last("ORDER BY (receivable_amount - COALESCE(written_off_amount, 0)) DESC LIMIT 5"));
        Map<Long, BizProject> projects = loadProjects(rows.stream()
                .map(BizReceivable::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return rows.stream().map(row -> {
            BizProject project = projects.get(row.getProjectId());
            BigDecimal openAmount = nvl(row.getReceivableAmount()).subtract(nvl(row.getWrittenOffAmount()));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("projectName", project != null ? project.getProjectName() : row.getProjectName());
            item.put("counterpartyName", project != null ? project.getOwnerCompanyName() : null);
            item.put("amount", openAmount);
            item.put("dueDate", row.getDueDate() == null ? null : row.getDueDate().toString());
            item.put("overdueDays", overdueDays(row.getDueDate(), today));
            return item;
        }).toList();
    }

    private Map<Long, BizProject> loadProjects(Set<Long> projectIds) {
        if (projectIds.isEmpty()) return Map.of();
        return projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(BizProject::getId, Function.identity()));
    }

    private static long overdueDays(LocalDate date, LocalDate today) {
        if (date == null) return 0L;
        return Math.max(0L, ChronoUnit.DAYS.between(date, today));
    }

    private static Map<String, Object> first(List<Map<String, Object>> rows) {
        return rows == null || rows.isEmpty() ? Map.of() : rows.get(0);
    }

    /** 单行聚合结果兜底：正常恒返回一行，空值时按无数据处理 */
    private static Map<String, Object> row(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    /** 剩余未付额 = 申请额 − 已勾稽额，负差归 0（与 sumApprovedUnpaidAgg 的 GREATEST 同口径） */
    private static BigDecimal remainingAmount(BizPaymentApply apply, Map<Long, BigDecimal> matchedByApply) {
        BigDecimal remaining = nvl(apply.getPaymentAmount())
                .subtract(matchedByApply.getOrDefault(apply.getId(), ZERO));
        return remaining.signum() < 0 ? ZERO : remaining;
    }

    private static long count(Map<String, Object> row) {
        Object value = row.get("cnt");
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static BigDecimal amount(Map<String, Object> row) {
        Object value = row.get("amt");
        return value == null ? ZERO : new BigDecimal(value.toString());
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
