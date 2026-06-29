package com.zwinsight.workflow.listener;

import com.zwinsight.workflow.service.DelegateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.stereotype.Component;

/**
 * 全局任务分配监听器 - 自动委托转发
 * <p>
 * 监听 Flowable 任务创建事件（TASK_ASSIGNED），在任务分配时检查
 * 被分配人是否有生效中的委托配置。如果有，则自动将任务转给代理人。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelegateTaskAssignListener implements FlowableEventListener {

    private final DelegateService delegateService;
    @Lazy
    @Autowired
    private TaskService taskService;

    @Override
    public void onEvent(FlowableEvent event) {
        if (event.getType() != FlowableEngineEventType.TASK_ASSIGNED) {
            return;
        }

        if (!(event instanceof FlowableEntityEventImpl)) {
            return;
        }

        FlowableEntityEventImpl entityEvent = (FlowableEntityEventImpl) event;
        Object entity = entityEvent.getEntity();
        if (!(entity instanceof TaskEntity)) {
            return;
        }

        TaskEntity taskEntity = (TaskEntity) entity;
        String assignee = taskEntity.getAssignee();
        if (assignee == null || assignee.isEmpty()) {
            return;
        }

        try {
            Long assigneeId = Long.parseLong(assignee);
            Long delegateUserId = delegateService.findDelegateUser(assigneeId);

            if (delegateUserId != null) {
                String originalAssignee = assignee;
                // 修改任务的 assignee 为代理人
                taskEntity.setAssignee(String.valueOf(delegateUserId));

                // 添加委托转发备注
                taskService.addComment(taskEntity.getId(), taskEntity.getProcessInstanceId(),
                        "【自动委托】原处理人 " + originalAssignee + " 已委托给 " + delegateUserId);

                log.info("任务自动委托转发: taskId={}, 原处理人={}, 代理人={}",
                        taskEntity.getId(), originalAssignee, delegateUserId);
            }
        } catch (NumberFormatException e) {
            log.debug("无法解析 assignee 为用户ID: {}", assignee);
        } catch (Exception e) {
            log.warn("委托转发处理异常: taskId={}, assignee={}", taskEntity.getId(), assignee, e);
        }
    }

    @Override
    public boolean isFailOnException() {
        // 委托转发失败不应中断流程
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
