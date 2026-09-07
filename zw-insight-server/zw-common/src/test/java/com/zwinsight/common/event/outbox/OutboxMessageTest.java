package com.zwinsight.common.event.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 发件箱事件消息（投递给处理器的入参）单元测试。
 *
 * <p>重点钉住 {@link OutboxMessage#isLastAttempt()} 的<b>真实行为</b>：
 * 该方法只判断 {@code attempts >= 1}，即「是否为非首次投递」，
 * 并<b>不</b>表示「已达 maxAttempts、失败即转死信」。原因是 OutboxMessage
 * 不携带 maxAttempts 字段，无法在此处复现调度器的死信判定。</p>
 *
 * <p>死信判定唯一权威实现是 {@code OutboxDispatcher.handleFailure}
 * （{@code attempts + 1 >= maxAttempts}）。消费方若需「最后一次尝试走降级分支」，
 * 不能依赖本方法——见 OutboxDispatcherTest 中对退避与死信阈值的钉住。</p>
 */
class OutboxMessageTest {

    @Nested
    @DisplayName("isLastAttempt — 实际语义为「非首次投递」")
    class IsLastAttemptTests {

        @Test
        @DisplayName("边界：attempts 为 null → false（生产时无上下文）")
        void nullAttempts_isFalse() {
            OutboxMessage<String> message = new OutboxMessage<>();
            message.setAttempts(null);

            assertThat(message.isLastAttempt()).isFalse();
        }

        @Test
        @DisplayName("边界：attempts = 0 → false（尚未尝试过）")
        void zeroAttempts_isFalse() {
            OutboxMessage<String> message = new OutboxMessage<>();
            message.setAttempts(0);

            assertThat(message.isLastAttempt()).isFalse();
        }

        @Test
        @DisplayName("正常：attempts = 1 → true（claim 时已自增，首次投递即为 1）")
        void firstAttempt_isTrue() {
            OutboxMessage<String> message = new OutboxMessage<>();
            message.setAttempts(1);

            assertThat(message.isLastAttempt()).isTrue();
        }

        @Test
        @DisplayName("契约：attempts 远小于 maxAttempts(5) 时仍返回 true —— 证明它不代表「最后一次」")
        void midRetry_isTrue_provingItIsNotReallyLastAttempt() {
            // 调度器默认 maxAttempts=5，第 2 次尝试离死信还差 3 次，
            // 但本方法已返回 true。此用例把该语义偏差钉成契约，
            // 防止消费方误用它做「最后一次尝试」的降级判断。
            OutboxMessage<String> message = new OutboxMessage<>();
            message.setAttempts(2);

            assertThat(message.isLastAttempt()).isTrue();
        }

        @Test
        @DisplayName("边界：负数 attempts → false（脏数据不触发降级）")
        void negativeAttempts_isFalse() {
            OutboxMessage<String> message = new OutboxMessage<>();
            message.setAttempts(-1);

            assertThat(message.isLastAttempt()).isFalse();
        }
    }

    @Test
    @DisplayName("正常：泛型负载与元数据完整读写（供 Jackson/处理器消费）")
    void allFields_areReadWrite() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 4, 12, 30);
        OutboxMessage<Payload> message = new OutboxMessage<>();

        message.setId(7001L);
        message.setTenantId(9999L);
        message.setEventType("CHANGE_EVENT_APPROVED");
        message.setAggregateType("ChangeEvent");
        message.setAggregateId(9001L);
        message.setIdempotencyKey("ChangeEvent:9001:CHANGE_EVENT_APPROVED:3");
        message.setPayload(new Payload("BG20260904001"));
        message.setRawPayload("{\"eventNumber\":\"BG20260904001\"}");
        message.setAttempts(1);
        message.setCreatedAt(createdAt);

        assertThat(message.getId()).isEqualTo(7001L);
        assertThat(message.getTenantId()).isEqualTo(9999L);
        assertThat(message.getEventType()).isEqualTo("CHANGE_EVENT_APPROVED");
        assertThat(message.getAggregateType()).isEqualTo("ChangeEvent");
        assertThat(message.getAggregateId()).isEqualTo(9001L);
        assertThat(message.getIdempotencyKey()).isEqualTo("ChangeEvent:9001:CHANGE_EVENT_APPROVED:3");
        assertThat(message.getPayload()).isNotNull();
        assertThat(message.getPayload().getEventNumber()).isEqualTo("BG20260904001");
        assertThat(message.getRawPayload()).isEqualTo("{\"eventNumber\":\"BG20260904001\"}");
        assertThat(message.getAttempts()).isEqualTo(1);
        assertThat(message.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("契约：tenantId 可为 null（生产时无上下文），消费方需自行判空回填")
    void tenantId_isNullable() {
        OutboxMessage<String> message = new OutboxMessage<>();
        message.setTenantId(null);

        assertThat(message.getTenantId()).isNull();
    }

    @Test
    @DisplayName("契约：rawPayload 独立于 payload 存在——负载反序列化失败时仍可读取排查")
    void rawPayload_retainedIndependentlyOfPayload() {
        OutboxMessage<Payload> message = new OutboxMessage<>();
        message.setPayload(null);
        message.setRawPayload("{ malformed json ");

        assertThat(message.getPayload()).isNull();
        assertThat(message.getRawPayload()).isEqualTo("{ malformed json ");
    }

    /** 测试用负载类型 */
    private static class Payload {
        private final String eventNumber;

        Payload(String eventNumber) {
            this.eventNumber = eventNumber;
        }

        String getEventNumber() {
            return eventNumber;
        }
    }
}
