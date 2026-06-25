package com.zwinsight.workflow.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.delegate.event.impl.FlowableProcessTerminatedEventImpl;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 审批驳回/撤回监听器
 * <p>
 * 监听流程终止事件（PROCESS_CANCELLED），当流程被驳回或撤回终止时，
 * 发布 ApprovalRejectEvent，触发数据回滚。
 * </p>
 * <p>
 * 注意：此监听器与 ProcessCompleteListener 互补：
 * - ProcessCompleteListener：监听流程正常完成（通过）
 * - ApprovalRejectListener：监听流程被终止（驳回/撤回）
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalRejectListener implements FlowableEventListener {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void onEvent(FlowableEvent event) {
        if (event.getType() == FlowableEngineEventType.PROCESS_CANCELLED) {
            handleProcessCancelled(event);
        }
    }

    private void handleProcessCancelled(FlowableEvent event) {
        try {
            ExecutionEntity executionEntity = null;

            if (event instanceof FlowableProcessTerminatedEventImpl terminatedEvent) {
                executionEntity = (ExecutionEntity) terminatedEvent.getEntity();
            }

            if (executionEntity == null) {
                return;
            }

            Map<String, Object> variables = executionEntity.getVariables();
            String businessType = (String) variables.get("businessType");
            Object businessIdObj = variables.get("businessId");
            String processInstanceId = executionEntity.getProcessInstanceId();

            if (businessType == null || businessIdObj == null) {
                return;
            }

            Long businessId = businessIdObj instanceof Long
                    ? (Long) businessIdObj
                    : Long.valueOf(businessIdObj.toString());

            log.info("流程被终止（驳回/撤回）, processInstanceId={}, businessType={}, businessId={}",
                    processInstanceId, businessType, businessId);

            // 发布驳回事件，触发数据回滚
            eventPublisher.publishEvent(new ApprovalRejectEvent(
                    this, processInstanceId, businessType, businessId, "REJECT"));

        } catch (Exception e) {
            log.error("驳回监听器处理异常", e);
        }
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }
}
