package com.zwinsight.budget.handler;

import com.zwinsight.budget.service.CostLedgerService;
import com.zwinsight.common.event.contract.ChangeEventApprovedEvent;
import com.zwinsight.common.event.outbox.OutboxMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 变更事件批准 → CBS 传导处理器单元测试。
 * <p>
 * 这是变更主链「审批通过 → 预算变更」的关键一跳，测错即断链。
 * 重点验证：方向解析（INCREASE/DECREASE）、幂等键构造、
 * 以及「有成本影响却无账户明细」这类数据缺陷必须显式暴露而非静默跳过。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChangeEventApprovedCostHandler 变更传导")
class ChangeEventApprovedCostHandlerTest {

    @Mock
    private CostLedgerService costLedgerService;

    @InjectMocks
    private ChangeEventApprovedCostHandler handler;

    private ChangeEventApprovedEvent event(BigDecimal costDelta,
                                           List<ChangeEventApprovedEvent.AccountDelta> accounts) {
        ChangeEventApprovedEvent e = new ChangeEventApprovedEvent();
        e.setEventId(555L);
        e.setEventNumber("CHG20260001");
        e.setProjectId(100L);
        e.setTenantId(1L);
        e.setCostDelta(costDelta);
        e.setScheduleDelayDays(3);
        e.setAffectedAccounts(accounts);
        e.setApprovedAt(LocalDateTime.now());
        e.setApprovedBy(9L);
        return e;
    }

    private ChangeEventApprovedEvent.AccountDelta delta(Long accountId, String type, String amount) {
        ChangeEventApprovedEvent.AccountDelta d = new ChangeEventApprovedEvent.AccountDelta();
        d.setAccountId(accountId);
        d.setDeltaType(type);
        d.setDeltaAmount(new BigDecimal(amount));
        return d;
    }

    private OutboxMessage<ChangeEventApprovedEvent> message(ChangeEventApprovedEvent payload) {
        OutboxMessage<ChangeEventApprovedEvent> m = new OutboxMessage<>();
        m.setId(1L);
        m.setEventType("CHANGE_EVENT_APPROVED");
        m.setAggregateType("ChangeEvent");
        m.setAggregateId(555L);
        m.setIdempotencyKey("ChangeEvent:555:CHANGE_EVENT_APPROVED:1");
        m.setPayload(payload);
        m.setAttempts(1);
        return m;
    }

    @Test
    @DisplayName("supports() 必须与生产方事件类型一致，否则事件无人消费")
    void supportsMatchesProducerEventType() {
        assertThat(handler.supports()).isEqualTo("CHANGE_EVENT_APPROVED");
    }

    @Nested
    @DisplayName("正常传导")
    class Propagation {

        @Test
        @DisplayName("单账户增加：生成 CURRENT 维度记账指令，金额取正")
        void propagatesIncrease() {
            when(costLedgerService.postBatch(anyList())).thenReturn(1);

            handler.handle(message(event(new BigDecimal("50000"),
                    List.of(delta(1001L, "INCREASE", "50000")))));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CostLedgerService.PostCommand>> captor =
                    ArgumentCaptor.forClass(List.class);
            verify(costLedgerService).postBatch(captor.capture());

            List<CostLedgerService.PostCommand> cmds = captor.getValue();
            assertThat(cmds).hasSize(1);
            CostLedgerService.PostCommand cmd = cmds.get(0);
            assertThat(cmd.accountId()).isEqualTo(1001L);
            assertThat(cmd.amountType()).isEqualTo(CostLedgerService.AMT_CURRENT);
            assertThat(cmd.deltaAmount()).isEqualByComparingTo("50000");
            assertThat(cmd.sourceType()).isEqualTo(CostLedgerService.SRC_CHANGE_EVENT);
            assertThat(cmd.sourceNumber()).isEqualTo("CHG20260001");
        }

        @Test
        @DisplayName("DECREASE 方向解析为负增量（方向与数值分离存储的计算收敛点）")
        void propagatesDecreaseAsNegative() {
            when(costLedgerService.postBatch(anyList())).thenReturn(1);

            handler.handle(message(event(new BigDecimal("-20000"),
                    List.of(delta(1001L, "DECREASE", "20000")))));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CostLedgerService.PostCommand>> captor =
                    ArgumentCaptor.forClass(List.class);
            verify(costLedgerService).postBatch(captor.capture());
            assertThat(captor.getValue().get(0).deltaAmount()).isEqualByComparingTo("-20000");
        }

