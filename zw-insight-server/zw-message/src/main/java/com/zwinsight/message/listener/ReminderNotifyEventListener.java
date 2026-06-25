package com.zwinsight.message.listener;

import com.zwinsight.common.event.ReminderNotifyEvent;
import com.zwinsight.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 整改催办通知事件监听器 - 接收 site 模块发布的催办事件，发送站内消息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderNotifyEventListener {

    private final MessageService messageService;

    @Async
    @EventListener
    public void onReminderNotify(ReminderNotifyEvent event) {
        try {
            messageService.sendMessage(
                    event.getTargetUserId(),
                    event.getTitle(),
                    event.getContent(),
                    event.getMessageType(),
                    event.getBusinessType(),
                    event.getBusinessId()
            );
            log.info("整改催办通知已发送, userId={}, businessId={}, type={}",
                    event.getTargetUserId(), event.getBusinessId(), event.getMessageType());
        } catch (Exception e) {
            log.error("整改催办通知发送失败, userId={}, businessId={}",
                    event.getTargetUserId(), event.getBusinessId(), e);
        }
    }
}
