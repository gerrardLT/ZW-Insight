package com.zwinsight.message.websocket;

import com.zwinsight.security.service.AuthService;
import com.zwinsight.security.util.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebSocketAuthInterceptor 单元测试：握手必须带有效 token，身份只来自 token，
 * 不再信任 URL 上的 userId 参数（防越权订阅他人消息）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSocketAuthInterceptorTest {

    @Mock private AuthService authService;
    @Mock private JwtUtils jwtUtils;

    @InjectMocks
    private WebSocketAuthInterceptor interceptor;

    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final WebSocketHandler handler = mock(WebSocketHandler.class);

    private boolean handshake(String uri, Map<String, Object> attributes) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create(uri));
        return interceptor.beforeHandshake(request, response, handler, attributes);
    }

    @Test
    @DisplayName("有效 token：放行，userId 来自 token 而非 URL 参数")
    void validToken_allowed_userIdFromToken() {
        when(authService.validateToken("good")).thenReturn(true);
        when(jwtUtils.getUserId("good")).thenReturn(42L);
        Map<String, Object> attributes = new HashMap<>();

        // URL 上伪造 userId=1（管理员）必须被忽略
        boolean allowed = handshake("ws://h/ws/message?userId=1&token=good", attributes);

        assertThat(allowed).isTrue();
        assertThat(attributes.get(WebSocketAuthInterceptor.ATTR_USER_ID)).isEqualTo("42");
    }

    @Test
    @DisplayName("缺少 token：拒绝（即便带了 userId 参数）")
    void missingToken_rejected() {
        Map<String, Object> attributes = new HashMap<>();

        assertThat(handshake("ws://h/ws/message?userId=1", attributes)).isFalse();
        assertThat(handshake("ws://h/ws/message", attributes)).isFalse();
        assertThat(handshake("ws://h/ws/message?token=", attributes)).isFalse();

        assertThat(attributes).isEmpty();
        verify(authService, never()).validateToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("token 已登出或过期（Redis 无记录）：拒绝，不下发身份")
    void loggedOutToken_rejected() {
        when(authService.validateToken("stale")).thenReturn(false);
        Map<String, Object> attributes = new HashMap<>();

        assertThat(handshake("ws://h/ws/message?token=stale", attributes)).isFalse();

        assertThat(attributes).isEmpty();
        verify(jwtUtils, never()).getUserId(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("token 解析异常（伪造/篡改）：拒绝")
    void malformedToken_rejected() {
        when(authService.validateToken("forged")).thenThrow(new IllegalArgumentException("bad jwt"));
        Map<String, Object> attributes = new HashMap<>();

        assertThat(handshake("ws://h/ws/message?token=forged", attributes)).isFalse();
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("token 缺少 userId 声明：拒绝")
    void tokenWithoutUserId_rejected() {
        when(authService.validateToken("nouid")).thenReturn(true);
        when(jwtUtils.getUserId("nouid")).thenReturn(null);
        Map<String, Object> attributes = new HashMap<>();

        assertThat(handshake("ws://h/ws/message?token=nouid", attributes)).isFalse();
        assertThat(attributes).isEmpty();
    }
}