        @Test
        @DisplayName("多账户混合方向：逐账户生成指令，合计与评估总额一致")
        void propagatesMultipleAccounts() {
            when(costLedgerService.postBatch(anyList())).thenReturn(2);

            List<ChangeEventApprovedEvent.AccountDelta> deltas = List.of(
                    delta(1001L, "INCREASE", "80000"),
                    delta(1002L, "DECREASE", "30000"));

            handler.handle(message(event(new BigDecimal("50000"), deltas)));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CostLedgerService.PostCommand>> captor =
                    ArgumentCaptor.forClass(List.class);
            verify(costLedgerService).postBatch(captor.capture());

            List<CostLedgerService.PostCommand> cmds = captor.getValue();
            assertThat(cmds).hasSize(2);
            BigDecimal sum = cmds.stream()
                    .map(CostLedgerService.PostCommand::deltaAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("幂等键带 accountId：多账户场景各自独立去重，部分重试不会重复记账")
        void idempotencyKeyIsPerAccount() {
            when(costLedgerService.postBatch(anyList())).thenReturn(2);

            handler.handle(message(event(new BigDecimal("50000"), List.of(
                    delta(1001L, "INCREASE", "30000"),
                    delta(1002L, "INCREASE", "20000")))));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CostLedgerService.PostCommand>> captor =
                    ArgumentCaptor.forClass(List.class);
            verify(costLedgerService).postBatch(captor.capture());

            List<String> sourceIds = captor.getValue().stream()
                    .map(CostLedgerService.PostCommand::sourceId).toList();
            assertThat(sourceIds).containsExactly("555:1001", "555:1002");
        }

        @Test
        @DisplayName("零变动明细被过滤，不产生无意义记账")
        void skipsZeroDeltas() {
            List<ChangeEventApprovedEvent.AccountDelta> deltas = new ArrayList<>();
            deltas.add(delta(1001L, "INCREASE", "0"));
            deltas.add(delta(1002L, "INCREASE", "25000"));
            when(costLedgerService.postBatch(anyList())).thenReturn(1);

            handler.handle(message(event(new BigDecimal("25000"), deltas)));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CostLedgerService.PostCommand>> captor =
                    ArgumentCaptor.forClass(List.class);
            verify(costLedgerService).postBatch(captor.capture());
            assertThat(captor.getValue()).hasSize(1);
            assertThat(captor.getValue().get(0).accountId()).isEqualTo(1002L);
        }

        @Test
        @DisplayName("明细全为零：不调用记账")
        void allZeroSkipsBatch() {
            handler.handle(message(event(BigDecimal.ZERO,
                    List.of(delta(1001L, "INCREASE", "0")))));

            verify(costLedgerService, never()).postBatch(anyList());
        }

        @Test
        @DisplayName("明细含 null 或缺 accountId：跳过该项而非抛 NPE（脏数据不应击穿消费）")
        void toleratesMalformedEntries() {
            List<ChangeEventApprovedEvent.AccountDelta> deltas = new ArrayList<>();
            deltas.add(null);
            deltas.add(delta(null, "INCREASE", "1000"));
            deltas.add(delta(1003L, "INCREASE", "1000"));
            when(costLedgerService.postBatch(anyList())).thenReturn(1);

            handler.handle(message(event(new BigDecimal("1000"), deltas)));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CostLedgerService.PostCommand>> captor =
                    ArgumentCaptor.forClass(List.class);
            verify(costLedgerService).postBatch(captor.capture());
            assertThat(captor.getValue()).hasSize(1);
            assertThat(captor.getValue().get(0).accountId()).isEqualTo(1003L);
        }
    }

    @Nested
    @DisplayName("无成本影响短路")
    class NoCostImpact {

        @Test
        @DisplayName("纯工期变更（无明细无成本影响）：不触碰 CBS，正常返回")
        void scheduleOnlyChangeSkips() {
            handler.handle(message(event(BigDecimal.ZERO, List.of())));

            verify(costLedgerService, never()).postBatch(anyList());
        }

        @Test
        @DisplayName("costDelta 为 null 且无明细：视为无成本影响")
        void nullCostDeltaSkips() {
            handler.handle(message(event(null, null)));

            verify(costLedgerService, never()).postBatch(anyList());
        }
    }

    @Nested
    @DisplayName("数据缺陷必须暴露")
    class DefectsMustSurface {

        @Test
        @DisplayName("有成本影响却无账户明细：抛异常触发重试/死信，绝不静默丢账")
        void costImpactWithoutDetailThrows() {
            assertThatThrownBy(() -> handler.handle(message(event(new BigDecimal("50000"), List.of()))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CHG20260001")
                    .hasMessageContaining("缺少受影响成本账户明细");

            verify(costLedgerService, never()).postBatch(anyList());
        }

        @Test
        @DisplayName("负载为 null：抛异常并带 outboxId 与原始负载便于排查")
        void nullPayloadThrows() {
            assertThatThrownBy(() -> handler.handle(message(null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("负载为空")
                    .hasMessageContaining("outboxId=1");
        }

        @Test
        @DisplayName("缺 eventId：抛异常（幂等键无从构造）")
        void missingEventIdThrows() {
            ChangeEventApprovedEvent e = event(new BigDecimal("1000"),
                    List.of(delta(1001L, "INCREASE", "1000")));
            e.setEventId(null);

            assertThatThrownBy(() -> handler.handle(message(e)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("eventId");
        }

        @Test
        @DisplayName("评估总额与明细合计不一致：以明细为准继续传导（留告警不阻断业务）")
        void mismatchFallsBackToDetail() {
            when(costLedgerService.postBatch(anyList())).thenReturn(1);

            // 声明 50000，明细合计只有 30000
            handler.handle(message(event(new BigDecimal("50000"),
                    List.of(delta(1001L, "INCREASE", "30000")))));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CostLedgerService.PostCommand>> captor =
                    ArgumentCaptor.forClass(List.class);
            verify(costLedgerService).postBatch(captor.capture());
            assertThat(captor.getValue().get(0).deltaAmount()).isEqualByComparingTo("30000");
        }
    }
}
