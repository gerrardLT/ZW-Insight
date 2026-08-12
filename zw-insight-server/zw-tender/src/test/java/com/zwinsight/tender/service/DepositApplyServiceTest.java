package com.zwinsight.tender.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.tender.domain.BizDepositApply;
import com.zwinsight.tender.mapper.BizDepositApplyMapper;
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

/**
 * DepositApplyService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class DepositApplyServiceTest {

    @Mock private BizDepositApplyMapper depositApplyMapper;
    @Mock private ApprovalService approvalService;

    @InjectMocks
    private DepositApplyService depositApplyService;

    @Test
    @DisplayName("新增保证金申请：状态初始化为 DRAFT")
    void testSave_initializesDraft() {
        BizDepositApply apply = new BizDepositApply();
        when(depositApplyMapper.insert(any(BizDepositApply.class))).thenReturn(1);

        depositApplyService.save(apply);

        assertThat(apply.getStatus()).isEqualTo("DRAFT");
        verify(depositApplyMapper).insert(apply);
    }

    @Test
    @DisplayName("删除：DRAFT 可删")
    void testDelete_draftAllowed() {
        BizDepositApply apply = new BizDepositApply();
        apply.setStatus("DRAFT");
        when(depositApplyMapper.selectById(1L)).thenReturn(apply);

        depositApplyService.delete(1L);

        verify(depositApplyMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除：非 DRAFT 拒绝")
    void testDelete_nonDraftRejected() {
        BizDepositApply apply = new BizDepositApply();
        apply.setStatus("PAID");
        when(depositApplyMapper.selectById(1L)).thenReturn(apply);

        assertThatThrownBy(() -> depositApplyService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("提交：发起审批流程 + 置 SUBMITTED 中间态（未等审批不得 PAID，2026-08-12 修复）")
    void testSubmit_startProcessAndUpdateStatus() {
        BizDepositApply apply = new BizDepositApply();
        apply.setId(1L);
        apply.setStatus("DRAFT");
        apply.setDepositAmount(new BigDecimal("10000"));
        apply.setProjectId(100L);

        when(depositApplyMapper.selectById(1L)).thenReturn(apply);
        when(approvalService.startProcess(eq("DEPOSIT_APPLY"), eq(1L),
                eq("deposit_apply_approval"), anyMap())).thenReturn("pi-001");

        depositApplyService.submit(1L);

        assertThat(apply.getStatus()).isEqualTo("SUBMITTED");
        assertThat(apply.getWorkflowInstanceId()).isEqualTo("pi-001");
        verify(depositApplyMapper).updateById(apply);
    }

    @Test
    @DisplayName("提交：不存在抛异常")
    void testSubmit_notFound() {
        when(depositApplyMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> depositApplyService.submit(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("保证金申请不存在");
    }

    @Test
    @DisplayName("提交：非 DRAFT 拒绝")
    void testSubmit_nonDraftRejected() {
        BizDepositApply apply = new BizDepositApply();
        apply.setId(1L);
        apply.setStatus("PAID");
        when(depositApplyMapper.selectById(1L)).thenReturn(apply);

        assertThatThrownBy(() -> depositApplyService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    // ============ 批次二 P1 修复钉住（2026-08-12） ============

    @Test
    @DisplayName("更新：仅 DRAFT 可编辑；不存在抛异常（原裸 updateById 致 PAID 金额可篡改）")
    void testUpdate_draftGuard() {
        BizDepositApply existing = new BizDepositApply();
        existing.setId(1L);
        existing.setStatus("PAID");
        when(depositApplyMapper.selectById(1L)).thenReturn(existing);
        assertThatThrownBy(() -> depositApplyService.update(existing))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可编辑");
        verify(depositApplyMapper, never()).updateById(any());

        when(depositApplyMapper.selectById(999L)).thenReturn(null);
        BizDepositApply missing = new BizDepositApply();
        missing.setId(999L);
        assertThatThrownBy(() -> depositApplyService.update(missing))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("保证金申请不存在");
    }

    @Test
    @DisplayName("更新：PUT 体携带 status 被置 null 不落库（防篡改）")
    void testUpdate_statusStripped() {
        BizDepositApply existing = new BizDepositApply();
        existing.setId(1L);
        existing.setStatus("DRAFT");
        when(depositApplyMapper.selectById(1L)).thenReturn(existing);

        BizDepositApply body = new BizDepositApply();
        body.setId(1L);
        body.setStatus("PAID");
        depositApplyService.update(body);

        assertThat(body.getStatus()).as("status 应被服务端置 null 防落库").isNull();
        verify(depositApplyMapper).updateById(body);
    }

    @Test
    @DisplayName("审批通过回调：SUBMITTED→PAID；非 SUBMITTED 幂等短路")
    void testOnApproved_transitionsAndIdempotent() {
        BizDepositApply submitted = new BizDepositApply();
        submitted.setId(1L);
        submitted.setStatus("SUBMITTED");
        when(depositApplyMapper.selectById(1L)).thenReturn(submitted);
        depositApplyService.onApproved(1L);
        assertThat(submitted.getStatus()).isEqualTo("PAID");

        BizDepositApply paid = new BizDepositApply();
        paid.setId(2L);
        paid.setStatus("PAID");
        when(depositApplyMapper.selectById(2L)).thenReturn(paid);
        depositApplyService.onApproved(2L);
        verify(depositApplyMapper, times(1)).updateById(any());

        depositApplyService.onApproved(999L);
        verify(depositApplyMapper, times(1)).updateById(any());
    }

    @Test
    @DisplayName("审批驳回回调：SUBMITTED→DRAFT；非 SUBMITTED 忽略（未付款无资金回冲）")
    void testOnRejected_backToDraft() {
        BizDepositApply submitted = new BizDepositApply();
        submitted.setId(1L);
        submitted.setStatus("SUBMITTED");
        when(depositApplyMapper.selectById(1L)).thenReturn(submitted);
        depositApplyService.onRejected(1L);
        assertThat(submitted.getStatus()).isEqualTo("DRAFT");

        BizDepositApply draft = new BizDepositApply();
        draft.setId(2L);
        draft.setStatus("DRAFT");
        when(depositApplyMapper.selectById(2L)).thenReturn(draft);
        depositApplyService.onRejected(2L);
        verify(depositApplyMapper, times(1)).updateById(any());
    }
}
