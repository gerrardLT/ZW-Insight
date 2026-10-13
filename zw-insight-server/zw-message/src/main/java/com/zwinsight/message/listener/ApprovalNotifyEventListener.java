package com.zwinsight.message.listener;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.event.ApprovalNotifyEvent;
import com.zwinsight.message.service.MessageService;
import com.zwinsight.message.websocket.MessageWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审批通知事件监听器 - 接收 workflow 模块发布的审批生命周期事件，
 * 为每个收件人落一条站内消息（messageType=APPROVAL）并做 WebSocket 实时推送。
 * <p><b>为何用普通 {@code @EventListener} 而非 {@code @TransactionalEventListener}</b>：
 * 发布方（{@code ApprovalNotifyPublisher}）已把所有事件统一延迟到主事务提交后发布，
 * 此处若再声明事务阶段，事件在「提交后」阶段无法再登记同步回调，会被静默丢弃。</p>
 * <p><b>租户上下文</b>：监听器为 {@code @Async}，ThreadLocal 不随异步线程继承，而
 * {@code msg_message} 插入需租户填充；此处从事件取 {@code tenantId} 设置异步线程上下文，
 * {@code finally} 清理防线程池复用污染（与 {@link UrgeNotifyEventListener} 同款约束）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalNotifyEventListener {

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final MessageService messageService;
    private final MessageWebSocketHandler webSocketHandler;

    @Async
    @EventListener
    public void onApprovalNotify(ApprovalNotifyEvent event) {
        if (event.getTenantId() == null) {
            // 不伪造归属：无租户的事件直接拒绝处理（发布方缺陷，醒目报错定位）
            log.error("审批通知事件缺失 tenantId，无法落库站内消息, notifyType={}, businessType={}, businessId={}",
                    event.getNotifyType(), event.getBusinessType(), event.getBusinessId());
            return;
        }
        if (event.getTargetUserIds() == null || event.getTargetUserIds().isEmpty()) {
            return;
        }
        SecurityContextHolder.setTenantId(event.getTenantId());
        SecurityContextHolder.markSystemTask();
        try {
            String pushJson = buildPushJson(event);
            for (Long userId : event.getTargetUserIds()) {
                if (userId == null) {
                    continue;
                }
                messageService.sendMessage(userId, event.getTitle(), event.getContent(),
                        "APPROVAL", event.getBusinessType(), event.getBusinessId());
                webSocketHandler.sendToUser(String.valueOf(userId), pushJson);
            }
            log.info("审批通知已推送, notifyType={}, businessType={}, businessId={}, 收件人数={}",
                    event.getNotifyType(), event.getBusinessType(), event.getBusinessId(),
                    event.getTargetUserIds().size());
        } catch (Exception e) {
            log.error("审批通知推送失败, notifyType={}, businessType={}, businessId={}",
                    event.getNotifyType(), event.getBusinessType(), event.getBusinessId(), e);
        } finally {
            SecurityContextHolder.clear();
        }
    }

    /** 用 Jackson 序列化，避免标题/内容含引号或换行时拼出非法 JSON 被前端静默丢弃 */
    private String buildPushJson(ApprovalNotifyEvent event) throws Exception {
        Map<String, Object> push = new LinkedHashMap<>();
        push.put("type", "APPROVAL");
        push.put("notifyType", event.getNotifyType());
        push.put("title", event.getTitle());
        push.put("content", event.getContent());
        push.put("businessType", event.getBusinessType());
        push.put("businessId", event.getBusinessId());
        push.put("processInstanceId", event.getProcessInstanceId());
        push.put("taskId", event.getTaskId());
        return JSON.writeValueAsString(push);
    }
}
