package com.zwinsight.purchase.service;

import com.zwinsight.budget.service.BudgetControlService;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.purchase.domain.BizPurchaseContract;
import com.zwinsight.purchase.domain.BizPurchaseContractDetail;
import com.zwinsight.purchase.mapper.BizPurchaseContractDetailMapper;
import com.zwinsight.purchase.mapper.BizPurchaseContractMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseContractServiceTest {

    @Mock private BizPurchaseContractMapper purchaseContractMapper;
    @Mock private BizPurchaseContractDetailMapper detailMapper;
    @Mock private SerialNumberService serialNumberService;
    @Mock private BudgetControlService budgetControlService;
    @Mock private ApprovalService approvalService;

    @InjectMocks
    private PurchaseContractService purchaseContractService;

    @Test
    @DisplayName("新增采购合同：自动编号+初始化累计字段")
    void testSave() {
        BizPurchaseContract contract = new BizPurchaseContract();
        contract.setProjectId(1L);
        contract.setContractAmount(new BigDecimal("100000"));
        when(serialNumberService.generate("PURCHASE_CONTRACT")).thenReturn("PC-2026-001");

        purchaseContractService.save(contract);

        assertThat(contract.getContractCode()).isEqualTo("PC-2026-001");
        assertThat(contract.getStatus()).isEqualTo("DRAFT");
        assertThat(contract.getCumulativeInbound()).isEqualTo(BigDecimal.ZERO);
        assertThat(contract.getCumulativeSettlement()).isEqualTo(BigDecimal.ZERO);
        assertThat(contract.getCumulativePaid()).isEqualTo(BigDecimal.ZERO);
        assertThat(contract.getCumulativeInvoiceReceived()).isEqualTo(BigDecimal.ZERO);
        verify(budgetControlService).checkBudget(1L, "MATERIAL", new BigDecimal("100000"));
        verify(purchaseContractMapper).insert(contract);
    }

    @Test
    @DisplayName("提交审批：DRAFT→EFFECTIVE并发起审批")
    void testSubmit() {
        BizPurchaseContract contract = new BizPurchaseContract();
        contract.setId(1L);
        contract.setStatus("DRAFT");
        contract.setContractAmount(new BigDecimal("100000"));
        contract.setProjectId(10L);
        when(purchaseContractMapper.selectById(1L)).thenReturn(contract);
        when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap())).thenReturn("proc-123");

        purchaseContractService.submit(1L);

        assertThat(contract.getStatus()).isEqualTo("EFFECTIVE");
        assertThat(contract.getWorkflowInstanceId()).isEqualTo("proc-123");
    }

    @Test
    @DisplayName("提交审批：非DRAFT拒绝")
    void testSubmit_nonDraft() {
        BizPurchaseContract contract = new BizPurchaseContract();
        contract.setId(1L);
        contract.setStatus("EFFECTIVE");
        when(purchaseContractMapper.selectById(1L)).thenReturn(contract);

        assertThatThrownBy(() -> purchaseContractService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("删除：DRAFT可删")
    void testDelete_draft() {
        BizPurchaseContract contract = new BizPurchaseContract();
        contract.setId(1L);
        contract.setStatus("DRAFT");
        when(purchaseContractMapper.selectById(1L)).thenReturn(contract);

        purchaseContractService.delete(1L);

        verify(purchaseContractMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除：非DRAFT拒绝")
    void testDelete_nonDraft() {
        BizPurchaseContract contract = new BizPurchaseContract();
        contract.setId(1L);
        contract.setStatus("EFFECTIVE");
        when(purchaseContractMapper.selectById(1L)).thenReturn(contract);

        assertThatThrownBy(() -> purchaseContractService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("查询：不存在抛异常")
    void testGetById_notFound() {
        when(purchaseContractMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> purchaseContractService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购合同不存在");
    }
}
