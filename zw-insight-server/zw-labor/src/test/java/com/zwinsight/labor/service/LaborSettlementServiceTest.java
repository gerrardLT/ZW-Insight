package com.zwinsight.labor.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.labor.domain.BizLaborContract;
import com.zwinsight.labor.domain.BizLaborSettlement;
import com.zwinsight.labor.mapper.BizLaborContractMapper;
import com.zwinsight.labor.mapper.BizLaborSettlementMapper;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * LaborSettlementService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class LaborSettlementServiceTest {

    @Mock private BizLaborSettlementMapper settlementMapper;
    @Mock private BizLaborContractMapper laborContractMapper;

    @InjectMocks
    private LaborSettlementService laborSettlementService;

    private BizLaborSettlement sampleSettlement;

    @BeforeEach
    void setUp() {
        sampleSettlement = new BizLaborSettlement();
        sampleSettlement.setId(1L);
        sampleSettlement.setContractId(10L);
        sampleSettlement.setProjectId(100L);
        sampleSettlement.setSettlementAmount(new BigDecimal("50000"));
        sampleSettlement.setStatus("DRAFT");
    }

    @Test
    @DisplayName("保存结算：状态初始化为 DRAFT")
    void testSave_draftInitialized() {
        BizLaborSettlement settlement = new BizLaborSettlement();
        when(settlementMapper.insert(any(BizLaborSettlement.class))).thenReturn(1);

        laborSettlementService.save(settlement);

        assertThat(settlement.getStatus()).isEqualTo("DRAFT");
        verify(settlementMapper).insert(settlement);
    }

    @Test
    @DisplayName("更新：DRAFT 可编辑")
    void testUpdate_draftAllowed() {
        when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);

        BizLaborSettlement update = new BizLaborSettlement();
        update.setId(1L);
        update.setSettlementAmount(new BigDecimal("60000"));
        laborSettlementService.update(update);

        verify(settlementMapper).updateById(update);
    }

    @Test
    @DisplayName("更新：非 DRAFT 拒绝")
    void testUpdate_nonDraftRejected() {
        sampleSettlement.setStatus("APPROVED");
        when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);

        BizLaborSettlement update = new BizLaborSettlement();
        update.setId(1L);

        assertThatThrownBy(() -> laborSettlementService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可编辑");
    }

    @Test
    @DisplayName("删除：DRAFT 可删")
    void testDelete_draftAllowed() {
        when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);

        laborSettlementService.delete(1L);

        verify(settlementMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除：非 DRAFT 拒绝")
    void testDelete_nonDraftRejected() {
        sampleSettlement.setStatus("APPROVED");
        when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);

        assertThatThrownBy(() -> laborSettlementService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("提交：状态变更 + 回写合同累计结算金额")
    void testSubmit_statusAndCumulativeUpdate() {
        BizLaborContract contract = new BizLaborContract();
        contract.setId(10L);
        contract.setCumulativeSettlement(new BigDecimal("100000"));
        when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);
        when(laborContractMapper.selectById(10L)).thenReturn(contract);

        laborSettlementService.submit(1L);

        assertThat(sampleSettlement.getStatus()).isEqualTo("APPROVED");
        verify(settlementMapper).updateById(sampleSettlement);
        // 100000 + 50000 = 150000
        verify(laborContractMapper).updateById(argThat(c ->
                c.getCumulativeSettlement().compareTo(new BigDecimal("150000")) == 0));
    }

    @Test
    @DisplayName("提交：合同不存在时跳过回写")
    void testSubmit_contractNotFound_skipsWriteback() {
        sampleSettlement.setContractId(999L);
        when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);
        when(laborContractMapper.selectById(999L)).thenReturn(null);

        laborSettlementService.submit(1L);

        verify(settlementMapper).updateById(sampleSettlement);
        verify(laborContractMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("提交：非 DRAFT 拒绝")
    void testSubmit_nonDraftRejected() {
        sampleSettlement.setStatus("APPROVED");
        when(settlementMapper.selectById(1L)).thenReturn(sampleSettlement);

        assertThatThrownBy(() -> laborSettlementService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("查询详情：不存在抛异常")
    void testGetById_notFound() {
        when(settlementMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> laborSettlementService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算记录不存在");
    }
}
