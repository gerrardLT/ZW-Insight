package com.zwinsight.finance.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.event.contract.ProcessSettlementApprovedEvent;
import com.zwinsight.contract.domain.BizContractPayTerm;
import com.zwinsight.contract.service.ContractPayTermService;
import com.zwinsight.finance.domain.BizRetentionMoney;
import com.zwinsight.finance.mapper.BizRetentionMoneyMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 过程结算质保金计提监听器单元测试（V2026_109，C2）
 */
@ExtendWith(MockitoExtension.class)
class ProcessSettlementRetentionListenerTest {

    @Mock
    private ContractPayTermService payTermService;

    @Mock
    private BizRetentionMoneyMapper retentionMapper;

    @InjectMocks
    private ProcessSettlementRetentionListener listener;

    private ProcessSettlementApprovedEvent event(String retentionDeduct) {
        ProcessSettlementApprovedEvent e = new ProcessSettlementApprovedEvent();
        e.setSettlementId(500L);
        e.setProjectId(10L);
        e.setContractId(20L);
        e.setNetAmount(new BigDecimal("95000"));
        e.setRetentionDeduct(retentionDeduct != null ? new BigDecimal(retentionDeduct) : null);
        e.setPeriodEnd(LocalDate.of(2026, 9, 30));
        return e;
    }

    @Test
    @DisplayName("扣留额>0 且条款带质保期：按单据事实计提，期限取条款月数")
    void accrues_withTermMonths() {
        when(retentionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        BizContractPayTerm term = new BizContractPayTerm();
        term.setRatio(new BigDecimal("3"));
        term.setRetentionMonths(24);
        when(payTermService.firstActiveTerm(eq("CONSTRUCTION"), eq(20L), eq("RETENTION"))).thenReturn(term);

        listener.onProcessSettlementApproved(event("3000"));

        ArgumentCaptor<BizRetentionMoney> captor = ArgumentCaptor.forClass(BizRetentionMoney.class);
        verify(retentionMapper).insert(captor.capture());
        BizRetentionMoney r = captor.getValue();
        assertThat(r.getRetentionAmount()).isEqualByComparingTo("3000");
        assertThat(r.getSourceType()).isEqualTo("PROCESS_SETTLEMENT");
        assertThat(r.getSourceId()).isEqualTo(500L);
        assertThat(r.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(r.getExpireDate()).isEqualTo(LocalDate.of(2028, 9, 30));
        assertThat(r.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("无条款：仍按单据事实计提，期限留空不猜默认")
    void accrues_withoutTerm_noGuessedExpiry() {
        when(retentionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(payTermService.firstActiveTerm(eq("CONSTRUCTION"), eq(20L), eq("RETENTION"))).thenReturn(null);

        listener.onProcessSettlementApproved(event("500"));

        ArgumentCaptor<BizRetentionMoney> captor = ArgumentCaptor.forClass(BizRetentionMoney.class);
        verify(retentionMapper).insert(captor.capture());
        assertThat(captor.getValue().getRetentionPeriod()).isNull();
        assertThat(captor.getValue().getExpireDate()).isNull();
    }

    @Test
    @DisplayName("幂等：同 settlementId 已计提跳过；扣留额空/0 不计提")
    void idempotentAndZeroSkips() {
        when(retentionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        listener.onProcessSettlementApproved(event("3000"));
        verify(retentionMapper, never()).insert(any(BizRetentionMoney.class));

        listener.onProcessSettlementApproved(event(null));
        listener.onProcessSettlementApproved(event("0"));
        verify(retentionMapper, never()).insert(any(BizRetentionMoney.class));
    }
}
