package com.zwinsight.workflow.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.zwinsight.common.event.SystemEventPublisher;
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
    private final com.zwinsight.workflow.notify.ApprovalNotifyPublisher approvalNotifyPublisher;
    @Lazy
    @Autowired
    private RuntimeService runtimeService;

    /** 租户ID字符串 → Long；缺失/非法返回 null（通知侧据此跳过，不伪造归属） */
    private static Long parseTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(tenantId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

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

                // 发布审批通过事件。审批回调是「系统操作」：各业务模块在回调里按 businessId（来自流程
                // 变量，受信）读单并回写，不应受「发起人/审批人的行级数据范围」约束——否则当审批人无
                // 该项目数据范围时，回调读单被 @DataPermission 谓词过滤为 null，业务侧多只 log.warn 后
                // return，单据永久卡 SUBMITTED（2026-10-12 实证：劳务产值单级 PM 审批）。
                // 由 SystemEventPublisher 临时标记系统任务并在发布后还原。
                SystemEventPublisher.publishSystemEvent(eventPublisher,
                        new ApprovalCompleteEvent(this, businessType, businessId, "APPROVED"));

                // 审批通过通知发起人（站内信）。通知失败不影响审批主流程：
                // notifyResult 内部已 try/catch，此处不改变原有的异常传播语义。
                approvalNotifyPublisher.notifyResult(businessType, businessId,
                        executionEntity.getProcessInstanceId(),
                        variables.get("initiator") == null ? null : String.valueOf(variables.get("initiator")),
                        "APPROVED", parseTenantId(executionEntity.getTenantId()));

            } catch (com.zwinsight.common.exception.BusinessException e) {
                // 业务侧可预期约束错误（如「退款失败：退款金额超过合同累计已付款」）原样上抛，
                // 由 GlobalExceptionHandler 透出**具体业务文案**；若被下面的包装成
                // 「审批业务生效失败」，用户只能看到通用「系统内部错误，请稍后重试」，无法定位
                // （2026-10-12 修复，实证见批次 6 报告 9K）。
                log.warn("流程完成回调业务失败（原样上抛以透出具体文案）, reason={}", e.getMessage());
                throw e;
            } catch (Exception e) {
                log.error("流程完成回调处理异常", e);
                throw new IllegalStateException("审批业务生效失败", e);
            }
        }
    }

    @Override
    public boolean isFailOnException() {
        return true;
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
