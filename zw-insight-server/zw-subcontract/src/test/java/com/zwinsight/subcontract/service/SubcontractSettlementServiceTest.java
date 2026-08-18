package com.zwinsight.subcontract.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.subcontract.domain.BizSubcontract;
import com.zwinsight.subcontract.domain.BizSubcontractSettlement;
import com.zwinsight.subcontract.domain.BizSubcontractSettlementDetail;
import com.zwinsight.subcontract.dto.SubcontractSettlementCreateRequest;
import com.zwinsight.subcontract.dto.SubcontractSettlementDetailDTO;
import com.zwinsight.subcontract.mapper.BizSubcontractMapper;
import com.zwinsight.subcontract.mapper.BizSubcontractSettlementMapper;
import com.zwinsight.subcontract.mapper.SubcontractSettlementDetailMapper;
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

@ExtendWith(MockitoExtension.class)
class SubcontractSettlementServiceTest {

    @Mock private BizSubcontractSettlementMapper settlementMapper;
    @Mock private SubcontractSettlementDetailMapper detailMapper;
    @Mock private BizSubcontractMapper subcontractMapper;

    private SubcontractSettlementService subSettlementService;

    @BeforeEach
    void setUp() {
        subSettlementService = new SubcontractSettlementService(settlementMapper, detailMapper, subcontractMapper);
    }

    @Test
    @DisplayName("创建结算单：明细行金额=quantity×unitPrice并汇总")
    void testCreateSettlement() {
        SubcontractSettlementCreateRequest request = new SubcontractSettlementCreateRequest();
        request.setContractId(1L);
        request.setProjectId(10L);

        SubcontractSettlementDetailDTO item = new SubcontractSettlementDetailDTO();
        item.setItemName("人工费");
        item.setQuantity(new BigDecimal("100"));
        item.setUnitPrice(new BigDecimal("50.555")); // 100 * 50.555 = 5055.50
        request.setDetails(List.of(item));

        // D5 校验适配（2026-08-17）：合同存在且归属项目 10，与请求一致
        BizSubcontract contract = new BizSubcontract();
        contract.setId(1L);
        contract.setProjectId(10L);
        when(subcontractMapper.selectById(1L)).thenReturn(contract);

        // 模拟 MyBatis-Plus 自动填充 ID
        doAnswer(invocation -> {
            BizSubcontractSettlement s = invocation.getArgument(0);
            s.setId(1L);
            return 1;
        }).when(settlementMapper).insert(any(BizSubcontractSettlement.class));

        Long id = subSettlementService.createSettlement(request);

        assertThat(id).isNotNull();
        verify(settlementMapper).insert(any());
        verify(detailMapper).insert(any());
        verify(settlementMapper).updateById(any()); // 更新总金额
    }

    @Test
    @DisplayName("提交结算：DRAFT→APPROVED并回写合同累计结算")
    void testSubmit_writeBackContract() {
        BizSubcontractSettlement settlement = new BizSubcontractSettlement();
        settlement.setId(1L);
        settlement.setContractId(100L);
        settlement.setProjectId(10L);
        settlement.setStatus("DRAFT");
        settlement.setSettlementAmount(new BigDecimal("50000"));
        when(settlementMapper.selectById(anyLong())).thenReturn(settlement);

        BizSubcontract contract = new BizSubcontract();
        contract.setContractAmount(new BigDecimal("500000")); // 合同金额需大于累计结算
        contract.setCumulativeSettlement(new BigDecimal("30000"));
        when(subcontractMapper.selectById(anyLong())).thenReturn(contract);

        subSettlementService.submit(1L);

        assertThat(settlement.getStatus()).isEqualTo("APPROVED");
        assertThat(contract.getCumulativeSettlement()).isEqualTo(new BigDecimal("80000"));
        verify(subcontractMapper).updateById(contract);
    }

    @Test
    @DisplayName("提交结算：不再回写项目 totalExpense（统一为付款口径）")
    void testSubmit_noProjectExpenseWriteback() {
        BizSubcontractSettlement settlement = new BizSubcontractSettlement();
        settlement.setId(1L);
        settlement.setContractId(100L);
        settlement.setProjectId(10L);
        settlement.setStatus("DRAFT");
        settlement.setSettlementAmount(new BigDecimal("20000"));
        when(settlementMapper.selectById(anyLong())).thenReturn(settlement);

        BizSubcontract contract = new BizSubcontract();
        contract.setContractAmount(new BigDecimal("500000")); // 合同金额需大于累计结算
        contract.setCumulativeSettlement(new BigDecimal("10000"));
        when(subcontractMapper.selectById(anyLong())).thenReturn(contract);

        // 不再依赖 projectMapper：结算只回写合同累计，不触碰项目 totalExpense
        subSettlementService.submit(1L);

        assertThat(settlement.getStatus()).isEqualTo("APPROVED");
        assertThat(contract.getCumulativeSettlement()).isEqualTo(new BigDecimal("30000"));
        verify(subcontractMapper).updateById(contract);
    }

