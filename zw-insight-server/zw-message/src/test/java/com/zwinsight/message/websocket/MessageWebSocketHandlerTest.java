package com.zwinsight.message.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MessageWebSocketHandler 单元测试：同一用户多端在线互不顶替、关闭只移除自身连接、
 * 身份只取自握手写入的会话属性。
 * <p>在线映射为静态共享，各用例使用互不相同的 userId，避免用例间串扰。</p>
 */
class MessageWebSocketHandlerTest {

    private final MessageWebSocketHandler handler = new MessageWebSocketHandler();

    private WebSocketSession session(String userId, boolean open) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(open);
        Map<String, Object> attributes = userId == null
                ? Map.of() : Map.of(WebSocketAuthInterceptor.ATTR_USER_ID, userId);
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }

    @Test
    @DisplayName("多端在线：同一用户两个连接都能收到推送（后登录不顶掉先登录）")
    void sameUserTwoSessions_bothReceive() throws IOException {
        WebSocketSession pc = session("ws-u1", true);
        WebSocketSession phone = session("ws-u1", true);
        handler.afterConnectionEstablished(pc);
        handler.afterConnectionEstablished(phone);

        handler.sendToUser("ws-u1", "hello");

        verify(pc).sendMessage(new TextMessage("hello"));
        verify(phone).sendMessage(new TextMessage("hello"));
    }

    @Test
    @DisplayName("关闭一个连接：只移除自身，另一端仍可收到推送")
    void closeOne_otherStillReceives() throws IOException {
        WebSocketSession pc = session("ws-u2", true);
        WebSocketSession phone = session("ws-u2", true);
        handler.afterConnectionEstablished(pc);
        handler.afterConnectionEstablished(phone);

        handler.afterConnectionClosed(pc, CloseStatus.NORMAL);
        handler.sendToUser("ws-u2", "after-close");

        verify(pc, never()).sendMessage(any());
        verify(phone).sendMessage(new TextMessage("after-close"));
    }

    @Test
    @DisplayName("全部连接关闭后推送为空操作，不抛异常")
    void allClosed_sendIsNoop() throws IOException {
        WebSocketSession pc = session("ws-u3", true);
        handler.afterConnectionEstablished(pc);
        handler.afterConnectionClosed(pc, CloseStatus.NORMAL);

        handler.sendToUser("ws-u3", "nobody");

        verify(pc, never()).sendMessage(any());
    }

    @Test
    @DisplayName("会话无已认证 userId（未经握手鉴权）：不登记为在线")
    void sessionWithoutAuthenticatedUser_notRegistered() throws IOException {
        WebSocketSession anonymous = session(null, true);
        handler.afterConnectionEstablished(anonymous);

        handler.sendToUser("null", "x");
        handler.broadcast("x");

        verify(anonymous, never()).sendMessage(any());
    }

    @Test
    @DisplayName("已关闭的连接被跳过；其他用户不受影响")
    void closedSessionSkipped_otherUsersIsolated() throws IOException {
        WebSocketSession dead = session("ws-u4", false);
        WebSocketSession other = session("ws-u5", true);
        handler.afterConnectionEstablished(dead);
        handler.afterConnectionEstablished(other);

        handler.sendToUser("ws-u4", "to-u4");

        verify(dead, never()).sendMessage(any());
        verify(other, never()).sendMessage(any());
    }

    @Test
    @DisplayName("单个连接发送失败不影响同一用户的其他连接")
    void sendFailureIsolatedPerSession() throws IOException {
        WebSocketSession broken = session("ws-u6", true);
        WebSocketSession healthy = session("ws-u6", true);
        doThrow(new IOException("broken pipe")).when(broken).sendMessage(any());
        handler.afterConnectionEstablished(broken);
        handler.afterConnectionEstablished(healthy);

        handler.sendToUser("ws-u6", "resilient");

        verify(healthy).sendMessage(new TextMessage("resilient"));
    }
}
