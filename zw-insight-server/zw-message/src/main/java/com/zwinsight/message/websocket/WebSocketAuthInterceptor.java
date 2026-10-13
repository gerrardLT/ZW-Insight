package com.zwinsight.message.websocket;

import com.zwinsight.security.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 握手鉴权拦截器。
 * <p><b>背景</b>：{@code WebSocketConfig} 注册的 {@code /ws/message} 不在 {@code AuthInterceptor}
 * 的 {@code /api/**} 保护范围内，原实现直接信任 URL 上的 {@code userId} 查询参数，
 * 任何人都能以任意 userId 建立连接并订阅他人消息（越权订阅）。现要求握手时携带
 * 有效 JWT（{@code ?token=xxx}），校验通过后把 userId 写入会话属性；
 * 处理器只从会话属性读取身份，不再信任查询参数。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    /** 会话属性键：已认证用户ID */
    public static final String ATTR_USER_ID = "userId";

    private final JwtUtils jwtUtils;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request.getURI());
        if (token == null) {
            log.warn("WebSocket 握手被拒绝：缺少 token, uri={}", request.getURI());
            return false;
        }
        try {
            Long userId = jwtUtils.getUserId(token);
            if (userId == null) {
                log.warn("WebSocket 握手被拒绝：token 缺少 userId");
                return false;
            }
            attributes.put(ATTR_USER_ID, String.valueOf(userId));
            return true;
        } catch (Exception e) {
            log.warn("WebSocket 握手被拒绝：token 无效或已过期, reason={}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }

    private String extractToken(URI uri) {
        String query = uri == null ? null : uri.getQuery();
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                String token = param.substring("token=".length());
                return token.isBlank() ? null : token;
            }
        }
        return null;
    }
}
