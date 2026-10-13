package com.zwinsight.message.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 站内消息 WebSocket 处理器。
 * <p><b>身份来源</b>：userId 由 {@link WebSocketAuthInterceptor} 在握手阶段校验 JWT 后写入会话属性，
 * 本处理器只读会话属性，不再信任 URL 查询参数（防越权订阅）。</p>
 * <p><b>多端在线</b>：同一用户可同时有多条连接（多浏览器/多设备），会话按 userId 分组保存为集合，
 * 关闭时只移除自身连接，避免「后登录顶掉先登录」导致的消息丢失。</p>
 */
@Slf4j
@Component
public class MessageWebSocketHandler extends TextWebSocketHandler {

    /** 在线用户会话映射: userId -> 该用户的全部活动连接 */
    private static final Map<String, Set<WebSocketSession>> ONLINE_SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = extractUserId(session);
        if (userId == null) {
            return;
        }
        ONLINE_SESSIONS.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WebSocket 连接建立, userId={}, 当前连接数={}", userId, ONLINE_SESSIONS.get(userId).size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = extractUserId(session);
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> sessions = ONLINE_SESSIONS.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            // 该用户已无活动连接时清理空集合，防止映射无限增长
            if (sessions.isEmpty()) {
                ONLINE_SESSIONS.remove(userId, sessions);
            }
        }
        log.info("WebSocket 连接关闭, userId={}, status={}", userId, status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端发来的心跳等消息可在此处理
        log.debug("收到 WebSocket 消息: {}", message.getPayload());
    }

    /**
     * 推送消息给指定用户的全部在线连接
     */
    public void sendToUser(String userId, String message) {
        Set<WebSocketSession> sessions = ONLINE_SESSIONS.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : sessions) {
            sendQuietly(session, message, userId);
        }
    }

    /**
     * 广播消息给所有在线用户
     */
    public void broadcast(String message) {
        ONLINE_SESSIONS.forEach((userId, sessions) ->
                sessions.forEach(session -> sendQuietly(session, message, userId)));
    }

    private void sendQuietly(WebSocketSession session, String message, String userId) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            // WebSocketSession 非线程安全：同一连接可能被多个推送线程并发写入，需加锁串行化
            synchronized (session) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (IOException e) {
            log.error("WebSocket 发送消息失败, userId={}", userId, e);
        }
    }

    /**
     * 从会话属性读取已认证的 userId（握手阶段由 WebSocketAuthInterceptor 写入）
     */
    private String extractUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get(WebSocketAuthInterceptor.ATTR_USER_ID);
        return userId == null ? null : String.valueOf(userId);
    }
}
