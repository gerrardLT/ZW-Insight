package com.zwinsight.machine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.machine.domain.*;
import com.zwinsight.machine.mapper.*;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 机械结算 审批通过回调 独立单元测试
 * <p>
 * 覆盖 P0-6：按结算明细逐机械分摊到对应机械合同（而非全额倾倒到首个合同），
 * 未匹配金额告警而非静默丢弃。
 * <p>
 * 独立于 {@link MachineWorkSettlementServiceTest}，使用干净的 @Mock + @InjectMocks 骨架，
 * 确保 stub 与 service 实际调用的 mapper 联动生效。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MachineWorkSettlementService 审批通过回调（P0-6 分摊回写）")
class MachineWorkSettlementOnApprovedTest {

    @Mock private BizMachineWorkSettlementMapper settlementMapper;
    @Mock private BizMachineWorkSettlementDetailMapper detailMapper;
    @Mock private BizMachineWorkLogMapper workLogMapper;
    @Mock private BizMachineLedgerMapper ledgerMapper;
    @Mock private BizMachineContractMapper contractMapper;
    @Mock private ApprovalService approvalService;

    private MachineWorkSettlementService settlementService;

    private static final String BUSINESS_TYPE = "machine_settlement";

    @BeforeEach
    void setUp() {
        // 手动构造，显式传入 mock，杜绝 @InjectMocks 构造注入不确定性
        settlementService = new MachineWorkSettlementService(
                settlementMapper, detailMapper, workLogMapper, ledgerMapper, contractMapper, approvalService);
    }

    @Test
    @DisplayName("正常：按明细分摊到各自机械合同，逐合同回写累计结算")
    void onApproved_allocatesByDetailToEachContract() {
        // Given：结算单存在，项目 1
        BizMachineWorkSettlement settlement = buildSettlement(100L, 0, new BigDecimal("8000.00"), 1L);
        when(settlementMapper.selectById(100L)).thenReturn(settlement);

        // 两条明细：ledger 5（挖掘机）小计 3000，ledger 6（塔吊）小计 5000
        BizMachineWorkSettlementDetail d1 = buildDetail(5L, new BigDecimal("3000.00"));
        BizMachineWorkSettlementDetail d2 = buildDetail(6L, new BigDecimal("5000.00"));
        when(detailMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(d1, d2));

        // 项目下两份生效合同：挖掘机合同 A（id=10）、塔吊合同 B（id=20）
        BizMachineContract cA = buildContract(10L, 1L, "挖掘机", new BigDecimal("1000.00"));
        BizMachineContract cB = buildContract(20L, 1L, "塔吊", new BigDecimal("2000.00"));
        when(contractMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cA, cB));

        // 台账：ledger 5->挖掘机，ledger 6->塔吊
        BizMachineLedger l5 = buildLedger(5L, "挖掘机", "EXC-001");
        BizMachineLedger l6 = buildLedger(6L, "塔吊", "TC-001");
        when(ledgerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(l5, l6));

        // 逐合同回写时按 id 再查
        when(contractMapper.selectById(10L)).thenReturn(cA);
        when(contractMapper.selectById(20L)).thenReturn(cB);

        // When
        settlementService.onApproved(new ApprovalCompleteEvent(this, BUSINESS_TYPE, 100L, "APPROVED"));

        // Then：状态置为 2（已审批）
        assertThat(settlement.getStatus()).isEqualTo(2);
        verify(settlementMapper).updateById(settlement);

