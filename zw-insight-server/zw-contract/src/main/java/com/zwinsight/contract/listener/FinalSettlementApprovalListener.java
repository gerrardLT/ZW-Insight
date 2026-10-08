package com.zwinsight.contract.listener;

import com.zwinsight.contract.service.FinalSettlementService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 合同竣工结算审批回调监听器（businessType=FINAL_SETTLEMENT）。
 * <p>通过 → {@link FinalSettlementService#onApproved}（合同置 SETTLED + 项目结算额累加）；
 * 驳回/撤回 → {@link FinalSettlementService#onRejected}（回退草稿）。
 * 通过回调的异常不吞，使审批引擎事务整体回滚。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinalSettlementApprovalListener {

    private static final String BUSINESS_TYPE = "FINAL_SETTLEMENT";

    private final FinalSettlementService finalSettlementService;

    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType()) || !"APPROVED".equals(event.getResult())) {
            return;
        }
        log.info("收到竣工结算审批回调, settlementId={}", event.getBusinessId());
        finalSettlementService.onApproved(event.getBusinessId());
    }

    @EventListener
    public void onApprovalReject(ApprovalRejectEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBizType())) {
            return;
        }
        log.info("收到竣工结算审批驳回回调, settlementId={}, rejectType={}", event.getBizId(), event.getRejectType());
        finalSettlementService.onRejected(event.getBizId());
    }
}
