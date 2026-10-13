package com.zwinsight.message.listener;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.event.ApprovalNotifyEvent;
import com.zwinsight.message.service.MessageService;
import com.zwinsight.message.websocket.MessageWebSocketHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * ApprovalNotifyEventListener 单元测试。
 * <p>核心断言：tenantId 缺失时拒落（不写幽灵租户）；正常路径为每个收件人落一条 APPROVAL
 * 站内消息并做 WebSocket 推送；推送体为合法 JSON；异常也必须清理租户上下文。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalNotifyEventListenerTest {

    @Mock private MessageService messageService;
    @Mock private MessageWebSocketHandler webSocketHandler;

    @InjectMocks
    private ApprovalNotifyEventListener listener;

    private ApprovalNotifyEvent event(List<Long> targets, Long tenantId) {
        return new ApprovalNotifyEvent(this, targets, ApprovalNotifyEvent.TYPE_TODO,
                "【待办提醒】付款申请", "您有一条【付款申请】待办任务（项目负责人审批），请及时办理。",
                "PAYMENT_APPLY", 55L, "pi-1", "task-1", tenantId);
    }

    @Test
    @DisplayName("tenantId 缺失：拒发站内消息（不落幽灵租户 0）")
    void nullTenant_skipsPersistence() {
        listener.onApprovalNotify(event(List.of(777L), null));

        verify(messageService, never()).sendMessage(any(), anyString(), anyString(),
                anyString(), anyString(), any());
        verify(webSocketHandler, never()).sendToUser(anyString(), anyString());
    }

    @Test
    @DisplayName("收件人为空：不发消息")
    void emptyTargets_skips() {
        listener.onApprovalNotify(event(List.of(), 5L));

        verify(messageService, never()).sendMessage(any(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("正常路径：每个收件人各落一条 APPROVAL 站内消息并 WebSocket 推送，finally 清理上下文")
    void withTenant_sendsForEachRecipientAndClears() {
        try (MockedStatic<SecurityContextHolder> sc = mockStatic(SecurityContextHolder.class)) {
            listener.onApprovalNotify(event(List.of(777L, 888L), 5L));

            sc.verify(() -> SecurityContextHolder.setTenantId(5L));
            sc.verify(SecurityContextHolder::clear);
        }
        verify(messageService, times(2)).sendMessage(any(), contains("待办提醒"), anyString(),
                eq("APPROVAL"), eq("PAYMENT_APPLY"), eq(55L));
        verify(messageService).sendMessage(eq(777L), anyString(), anyString(),
                eq("APPROVAL"), eq("PAYMENT_APPLY"), eq(55L));
        verify(messageService).sendMessage(eq(888L), anyString(), anyString(),
                eq("APPROVAL"), eq("PAYMENT_APPLY"), eq(55L));
        verify(webSocketHandler, times(2)).sendToUser(anyString(), anyString());
    }

    @Test
    @DisplayName("WebSocket 推送体是合法 JSON：标题/内容含引号换行时被正确转义")
    void pushPayloadIsValidJsonWhenTextHasSpecialChars() throws Exception {
        ApprovalNotifyEvent tricky = new ApprovalNotifyEvent(this, List.of(777L),
                ApprovalNotifyEvent.TYPE_REJECTED, "【审批驳回】\"付款申请\"", "第一行\n第二行\\路径",
                "PAYMENT_APPLY", 55L, "pi-1", null, 5L);
        try (MockedStatic<SecurityContextHolder> sc = mockStatic(SecurityContextHolder.class)) {
            listener.onApprovalNotify(tricky);
        }
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(webSocketHandler).sendToUser(eq("777"), captor.capture());
        com.fasterxml.jackson.databind.JsonNode node =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(captor.getValue());
        assertThat(node.get("title").asText()).isEqualTo("【审批驳回】\"付款申请\"");
        assertThat(node.get("content").asText()).isEqualTo("第一行\n第二行\\路径");
        assertThat(node.get("notifyType").asText()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("落库抛异常：不影响 finally 清理上下文（防线程池复用污染）")
    void persistenceFailure_stillClearsContext() {
        doThrow(new RuntimeException("db down")).when(messageService)
                .sendMessage(any(), any(), any(), any(), any(), any());
        try (MockedStatic<SecurityContextHolder> sc = mockStatic(SecurityContextHolder.class)) {
            listener.onApprovalNotify(event(List.of(777L), 5L));

            sc.verify(SecurityContextHolder::clear);
        }
        verify(webSocketHandler, never()).sendToUser(anyString(), anyString());
    }
}
