package com.zwinsight.message.listener;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.event.UrgeNotifyEvent;
import com.zwinsight.message.service.MessageService;
import com.zwinsight.message.service.WeChatWorkService;
import com.zwinsight.message.websocket.MessageWebSocketHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * UrgeNotifyEventListener 单元测试（2026-09-25 租户上下文修复配套）。
 * <p>核心断言：事件 tenantId 缺失时拒落（不写幽灵租户）；正常路径异步线程
 * 先设上下文再落库并 finally 清理（防线程池复用污染）；发送异常也必须清理。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UrgeNotifyEventListenerTest {

    @Mock private MessageService messageService;
    @Mock private MessageWebSocketHandler webSocketHandler;
    @Mock private WeChatWorkService weChatWorkService;

    @InjectMocks
    private UrgeNotifyEventListener listener;

    private UrgeNotifyEvent event(Long tenantId) {
        return new UrgeNotifyEvent(this, 777L, "【催办通知】财务复核", "请尽快处理",
                "pi-1", "task-1", tenantId);
    }

    @Test
    @DisplayName("tenantId 缺失：拒发站内消息（不落幽灵租户 0）")
    void nullTenant_skipsPersistence() {
        listener.onUrgeNotify(event(null));

        verify(messageService, never()).sendMessage(any(), anyString(), anyString(),
                anyString(), anyString(), isNull());
        verify(webSocketHandler, never()).sendToUser(anyString(), anyString());
    }

    @Test
    @DisplayName("正常路径：先恢复租户上下文再落库，WebSocket 推送送达，finally 清理上下文")
    void withTenant_setsContextSendsMessageAndClears() {
        try (MockedStatic<SecurityContextHolder> sc = mockStatic(SecurityContextHolder.class)) {
            listener.onUrgeNotify(event(5L));

            sc.verify(() -> SecurityContextHolder.setTenantId(5L));
            sc.verify(SecurityContextHolder::clear);
        }
        verify(messageService).sendMessage(eq(777L), contains("催办通知"), anyString(),
                eq("URGE"), eq("WORKFLOW"), isNull());
        verify(webSocketHandler).sendToUser(eq("777"), contains("task-1"));
    }

    @Test
    @DisplayName("落库抛异常：不影响 finally 清理上下文（防线程池复用污染）")
    void persistenceFailure_stillClearsContext() {
        doThrow(new RuntimeException("db down")).when(messageService)
                .sendMessage(any(), any(), any(), any(), any(), any());
        try (MockedStatic<SecurityContextHolder> sc = mockStatic(SecurityContextHolder.class)) {
            listener.onUrgeNotify(event(5L));

            // 异常被监听器 catch（有 error 日志），但上下文必须清理
            sc.verify(SecurityContextHolder::clear);
        }
        verify(webSocketHandler, never()).sendToUser(anyString(), anyString());
    }
}
