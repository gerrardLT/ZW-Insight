package com.zwinsight.message.config;

import com.zwinsight.message.websocket.MessageWebSocketHandler;
import com.zwinsight.message.websocket.WebSocketAuthInterceptor;
import com.zwinsight.security.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final MessageWebSocketHandler messageWebSocketHandler;
    private final JwtUtils jwtUtils;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // /ws/message 不在 AuthInterceptor 的 /api/** 保护范围内，故握手阶段用 JWT 鉴权
        // （客户端连接格式：ws://host/ws/message?token=<jwt>），防止任意 userId 越权订阅他人消息
        registry.addHandler(messageWebSocketHandler, "/ws/message")
                .addInterceptors(new WebSocketAuthInterceptor(jwtUtils))
                .setAllowedOrigins("*");
    }
}
