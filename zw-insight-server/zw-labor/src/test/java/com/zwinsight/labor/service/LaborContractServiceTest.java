package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.labor.domain.BizLaborContract;
import com.zwinsight.labor.mapper.BizLaborContractMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LaborContractService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class LaborContractServiceTest {

    @Mock private BizLaborContractMapper laborContractMapper;
    @Mock private BizBudgetDetailMapper budgetDetailMapper;

    @InjectMocks
    private LaborContractService laborContractService;

    @Test
    @DisplayName("保存合同：DRAFT 初始化 + 零累计字段")
    void testSave_draftWithZeroDefaults() {
        BizLaborContract contract = new BizLaborContract();
        contract.setProjectId(100L);
        contract.setContractAmount(new BigDecimal("200000"));
        when(laborContractMapper.insert(any(BizLaborContract.class))).thenReturn(1);

        laborContractService.save(contract);

        assertThat(contract.getStatus()).isEqualTo("DRAFT");
        assertThat(contract.getCumulativeSettlement()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(contract.getCumulativePaid()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("保存合同：金额超出预算抛异常")
    void testSave_exceedsBudget_throws() {
        BizLaborContract contract = new BizLaborContract();
        contract.setProjectId(100L);
        contract.setBudgetId(1L);
        contract.setContractAmount(new BigDecimal("500000"));

        BizBudgetDetail detail = new BizBudgetDetail();
        detail.setBudgetTotalPrice(new BigDecimal("300000"));
        when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(detail));
        when(laborContractMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> laborContractService.save(contract))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("劳务合同金额超出预算");
    }

    @Test
    @DisplayName("提交：DRAFT→EFFECTIVE")
    void testSubmit_draftToEffective() {
        BizLaborContract contract = new BizLaborContract();
        contract.setId(1L);
        contract.setStatus("DRAFT");
        when(laborContractMapper.selectById(1L)).thenReturn(contract);

        laborContractService.submit(1L);

        verify(laborContractMapper).updateById(argThat(c -> "EFFECTIVE".equals(c.getStatus())));
    }

    @Test
    @DisplayName("提交：非 DRAFT 拒绝")
    void testSubmit_nonDraftRejected() {
        BizLaborContract contract = new BizLaborContract();
        contract.setId(1L);
        contract.setStatus("EFFECTIVE");
        when(laborContractMapper.selectById(1L)).thenReturn(contract);

        assertThatThrownBy(() -> laborContractService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("更新：非 DRAFT 拒绝")
    void testUpdate_nonDraftRejected() {
        BizLaborContract existing = new BizLaborContract();
        existing.setId(1L);
        existing.setStatus("EFFECTIVE");
        when(laborContractMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> laborContractService.update(new BizLaborContract() {{ setId(1L); }}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可编辑");
    }

    @Test
    @DisplayName("删除：DRAFT 可删")
    void testDelete_draftAllowed() {
        BizLaborContract existing = new BizLaborContract();
        existing.setId(1L);
        existing.setStatus("DRAFT");
        when(laborContractMapper.selectById(1L)).thenReturn(existing);

        laborContractService.delete(1L);

        verify(laborContractMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除：非 DRAFT 拒绝")
    void testDelete_nonDraftRejected() {
        BizLaborContract existing = new BizLaborContract();
        existing.setId(1L);
        existing.setStatus("EFFECTIVE");
        when(laborContractMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> laborContractService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("查询详情：不存在抛异常")
    void testGetById_notFound() {
        when(laborContractMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> laborContractService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("劳务合同不存在");
    }
}
