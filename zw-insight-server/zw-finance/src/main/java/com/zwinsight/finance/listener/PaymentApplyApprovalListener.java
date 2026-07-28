package com.zwinsight.finance.listener;

import com.zwinsight.finance.service.PaymentApplyService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 付款申请审批回调监听器
 * <p>
 * 监听审批事件，当 businessType == "PAYMENT_APPLY" 时分发到 {@link PaymentApplyService}：
 * - 审批通过（ApprovalCompleteEvent，result=APPROVED）→ 回写合同累计已付与项目总支出
 * - 审批驳回/撤回（ApprovalRejectEvent）→ 状态置 REJECTED（数据未生效，无需回滚）
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentApplyApprovalListener {

    private static final String BUSINESS_TYPE = "PAYMENT_APPLY";

    private final PaymentApplyService paymentApplyService;

    /**
     * 审批通过回调
     */
    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) {
            return;
        }
        Long paymentApplyId = event.getBusinessId();
        log.info("收到付款申请审批回调, paymentApplyId={}, result={}", paymentApplyId, event.getResult());
        if ("APPROVED".equals(event.getResult())) {
            paymentApplyService.onApproved(paymentApplyId);
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
        Long paymentApplyId = event.getBizId();
        log.info("收到付款申请审批驳回回调, paymentApplyId={}, rejectType={}", paymentApplyId, event.getRejectType());
        paymentApplyService.onRejected(paymentApplyId);
    }
}
