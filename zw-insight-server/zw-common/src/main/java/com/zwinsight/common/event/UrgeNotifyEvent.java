package com.zwinsight.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 催办通知事件 - 由 workflow 模块发布，message 模块监听处理
 */
@Getter
public class UrgeNotifyEvent extends ApplicationEvent {

    /**
     * 被催办人用户ID
     */
    private final Long targetUserId;

    /**
     * 消息标题
     */
    private final String title;

    /**
     * 消息内容
     */
    private final String content;

    /**
     * 流程实例ID
     */
    private final String processInstanceId;

    /**
     * 任务ID
     */
    private final String taskId;

    /**
     * 租户ID（2026-09-25 新增）：监听器为 {@code @Async}，ThreadLocal 租户上下文
     * 不会传递到异步线程，而 msg_message 插入需租户填充；无此字段时
     * 手动/自动催办的站内消息 INSERT 均被写防护拒绝（异常被监听器 catch 吞掉，
     * 站内消息从未落库）。发布方必须显式传入，监听器据此设置异步线程上下文。
     */
    private final Long tenantId;

    public UrgeNotifyEvent(Object source, Long targetUserId, String title, String content,
                           String processInstanceId, String taskId, Long tenantId) {
        super(source);
        this.targetUserId = targetUserId;
        this.title = title;
        this.content = content;
        this.processInstanceId = processInstanceId;
        this.taskId = taskId;
        this.tenantId = tenantId;
    }
}
