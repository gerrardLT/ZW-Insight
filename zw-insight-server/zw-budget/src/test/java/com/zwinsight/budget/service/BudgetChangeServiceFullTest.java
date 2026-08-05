package com.zwinsight.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.zwinsight.common.result.PageResult;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BudgetChangeService 补充测试（与 BudgetChangeServiceTest 互补）
 * <p>覆盖：查询/编辑/删除守卫、提交审批、已占用预算科目映射、变更轨迹、撤回正常分支。</p>
 */
@ExtendWith(MockitoExtension.class)
class BudgetChangeServiceFullTest {

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
    private BudgetChangeService service;

    private BizBudgetChange change(Long id, String status) {
        BizBudgetChange c = new BizBudgetChange();
        c.setId(id);
        c.setProjectId(1L);
        c.setBudgetId(2L);
        c.setStatus(status);
        c.setTotalAdjustAmount(new BigDecimal("100"));
        return c;
    }

    private BizBudgetChangeDetail detail(String category, String original, String adjust) {
        BizBudgetChangeDetail d = new BizBudgetChangeDetail();
        d.setChangeId(1L);
        d.setBudgetDetailId(5L);
        d.setCostCategory(category);
        d.setItemName("明细项");
        d.setOriginalAmount(new BigDecimal(original));
        d.setAdjustAmount(new BigDecimal(adjust));
        return d;
    }

    private BudgetChangeDTO dto(String original, String adjust) {
        BudgetChangeDTO dto = new BudgetChangeDTO();
        dto.setProjectId(1L);
        dto.setBudgetId(2L);
        dto.setChangeReason("调整原因");
        BudgetChangeDetailDTO d = new BudgetChangeDetailDTO();
        d.setBudgetDetailId(5L);
        d.setCostCategory("MATERIAL");
        d.setItemName("明细项");
        d.setOriginalAmount(new BigDecimal(original));
        d.setAdjustAmount(new BigDecimal(adjust));
        dto.setDetails(Collections.singletonList(d));
        return dto;
    }

    // ── 查询 ──────────────────────────────────

    @Test
    @DisplayName("page/getById/getDetailsByChangeId/getChangeTraceByProject - 查询透传与守卫")
    void query_variants() {
        Page<BizBudgetChange> page = new Page<>(1, 10);
        page.setRecords(Collections.emptyList());
        page.setTotal(0L);
        when(budgetChangeMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        PageResult<BizBudgetChange> result = service.page(1, 10, 1L, "DRAFT");
        assertThat(result.getRecords()).isEmpty();

        when(budgetChangeMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L)).hasMessageContaining("预算变更记录不存在");

        when(budgetChangeDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(detail("MATERIAL", "100", "50")));
        assertThat(service.getDetailsByChangeId(1L)).hasSize(1);

        when(budgetChangeMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(change(1L, "APPROVED")));
        assertThat(service.getChangeTraceByProject(1L)).hasSize(1);
    }

    // ── update / delete ──────────────────────────────────

    @Test
    @DisplayName("update - 不存在/非草稿抛异常；正常重算总额并重建明细")
    void update_variants() {
        when(budgetChangeMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.update(99L, dto("100", "10")))
                .hasMessageContaining("预算变更记录不存在");

        when(budgetChangeMapper.selectById(1L)).thenReturn(change(1L, "SUBMITTED"));
        assertThatThrownBy(() -> service.update(1L, dto("100", "10")))
                .hasMessageContaining("仅草稿状态可编辑");

        when(budgetChangeMapper.selectById(2L)).thenReturn(change(2L, "DRAFT"));
        service.update(2L, dto("100", "30"));
        verify(budgetChangeMapper).updateById(argThat(c ->
                c.getTotalAdjustAmount().compareTo(new BigDecimal("30")) == 0));
        verify(budgetChangeDetailMapper).delete(any(LambdaQueryWrapper.class));
        verify(budgetChangeDetailMapper).insert(argThat(d ->
                d.getAdjustedAmount().compareTo(new BigDecimal("130")) == 0));
    }

