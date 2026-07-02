package com.zwinsight.machine.service;

import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.machine.domain.BizMachineContract;
import com.zwinsight.machine.mapper.BizMachineContractMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MachineContractServiceTest {

    @Mock private BizMachineContractMapper machineContractMapper;
    @Mock private BizBudgetDetailMapper budgetDetailMapper;

    private MachineContractService machineContractService;

    @BeforeEach
    void setUp() {
        machineContractService = new MachineContractService(machineContractMapper, budgetDetailMapper);
    }

    @Test
    @DisplayName("新增合同：无预算时默认DRAFT并初始化累计字段")
    void testSave_noBudget() {
        BizMachineContract contract = new BizMachineContract();
        contract.setContractAmount(new BigDecimal("50000"));

        machineContractService.save(contract);

        assertThat(contract.getStatus()).isEqualTo("DRAFT");
        assertThat(contract.getCumulativeSettlement()).isEqualTo(BigDecimal.ZERO);
        assertThat(contract.getCumulativePaid()).isEqualTo(BigDecimal.ZERO);
        verify(machineContractMapper).insert(contract);
    }

    @Test
    @DisplayName("新增合同：预算充足时正常保存")
    void testSave_budgetSufficient() {
        BizMachineContract contract = new BizMachineContract();
        contract.setProjectId(1L);
        contract.setBudgetId(100L);
        contract.setContractAmount(new BigDecimal("30000"));
        when(budgetDetailMapper.selectList(any())).thenReturn(List.of(mockBudgetDetail("80000")));
        when(machineContractMapper.selectList(any())).thenReturn(Collections.emptyList());

        machineContractService.save(contract);

        assertThat(contract.getStatus()).isEqualTo("DRAFT");
        verify(machineContractMapper).insert(contract);
    }

    @Test
    @DisplayName("新增合同：预算不足抛异常")
    void testSave_budgetExceeded() {
        BizMachineContract contract = new BizMachineContract();
        contract.setProjectId(1L);
        contract.setBudgetId(100L);
        contract.setContractAmount(new BigDecimal("60000"));
        lenient().when(budgetDetailMapper.selectList(any())).thenReturn(List.of(mockBudgetDetail("50000")));
        lenient().when(machineContractMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> machineContractService.save(contract))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超出预算");
    }

    @Test
    @DisplayName("提交：DRAFT→EFFECTIVE")
    void testSubmit() {
        BizMachineContract contract = new BizMachineContract();
        contract.setId(1L);
        contract.setStatus("DRAFT");
        when(machineContractMapper.selectById(anyLong())).thenReturn(contract);

        machineContractService.submit(1L);

        assertThat(contract.getStatus()).isEqualTo("EFFECTIVE");
    }

    @Test
    @DisplayName("提交：非DRAFT拒绝")
    void testSubmit_nonDraft() {
        BizMachineContract contract = new BizMachineContract();
        contract.setId(1L);
        contract.setStatus("EFFECTIVE");
        when(machineContractMapper.selectById(anyLong())).thenReturn(contract);

        assertThatThrownBy(() -> machineContractService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("删除：DRAFT可删")
    void testDelete_draftAllowed() {
        BizMachineContract contract = new BizMachineContract();
        contract.setId(1L);
        contract.setStatus("DRAFT");
        when(machineContractMapper.selectById(anyLong())).thenReturn(contract);

        machineContractService.delete(1L);

        verify(machineContractMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除：非DRAFT拒绝")
    void testDelete_nonDraft() {
        BizMachineContract contract = new BizMachineContract();
        contract.setId(1L);
        contract.setStatus("EFFECTIVE");
        when(machineContractMapper.selectById(anyLong())).thenReturn(contract);

        assertThatThrownBy(() -> machineContractService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("查询：不存在抛异常")
    void testGetById_notFound() {
        when(machineContractMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> machineContractService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("机械合同不存在");
    }

    private BizBudgetDetail mockBudgetDetail(String total) {
        BizBudgetDetail d = new BizBudgetDetail();
        d.setBudgetTotalPrice(new BigDecimal(total));
        return d;
    }
}
