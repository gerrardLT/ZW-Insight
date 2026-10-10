package com.zwinsight.finance.listener;

import com.zwinsight.finance.service.FundTransferService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 资金调拨审批回调监听器
 * <p>
 * 监听审批事件，当 businessType == "FUND_TRANSFER" 时分发到 {@link FundTransferService}：
 * - 审批通过（ApprovalCompleteEvent，result=APPROVED）→ 状态置 APPROVED 并回写项目总收入/总支出
 * - 审批驳回/撤回（ApprovalRejectEvent）→ 状态置 REJECTED（项目资金未回写，无需回滚）
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FundTransferApprovalListener {

    private static final String BUSINESS_TYPE = "FUND_TRANSFER";

    private final FundTransferService fundTransferService;

    /**
     * 审批通过回调
     */
    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) {
            return;
        }
        Long transferId = event.getBusinessId();
        log.info("收到资金调拨审批回调, transferId={}, result={}", transferId, event.getResult());
        if ("APPROVED".equals(event.getResult())) {
            fundTransferService.onApproved(transferId);
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
        Long transferId = event.getBizId();
        log.info("收到资金调拨审批驳回回调, transferId={}, rejectType={}", transferId, event.getRejectType());
        fundTransferService.onRejected(transferId);
    }
}
