package com.zwinsight.finance.listener;

import com.zwinsight.finance.service.ReserveFundApplyService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 备用金申请审批回调监听器
 * <p>
 * 监听审批事件，当 businessType == "RESERVE_FUND_APPLY" 时分发到 {@link ReserveFundApplyService}：
 * - 审批通过（ApprovalCompleteEvent，result=APPROVED）→ 状态置 APPROVED（此后才可归还/被冲抵）
 * - 审批驳回/撤回（ApprovalRejectEvent）→ 状态置 REJECTED（数据未生效，无需回滚）
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReserveFundApplyApprovalListener {

    private static final String BUSINESS_TYPE = "RESERVE_FUND_APPLY";

    private final ReserveFundApplyService reserveFundApplyService;

    /**
     * 审批通过回调
     */
    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) {
            return;
        }
        Long applyId = event.getBusinessId();
        log.info("收到备用金申请审批回调, applyId={}, result={}", applyId, event.getResult());
        if ("APPROVED".equals(event.getResult())) {
            reserveFundApplyService.onApproved(applyId);
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
        Long applyId = event.getBizId();
        log.info("收到备用金申请审批驳回回调, applyId={}, rejectType={}", applyId, event.getRejectType());
        reserveFundApplyService.onRejected(applyId);
    }
}
