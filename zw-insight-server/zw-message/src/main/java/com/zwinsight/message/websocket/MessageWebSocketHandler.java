package com.zwinsight.message.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MessageWebSocketHandler extends TextWebSocketHandler {

    /** 在线用户会话映射: userId -> WebSocketSession */
    private static final Map<String, WebSocketSession> ONLINE_SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = extractUserId(session);
        if (userId != null) {
            ONLINE_SESSIONS.put(userId, session);
            log.info("WebSocket 连接建立, userId={}", userId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = extractUserId(session);
        if (userId != null) {
            ONLINE_SESSIONS.remove(userId);
            log.info("WebSocket 连接关闭, userId={}, status={}", userId, status);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端发来的心跳等消息可在此处理
        log.debug("收到 WebSocket 消息: {}", message.getPayload());
    }

    /**
     * 推送消息给指定用户
     */
    public void sendToUser(String userId, String message) {
        WebSocketSession session = ONLINE_SESSIONS.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.error("WebSocket 发送消息失败, userId={}", userId, e);
            }
        }
    }

    /**
     * 广播消息给所有在线用户
     */
    public void broadcast(String message) {
        ONLINE_SESSIONS.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    log.error("WebSocket 广播失败", e);
                }
            }
        });
    }

    /**
     * 从 WebSocket 连接 URL 参数中提取 userId
     * 连接 URL 格式: ws://host/ws/message?userId=xxx
     */
    private String extractUserId(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query != null && query.contains("userId=")) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("userId=")) {
                    return param.substring(7);
                }
            }
        }
        return null;
    }
}
