package com.zwinsight.machine.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.machine.domain.BizMachineRepair;
import com.zwinsight.machine.mapper.BizMachineRepairMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MachineRepairServiceTest {

    @Mock private BizMachineRepairMapper repairMapper;

    private MachineRepairService machineRepairService;

    @BeforeEach
    void setUp() {
        machineRepairService = new MachineRepairService(repairMapper);
    }

    @Test
    @DisplayName("报修：设置日期和REPORTED状态")
    void testReport() {
        BizMachineRepair repair = new BizMachineRepair();
        repair.setMachineId(1L);
        repair.setFaultDescription("液压管破裂");

        machineRepairService.report(repair);

        assertThat(repair.getReportDate()).isEqualTo(LocalDate.now());
        assertThat(repair.getRepairStatus()).isEqualTo("REPORTED");
        verify(repairMapper).insert(repair);
    }

    @Test
    @DisplayName("派工：REPORTED→DISPATCHED")
    void testDispatch() {
        BizMachineRepair repair = new BizMachineRepair();
        repair.setId(1L);
        repair.setRepairStatus("REPORTED");
        when(repairMapper.selectById(anyLong())).thenReturn(repair);

        machineRepairService.dispatch(1L, "李师傅");

        assertThat(repair.getRepairPerson()).isEqualTo("李师傅");
        assertThat(repair.getRepairStatus()).isEqualTo("DISPATCHED");
    }

    @Test
    @DisplayName("派工：非REPORTED拒绝")
    void testDispatch_nonReported() {
        BizMachineRepair repair = new BizMachineRepair();
        repair.setId(1L);
        repair.setRepairStatus("DISPATCHED");
        when(repairMapper.selectById(anyLong())).thenReturn(repair);

        assertThatThrownBy(() -> machineRepairService.dispatch(1L, "李师傅"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已报修状态可派工");
    }

    @Test
    @DisplayName("完成：DISPATCHED→COMPLETED")
    void testComplete() {
        BizMachineRepair repair = new BizMachineRepair();
        repair.setId(1L);
        repair.setRepairStatus("DISPATCHED");
        when(repairMapper.selectById(anyLong())).thenReturn(repair);

        BizMachineRepair update = new BizMachineRepair();
        update.setRepairCost(new BigDecimal("5000"));

        machineRepairService.complete(1L, update);

        assertThat(repair.getRepairStatus()).isEqualTo("COMPLETED");
        assertThat(repair.getRepairCost()).isEqualTo(new BigDecimal("5000"));
        assertThat(repair.getRepairDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("完成：REPORTED状态拒绝")
    void testComplete_reported_rejected() {
        BizMachineRepair repair = new BizMachineRepair();
        repair.setId(1L);
        repair.setRepairStatus("REPORTED");
        when(repairMapper.selectById(anyLong())).thenReturn(repair);

        assertThatThrownBy(() -> machineRepairService.complete(1L, new BizMachineRepair()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已派工或维修中状态可完成");
    }

    @Test
    @DisplayName("派工：记录不存在抛异常")
    void testDispatch_notFound() {
        when(repairMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> machineRepairService.dispatch(999L, "李师傅"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("维修记录不存在");
    }
}
