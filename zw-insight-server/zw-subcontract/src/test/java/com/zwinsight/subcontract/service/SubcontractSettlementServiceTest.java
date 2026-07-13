package com.zwinsight.subcontract.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.subcontract.domain.BizSubcontract;
import com.zwinsight.subcontract.domain.BizSubcontractSettlement;

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
    @Mock private BizProjectMapper projectMapper;

    private SubcontractSettlementService subSettlementService;

    @BeforeEach
    void setUp() {
        subSettlementService = new SubcontractSettlementService(settlementMapper, detailMapper, subcontractMapper, projectMapper);
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

        BizProject project = new BizProject();
        project.setId(10L);
        project.setTotalExpense(BigDecimal.ZERO);
        when(projectMapper.selectById(anyLong())).thenReturn(project);

        subSettlementService.submit(1L);

        assertThat(settlement.getStatus()).isEqualTo("APPROVED");
        assertThat(contract.getCumulativeSettlement()).isEqualTo(new BigDecimal("80000"));
        verify(subcontractMapper).updateById(contract);
    }

    @Test
    @DisplayName("提交结算：同时回写项目总支出")
    void testSubmit_writeBackProject() {
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

        BizProject project = new BizProject();
        project.setTotalExpense(new BigDecimal("100000"));
        when(projectMapper.selectById(anyLong())).thenReturn(project);

        subSettlementService.submit(1L);

        assertThat(project.getTotalExpense()).isEqualTo(new BigDecimal("120000"));
        verify(projectMapper).updateById(project);
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
    @DisplayName("查询：不存在抛异常")
    void testGetById_notFound() {
        when(settlementMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> subSettlementService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算记录不存在");
    }
}
