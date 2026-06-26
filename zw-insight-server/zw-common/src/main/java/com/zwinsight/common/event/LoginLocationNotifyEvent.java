package com.zwinsight.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 异地登录通知事件 - 由 security 模块发布，message 模块监听处理。
 *
 * <p>当检测到用户本次登录归属地与最近一次登录归属地不一致时，
 * security 模块发布此事件，message 模块的监听器据此保存站内消息并通过
 * WebSocket 实时推送，避免 security 模块直接依赖 message 模块。</p>
 *
 * <p>消息内容应包含：登录时间、登录 IP、归属地、设备信息（需求 9.3）。</p>
 */
@Getter
public class LoginLocationNotifyEvent extends ApplicationEvent {

    /**
     * 接收通知的用户ID
     */
    private final Long targetUserId;

    /**
     * 消息标题
     */
    private final String title;

    /**
     * 消息内容（含登录时间、登录IP、归属地、设备信息）
     */
    private final String content;

    /**
     * 本次登录 IP
     */
    private final String ip;

    /**
     * 本次登录归属地（省份|城市）
     */
    private final String location;

    public LoginLocationNotifyEvent(Object source, Long targetUserId, String title, String content,
                                    String ip, String location) {
        super(source);
        this.targetUserId = targetUserId;
        this.title = title;
        this.content = content;
        this.ip = ip;
        this.location = location;
    }
}
