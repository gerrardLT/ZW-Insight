package com.zwinsight.machine.service;

import com.zwinsight.common.exception.BusinessException;
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
class MachineLedgerServiceTest {

    @Mock private BizMachineLedgerMapper ledgerMapper;
    @Mock private BizMachineEntryMapper entryMapper;

    private MachineLedgerService machineLedgerService;

    @BeforeEach
    void setUp() {
        machineLedgerService = new MachineLedgerService(ledgerMapper, entryMapper);
    }

    @Test
    @DisplayName("新增台账：默认REGISTERED状态")
    void testSave() {
        BizMachineLedger ledger = new BizMachineLedger();
        ledger.setMachineName("挖掘机-A01");
        ledger.setMachineType("挖掘机");
        when(ledgerMapper.insert(any())).thenReturn(1);

        machineLedgerService.save(ledger);

        assertThat(ledger.getStatus()).isEqualTo("REGISTERED");
        verify(ledgerMapper).insert(ledger);
    }

    @Test
    @DisplayName("更新台账：正常更新")
    void testUpdate() {
        BizMachineLedger existing = new BizMachineLedger();
        existing.setId(1L);
        when(ledgerMapper.selectById(anyLong())).thenReturn(existing);

        BizMachineLedger update = new BizMachineLedger();
        update.setId(1L);
        update.setMachineName("挖掘机-B02");

        machineLedgerService.update(update);

        verify(ledgerMapper).updateById(update);
    }

    @Test
    @DisplayName("更新台账：不存在抛异常")
    void testUpdate_notFound() {
        when(ledgerMapper.selectById(anyLong())).thenReturn(null);

        BizMachineLedger ledger = new BizMachineLedger();
        ledger.setId(999L);

        assertThatThrownBy(() -> machineLedgerService.update(ledger))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("机械台账不存在");
    }

    @Test
    @DisplayName("删除台账：正常删除")
    void testDelete() {
        machineLedgerService.delete(1L);
        verify(ledgerMapper).deleteById(1L);
    }
}
