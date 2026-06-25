package com.zwinsight.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 整改催办通知事件 - 由 site 模块的定时任务发布，message 模块监听处理
 */
@Getter
public class ReminderNotifyEvent extends ApplicationEvent {

    /**
     * 接收人用户ID
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
     * 消息类型：REMINDER-普通催办 / ESCALATION-升级催办
     */
    private final String messageType;

    /**
     * 业务类型：RECTIFICATION
     */
    private final String businessType;

    /**
     * 关联业务ID（检查记录ID）
     */
    private final Long businessId;

    public ReminderNotifyEvent(Object source, Long targetUserId, String title, String content,
                               String messageType, String businessType, Long businessId) {
        super(source);
        this.targetUserId = targetUserId;
        this.title = title;
        this.content = content;
        this.messageType = messageType;
        this.businessType = businessType;
        this.businessId = businessId;
    }
}
