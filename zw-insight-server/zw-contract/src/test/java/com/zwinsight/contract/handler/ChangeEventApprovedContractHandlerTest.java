package com.zwinsight.contract.handler;

import com.zwinsight.common.event.contract.ChangeEventApprovedEvent;
import com.zwinsight.common.event.outbox.OutboxMessage;
import com.zwinsight.contract.domain.BizChangeEvent;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizChangeEventMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 变更事件批准 → 施工合同累计变更额回写处理器单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChangeEventApprovedContractHandler 合同变更额回写")
class ChangeEventApprovedContractHandlerTest {

    @Mock
    private BizConstructionContractMapper contractMapper;

    @Mock
    private BizChangeEventMapper changeEventMapper;

    @InjectMocks
    private ChangeEventApprovedContractHandler handler;

    private static BizChangeEvent eventRow(Long id, Integer written) {
        BizChangeEvent e = new BizChangeEvent();
        e.setId(id);
        e.setContractChangeWritten(written);
        return e;
    }

    private static OutboxMessage<ChangeEventApprovedEvent> message(ChangeEventApprovedEvent payload) {
        OutboxMessage<ChangeEventApprovedEvent> msg = new OutboxMessage<>();
        msg.setId(1L);
        msg.setPayload(payload);
        return msg;
    }

    @Test
    @DisplayName("supports: 返回 CHANGE_EVENT_APPROVED")
    void supports_returnsApprovedType() {
        assertThat(handler.supports()).isEqualTo("CHANGE_EVENT_APPROVED");
    }

    @Test
    @DisplayName("正常：显式指定 contractId 时原子累加并置幂等标记")
    void handle_withExplicitContractId_updatesCumulativeChangeAmount() {
        ChangeEventApprovedEvent payload = new ChangeEventApprovedEvent();
        payload.setEventId(1001L);
        payload.setEventNumber("CHG20260001");
        payload.setProjectId(90001L);
        payload.setContractId(91001L);
        payload.setCostDelta(new BigDecimal("50000.00"));

        when(changeEventMapper.selectById(1001L)).thenReturn(eventRow(1001L, 0));
        when(contractMapper.addCumulativeChangeAmount(91001L, new BigDecimal("50000.00"))).thenReturn(1);

        handler.handle(message(payload));

        verify(contractMapper).addCumulativeChangeAmount(91001L, new BigDecimal("50000.00"));
        verify(changeEventMapper).updateById(any(BizChangeEvent.class));
        verify(contractMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("幂等：事件已回写过（标记=1）时跳过，不再累加")
    void handle_alreadyWritten_isIdempotent() {
        ChangeEventApprovedEvent payload = new ChangeEventApprovedEvent();
        payload.setEventId(1005L);
        payload.setEventNumber("CHG20260005");
        payload.setProjectId(90001L);
        payload.setContractId(91001L);
        payload.setCostDelta(new BigDecimal("50000.00"));

        when(changeEventMapper.selectById(1005L)).thenReturn(eventRow(1005L, 1));

        handler.handle(message(payload));

        verify(contractMapper, never()).addCumulativeChangeAmount(anyLong(), any());
        verify(changeEventMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("正常：未指定 contractId 且项目有唯一生效施工合同时，自动匹配并回写")
    void handle_withoutContractId_singleEffectiveContract_autoMatchesAndUpdates() {
        ChangeEventApprovedEvent payload = new ChangeEventApprovedEvent();
        payload.setEventId(1002L);
        payload.setEventNumber("CHG20260002");
        payload.setProjectId(90001L);
        payload.setCostDelta(new BigDecimal("30000.00"));

        when(changeEventMapper.selectById(1002L)).thenReturn(eventRow(1002L, 0));
        BizConstructionContract c = new BizConstructionContract();
        c.setId(91001L);
        when(contractMapper.selectList(any())).thenReturn(List.of(c));
        when(contractMapper.addCumulativeChangeAmount(91001L, new BigDecimal("30000.00"))).thenReturn(1);

        handler.handle(message(payload));

        verify(contractMapper).addCumulativeChangeAmount(91001L, new BigDecimal("30000.00"));
    }

    @Test
    @DisplayName("边界：项目下有多份生效施工合同且未指定 contractId 时，跳过回写不猜测")
    void handle_withoutContractId_multipleEffectiveContracts_skipsUpdate() {
        ChangeEventApprovedEvent payload = new ChangeEventApprovedEvent();
        payload.setEventId(1003L);
        payload.setEventNumber("CHG20260003");
        payload.setProjectId(90001L);
        payload.setCostDelta(new BigDecimal("30000.00"));

        when(changeEventMapper.selectById(1003L)).thenReturn(eventRow(1003L, 0));
        when(contractMapper.selectList(any())).thenReturn(
                List.of(new BizConstructionContract(), new BizConstructionContract()));

        handler.handle(message(payload));

        verify(contractMapper, never()).addCumulativeChangeAmount(anyLong(), any());
        verify(changeEventMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("边界：无成本影响（costDelta=0）时跳过回写")
    void handle_zeroCostDelta_skipsUpdate() {
        ChangeEventApprovedEvent payload = new ChangeEventApprovedEvent();
        payload.setEventId(1004L);
        payload.setEventNumber("CHG20260004");
        payload.setProjectId(90001L);
        payload.setContractId(91001L);
        payload.setCostDelta(BigDecimal.ZERO);

        handler.handle(message(payload));

        verify(contractMapper, never()).addCumulativeChangeAmount(anyLong(), any());
        verify(changeEventMapper, never()).selectById(eq(1004L));
    }
}
