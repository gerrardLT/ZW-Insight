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

    public UrgeNotifyEvent(Object source, Long targetUserId, String title, String content,
                           String processInstanceId, String taskId) {
        super(source);
        this.targetUserId = targetUserId;
        this.title = title;
        this.content = content;
        this.processInstanceId = processInstanceId;
        this.taskId = taskId;
    }
}
