package com.zwinsight.contract.listener;

import com.zwinsight.contract.service.ChangeVisaService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 变更签证审批回调监听器
 * <p>
 * 监听审批事件，当 businessType == "CHANGE_VISA" 时分发到 {@link ChangeVisaService}：
 * - 审批通过（ApprovalCompleteEvent，result=APPROVED）→ 状态置 APPROVED 并回写合同累计变更金额
 * - 审批驳回/撤回（ApprovalRejectEvent）→ 状态置 REJECTED（合同累计金额未回写，无需回滚）
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeVisaApprovalListener {

    private static final String BUSINESS_TYPE = "CHANGE_VISA";

    private final ChangeVisaService changeVisaService;

    /**
     * 审批通过回调
     */
    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) {
            return;
        }
        Long changeVisaId = event.getBusinessId();
        log.info("收到变更签证审批回调, changeVisaId={}, result={}", changeVisaId, event.getResult());
        if ("APPROVED".equals(event.getResult())) {
            changeVisaService.onApproved(changeVisaId);
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
        Long changeVisaId = event.getBizId();
        log.info("收到变更签证审批驳回回调, changeVisaId={}, rejectType={}", changeVisaId, event.getRejectType());
        changeVisaService.onRejected(changeVisaId);
    }
}
