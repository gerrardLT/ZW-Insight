package com.zwinsight.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizBudgetChange;
import com.zwinsight.budget.domain.BizBudgetChangeDetail;
import com.zwinsight.budget.dto.BudgetChangeDTO;
import com.zwinsight.budget.dto.BudgetChangeDetailDTO;
import com.zwinsight.budget.mapper.BizBudgetChangeDetailMapper;
import com.zwinsight.budget.mapper.BizBudgetChangeMapper;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.mapper.BizBudgetMapper;
import com.zwinsight.budget.mapper.BudgetOccupiedMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BudgetChangeService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class BudgetChangeServiceTest {

    @Mock
    private BizBudgetChangeMapper budgetChangeMapper;

    @Mock
    private BizBudgetChangeDetailMapper budgetChangeDetailMapper;

    @Mock
    private BizBudgetDetailMapper budgetDetailMapper;

    @Mock
    private BizBudgetMapper budgetMapper;

    @Mock
    private BudgetOccupiedMapper budgetOccupiedMapper;

    @Mock
    private ApprovalService approvalService;

    @Mock
    private BizProjectMapper projectMapper;

    @InjectMocks
    private BudgetChangeService budgetChangeService;

    private BizBudgetChange sampleChange;

    @BeforeEach
    void setUp() {
        sampleChange = new BizBudgetChange();
        sampleChange.setId(1L);
        sampleChange.setProjectId(100L);
        sampleChange.setBudgetId(200L);
        sampleChange.setChangeReason("成本调整");
        sampleChange.setStatus("DRAFT");
        sampleChange.setTotalAdjustAmount(new BigDecimal("-50000"));
    }

    // =====================================================================
    // 1. testValidateBeforeSubmit_rejectWhenAdjustedLessThanOccupied
    // =====================================================================

    @Test
    @DisplayName("提交前校验：调减时调整后金额 < 已占用预算，应抛出异常")
    void testValidateBeforeSubmit_rejectWhenAdjustedLessThanOccupied() {
        // given: 变更单存在
        when(budgetChangeMapper.selectById(1L)).thenReturn(sampleChange);

        // given: 变更明细 - 分包费从 100000 调减 60000（调整后 = 40000）
        BizBudgetChangeDetail detail = new BizBudgetChangeDetail();
        detail.setId(10L);
        detail.setChangeId(1L);
        detail.setBudgetDetailId(301L);
        detail.setCostCategory("SUBCONTRACT");
        detail.setItemName("土建分包");
        detail.setOriginalAmount(new BigDecimal("100000"));
        detail.setAdjustAmount(new BigDecimal("-60000"));
        detail.setAdjustedAmount(new BigDecimal("40000"));

        when(budgetChangeDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(detail));

        // given: 已占用预算 = 50000（> 调整后的 40000）
        when(budgetOccupiedMapper.sumSubcontractAmount(100L))
                .thenReturn(new BigDecimal("50000"));

        // when & then: 校验应抛出 BusinessException
        assertThatThrownBy(() -> budgetChangeService.validateBeforeSubmit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("预算余额不足以支撑调减");
    }

    // =====================================================================
    // 2. testOnApproved_updatesCorrectly
    // =====================================================================

    @Test
    @DisplayName("审批通过后：状态更新为APPROVED，明细回写预算，汇总回写项目预算金额")
    void testOnApproved_updatesCorrectly() {
        // given: 变更单状态为 SUBMITTED
        sampleChange.setStatus("SUBMITTED");
        when(budgetChangeMapper.selectById(1L)).thenReturn(sampleChange);

        // given: 两条变更明细
        BizBudgetChangeDetail detail1 = new BizBudgetChangeDetail();
        detail1.setId(10L);
        detail1.setChangeId(1L);
        detail1.setBudgetDetailId(301L);
        detail1.setAdjustAmount(new BigDecimal("30000"));

        BizBudgetChangeDetail detail2 = new BizBudgetChangeDetail();
        detail2.setId(11L);
        detail2.setChangeId(1L);
        detail2.setBudgetDetailId(302L);
        detail2.setAdjustAmount(new BigDecimal("-10000"));

        when(budgetChangeDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(detail1, detail2));

        // given: 回写后的汇总新总额
        BigDecimal newTotal = new BigDecimal("520000");
        when(budgetDetailMapper.sumBudgetTotalPriceByBudgetId(200L)).thenReturn(newTotal);

        // when
        budgetChangeService.onApproved(1L);

        // then: 状态更新为 APPROVED
        verify(budgetChangeMapper).updateById(argThat(change ->
                "APPROVED".equals(change.getStatus())));

        // then: 每条明细逐科目回写
        verify(budgetDetailMapper).addBudgetTotalPrice(301L, new BigDecimal("30000"));
        verify(budgetDetailMapper).addBudgetTotalPrice(302L, new BigDecimal("-10000"));

        // then: 汇总预算总额回写至项目
        verify(budgetDetailMapper).sumBudgetTotalPriceByBudgetId(200L);
        verify(projectMapper).updateBudgetAmount(100L, newTotal);
    }

    // =====================================================================
    // 3. testOnRejected_statusChangeOnly
    // =====================================================================

    @Test
    @DisplayName("审批驳回：仅更新状态为REJECTED，不修改原预算数据")
    void testOnRejected_statusChangeOnly() {
        // given
        sampleChange.setStatus("SUBMITTED");
        when(budgetChangeMapper.selectById(1L)).thenReturn(sampleChange);

        // when
        budgetChangeService.onRejected(1L);

        // then: 状态更新为 REJECTED
        verify(budgetChangeMapper).updateById(argThat(change ->
                "REJECTED".equals(change.getStatus())));

        // then: 不应调用任何预算修改相关的方法
        verifyNoInteractions(budgetDetailMapper);
        verifyNoInteractions(projectMapper);
        verifyNoInteractions(budgetOccupiedMapper);
    }

    // =====================================================================
    // 4. testWithdraw_rejectWhenNotSubmitted
    // =====================================================================

    @Test
    @DisplayName("撤回操作：非SUBMITTED状态拒绝撤回")
    void testWithdraw_rejectWhenNotSubmitted() {
        // given: 变更单状态为 DRAFT（非 SUBMITTED）
        sampleChange.setStatus("DRAFT");
        when(budgetChangeMapper.selectById(1L)).thenReturn(sampleChange);

        // when & then: 应抛出异常
        assertThatThrownBy(() -> budgetChangeService.withdraw(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已提交状态可撤回");

        // then: 不应执行任何更新操作
        verify(budgetChangeMapper, never()).updateById(any());
    }

    // =====================================================================
    // 5. testSave_calculatesCorrectTotalAmount
    // =====================================================================

    @Test
    @DisplayName("创建变更单：totalAdjustAmount = SUM(details.adjustAmount)")
    void testSave_calculatesCorrectTotalAmount() {
        // given: 构造 DTO，含 3 条明细
        BudgetChangeDTO dto = new BudgetChangeDTO();
        dto.setProjectId(100L);
        dto.setBudgetId(200L);
        dto.setChangeReason("工程量变化调整");

        BudgetChangeDetailDTO d1 = new BudgetChangeDetailDTO();
        d1.setBudgetDetailId(301L);
        d1.setCostCategory("MATERIAL");
        d1.setCostSubcategory("钢材");
        d1.setItemName("钢材采购");
        d1.setOriginalAmount(new BigDecimal("200000"));
        d1.setAdjustAmount(new BigDecimal("50000"));

        BudgetChangeDetailDTO d2 = new BudgetChangeDetailDTO();
        d2.setBudgetDetailId(302L);
        d2.setCostCategory("LABOR");
        d2.setCostSubcategory("木工");
        d2.setItemName("木工劳务");
        d2.setOriginalAmount(new BigDecimal("150000"));
        d2.setAdjustAmount(new BigDecimal("-20000"));

        BudgetChangeDetailDTO d3 = new BudgetChangeDetailDTO();
        d3.setBudgetDetailId(303L);
        d3.setCostCategory("MACHINE");
        d3.setCostSubcategory("塔吊");
        d3.setItemName("塔吊租赁");
        d3.setOriginalAmount(new BigDecimal("80000"));
        d3.setAdjustAmount(new BigDecimal("10000"));

        dto.setDetails(Arrays.asList(d1, d2, d3));

        // given: insert 返回成功
        when(budgetChangeMapper.insert(any(BizBudgetChange.class))).thenReturn(1);
        when(budgetChangeDetailMapper.insert(any(BizBudgetChangeDetail.class))).thenReturn(1);

        // when
        budgetChangeService.save(dto);

        // then: 主表 totalAdjustAmount = 50000 + (-20000) + 10000 = 40000
        verify(budgetChangeMapper).insert(argThat(change -> {
            BigDecimal expectedTotal = new BigDecimal("40000");
            return expectedTotal.compareTo(change.getTotalAdjustAmount()) == 0
                    && "DRAFT".equals(change.getStatus())
                    && change.getProjectId().equals(100L)
                    && change.getBudgetId().equals(200L);
        }));

        // then: 明细应插入 3 条，且 adjustedAmount = originalAmount + adjustAmount
        verify(budgetChangeDetailMapper, times(3)).insert(any(BizBudgetChangeDetail.class));

        // 验证第一条明细的 adjustedAmount = 200000 + 50000 = 250000
        verify(budgetChangeDetailMapper).insert(argThat(detail ->
                detail.getCostCategory() != null
                        && "MATERIAL".equals(detail.getCostCategory())
                        && new BigDecimal("250000").compareTo(detail.getAdjustedAmount()) == 0
        ));
    }
}
