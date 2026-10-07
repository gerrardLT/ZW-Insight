package com.zwinsight.purchase.listener;

import com.zwinsight.purchase.service.PurchaseContractService;
import com.zwinsight.purchase.service.PurchaseSettlementService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 采购模块审批回调监听器（P3-M5 A1 / PI-3）
 * <p>
 * 监听工作流审批完成与驳回事件，闭环驱动：
 * 1) PURCHASE_CONTRACT 采购合同审批通过/驳回
 * 2) PURCHASE_SETTLEMENT 采购结算审批通过/驳回
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseApprovalListener {

    private static final String TYPE_CONTRACT = "PURCHASE_CONTRACT";
    private static final String TYPE_SETTLEMENT = "PURCHASE_SETTLEMENT";

    private final PurchaseContractService contractService;
    private final PurchaseSettlementService settlementService;

    /**
     * 审批通过回调
     */
    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        String businessType = event.getBusinessType();
        Long businessId = event.getBusinessId();

        if (TYPE_CONTRACT.equals(businessType)) {
            log.info("收到采购合同审批通过事件: id={}, result={}", businessId, event.getResult());
            if ("APPROVED".equals(event.getResult())) {
                contractService.onApproved(businessId);
            }
        } else if (TYPE_SETTLEMENT.equals(businessType)) {
            log.info("收到采购结算审批通过事件: id={}, result={}", businessId, event.getResult());
            if ("APPROVED".equals(event.getResult())) {
                settlementService.onApproved(businessId);
            }
        }
    }

    /**
     * 审批驳回/撤回回调
     */
    @EventListener
    public void onApprovalReject(ApprovalRejectEvent event) {
        String bizType = event.getBizType();
        Long bizId = event.getBizId();

        if (TYPE_CONTRACT.equals(bizType)) {
            log.info("收到采购合同审批驳回/撤回事件: id={}, rejectType={}", bizId, event.getRejectType());
            contractService.onRejected(bizId);
        } else if (TYPE_SETTLEMENT.equals(bizType)) {
            log.info("收到采购结算审批驳回/撤回事件: id={}, rejectType={}", bizId, event.getRejectType());
            settlementService.onRejected(bizId);
        }
    }
}
