package com.zwinsight.message.listener;

import com.zwinsight.common.event.UrgeNotifyEvent;
import com.zwinsight.message.service.MessageService;
import com.zwinsight.message.websocket.MessageWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 催办通知事件监听器 - 接收 workflow 模块发布的催办事件，发送站内消息 + WebSocket 推送
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UrgeNotifyEventListener {

    private final MessageService messageService;
    private final MessageWebSocketHandler webSocketHandler;

    @Async
    @EventListener
    public void onUrgeNotify(UrgeNotifyEvent event) {
        try {
            // 1. 保存站内消息
            messageService.sendMessage(
                    event.getTargetUserId(),
                    event.getTitle(),
                    event.getContent(),
                    "URGE",
                    "WORKFLOW",
                    null
            );

            // 2. WebSocket 实时推送
            String pushMessage = "{\"type\":\"URGE\",\"title\":\"" + event.getTitle()
                    + "\",\"content\":\"" + event.getContent()
                    + "\",\"taskId\":\"" + event.getTaskId()
                    + "\",\"processInstanceId\":\"" + event.getProcessInstanceId() + "\"}";
            webSocketHandler.sendToUser(String.valueOf(event.getTargetUserId()), pushMessage);

            log.info("催办通知已推送, userId={}, taskId={}", event.getTargetUserId(), event.getTaskId());
        } catch (Exception e) {
            log.error("催办通知推送失败, userId={}, taskId={}", event.getTargetUserId(), event.getTaskId(), e);
        }
    }
}
