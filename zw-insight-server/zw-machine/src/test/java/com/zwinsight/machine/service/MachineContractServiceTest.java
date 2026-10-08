package com.zwinsight.machine.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.machine.domain.BizMachineContract;
import com.zwinsight.machine.mapper.BizMachineContractMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MachineContractServiceTest {
    @Mock BizMachineContractMapper machineContractMapper;
    @Mock BizBudgetDetailMapper budgetDetailMapper;
    @Mock SerialNumberService serialNumberService;
    @Mock ApprovalService approvalService;
    @InjectMocks MachineContractService service;

    @BeforeEach void setup() {
        SecurityContextHolder.setTenantId(9999L);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizMachineContract.class);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clear(); }
    private BizMachineContract contract() {
        var c = new BizMachineContract();
        c.setId(1L); c.setTenantId(9999L); c.setProjectId(10L); c.setStatus("DRAFT");
        c.setContractAmount(new BigDecimal("10000")); c.setUnitPrice(new BigDecimal("100"));
        c.setRentalType("SHIFT"); c.setStartDate(LocalDate.of(2026, 1, 1)); c.setEndDate(LocalDate.of(2026, 12, 31));
        return c;
    }
    @Test void save_resetsUntrustedFields() {
        var c = contract(); c.setCumulativeSettlement(BigDecimal.TEN); c.setStatus("EFFECTIVE");
        when(serialNumberService.generate("MACHINE_CONTRACT")).thenReturn("T9JX001");
        service.save(c);
        assertThat(c.getId()).isNull(); assertThat(c.getStatus()).isEqualTo("DRAFT");
        assertThat(c.getCumulativeSettlement()).isZero(); assertThat(c.getContractCode()).isEqualTo("T9JX001");
        verify(machineContractMapper).insert(c);
    }
    @Test void save_unknownPricingRejected() {
        var c = contract(); c.setRentalType("unknown");
        assertThatThrownBy(() -> service.save(c)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(serialNumberService);
    }
    @Test void save_missingUnitPriceRejected() {
        var c = contract(); c.setUnitPrice(null);
        assertThatThrownBy(() -> service.save(c)).isInstanceOf(BusinessException.class);
    }
    @Test void submit_startsApprovalNotEffective() {
        var c = contract(); when(machineContractMapper.selectById(1L)).thenReturn(c);
        when(approvalService.startProcess(eq("MACHINE_CONTRACT"), eq(1L), eq("machine_contract_approval"), anyMap())).thenReturn("p1");
        when(machineContractMapper.update(isNull(), any())).thenReturn(1);
        service.submit(1L);
        assertThat(c.getStatus()).isEqualTo("DRAFT");
        verify(machineContractMapper).update(isNull(), any());
    }
    @Test void submit_crossTenantRejected() {
        var c = contract(); c.setTenantId(1L); when(machineContractMapper.selectById(1L)).thenReturn(c);
        assertThatThrownBy(() -> service.submit(1L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(approvalService);
    }
    @Test void update_usesWhitelistNotRequestEntity() {
        var original = contract(); var input = contract(); input.setStatus("EFFECTIVE"); input.setCumulativePaid(BigDecimal.TEN);
        when(machineContractMapper.selectById(1L)).thenReturn(original);
        when(machineContractMapper.update(isNull(), any())).thenReturn(1);
        service.update(input); verify(machineContractMapper, never()).updateById(any());
    }
    @Test void update_concurrentStateChangeRejected() {
        when(machineContractMapper.selectById(1L)).thenReturn(contract());
        assertThatThrownBy(() -> service.update(contract())).isInstanceOf(BusinessException.class);
    }
    @Test void getById_missingRejected() {
        assertThatThrownBy(() -> service.getById(1L)).isInstanceOf(BusinessException.class);
    }
    @Test void delete_draftAllowed() {
        when(machineContractMapper.selectById(1L)).thenReturn(contract());
        service.delete(1L); verify(machineContractMapper).deleteById(1L);
    }
    @Test void delete_effectiveRejected() {
        var c = contract(); c.setStatus("EFFECTIVE"); when(machineContractMapper.selectById(1L)).thenReturn(c);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BusinessException.class);
    }
}
