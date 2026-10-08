package com.zwinsight.machine.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.machine.domain.*;
import com.zwinsight.machine.mapper.*;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import com.zwinsight.workflow.service.ApprovalService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MachineWorkSettlementOnApprovedTest {
    @Mock BizMachineWorkSettlementMapper settlementMapper;
    @Mock BizMachineWorkSettlementDetailMapper detailMapper;
    @Mock BizMachineWorkLogMapper workLogMapper;
    @Mock BizMachineLedgerMapper ledgerMapper;
    @Mock BizMachineContractMapper contractMapper;
    @Mock ApprovalService approvalService;
    @InjectMocks MachineWorkSettlementService service;
    @BeforeEach void setup() {
        SecurityContextHolder.setTenantId(9999L);
        for (Class<?> type : List.of(BizMachineWorkSettlement.class, BizMachineWorkSettlementDetail.class))
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clear(); }
    private ApprovalCompleteEvent event() { return new ApprovalCompleteEvent(this, "machine_settlement", 1L, "APPROVED"); }
    private void fixture() {
        var s = new BizMachineWorkSettlement(); s.setId(1L); s.setTenantId(9999L); s.setProjectId(10L);
        s.setStatus(1); s.setTotalAmount(BigDecimal.TEN);
        when(settlementMapper.selectById(1L)).thenReturn(s);
        lenient().when(settlementMapper.updateById(any())).thenReturn(1);
        lenient().when(settlementMapper.update(isNull(), any())).thenReturn(1);
        var d = new BizMachineWorkSettlementDetail(); d.setContractId(20L); d.setSubtotal(BigDecimal.TEN); d.setWorkLogIds(List.of(30L));
        when(detailMapper.selectList(any())).thenReturn(List.of(d));
        var l = new BizMachineWorkLog(); l.setId(30L); l.setProjectId(10L); l.setContractId(20L); l.setStatus("CONFIRMED"); l.setSettlementStatus("UNSETTLED");
        lenient().when(workLogMapper.lockById(30L, 9999L)).thenReturn(l);
        lenient().when(workLogMapper.updateById(l)).thenReturn(1);
    }
    @Test void approved_atomicContractIncrement() {
        fixture(); when(contractMapper.addSettlement(20L, BigDecimal.TEN, 9999L, 10L)).thenReturn(1);
        service.onApproved(event()); verify(contractMapper).addSettlement(20L, BigDecimal.TEN, 9999L, 10L);
        verify(contractMapper, never()).updateById(any());
    }
    @Test void approved_overLimitThrowsToRollbackTransaction() {
        fixture(); assertThatThrownBy(() -> service.onApproved(event())).isInstanceOf(BusinessException.class).hasMessageContaining("审批已回滚");
    }
    @Test void approved_duplicateDoesNotIncrement() {
        var s = new BizMachineWorkSettlement(); s.setStatus(2); s.setTenantId(9999L);
        when(settlementMapper.selectById(1L)).thenReturn(s);
        service.onApproved(event()); verifyNoInteractions(contractMapper, detailMapper, workLogMapper);
    }
    @Test void approved_nonApprovedIgnored() {
        service.onApproved(new ApprovalCompleteEvent(this, "machine_settlement", 1L, "REJECTED"));
        verifyNoInteractions(settlementMapper);
    }
}
