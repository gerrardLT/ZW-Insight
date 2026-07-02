package com.zwinsight.machine.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.machine.domain.BizMachineEntry;
import com.zwinsight.machine.domain.BizMachineLedger;
import com.zwinsight.machine.mapper.BizMachineEntryMapper;
import com.zwinsight.machine.mapper.BizMachineLedgerMapper;
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

    private MachineEntryService machineEntryService;

    @BeforeEach
    void setUp() {
        machineEntryService = new MachineEntryService(entryMapper, ledgerMapper);
    }

    @Test
    @DisplayName("进场：REGISTERED状态可进场")
    void testEntryIn_registered() {
        BizMachineEntry entry = new BizMachineEntry();
        entry.setMachineId(1L);
        BizMachineLedger ledger = new BizMachineLedger();
        ledger.setStatus("REGISTERED");
        when(ledgerMapper.selectById(anyLong())).thenReturn(ledger);

        machineEntryService.entryIn(entry);

        assertThat(entry.getEntryType()).isEqualTo("IN");
        assertThat(ledger.getStatus()).isEqualTo("IN_FIELD");
        verify(entryMapper).insert(entry);
    }

    @Test
    @DisplayName("进场：OUT_FIELD状态可再次进场")
    void testEntryIn_outField() {
        BizMachineEntry entry = new BizMachineEntry();
        entry.setMachineId(1L);
        BizMachineLedger ledger = new BizMachineLedger();
        ledger.setStatus("OUT_FIELD");
        when(ledgerMapper.selectById(anyLong())).thenReturn(ledger);

        machineEntryService.entryIn(entry);

        assertThat(entry.getEntryType()).isEqualTo("IN");
        assertThat(ledger.getStatus()).isEqualTo("IN_FIELD");
    }

    @Test
    @DisplayName("进场：IN_FIELD状态拒绝")
    void testEntryIn_inField_rejected() {
        BizMachineEntry entry = new BizMachineEntry();
        entry.setMachineId(1L);
        BizMachineLedger ledger = new BizMachineLedger();
        ledger.setStatus("IN_FIELD");
        when(ledgerMapper.selectById(anyLong())).thenReturn(ledger);

        assertThatThrownBy(() -> machineEntryService.entryIn(entry))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已登记或已退场的机械可进场");
    }

    @Test
    @DisplayName("退场：IN_FIELD可退场")
    void testEntryOut() {
        BizMachineEntry entry = new BizMachineEntry();
        entry.setMachineId(1L);
        BizMachineLedger ledger = new BizMachineLedger();
        ledger.setStatus("IN_FIELD");
        when(ledgerMapper.selectById(anyLong())).thenReturn(ledger);

        machineEntryService.entryOut(entry);

        assertThat(entry.getEntryType()).isEqualTo("OUT");
        assertThat(ledger.getStatus()).isEqualTo("OUT_FIELD");
    }

    @Test
    @DisplayName("退场：REGISTERED状态拒绝")
    void testEntryOut_registered_rejected() {
        BizMachineEntry entry = new BizMachineEntry();
        entry.setMachineId(1L);
        BizMachineLedger ledger = new BizMachineLedger();
        ledger.setStatus("REGISTERED");
        when(ledgerMapper.selectById(anyLong())).thenReturn(ledger);

        assertThatThrownBy(() -> machineEntryService.entryOut(entry))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅在场的机械可退场");
    }

    @Test
    @DisplayName("进退场：机械不存在抛异常")
    void testEntryIn_machineNotFound() {
        BizMachineEntry entry = new BizMachineEntry();
        entry.setMachineId(999L);
        when(ledgerMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> machineEntryService.entryIn(entry))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("机械不存在");
    }
}