    @Test
    @DisplayName("提交结算：非DRAFT拒绝")
    void testSubmit_nonDraft() {
        BizSubcontractSettlement settlement = new BizSubcontractSettlement();
        settlement.setId(1L);
        settlement.setStatus("APPROVED");
        when(settlementMapper.selectById(anyLong())).thenReturn(settlement);

        assertThatThrownBy(() -> subSettlementService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("删除结算：DRAFT状态级联删明细")
    void testDelete_cascadeDetails() {
        BizSubcontractSettlement settlement = new BizSubcontractSettlement();
        settlement.setId(1L);
        settlement.setStatus("DRAFT");
        when(settlementMapper.selectById(anyLong())).thenReturn(settlement);

        subSettlementService.delete(1L);

        verify(detailMapper).delete(any());
        verify(settlementMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除结算：非DRAFT拒绝")
    void testDelete_nonDraft() {
        BizSubcontractSettlement settlement = new BizSubcontractSettlement();
        settlement.setId(1L);
        settlement.setStatus("APPROVED");
        when(settlementMapper.selectById(anyLong())).thenReturn(settlement);

        assertThatThrownBy(() -> subSettlementService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("删除结算：E2E_TEST_ 标记数据非DRAFT放行（E2eTestGuard）")
    void testDelete_e2eMarkerBypass() {
        BizSubcontractSettlement settlement = new BizSubcontractSettlement();
        settlement.setId(1L);
        settlement.setStatus("APPROVED");
        settlement.setRemark("E2E_TEST_1723900000000");
        when(settlementMapper.selectById(anyLong())).thenReturn(settlement);

        subSettlementService.delete(1L);

        verify(detailMapper).delete(any());
        verify(settlementMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除结算：主表无标记、明细 itemName 带 E2E_TEST_ 前缀非DRAFT放行")
    void testDelete_detailMarkerBypass() {
        // 主表仅 status/remark，真实场景标记落在明细 itemName
        BizSubcontractSettlement settlement = new BizSubcontractSettlement();
        settlement.setId(2L);
        settlement.setStatus("APPROVED");
        when(settlementMapper.selectById(anyLong())).thenReturn(settlement);
        BizSubcontractSettlementDetail detail = new BizSubcontractSettlementDetail();
        detail.setItemName("E2E_TEST_1723900000000_土方工程");
        when(detailMapper.selectList(any())).thenReturn(java.util.List.of(detail));

        subSettlementService.delete(2L);

        verify(settlementMapper).deleteById(2L);
    }

    @Test
    @DisplayName("查询：不存在抛异常")
    void testGetById_notFound() {
        when(settlementMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> subSettlementService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算记录不存在");
    }

    // ==================== 审计缺陷 D5 钉住：结算项目与合同归属一致性 ====================

    private SubcontractSettlementCreateRequest settlementRequest(Long contractId, Long projectId) {
        SubcontractSettlementCreateRequest request = new SubcontractSettlementCreateRequest();
        request.setContractId(contractId);
        request.setProjectId(projectId);
        SubcontractSettlementDetailDTO item = new SubcontractSettlementDetailDTO();
        item.setItemName("人工费");
        item.setQuantity(new BigDecimal("10"));
        item.setUnitPrice(new BigDecimal("100"));
        request.setDetails(List.of(item));
        return request;
    }

    @Test
    @DisplayName("创建结算：合同不存在拒绝（D5）")
    void testCreateSettlement_contractNotFound() {
        when(subcontractMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> subSettlementService.createSettlement(settlementRequest(99L, 10L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("关联分包合同不存在");
        verify(settlementMapper, never()).insert(any());
    }

    @Test
    @DisplayName("创建结算：项目与合同归属不一致拒绝（D5）")
    void testCreateSettlement_projectMismatch() {
        BizSubcontract contract = new BizSubcontract();
        contract.setId(1L);
        contract.setProjectId(10L);
        when(subcontractMapper.selectById(1L)).thenReturn(contract);

        assertThatThrownBy(() -> subSettlementService.createSettlement(settlementRequest(1L, 20L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算项目与分包合同所属项目不一致");
        verify(settlementMapper, never()).insert(any());
    }

    @Test
    @DisplayName("更新结算：项目与合同归属不一致拒绝（D5）")
    void testUpdateSettlement_projectMismatch() {
        BizSubcontractSettlement existing = new BizSubcontractSettlement();
        existing.setId(1L);
        existing.setStatus("DRAFT");
        when(settlementMapper.selectById(1L)).thenReturn(existing);

        BizSubcontract contract = new BizSubcontract();
        contract.setId(1L);
        contract.setProjectId(10L);
        when(subcontractMapper.selectById(1L)).thenReturn(contract);

        assertThatThrownBy(() -> subSettlementService.updateSettlement(1L, settlementRequest(1L, 20L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算项目与分包合同所属项目不一致");
        verify(settlementMapper, never()).updateById(any());
    }
}
