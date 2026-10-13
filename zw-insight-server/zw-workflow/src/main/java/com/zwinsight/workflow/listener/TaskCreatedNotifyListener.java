package com.zwinsight.workflow.listener;

import com.zwinsight.workflow.notify.ApprovalNotifyPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.stereotype.Component;

/**
 * 待办任务创建监听器 - 新待办产生时通知处理人/候选人。
 * <p>
 * 监听 Flowable {@code TASK_CREATED}：流程推进到任一审批节点都会触发（含首节点、退回重走、
 * 分档追加审批），统一在此处发布审批通知事件，避免在每个业务 Service 里散落通知代码。
 * 收件人解析（含候选组角色成员）由 {@link ApprovalNotifyPublisher#notifyTodo} 在事务提交后完成。</p>
 * <p>通知失败不影响流程流转（{@code isFailOnException=false}）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCreatedNotifyListener implements FlowableEventListener {

    private final ApprovalNotifyPublisher approvalNotifyPublisher;

    @Override
    public void onEvent(FlowableEvent event) {
        if (event.getType() != FlowableEngineEventType.TASK_CREATED) {
            return;
        }
        if (!(event instanceof FlowableEntityEventImpl entityEvent)) {
            return;
        }
        Object entity = entityEvent.getEntity();
        if (!(entity instanceof TaskEntity taskEntity)) {
            return;
        }
        try {
            approvalNotifyPublisher.notifyTodo(taskEntity.getId());
        } catch (Exception e) {
            log.warn("待办通知发布异常（不影响流程）, taskId={}", taskEntity.getId(), e);
        }
    }

    @Override
    public boolean isFailOnException() {
        // 通知失败不应中断流程流转
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
