package com.zwinsight.workflow.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.RuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.flowable.engine.delegate.event.impl.FlowableProcessTerminatedEventImpl;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 流程结束全局监听器
 * <p>
 * 当流程实例完成（PROCESS_COMPLETED）时，根据流程变量中的 businessType 和 businessId
 * 发布 Spring 应用事件，由各业务模块监听处理审批通过回调。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessCompleteListener implements FlowableEventListener {

    private final ApplicationEventPublisher eventPublisher;
    @Lazy
    @Autowired
    private RuntimeService runtimeService;

    @Override
    public void onEvent(FlowableEvent event) {
        if (event.getType() == FlowableEngineEventType.PROCESS_COMPLETED) {
            try {
                ExecutionEntity executionEntity = null;
                if (event instanceof FlowableProcessTerminatedEventImpl terminatedEvent) {
                    executionEntity = (ExecutionEntity) terminatedEvent.getEntity();
                } else {
                    // 尝试从事件中获取执行实体
                    if (event instanceof org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl entityEvent) {
                        Object entity = entityEvent.getEntity();
                        if (entity instanceof ExecutionEntity exec) {
                            executionEntity = exec;
                        }
                    }
                }

                if (executionEntity == null) {
                    return;
                }

                Map<String, Object> variables = executionEntity.getVariables();
                String businessType = (String) variables.get("businessType");
                Object businessIdObj = variables.get("businessId");

                if (businessType == null || businessIdObj == null) {
                    return;
                }

                Long businessId = businessIdObj instanceof Long
                        ? (Long) businessIdObj
                        : Long.valueOf(businessIdObj.toString());

                log.info("流程完成回调, businessType={}, businessId={}", businessType, businessId);

                // 发布审批通过事件
                eventPublisher.publishEvent(new ApprovalCompleteEvent(this, businessType, businessId, "APPROVED"));

            } catch (Exception e) {
                log.error("流程完成回调处理异常", e);
            }
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

    /**
     * 审批完成事件
     */
    public static class ApprovalCompleteEvent extends org.springframework.context.ApplicationEvent {

        private final String businessType;
        private final Long businessId;
        private final String result;

        public ApprovalCompleteEvent(Object source, String businessType, Long businessId, String result) {
            super(source);
            this.businessType = businessType;
            this.businessId = businessId;
            this.result = result;
        }

        public String getBusinessType() {
            return businessType;
        }

        public Long getBusinessId() {
            return businessId;
        }

        public String getResult() {
            return result;
        }
    }
}
