package com.zwinsight.machine.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.machine.domain.BizMachineEntry;
import com.zwinsight.machine.domain.BizMachineLedger;
import com.zwinsight.machine.mapper.BizMachineEntryMapper;
import com.zwinsight.machine.mapper.BizMachineLedgerMapper;
import com.zwinsight.machine.mapper.BizMachineWorkLogMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MachineEntryServiceTest {

    @Mock private BizMachineEntryMapper entryMapper;
    @Mock private BizMachineLedgerMapper ledgerMapper;
    @Mock private BizProjectMapper projectMapper;
    @Mock private BizMachineWorkLogMapper workLogMapper;

    private MachineEntryService machineEntryService;

    @BeforeEach
    void setUp() {
        machineEntryService = new MachineEntryService(entryMapper, ledgerMapper, projectMapper, workLogMapper);
    }

    @Test
    @DisplayName("进场：REGISTERED状态可进场")
    void testEntryIn_registered() {
        com.zwinsight.common.config.SecurityContextHolder.setTenantId(1L);
        when(projectMapper.selectById(10L)).thenReturn(new com.zwinsight.project.domain.BizProject());
        BizMachineEntry entry = new BizMachineEntry();
        entry.setMachineId(1L);
        entry.setProjectId(10L);
        entry.setEntryDate(java.time.LocalDate.now());
        BizMachineLedger ledger = new BizMachineLedger();
        ledger.setStatus("REGISTERED");
        when(ledgerMapper.lockById(eq(1L), eq(1L))).thenReturn(ledger);
        when(ledgerMapper.updateById(any())).thenReturn(1);

        machineEntryService.entryIn(entry);

        assertThat(entry.getEntryType()).isEqualTo("IN");
        assertThat(ledger.getStatus()).isEqualTo("IN_FIELD");
        verify(entryMapper).insert(entry);
    }

    @Test
    @DisplayName("进场：OUT_FIELD状态可再次进场")
    void testEntryIn_outField() {
        com.zwinsight.common.config.SecurityContextHolder.setTenantId(1L);
        when(projectMapper.selectById(10L)).thenReturn(new com.zwinsight.project.domain.BizProject());
        BizMachineEntry entry = new BizMachineEntry();
        entry.setMachineId(1L);
        entry.setProjectId(10L);
        entry.setEntryDate(java.time.LocalDate.now());
        BizMachineLedger ledger = new BizMachineLedger();
        ledger.setStatus("OUT_FIELD");
        when(ledgerMapper.lockById(eq(1L), eq(1L))).thenReturn(ledger);
        when(ledgerMapper.updateById(any())).thenReturn(1);

        machineEntryService.entryIn(entry);

        assertThat(entry.getEntryType()).isEqualTo("IN");
        assertThat(ledger.getStatus()).isEqualTo("IN_FIELD");
    }

    @Test
    @DisplayName("进场：IN_FIELD状态拒绝")
    void testEntryIn_inField_rejected() {
        com.zwinsight.common.config.SecurityContextHolder.setTenantId(1L);
        when(projectMapper.selectById(10L)).thenReturn(new com.zwinsight.project.domain.BizProject());
        BizMachineEntry entry = new BizMachineEntry();
        entry.setMachineId(1L);
        entry.setProjectId(10L);
        entry.setEntryDate(java.time.LocalDate.now());
        BizMachineLedger ledger = new BizMachineLedger();
        ledger.setStatus("IN_FIELD");
        when(ledgerMapper.lockById(eq(1L), eq(1L))).thenReturn(ledger);

        assertThatThrownBy(() -> machineEntryService.entryIn(entry))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已登记或已退场的机械可进场");
    }

    @Test
    @DisplayName("退场：IN_FIELD且工作量已全部结算可退场")
    void testEntryOut() {
        com.zwinsight.common.config.SecurityContextHolder.setTenantId(1L);
        when(projectMapper.selectById(10L)).thenReturn(new com.zwinsight.project.domain.BizProject());
        BizMachineEntry entry = new BizMachineEntry();
        entry.setMachineId(1L);
        entry.setProjectId(10L);
        entry.setEntryDate(java.time.LocalDate.now());
        BizMachineLedger ledger = new BizMachineLedger();
        ledger.setId(1L);
        ledger.setStatus("IN_FIELD");
        when(ledgerMapper.lockById(eq(1L), eq(1L))).thenReturn(ledger);
        // 无未结算工作量
        when(workLogMapper.selectCount(any())).thenReturn(0L);
        when(ledgerMapper.updateById(any())).thenReturn(1);

        machineEntryService.entryOut(entry);

        assertThat(entry.getEntryType()).isEqualTo("OUT");
    }

    @Test
    @DisplayName("退场：存在未结算工作量时拒绝")
    void testEntryOut_unsettledWorkLog_rejected() {
        com.zwinsight.common.config.SecurityContextHolder.setTenantId(1L);
        when(projectMapper.selectById(10L)).thenReturn(new com.zwinsight.project.domain.BizProject());
        BizMachineEntry entry = new BizMachineEntry();
        entry.setMachineId(1L);
        entry.setProjectId(10L);
        entry.setEntryDate(java.time.LocalDate.now());
        BizMachineLedger ledger = new BizMachineLedger();
        ledger.setStatus("IN_FIELD");
        when(ledgerMapper.lockById(eq(1L), eq(1L))).thenReturn(ledger);
        // 存在 2 条未结算工作量
        when(workLogMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> machineEntryService.entryOut(entry))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未结算的工作量记录");
        verify(entryMapper, never()).insert(any());
    }

    @Test
    @DisplayName("退场：REGISTERED状态拒绝")
    void testEntryOut_registered_rejected() {
        com.zwinsight.common.config.SecurityContextHolder.setTenantId(1L);
        when(projectMapper.selectById(10L)).thenReturn(new com.zwinsight.project.domain.BizProject());
        BizMachineEntry entry = new BizMachineEntry();
        entry.setMachineId(1L);
        entry.setProjectId(10L);
        entry.setEntryDate(java.time.LocalDate.now());
        BizMachineLedger ledger = new BizMachineLedger();
        ledger.setStatus("REGISTERED");
        when(ledgerMapper.lockById(eq(1L), eq(1L))).thenReturn(ledger);

        assertThatThrownBy(() -> machineEntryService.entryOut(entry))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅在场的机械可退场");
    }

    @Test
    @DisplayName("进退场：机械不存在抛异常")
    void testEntryIn_machineNotFound() {
        com.zwinsight.common.config.SecurityContextHolder.setTenantId(1L);
        when(projectMapper.selectById(10L)).thenReturn(new com.zwinsight.project.domain.BizProject());
        BizMachineEntry entry = new BizMachineEntry();
        entry.setMachineId(999L);
        entry.setProjectId(10L);
        entry.setEntryDate(java.time.LocalDate.now());
        when(ledgerMapper.lockById(eq(999L), eq(1L))).thenReturn(null);

        assertThatThrownBy(() -> machineEntryService.entryIn(entry))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("机械不存在");
    }
}