    @Test
    @DisplayName("delete - 不存在/非草稿抛异常；正常连同明细删除")
    void delete_variants() {
        when(budgetChangeMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(99L)).hasMessageContaining("预算变更记录不存在");

        when(budgetChangeMapper.selectById(1L)).thenReturn(change(1L, "APPROVED"));
        assertThatThrownBy(() -> service.delete(1L)).hasMessageContaining("仅草稿状态可删除");

        when(budgetChangeMapper.selectById(2L)).thenReturn(change(2L, "DRAFT"));
        service.delete(2L);
        verify(budgetChangeDetailMapper).delete(any(LambdaQueryWrapper.class));
        verify(budgetChangeMapper).deleteById(2L);
    }

    // ── calculateOccupiedBudget 科目映射 ──────────────────────────────────

    @Test
    @DisplayName("calculateOccupiedBudget - 四科目映射、null 入参与未知科目返回 0")
    void calculateOccupiedBudget_categoryMapping() {
        when(budgetOccupiedMapper.sumSubcontractAmount(1L)).thenReturn(new BigDecimal("10"));
        when(budgetOccupiedMapper.sumLaborContractAmount(1L)).thenReturn(new BigDecimal("20"));
        when(budgetOccupiedMapper.sumMachineContractAmount(1L)).thenReturn(new BigDecimal("30"));
        when(budgetOccupiedMapper.sumPurchaseContractAmount(1L)).thenReturn(new BigDecimal("40"));

        assertThat(service.calculateOccupiedBudget(1L, "SUBCONTRACT")).isEqualByComparingTo("10");
        assertThat(service.calculateOccupiedBudget(1L, "LABOR")).isEqualByComparingTo("20");
        assertThat(service.calculateOccupiedBudget(1L, "MACHINE")).isEqualByComparingTo("30");
        assertThat(service.calculateOccupiedBudget(1L, "MATERIAL")).isEqualByComparingTo("40");

        assertThat(service.calculateOccupiedBudget(null, "MATERIAL")).isEqualByComparingTo("0");
        assertThat(service.calculateOccupiedBudget(1L, null)).isEqualByComparingTo("0");
        assertThat(service.calculateOccupiedBudget(1L, "UNKNOWN")).isEqualByComparingTo("0");
    }

    // ── submit ──────────────────────────────────

    @Test
    @DisplayName("submit - 不存在/非草稿抛异常")
    void submit_guardCases_throws() {
        when(budgetChangeMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(99L)).hasMessageContaining("预算变更记录不存在");

        when(budgetChangeMapper.selectById(1L)).thenReturn(change(1L, "APPROVED"));
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("仅草稿状态可提交审批");
    }

    @Test
    @DisplayName("submit - 正常（调增明细无需占用校验）：启动流程置 SUBMITTED")
    void submit_success_increase() {
        when(budgetChangeMapper.selectById(1L)).thenReturn(change(1L, "DRAFT"));
        // 调增明细（adjust > 0），不触发占用校验
        when(budgetChangeDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(detail("MATERIAL", "100", "50")));
        when(approvalService.startProcess(eq("BUDGET_CHANGE"), eq(1L),
                eq("budget_change_approval"), anyMap())).thenReturn("proc-1");

        service.submit(1L);

        verify(approvalService).startProcess(eq("BUDGET_CHANGE"), eq(1L),
                eq("budget_change_approval"), anyMap());
        verify(budgetChangeMapper).updateById(argThat(c ->
                "SUBMITTED".equals(c.getStatus()) && "proc-1".equals(c.getWorkflowInstanceId())));
        verify(budgetOccupiedMapper, never()).sumPurchaseContractAmount(any());
    }

    // ── withdraw 正常分支 ──────────────────────────────────

    @Test
    @DisplayName("withdraw - SUBMITTED 正常撤回置 WITHDRAWN")
    void withdraw_success() {
        when(budgetChangeMapper.selectById(1L)).thenReturn(change(1L, "SUBMITTED"));

        service.withdraw(1L);

        verify(budgetChangeMapper).updateById(argThat(c -> "WITHDRAWN".equals(c.getStatus())));
    }

    @Test
    @DisplayName("validateBeforeSubmit - 变更记录不存在抛异常")
    void validateBeforeSubmit_notFound_throws() {
        when(budgetChangeMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.validateBeforeSubmit(99L))
                .hasMessageContaining("预算变更记录不存在");
    }
}
