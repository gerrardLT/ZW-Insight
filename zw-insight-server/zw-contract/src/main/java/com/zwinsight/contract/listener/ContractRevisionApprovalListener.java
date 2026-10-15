package com.zwinsight.contract.listener;
import com.zwinsight.contract.service.ContractRevisionService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class ContractRevisionApprovalListener {
    private final ContractRevisionService service;
    @EventListener public void complete(ApprovalCompleteEvent e) {
        if ("CONTRACT_REVISION".equals(e.getBusinessType()) && "APPROVED".equals(e.getResult())) service.approve(e.getBusinessId());
    }
    @EventListener public void reject(ApprovalRejectEvent e) {
        if ("CONTRACT_REVISION".equals(e.getBizType())) service.reject(e.getBizId(), "WITHDRAW".equals(e.getRejectType()));
    }
}
