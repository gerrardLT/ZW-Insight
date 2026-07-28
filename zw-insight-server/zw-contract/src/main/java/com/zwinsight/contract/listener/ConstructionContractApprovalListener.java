package com.zwinsight.contract.listener;

import com.zwinsight.contract.service.ConstructionContractService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 施工合同审批回调监听器
 * <p>
 * 监听审批事件，当 businessType == "CONSTRUCTION_CONTRACT" 时分发到 {@link ConstructionContractService}：
 * - 审批通过（ApprovalCompleteEvent，result=APPROVED）→ 合同置 EFFECTIVE + 回写项目合同金额 + 项目状态流转 CONSTRUCTION
 * - 审批驳回/撤回（ApprovalRejectEvent）→ 回退 DRAFT 可修改重提
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConstructionContractApprovalListener {

    private static final String BUSINESS_TYPE = "CONSTRUCTION_CONTRACT";

    private final ConstructionContractService contractService;

    /**
     * 审批通过回调
     */
    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) {
            return;
        }
        Long contractId = event.getBusinessId();
        log.info("收到施工合同审批回调, contractId={}, result={}", contractId, event.getResult());
        if ("APPROVED".equals(event.getResult())) {
            contractService.onApproved(contractId);
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
        Long contractId = event.getBizId();
        log.info("收到施工合同审批驳回回调, contractId={}, rejectType={}", contractId, event.getRejectType());
        contractService.onRejected(contractId);
    }
}
