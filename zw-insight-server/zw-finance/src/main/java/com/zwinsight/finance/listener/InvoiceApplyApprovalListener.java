package com.zwinsight.finance.listener;

import com.zwinsight.finance.service.InvoiceApplyService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 开票申请审批回调监听器
 * <p>
 * 监听审批事件，当 businessType == "INVOICE_APPLY" 时分发到 {@link InvoiceApplyService}：
 * - 审批通过（ApprovalCompleteEvent，result=APPROVED）→ 回写合同累计开票金额
 * - 审批驳回/撤回（ApprovalRejectEvent）→ 状态置 REJECTED（数据未生效，无需回滚）
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceApplyApprovalListener {

    private static final String BUSINESS_TYPE = "INVOICE_APPLY";

    private final InvoiceApplyService invoiceApplyService;

    /**
     * 审批通过回调
     */
    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) {
            return;
        }
        Long invoiceApplyId = event.getBusinessId();
        log.info("收到开票申请审批回调, invoiceApplyId={}, result={}", invoiceApplyId, event.getResult());
        if ("APPROVED".equals(event.getResult())) {
            invoiceApplyService.onApproved(invoiceApplyId);
        }
    }

    /**
     * 审批驳回/撤回回调
     */
    @EventListener
    public void onApprovalReject(ApprovalRejectEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBizType())) {
            return;
        }
        Long invoiceApplyId = event.getBizId();
        log.info("收到开票申请审批驳回回调, invoiceApplyId={}, rejectType={}", invoiceApplyId, event.getRejectType());
        invoiceApplyService.onRejected(invoiceApplyId);
    }
}
