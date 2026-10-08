package com.zwinsight.message.listener;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.event.UrgeNotifyEvent;
import com.zwinsight.message.service.MessageService;
import com.zwinsight.message.service.WeChatWorkService;
import com.zwinsight.message.websocket.MessageWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 催办通知事件监听器 - 接收 workflow 模块发布的催办事件，发送站内消息 + WebSocket 推送
 * <p><b>租户上下文传递（2026-09-25 修复）</b>：本监听器为 {@code @Async}，
 * ThreadLocal 上下文不随异步线程继承；而 msg_message 插入含 tenantId 字段，
 * 无上下文时 INSERT 被写防护拒绝且异常被内部 catch 吞掉 —— 站内消息从未落库
 * （含手动催办）。现改为从事件取 tenantId 设置异步线程上下文，finally 清理
 * 防止线程池复用污染；tenantId 缺失时记 ERROR 不伪造归属。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UrgeNotifyEventListener {

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private final MessageService messageService;
    private final MessageWebSocketHandler webSocketHandler;
    private final WeChatWorkService weChatWorkService;

    @Async
    @EventListener
    public void onUrgeNotify(UrgeNotifyEvent event) {
        if (event.getTenantId() == null) {
            // 不伪造归属：无租户的事件直接拒绝处理（发布方缺陷，醒目报错定位）
            log.error("催办通知事件缺失 tenantId，无法落库站内消息, taskId={}, targetUserId={}",
                    event.getTaskId(), event.getTargetUserId());
            return;
        }
        SecurityContextHolder.setTenantId(event.getTenantId());
        SecurityContextHolder.markSystemTask();
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
            // 用 Jackson 序列化：标题/内容含引号、换行或反斜杠时，原字符串拼接会产生非法 JSON，前端静默丢弃推送
            java.util.Map<String, Object> push = new java.util.LinkedHashMap<>();
            push.put("type", "URGE");
            push.put("title", event.getTitle());
            push.put("content", event.getContent());
            push.put("taskId", event.getTaskId());
            push.put("processInstanceId", event.getProcessInstanceId());
            webSocketHandler.sendToUser(String.valueOf(event.getTargetUserId()), JSON.writeValueAsString(push));

            // 3. 企微群机器人推送（配置 wework.robot.enabled=true 时生效）。
            // 站内消息已落库即视为通知成功；企微是尽力渠道，未启用/失败只记录，不撤销站内消息
            boolean weworkSent = weChatWorkService.sendText("【催办提醒】" + event.getTitle() + "\n" + event.getContent());

            log.info("催办通知已推送, userId={}, taskId={}, weworkSent={}",
                    event.getTargetUserId(), event.getTaskId(), weworkSent);
        } catch (Exception e) {
            log.error("催办通知推送失败, userId={}, taskId={}", event.getTargetUserId(), event.getTaskId(), e);
        } finally {
            SecurityContextHolder.clear();
        }
    }
}
