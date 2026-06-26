package com.zwinsight.message.listener;

import com.zwinsight.common.event.LoginLocationNotifyEvent;
import com.zwinsight.message.service.MessageService;
import com.zwinsight.message.websocket.MessageWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异地登录通知事件监听器 - 接收 security 模块发布的异地登录事件，
 * 发送站内消息 + WebSocket 实时推送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginLocationNotifyEventListener {

    private final MessageService messageService;
    private final MessageWebSocketHandler webSocketHandler;

    @Async
    @EventListener
    public void onLoginLocationNotify(LoginLocationNotifyEvent event) {
        try {
            // 1. 保存站内消息
            messageService.sendMessage(
                    event.getTargetUserId(),
                    event.getTitle(),
                    event.getContent(),
                    "SECURITY",
                    "LOGIN_LOCATION",
                    null
            );

            // 2. WebSocket 实时推送
            String pushMessage = "{\"type\":\"SECURITY\",\"title\":\"" + event.getTitle()
                    + "\",\"content\":\"" + event.getContent()
                    + "\",\"ip\":\"" + event.getIp()
                    + "\",\"location\":\"" + event.getLocation() + "\"}";
            webSocketHandler.sendToUser(String.valueOf(event.getTargetUserId()), pushMessage);

            log.info("异地登录通知已推送, userId={}, location={}", event.getTargetUserId(), event.getLocation());
        } catch (Exception e) {
            log.error("异地登录通知推送失败, userId={}, location={}",
                    event.getTargetUserId(), event.getLocation(), e);
        }
    }
}
