package com.zwinsight.finance.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zwinsight.common.event.project.ProjectDeletedEvent;
import com.zwinsight.finance.domain.BizBankAccount;
import com.zwinsight.finance.domain.BizFinanceLock;
import com.zwinsight.finance.domain.BizInvoiceApply;
import com.zwinsight.finance.domain.BizInvoiceReceived;
import com.zwinsight.finance.domain.BizOtherPayment;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.domain.BizPaymentReceived;
import com.zwinsight.finance.domain.BizProjectReimbursement;
import com.zwinsight.finance.domain.BizProjectSettlement;
import com.zwinsight.finance.domain.BizReserveFundApply;
import com.zwinsight.finance.domain.BizRetentionMoney;
import com.zwinsight.finance.mapper.BizBankAccountMapper;
import com.zwinsight.finance.mapper.BizFinanceLockMapper;
import com.zwinsight.finance.mapper.BizInvoiceApplyMapper;
import com.zwinsight.finance.mapper.BizInvoiceReceivedMapper;
import com.zwinsight.finance.mapper.BizOtherPaymentMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.BizPaymentReceivedMapper;
import com.zwinsight.finance.mapper.BizProjectReimbursementMapper;
import com.zwinsight.finance.mapper.BizProjectSettlementMapper;
import com.zwinsight.finance.mapper.BizReserveFundApplyMapper;
import com.zwinsight.finance.mapper.BizRetentionMoneyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 项目删除级联清理监听器（zw-finance 模块，R7-02）。
 * <p>监听 {@link ProjectDeletedEvent}，清理本模块含 {@code project_id} 列的 11 张表。
 * 实现约定与异常传播策略详见
 * {@code com.zwinsight.contract.listener.ContractProjectCascadeCleanupListener} 的类注释。</p>
 *
 * <p>线上取证（2026-09-18 Round 7）：本模块遗留孤儿——收款登记 4+3 条、开票申请 4+3 条、
 * 质保金 3 条、付款申请 3 条、项目最终结算 4 条、银行账户 12 条。</p>
 *
 * <p><b>注意</b>：付款申请/收款登记审批通过时会回写合同 {@code cumulative_paid} 与
 * 项目 {@code total_expense}。本监听器只做「项目已删除后的孤儿清理」，此时合同与项目
 * 本身也已作废，无需反向冲销累计值；正常业务撤回单据的回滚由各单据自己的
 * delete 路径负责（如 {@code PaymentApplyService.delete}），职责不重叠。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinanceProjectCascadeCleanupListener {

    private final BizPaymentApplyMapper paymentApplyMapper;
    private final BizPaymentReceivedMapper paymentReceivedMapper;
    private final BizInvoiceApplyMapper invoiceApplyMapper;
    private final BizInvoiceReceivedMapper invoiceReceivedMapper;
    private final BizProjectSettlementMapper projectSettlementMapper;
    private final BizRetentionMoneyMapper retentionMoneyMapper;
    private final BizBankAccountMapper bankAccountMapper;
    private final BizOtherPaymentMapper otherPaymentMapper;
    private final BizProjectReimbursementMapper projectReimbursementMapper;
    private final BizReserveFundApplyMapper reserveFundApplyMapper;
    private final BizFinanceLockMapper financeLockMapper;

    @EventListener
    public void onProjectDeleted(ProjectDeletedEvent event) {
        Long projectId = event.getProjectId();
        int paymentApplies = paymentApplyMapper.delete(
                new QueryWrapper<BizPaymentApply>().eq("project_id", projectId));
        int paymentReceived = paymentReceivedMapper.delete(
                new QueryWrapper<BizPaymentReceived>().eq("project_id", projectId));
        int invoiceApplies = invoiceApplyMapper.delete(
                new QueryWrapper<BizInvoiceApply>().eq("project_id", projectId));
        int invoiceReceived = invoiceReceivedMapper.delete(
                new QueryWrapper<BizInvoiceReceived>().eq("project_id", projectId));
        int settlements = projectSettlementMapper.delete(
                new QueryWrapper<BizProjectSettlement>().eq("project_id", projectId));
        int retentions = retentionMoneyMapper.delete(
                new QueryWrapper<BizRetentionMoney>().eq("project_id", projectId));
        int bankAccounts = bankAccountMapper.delete(
                new QueryWrapper<BizBankAccount>().eq("project_id", projectId));
        int otherPayments = otherPaymentMapper.delete(
                new QueryWrapper<BizOtherPayment>().eq("project_id", projectId));
        int reimbursements = projectReimbursementMapper.delete(
                new QueryWrapper<BizProjectReimbursement>().eq("project_id", projectId));
        int reserveFunds = reserveFundApplyMapper.delete(
                new QueryWrapper<BizReserveFundApply>().eq("project_id", projectId));
        int financeLocks = financeLockMapper.delete(
                new QueryWrapper<BizFinanceLock>().eq("project_id", projectId));

        log.info("项目删除级联清理[finance]完成, projectId={}, 付款申请={} 收款={} 开票={} "
                        + "收票={} 最终结算={} 质保金={} 银行账户={} 其他支付={} 报销={} 备用金={} 封账={}",
                projectId, paymentApplies, paymentReceived, invoiceApplies, invoiceReceived,
                settlements, retentions, bankAccounts, otherPayments, reimbursements,
                reserveFunds, financeLocks);
    }
}
