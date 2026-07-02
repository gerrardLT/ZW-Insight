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
    @DisplayName("提交：发起审批流程 + 更新状态")
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

        assertThat(apply.getStatus()).isEqualTo("PAID");
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
}
