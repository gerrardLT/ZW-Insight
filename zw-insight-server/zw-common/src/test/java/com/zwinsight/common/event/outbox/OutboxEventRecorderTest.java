package com.zwinsight.common.event.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发件箱事件录制器（Transactional Outbox 生产端）单元测试。
 *
 * <p>覆盖三条关键契约：</p>
 * <ol>
 *   <li><b>幂等键构造</b>：{@code aggregateType:aggregateId:eventType:version}，
 *       version 为 null 时退化为毫秒时间戳判别式；</li>
 *   <li><b>幂等命中静默跳过</b>：撞唯一键返回 false 而非抛异常（同一次流转重入属正常）；</li>
 *   <li><b>失败即抛</b>：除幂等命中外的任何写入失败都要抛出，让业务事务一并回滚——
 *       宁可业务失败，也不能出现「状态改了但事件丢了」。</li>
 * </ol>
 *
 * <p>{@code sys_outbox_event} 是 {@code sys_} 前缀表，被租户拦截器 ignoreTable 排除，
 * 因此 tenantId 必须由录制器显式从 {@link SecurityContextHolder} 取值，此行为一并钉住。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutboxEventRecorderTest {

    @Mock
    private OutboxEventMapper outboxEventMapper;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxEventRecorder recorder;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        // 静态上下文若残留会污染同 JVM 内的后续测试类
        SecurityContextHolder.clear();
    }

    // ==================== record（对象负载） ====================

    @Nested
    @DisplayName("record — 负载为对象，内部序列化为 JSON")
    class RecordTests {

        @Test
        @DisplayName("正常：序列化成功后写入，返回 true")
        void serializesAndInserts_returnsTrue() throws Exception {
            SecurityContextHolder.setTenantId(9999L);
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"costDelta\":1000}");

            boolean result = recorder.record("CHANGE_EVENT_APPROVED", "ChangeEvent", 9001L, 3,
                    new Object());

            assertThat(result).isTrue();
            verify(outboxEventMapper).insert(any(OutboxEvent.class));
        }

        @Test
        @DisplayName("正常：序列化结果作为 payload 落库")
        void serializedJson_becomesPayload() throws Exception {
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventNumber\":\"BG001\"}");

            recorder.record("CHANGE_EVENT_APPROVED", "ChangeEvent", 9001L, 1, new Object());

            OutboxEvent saved = captureInserted();
            assertThat(saved.getPayload()).isEqualTo("{\"eventNumber\":\"BG001\"}");
        }

        @Test
        @DisplayName("异常：序列化失败抛 IllegalStateException（编码缺陷必须暴露，不可静默丢事件）")
        void serializationFailure_throwsIllegalState() throws Exception {
            when(objectMapper.writeValueAsString(any()))
                    .thenThrow(new JsonProcessingException("boom") {});

            assertThatThrownBy(() -> recorder.record("CHANGE_EVENT_APPROVED", "ChangeEvent", 9001L, 1,
                    new Object()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("发件箱事件负载序列化失败")
                    .hasMessageContaining("CHANGE_EVENT_APPROVED")
                    .hasMessageContaining("ChangeEvent")
                    .hasMessageContaining("9001")
                    .hasCauseInstanceOf(JsonProcessingException.class);

            // fail-fast：不得留下半截写入
            verify(outboxEventMapper, never()).insert(any(OutboxEvent.class));
        }
    }

    // ==================== recordJson（JSON 负载） ====================

    @Nested
    @DisplayName("recordJson — 字段填充与幂等键构造")
    class RecordJsonFieldsTests {

        @Test
        @DisplayName("正常：初始状态 PENDING、attempts=0、maxAttempts=5")
        void setsInitialDeliveryFields() {
            recorder.recordJson("CHANGE_EVENT_APPROVED", "ChangeEvent", 9001L, 1, "{}");

            OutboxEvent saved = captureInserted();
            assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING.name());
            assertThat(saved.getAttempts()).isZero();
            assertThat(saved.getMaxAttempts()).isEqualTo(5);
        }

        @Test
        @DisplayName("正常：业务标识字段逐一如实落库")
        void setsBusinessIdentityFields() {
            recorder.recordJson("CHANGE_EVENT_APPROVED", "ChangeEvent", 9001L, 1,
                    "{\"costDelta\":1000}");

            OutboxEvent saved = captureInserted();
            assertThat(saved.getEventType()).isEqualTo("CHANGE_EVENT_APPROVED");
            assertThat(saved.getAggregateType()).isEqualTo("ChangeEvent");
            assertThat(saved.getAggregateId()).isEqualTo(9001L);
            assertThat(saved.getPayload()).isEqualTo("{\"costDelta\":1000}");
        }

        @Test
        @DisplayName("正常：幂等键格式为 aggregateType:aggregateId:eventType:version")
        void buildsIdempotencyKey_fromVersion() {
            recorder.recordJson("CHANGE_EVENT_APPROVED", "ChangeEvent", 9001L, 7, "{}");

            assertThat(captureInserted().getIdempotencyKey())
                    .isEqualTo("ChangeEvent:9001:CHANGE_EVENT_APPROVED:7");
        }

        @Test
        @DisplayName("边界：version 为 null 时判别式退化为 t+毫秒时间戳（保证二次流转 key 不同）")
        void nullVersion_fallsBackToTimestampDiscriminator() {
            long before = System.currentTimeMillis();

            recorder.recordJson("CHANGE_EVENT_APPROVED", "ChangeEvent", 9001L, null, "{}");

            String key = captureInserted().getIdempotencyKey();
            assertThat(key).startsWith("ChangeEvent:9001:CHANGE_EVENT_APPROVED:t");

            String discriminator = key.substring(key.lastIndexOf(':') + 2);
            assertThat(discriminator).containsOnlyDigits();
            assertThat(Long.parseLong(discriminator)).isGreaterThanOrEqualTo(before);
        }

        @Test
        @DisplayName("契约：tenantId 从 SecurityContextHolder 显式取值（sys_ 表被租户拦截器排除）")
        void takesTenantId_fromSecurityContext() {
            SecurityContextHolder.setTenantId(9999L);

            recorder.recordJson("CHANGE_EVENT_APPROVED", "ChangeEvent", 9001L, 1, "{}");

            assertThat(captureInserted().getTenantId()).isEqualTo(9999L);
        }

        @Test
        @DisplayName("边界：无租户上下文时 tenantId 为 null（跨租户任务允许）")
        void withoutTenantContext_tenantIdIsNull() {
            recorder.recordJson("CHANGE_EVENT_APPROVED", "ChangeEvent", 9001L, 1, "{}");

            assertThat(captureInserted().getTenantId()).isNull();
        }
    }

    // ==================== 幂等与失败语义 ====================

    @Nested
    @DisplayName("recordJson — 幂等命中与失败语义")
    class RecordJsonIdempotencyTests {

        @Test
        @DisplayName("正常：撞唯一键视为幂等命中，返回 false 且不抛异常")
        void duplicateKey_isIdempotentHit_returnsFalse() {
            when(outboxEventMapper.insert(any(OutboxEvent.class)))
                    .thenThrow(new DuplicateKeyException("idempotency_key 唯一约束冲突"));

            boolean result = recorder.recordJson("CHANGE_EVENT_APPROVED", "ChangeEvent", 9001L, 3, "{}");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("异常：非幂等类的 DB 失败向上抛出，让业务事务一并回滚")
        void otherDataAccessException_propagates() {
            when(outboxEventMapper.insert(any(OutboxEvent.class)))
                    .thenThrow(new TestDataAccessException("连接中断"));

            assertThatThrownBy(() -> recorder.recordJson("CHANGE_EVENT_APPROVED", "ChangeEvent",
                    9001L, 3, "{}"))
                    .isInstanceOf(TestDataAccessException.class)
                    .hasMessageContaining("连接中断");
        }
    }

    // ==================== 参数校验 ====================

    @Nested
    @DisplayName("recordJson — 参数校验（幂等键依赖这些字段定位业务对象）")
    class RecordJsonValidationTests {

        @Test
        @DisplayName("异常：eventType 为 null / 空白 → IllegalArgumentException")
        void blankEventType_throws() {
            assertThatThrownBy(() -> recorder.recordJson(null, "ChangeEvent", 9001L, 1, "{}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("eventType");

            assertThatThrownBy(() -> recorder.recordJson("   ", "ChangeEvent", 9001L, 1, "{}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("eventType");
        }

        @Test
        @DisplayName("异常：aggregateType 为 null / 空白 → IllegalArgumentException")
        void blankAggregateType_throws() {
            assertThatThrownBy(() -> recorder.recordJson("E", null, 9001L, 1, "{}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("aggregateType");

            assertThatThrownBy(() -> recorder.recordJson("E", "  ", 9001L, 1, "{}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("aggregateType");
        }

        @Test
        @DisplayName("异常：aggregateId 为 null → IllegalArgumentException")
        void nullAggregateId_throws() {
            assertThatThrownBy(() -> recorder.recordJson("E", "ChangeEvent", null, 1, "{}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("aggregateId");
        }

        @Test
        @DisplayName("契约：任一校验失败都不得触达 DB（避免写入残缺幂等键的记录）")
        void validationFailure_neverTouchesDb() {
            assertThatThrownBy(() -> recorder.recordJson(null, null, null, 1, "{}"))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(outboxEventMapper, never()).insert(any(OutboxEvent.class));
        }
    }

    // ==================== 辅助 ====================

    private OutboxEvent captureInserted() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventMapper).insert(captor.capture());
        return captor.getValue();
    }

    /** 具体的 DataAccessException 子类，用于模拟非幂等类写入失败 */
    private static class TestDataAccessException extends DataAccessException {
        TestDataAccessException(String msg) {
            super(msg);
        }
    }
}
