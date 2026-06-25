package com.zwinsight.finance.listener;

import com.zwinsight.finance.service.ProjectSettlementService;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 项目结算审批回调监听器
 * <p>
 * 监听审批完成事件，当 businessType == "PROJECT_SETTLEMENT" 时
 * 分发到 ProjectSettlementService 对应的回调方法。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementApprovalListener {

    private static final String BUSINESS_TYPE = "PROJECT_SETTLEMENT";

    private final ProjectSettlementService projectSettlementService;

    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) {
            return;
        }

        Long settlementId = event.getBusinessId();
        String result = event.getResult();

        log.info("收到项目结算审批回调, settlementId={}, result={}", settlementId, result);

        if ("APPROVED".equals(result)) {
            projectSettlementService.onApproved(settlementId);
        } else if ("REJECTED".equals(result)) {
            projectSettlementService.onRejected(settlementId);
        }
    }
}