        // 挖掘机合同 A：1000 + 3000 = 4000
        verify(contractMapper).updateById(argThat(c ->
                c.getId().equals(10L)
                        && c.getCumulativeSettlement().compareTo(new BigDecimal("4000.00")) == 0));
        // 塔吊合同 B：2000 + 5000 = 7000
        verify(contractMapper).updateById(argThat(c ->
                c.getId().equals(20L)
                        && c.getCumulativeSettlement().compareTo(new BigDecimal("7000.00")) == 0));
        // 两份合同各回写一次
        verify(contractMapper, times(2)).updateById(any());
    }

    @Test
    @DisplayName("非 APPROVED 结果：直接返回，不触碰结算单")
    void onApproved_nonApprovedResult_returnsEarly() {
        settlementService.onApproved(new ApprovalCompleteEvent(this, BUSINESS_TYPE, 100L, "REJECTED"));

        verify(settlementMapper, never()).selectById(anyLong());
        verify(settlementMapper, never()).updateById(any());
        verify(contractMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("非本业务类型：直接返回，不触碰结算单")
    void onApproved_otherBusinessType_returnsEarly() {
        settlementService.onApproved(new ApprovalCompleteEvent(this, "contract_approval", 100L, "APPROVED"));

        verify(settlementMapper, never()).selectById(anyLong());
        verify(settlementMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("结算单不存在：告警返回，不回写合同")
    void onApproved_settlementNotFound_returnsEarly() {
        when(settlementMapper.selectById(999L)).thenReturn(null);

        settlementService.onApproved(new ApprovalCompleteEvent(this, BUSINESS_TYPE, 999L, "APPROVED"));

        verify(settlementMapper, never()).updateById(any());
        verify(contractMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("机械名称未匹配到合同：状态仍置 2，但不回写任何合同（金额告警不静默丢弃）")
    void onApproved_unmatchedMachine_skipContractButApprove() {
        BizMachineWorkSettlement settlement = buildSettlement(100L, 0, new BigDecimal("3000.00"), 1L);
        when(settlementMapper.selectById(100L)).thenReturn(settlement);

        // 明细 ledger 5，小计 3000
        BizMachineWorkSettlementDetail d1 = buildDetail(5L, new BigDecimal("3000.00"));
        when(detailMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(d1));

        // 合同为"泵车"，与台账机械名"挖掘机"不匹配
        BizMachineContract cA = buildContract(10L, 1L, "泵车", new BigDecimal("1000.00"));
        when(contractMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cA));

        BizMachineLedger l5 = buildLedger(5L, "挖掘机", "EXC-001");
        when(ledgerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(l5));

        settlementService.onApproved(new ApprovalCompleteEvent(this, BUSINESS_TYPE, 100L, "APPROVED"));

        // 状态置为已审批
        assertThat(settlement.getStatus()).isEqualTo(2);
        verify(settlementMapper).updateById(settlement);
        // 未匹配 -> 不按 id 查合同、不回写合同
        verify(contractMapper, never()).selectById(anyLong());
        verify(contractMapper, never()).updateById(any());
    }

    // ==================== 辅助方法 ====================

    private BizMachineWorkSettlement buildSettlement(Long id, Integer status, BigDecimal totalAmount, Long projectId) {
        BizMachineWorkSettlement settlement = new BizMachineWorkSettlement();
        settlement.setId(id);
        settlement.setStatus(status);
        settlement.setTotalAmount(totalAmount);
        settlement.setProjectId(projectId);
        return settlement;
    }

    private BizMachineWorkSettlementDetail buildDetail(Long ledgerId, BigDecimal subtotal) {
        BizMachineWorkSettlementDetail detail = new BizMachineWorkSettlementDetail();
        detail.setLedgerId(ledgerId);
        detail.setSubtotal(subtotal);
        return detail;
    }

    private BizMachineContract buildContract(Long id, Long projectId, String machineName, BigDecimal cumulativeSettlement) {
        BizMachineContract contract = new BizMachineContract();
        contract.setId(id);
        contract.setProjectId(projectId);
        contract.setMachineName(machineName);
        contract.setStatus("EFFECTIVE");
        contract.setCumulativeSettlement(cumulativeSettlement);
        return contract;
    }

    private BizMachineLedger buildLedger(Long id, String machineName, String machineCode) {
        BizMachineLedger ledger = new BizMachineLedger();
        ledger.setId(id);
        ledger.setMachineName(machineName);
        ledger.setMachineCode(machineCode);
        return ledger;
    }
}
