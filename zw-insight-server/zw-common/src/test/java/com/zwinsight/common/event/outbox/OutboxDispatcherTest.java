package com.zwinsight.common.event.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发件箱投递调度器单元测试。
 *
 * <p>{@code deliver} / {@code handleFailure} / {@code invokeHandler} 均为私有方法，
 * 只能通过公开入口 {@link OutboxDispatcher#poll()} 与 {@link OutboxDispatcher#purge()}
 * 驱动，故本测试以「给定一批到期事件 → 断言 Mapper 交互与处理器调用」为基本形态。</p>
 *
 * <p>钉住的核心契约：</p>
 * <ol>
 *   <li><b>集群安全</b>：claim 抢不到（返回≠1）即 SKIPPED，绝不重复投递；</li>
 *   <li><b>无消费者不算成功</b>：未注册处理器记为失败并重试，不静默丢弃；</li>
 *   <li><b>指数退避</b>：60s × 2^(attempts-1)，未达 maxAttempts 回落 PENDING；</li>
 *   <li><b>死信阈值</b>：attempts+1 ≥ maxAttempts 转 DEAD（而非用 OutboxMessage.isLastAttempt）；</li>
 *   <li><b>租户上下文</b>：投递前回填 event.tenantId，finally 恢复原值或清理；</li>
 *   <li><b>调度线程不死</b>：查询/清理异常被吞并记录，等下一轮。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxDispatcherTest {

    private static final String EVENT_TYPE = "CHANGE_EVENT_APPROVED";

    @Mock
    private OutboxEventMapper mapper;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    // ==================== 轮询健壮性 ====================

    @Nested
    @DisplayName("poll — 轮询健壮性")
    class PollRobustnessTests {

        @Test
        @DisplayName("异常：查询待投递事件失败时吞掉异常，不让调度线程死掉")
        void queryFailure_isSwallowed() {
            OutboxDispatcher dispatcher = newDispatcher();
            when(mapper.selectDueEvents(any(), anyInt()))
                    .thenThrow(new RuntimeException("表未建"));

            // 不抛异常即为通过：调度线程存活，等下一轮
            dispatcher.poll();

            verify(mapper, never()).claimForDelivery(anyLong());
        }

        @Test
        @DisplayName("边界：无到期事件时直接返回，不触发 claim")
        void emptyDueList_shortCircuits() {
            OutboxDispatcher dispatcher = newDispatcher();
            when(mapper.selectDueEvents(any(), anyInt())).thenReturn(List.of());

            dispatcher.poll();

            verify(mapper, never()).claimForDelivery(anyLong());
            verify(mapper, never()).markDelivered(anyLong(), any());
        }

        @Test
        @DisplayName("契约：单批上限为 50 条（防止一次拉太多占满连接/内存）")
        void batchSize_isCappedAt50() {
            OutboxDispatcher dispatcher = newDispatcher();
            when(mapper.selectDueEvents(any(), anyInt())).thenReturn(List.of());

            dispatcher.poll();

            verify(mapper).selectDueEvents(any(LocalDateTime.class), eq(50));
        }
    }

    // ==================== 集群安全 ====================

    @Nested
    @DisplayName("deliver — 集群安全与路由")
    class ClusterSafetyTests {

        @Test
        @DisplayName("正常：claim 成功（返回 1）后调用处理器并标记已投递")
        void claimSucceeded_deliversAndMarksDelivered() throws Exception {
            RecordingHandler handler = new RecordingHandler(EVENT_TYPE);
            OutboxDispatcher dispatcher = newDispatcher(handler);
            OutboxEvent event = pendingEvent(1L, 0, 5);
            givenDue(event);
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            dispatcher.poll();

            assertThat(handler.invocations.get()).isEqualTo(1);
            verify(mapper).markDelivered(eq(1L), any(LocalDateTime.class));
            verify(mapper, never()).markDead(anyLong(), anyString());
        }

        @Test
        @DisplayName("契约：claim 抢不到（返回 0）时跳过，不调处理器也不改状态")
        void claimLost_skipsEntirely() {
            RecordingHandler handler = new RecordingHandler(EVENT_TYPE);
            OutboxDispatcher dispatcher = newDispatcher(handler);
            givenDue(pendingEvent(1L, 0, 5));
            when(mapper.claimForDelivery(1L)).thenReturn(0);

            dispatcher.poll();

            assertThat(handler.invocations.get()).isZero();
            verify(mapper, never()).markDelivered(anyLong(), any());
            verify(mapper, never()).markFailedWithRetry(anyLong(), anyString(), any());
            verify(mapper, never()).markDead(anyLong(), anyString());
        }

        @Test
        @DisplayName("契约：未注册该事件类型的处理器时记为失败并重试，不静默丢弃")
        void noHandlerRegistered_countsAsFailure_notSilentlyDropped() {
            OutboxDispatcher dispatcher = newDispatcher();  // 不注册任何处理器
            OutboxEvent event = pendingEvent(1L, 0, 5);
            givenDue(event);
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            dispatcher.poll();

            // 无消费者不是「成功」：必须留下失败痕迹供人工察觉
            verify(mapper, never()).markDelivered(anyLong(), any());
            verify(mapper).markFailedWithRetry(eq(1L), anyString(), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("正常：同一事件类型注册多个处理器时扇出，全部被调用")
        void multipleHandlers_fanOut() {
            RecordingHandler first = new RecordingHandler(EVENT_TYPE);
            RecordingHandler second = new RecordingHandler(EVENT_TYPE);
            OutboxDispatcher dispatcher = newDispatcher(first, second);
            givenDue(pendingEvent(1L, 0, 5));
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            dispatcher.poll();

            assertThat(first.invocations.get()).isEqualTo(1);
            assertThat(second.invocations.get()).isEqualTo(1);
            verify(mapper).markDelivered(eq(1L), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("契约：支持多个事件类型并存，各自路由到对应处理器")
        void differentEventTypes_routeSeparately() {
            RecordingHandler changeHandler = new RecordingHandler(EVENT_TYPE);
            RecordingHandler otherHandler = new RecordingHandler("OTHER_EVENT");
            OutboxDispatcher dispatcher = newDispatcher(changeHandler, otherHandler);

            OutboxEvent changeEvent = pendingEvent(1L, 0, 5);
            OutboxEvent otherEvent = pendingEvent(2L, 0, 5);
            otherEvent.setEventType("OTHER_EVENT");
            givenDue(changeEvent, otherEvent);
            when(mapper.claimForDelivery(anyLong())).thenReturn(1);

            dispatcher.poll();

            assertThat(changeHandler.invocations.get()).isEqualTo(1);
            assertThat(otherHandler.invocations.get()).isEqualTo(1);
            assertThat(changeHandler.lastEventType).isEqualTo(EVENT_TYPE);
            assertThat(otherHandler.lastEventType).isEqualTo("OTHER_EVENT");
        }

        @Test
        @DisplayName("异常：处理器 supports() 返回空白时跳过注册，不影响其他处理器")
        void blankSupports_isSkippedAtRegistration() {
            RecordingHandler blank = new RecordingHandler("   ");
            RecordingHandler valid = new RecordingHandler(EVENT_TYPE);
            OutboxDispatcher dispatcher = newDispatcher(blank, valid);

            givenDue(pendingEvent(1L, 0, 5));
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            dispatcher.poll();

            assertThat(valid.invocations.get()).isEqualTo(1);
            assertThat(blank.invocations.get()).isZero();
        }
    }

    // ==================== 失败处理：退避与死信 ====================

    @Nested
    @DisplayName("handleFailure — 指数退避与死信阈值")
    class FailureHandlingTests {

        @Test
        @DisplayName("正常：首次失败（attempts 0→1，max 5）安排 60 秒后重试，回落 PENDING")
        void firstFailure_retriesAfter60Seconds() {
            FailingHandler handler = new FailingHandler(EVENT_TYPE, "下游超时");
            OutboxDispatcher dispatcher = newDispatcher(handler);
            givenDue(pendingEvent(1L, 0, 5));
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            LocalDateTime before = LocalDateTime.now();
            dispatcher.poll();
            LocalDateTime after = LocalDateTime.now();

            ArgumentCaptor<LocalDateTime> retryAt = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
            verify(mapper).markFailedWithRetry(eq(1L), error.capture(), retryAt.capture());
            verify(mapper, never()).markDead(anyLong(), anyString());

            assertThat(error.getValue()).contains("下游超时");
            // 退避 60s × 2^(1-1) = 60s，容忍测试执行耗时
            assertThat(retryAt.getValue()).isBetween(before.plusSeconds(59), after.plusSeconds(61));
        }

        @Test
        @DisplayName("正常：第二次失败（attempts 1→2）退避翻倍为 120 秒")
        void secondFailure_backoffDoublesTo120Seconds() {
            FailingHandler handler = new FailingHandler(EVENT_TYPE, "锁冲突");
            OutboxDispatcher dispatcher = newDispatcher(handler);
            givenDue(pendingEvent(1L, 1, 5));
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            LocalDateTime before = LocalDateTime.now();
            dispatcher.poll();

            ArgumentCaptor<LocalDateTime> retryAt = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(mapper).markFailedWithRetry(eq(1L), anyString(), retryAt.capture());

            // 60s × 2^(2-1) = 120s
            assertThat(retryAt.getValue()).isBetween(before.plusSeconds(119), before.plusSeconds(122));
        }

        @Test
        @DisplayName("契约：attempts+1 达到 maxAttempts 时转死信 DEAD，不再安排重试")
        void reachingMaxAttempts_marksDead() {
            FailingHandler handler = new FailingHandler(EVENT_TYPE, "契约不兼容");
            OutboxDispatcher dispatcher = newDispatcher(handler);
            // claim 时 SQL 已自增 attempts，故读到的旧值 4 表示本次是第 5 次
            givenDue(pendingEvent(1L, 4, 5));
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            dispatcher.poll();

            ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
            verify(mapper).markDead(eq(1L), error.capture());
            verify(mapper, never()).markFailedWithRetry(anyLong(), anyString(), any());
            assertThat(error.getValue()).contains("契约不兼容");
        }

        @Test
        @DisplayName("边界：attempts+1 恰好差一次到上限时仍重试（阈值判定为 >= 而非 >）")
        void oneBeforeMax_stillRetries() {
            FailingHandler handler = new FailingHandler(EVENT_TYPE, "临时故障");
            OutboxDispatcher dispatcher = newDispatcher(handler);
            givenDue(pendingEvent(1L, 3, 5));   // 3+1=4 < 5
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            dispatcher.poll();

            verify(mapper).markFailedWithRetry(eq(1L), anyString(), any(LocalDateTime.class));
            verify(mapper, never()).markDead(anyLong(), anyString());
        }

        @Test
        @DisplayName("边界：maxAttempts 为 null 时回落到默认值 5")
        void nullMaxAttempts_fallsBackToDefault5() {
            FailingHandler handler = new FailingHandler(EVENT_TYPE, "err");
            OutboxDispatcher dispatcher = newDispatcher(handler);
            OutboxEvent event = pendingEvent(1L, 4, null);   // maxAttempts=null，attempts=4
            givenDue(event);
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            dispatcher.poll();

            // 4+1=5 >= 默认 5 → 死信
            verify(mapper).markDead(eq(1L), anyString());
        }

        @Test
        @DisplayName("边界：attempts 为 null 时按 0 计（本次为第 1 次），安排重试而非死信")
        void nullAttempts_treatedAsFirst() {
            FailingHandler handler = new FailingHandler(EVENT_TYPE, "err");
            OutboxDispatcher dispatcher = newDispatcher(handler);
            givenDue(pendingEvent(1L, null, 5));
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            dispatcher.poll();

            verify(mapper).markFailedWithRetry(eq(1L), anyString(), any(LocalDateTime.class));
            verify(mapper, never()).markDead(anyLong(), anyString());
        }

        @Test
        @DisplayName("边界：异常无 message 时用异常类名兜底，不落 null 错误信息")
        void exceptionWithoutMessage_usesClassName() {
            FailingHandler handler = new FailingHandler(EVENT_TYPE, null);
            handler.throwNpe = true;
            OutboxDispatcher dispatcher = newDispatcher(handler);
            givenDue(pendingEvent(1L, 0, 5));
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            dispatcher.poll();

            ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
            verify(mapper).markFailedWithRetry(eq(1L), error.capture(), any(LocalDateTime.class));
            assertThat(error.getValue()).isEqualTo(NullPointerException.class.getSimpleName());
        }

        @Test
        @DisplayName("契约：超长错误信息被截断到 990 字符以内（列宽 1000）")
        void longErrorMessage_isTruncated() {
            FailingHandler handler = new FailingHandler(EVENT_TYPE, "x".repeat(2000));
            OutboxDispatcher dispatcher = newDispatcher(handler);
            givenDue(pendingEvent(1L, 0, 5));
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            dispatcher.poll();

            ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
            verify(mapper).markFailedWithRetry(eq(1L), error.capture(), any(LocalDateTime.class));
            assertThat(error.getValue()).hasSize(990);
        }
    }

    // ==================== 租户上下文 ====================

    @Nested
    @DisplayName("deliver — 租户上下文回填与恢复")
    class TenantContextTests {

        @Test
        @DisplayName("契约：投递期间把 event.tenantId 回填到 SecurityContextHolder，处理器可读到")
        void backfillsTenantId_duringDelivery() {
            TenantObservingHandler handler = new TenantObservingHandler(EVENT_TYPE);
            OutboxDispatcher dispatcher = newDispatcher(handler);
            OutboxEvent event = pendingEvent(1L, 0, 5);
            event.setTenantId(9999L);
            givenDue(event);
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            dispatcher.poll();

            assertThat(handler.observedTenantId).isEqualTo(9999L);
        }

        @Test
        @DisplayName("契约：投递结束后清理上下文（原值为 null 时），避免污染线程池后续任务")
        void clearsContext_afterDelivery_whenNoPreviousTenant() {
            TenantObservingHandler handler = new TenantObservingHandler(EVENT_TYPE);
            OutboxDispatcher dispatcher = newDispatcher(handler);
            OutboxEvent event = pendingEvent(1L, 0, 5);
            event.setTenantId(9999L);
            givenDue(event);
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            dispatcher.poll();

            assertThat(SecurityContextHolder.getTenantId()).isNull();
        }

        @Test
        @DisplayName("契约：调度线程原有租户上下文在投递后被恢复（不被事件租户覆盖）")
        void restoresPreviousTenant_afterDelivery() {
            TenantObservingHandler handler = new TenantObservingHandler(EVENT_TYPE);
            OutboxDispatcher dispatcher = newDispatcher(handler);
            OutboxEvent event = pendingEvent(1L, 0, 5);
            event.setTenantId(9999L);
            givenDue(event);
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            SecurityContextHolder.setTenantId(1L);   // 模拟调度线程已有上下文
            dispatcher.poll();

            assertThat(handler.observedTenantId).isEqualTo(9999L);
            assertThat(SecurityContextHolder.getTenantId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("边界：event.tenantId 为 null 时不覆盖上下文，且失败路径同样执行 finally 恢复")
        void nullEventTenant_doesNotOverride_andFailureStillRestores() {
            FailingHandler handler = new FailingHandler(EVENT_TYPE, "err");
            OutboxDispatcher dispatcher = newDispatcher(handler);
            OutboxEvent event = pendingEvent(1L, 0, 5);
            event.setTenantId(null);
            givenDue(event);
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            SecurityContextHolder.setTenantId(1L);
            dispatcher.poll();

            assertThat(SecurityContextHolder.getTenantId()).isEqualTo(1L);
        }
    }

    // ==================== 负载反序列化 ====================

    @Nested
    @DisplayName("invokeHandler — 负载反序列化与泛型解析")
    class PayloadDeserializationTests {

        @Test
        @DisplayName("正常：声明了具体泛型的处理器收到反序列化后的负载对象")
        void typedHandler_receivesDeserializedPayload() {
            ObjectMapper objectMapper = new ObjectMapper();
            TypedHandler handler = new TypedHandler();
            OutboxDispatcher dispatcher = newDispatcher(objectMapper, handler);

            OutboxEvent event = pendingEvent(1L, 0, 5);
            event.setPayload("{\"eventNumber\":\"BG001\",\"costDelta\":1000}");
            givenDue(event);
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            dispatcher.poll();

            assertThat(handler.received).isNotNull();
            assertThat(handler.received.getEventNumber()).isEqualTo("BG001");
            verify(mapper).markDelivered(eq(1L), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("契约：泛型被擦除为 Object 的处理器收到原始 JSON 字符串，自行解析")
        void objectGenericHandler_receivesRawJson() {
            RawJsonHandler handler = new RawJsonHandler(EVENT_TYPE);
            OutboxDispatcher dispatcher = newDispatcher(handler);

            OutboxEvent event = pendingEvent(1L, 0, 5);
            event.setPayload("{\"any\":\"shape\"}");
            givenDue(event);
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            dispatcher.poll();

            assertThat(handler.receivedRawJson).isEqualTo("{\"any\":\"shape\"}");
            verify(mapper).markDelivered(eq(1L), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("异常：负载与处理器泛型不兼容时按失败处理（进重试/死信留证），不静默跳过")
        void incompatiblePayload_countsAsFailure() {
            ObjectMapper objectMapper = new ObjectMapper();
            TypedHandler handler = new TypedHandler();
            OutboxDispatcher dispatcher = newDispatcher(objectMapper, handler);

            OutboxEvent event = pendingEvent(1L, 0, 5);
            event.setPayload("{ 这不是合法 JSON ");
            givenDue(event);
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            dispatcher.poll();

            assertThat(handler.received).isNull();
            verify(mapper, never()).markDelivered(anyLong(), any());
            verify(mapper).markFailedWithRetry(eq(1L), anyString(), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("契约：rawPayload 始终携带原始 JSON，即使反序列化成功也保留（便于排查）")
        void rawPayload_alwaysRetained() {
            ObjectMapper objectMapper = new ObjectMapper();
            RawPayloadObservingHandler handler = new RawPayloadObservingHandler();
            OutboxDispatcher dispatcher = newDispatcher(objectMapper, handler);

            OutboxEvent event = pendingEvent(1L, 0, 5);
            event.setPayload("{\"eventNumber\":\"BG002\"}");
            givenDue(event);
            when(mapper.claimForDelivery(1L)).thenReturn(1);

            dispatcher.poll();

            assertThat(handler.observedRawPayload).isEqualTo("{\"eventNumber\":\"BG002\"}");
        }

        @Test
        @DisplayName("契约：消息元数据（幂等键/聚合根/尝试次数）如实传递给处理器")
        void messageMetadata_passedThrough() {
            MetadataObservingHandler handler = new MetadataObservingHandler(EVENT_TYPE);
            OutboxDispatcher dispatcher = newDispatcher(handler);

            OutboxEvent event = pendingEvent(7001L, 2, 5);
            event.setAggregateType("ChangeEvent");
            event.setAggregateId(9001L);
            event.setIdempotencyKey("ChangeEvent:9001:CHANGE_EVENT_APPROVED:3");
            event.setTenantId(9999L);
            givenDue(event);
            when(mapper.claimForDelivery(7001L)).thenReturn(1);

            dispatcher.poll();

            assertThat(handler.observedId).isEqualTo(7001L);
            assertThat(handler.observedAggregateType).isEqualTo("ChangeEvent");
            assertThat(handler.observedAggregateId).isEqualTo(9001L);
            assertThat(handler.observedIdempotencyKey).isEqualTo("ChangeEvent:9001:CHANGE_EVENT_APPROVED:3");
            assertThat(handler.observedAttempts).isEqualTo(2);
        }
    }

    // ==================== 历史清理 ====================

    @Nested
    @DisplayName("purge — 已投递事件保留期清理")
    class PurgeTests {

        @Test
        @DisplayName("正常：按 30 天保留期清理已投递事件")
        void purgesDeliveredOlderThan30Days() {
            OutboxDispatcher dispatcher = newDispatcher();
            when(mapper.purgeDeliveredBefore(any(LocalDateTime.class))).thenReturn(3);

            LocalDateTime before = LocalDateTime.now().minusDays(30);
            dispatcher.purge();

            ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(mapper).purgeDeliveredBefore(threshold.capture());
            // 阈值约为「now - 30 天」，容忍执行耗时
            assertThat(threshold.getValue()).isBetween(before.minusMinutes(1), before.plusMinutes(1));
        }

        @Test
        @DisplayName("异常：清理失败时吞掉异常，不影响调度器存活")
        void purgeFailure_isSwallowed() {
            OutboxDispatcher dispatcher = newDispatcher();
            doThrow(new RuntimeException("DB 抖动")).when(mapper).purgeDeliveredBefore(any());

            dispatcher.purge();   // 不抛异常即为通过
        }

        @Test
        @DisplayName("边界：无可清理记录时不产生副作用")
        void nothingToPurge_isNoop() {
            OutboxDispatcher dispatcher = newDispatcher();
            when(mapper.purgeDeliveredBefore(any(LocalDateTime.class))).thenReturn(0);

            dispatcher.purge();

            verify(mapper).purgeDeliveredBefore(any(LocalDateTime.class));
            verify(mapper, never()).markDead(anyLong(), anyString());
        }
    }

    // ==================== 辅助：构造与夹具 ====================

    private void givenDue(OutboxEvent... events) {
        when(mapper.selectDueEvents(any(LocalDateTime.class), anyInt())).thenReturn(List.of(events));
    }

    private static OutboxEvent pendingEvent(Long id, Integer attempts, Integer maxAttempts) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setEventType(EVENT_TYPE);
        event.setAggregateType("ChangeEvent");
        event.setAggregateId(9001L);
        event.setIdempotencyKey("ChangeEvent:9001:" + EVENT_TYPE + ":1");
        event.setPayload("{}");
        event.setStatus(OutboxStatus.PENDING.name());
        event.setAttempts(attempts);
        event.setMaxAttempts(maxAttempts);
        event.setCreatedAt(LocalDateTime.now());
        return event;
    }

    /** 用默认 ObjectMapper 构造调度器（负载为原始 JSON 分支不需要真实反序列化） */
    private OutboxDispatcher newDispatcher(OutboxEventHandler<?>... handlers) {
        return newDispatcher(new ObjectMapper(), handlers);
    }

    @SuppressWarnings("unchecked")
    private OutboxDispatcher newDispatcher(ObjectMapper objectMapper, OutboxEventHandler<?>... handlers) {
        ObjectProvider<OutboxEventHandler<?>> provider = mock(ObjectProvider.class);
        // ObjectProvider.forEach 是 Iterable 的 default 方法，mock 后不会真实执行，
        // 因此用 doAnswer 手动把处理器逐个喂给构造器里的 register 消费者。
        doAnswer(invocation -> {
            Consumer<OutboxEventHandler<?>> consumer = invocation.getArgument(0);
            for (OutboxEventHandler<?> handler : handlers) {
                consumer.accept(handler);
            }
            return null;
        }).when(provider).forEach(any());
        return new OutboxDispatcher(mapper, objectMapper, provider);
    }

    /** 记录调用次数的处理器（负载走原始 JSON 分支） */
    private static class RecordingHandler implements OutboxEventHandler<Object> {
        final AtomicInteger invocations = new AtomicInteger();
        volatile String lastEventType;
        private final String eventType;

        RecordingHandler(String eventType) {
            this.eventType = eventType;
        }

        @Override
        public String supports() {
            return eventType;
        }

        @Override
        public void handle(OutboxMessage<Object> message) {
            invocations.incrementAndGet();
            lastEventType = message.getEventType();
        }
    }

    /** 恒定抛异常的处理器 */
    private static class FailingHandler implements OutboxEventHandler<Object> {
        private final String eventType;
        private final String message;
        boolean throwNpe;

        FailingHandler(String eventType, String message) {
            this.eventType = eventType;
            this.message = message;
        }

        @Override
        public String supports() {
            return eventType;
        }

        @Override
        public void handle(OutboxMessage<Object> msg) throws Exception {
            if (throwNpe) {
                throw new NullPointerException();
            }
            throw new RuntimeException(message);
        }
    }

    /** 观察投递期间租户上下文的处理器 */
    private static class TenantObservingHandler implements OutboxEventHandler<Object> {
        volatile Long observedTenantId;
        private final String eventType;

        TenantObservingHandler(String eventType) {
            this.eventType = eventType;
        }

        @Override
        public String supports() {
            return eventType;
        }

        @Override
        public void handle(OutboxMessage<Object> message) {
            observedTenantId = SecurityContextHolder.getTenantId();
        }
    }

    /** 测试用负载类型（公开 getter/setter 供 Jackson 反序列化） */
    public static class TestPayload {
        private String eventNumber;
        private Long costDelta;

        public String getEventNumber() {
            return eventNumber;
        }

        public void setEventNumber(String eventNumber) {
            this.eventNumber = eventNumber;
        }

        public Long getCostDelta() {
            return costDelta;
        }

        public void setCostDelta(Long costDelta) {
            this.costDelta = costDelta;
        }
    }

    /** 声明具体泛型的处理器：应收到反序列化后的对象 */
    private static class TypedHandler implements OutboxEventHandler<TestPayload> {
        volatile TestPayload received;

        @Override
        public String supports() {
            return EVENT_TYPE;
        }

        @Override
        public void handle(OutboxMessage<TestPayload> message) {
            received = message.getPayload();
        }
    }

    /** 观察 rawPayload 的处理器 */
    private static class RawPayloadObservingHandler implements OutboxEventHandler<TestPayload> {
        volatile String observedRawPayload;

        @Override
        public String supports() {
            return EVENT_TYPE;
        }

        @Override
        public void handle(OutboxMessage<TestPayload> message) {
            observedRawPayload = message.getRawPayload();
        }
    }

    /** 观察消息元数据的处理器 */
    private static class MetadataObservingHandler implements OutboxEventHandler<Object> {
        volatile Long observedId;
        volatile String observedAggregateType;
        volatile Long observedAggregateId;
        volatile String observedIdempotencyKey;
        volatile Integer observedAttempts;
        private final String eventType;

        MetadataObservingHandler(String eventType) {
            this.eventType = eventType;
        }

        @Override
        public String supports() {
            return eventType;
        }

        @Override
        public void handle(OutboxMessage<Object> message) {
            observedId = message.getId();
            observedAggregateType = message.getAggregateType();
            observedAggregateId = message.getAggregateId();
            observedIdempotencyKey = message.getIdempotencyKey();
            observedAttempts = message.getAttempts();
        }
    }

    /** 泛型擦除为 Object 的处理器：应收到原始 JSON 字符串 */
    private static class RawJsonHandler implements OutboxEventHandler<Object> {
        volatile String receivedRawJson;
        private final String eventType;

        RawJsonHandler(String eventType) {
            this.eventType = eventType;
        }

        @Override
        public String supports() {
            return eventType;
        }

        @Override
        public void handle(OutboxMessage<Object> message) {
            // payloadType 解析为 null 时，调度器把原始 JSON 直接作为 payload 传入
            receivedRawJson = (String) message.getPayload();
        }
    }
}
