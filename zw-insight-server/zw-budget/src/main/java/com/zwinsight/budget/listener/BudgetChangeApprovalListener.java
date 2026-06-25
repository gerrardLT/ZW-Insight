package com.zwinsight.budget.listener;

import com.zwinsight.budget.service.BudgetChangeService;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 预算变更审批回调监听器
 * <p>
 * 监听审批完成事件，根据业务类型分发到对应的回调方法。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetChangeApprovalListener {

    private static final String BUSINESS_TYPE = "BUDGET_CHANGE";

    private final BudgetChangeService budgetChangeService;

    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) {
            return;
        }

        Long changeId = event.getBusinessId();
        String result = event.getResult();

        log.info("收到预算变更审批回调, changeId={}, result={}", changeId, result);

        if ("APPROVED".equals(result)) {
            budgetChangeService.onApproved(changeId);
        } else if ("REJECTED".equals(result)) {
            budgetChangeService.onRejected(changeId);
        }
    }
}
