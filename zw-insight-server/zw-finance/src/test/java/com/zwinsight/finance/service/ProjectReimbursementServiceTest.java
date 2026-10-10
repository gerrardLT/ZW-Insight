package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizProjectReimbursement;
import com.zwinsight.finance.domain.BizReserveFundApply;
import com.zwinsight.finance.mapper.BizProjectReimbursementMapper;
import com.zwinsight.finance.mapper.BizReserveFundApplyMapper;
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
 * ProjectReimbursementService 单元测试
 * <p>项目报销：提交置 SUBMITTED 发起审批，审批通过回调 onApproved 才置 APPROVED 并冲抵备用金。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectReimbursementServiceTest {

    @Mock
    private BizProjectReimbursementMapper reimbursementMapper;

    @Mock
    private BizReserveFundApplyMapper reserveFundApplyMapper;

    @Mock
    private ApprovalService approvalService;

    @Mock
    private ReimbursementDetailService reimbursementDetailService;

    @InjectMocks
    private ProjectReimbursementService service;

    private BizProjectReimbursement reimbursement(Long id, String status, Integer offsetFlag, Long reserveId, String offsetAmount) {
        BizProjectReimbursement r = new BizProjectReimbursement();
        r.setId(id);
        r.setProjectId(1L);
        r.setStatus(status);
        r.setTotalAmount(new BigDecimal("1000"));
        r.setOffsetReserve(offsetFlag);
        r.setReserveApplyId(reserveId);
        r.setOffsetAmount(offsetAmount == null ? null : new BigDecimal(offsetAmount));
        return r;
    }

    @Test
    @DisplayName("page - 分页透传")
    void page_delegates() {
        Page<BizProjectReimbursement> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(reimbursement(1L, "DRAFT", null, null, null)));
        page.setTotal(1L);
        when(reimbursementMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BizProjectReimbursement> result = service.page(1, 10, 1L);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("save - 置 DRAFT")
    void save_setsDraft() {
        BizProjectReimbursement r = reimbursement(null, null, null, null, null);

        service.save(r);

        assertThat(r.getStatus()).isEqualTo("DRAFT");
        verify(reimbursementMapper).insert(r);
    }

    @Test
    @DisplayName("submit - 守卫：不存在/非草稿非驳回抛异常")
    void submit_guardCases_throws() {
        when(reimbursementMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(99L)).hasMessageContaining("报销记录不存在");

        when(reimbursementMapper.selectById(1L)).thenReturn(reimbursement(1L, "SUBMITTED", null, null, null));
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("仅草稿或已驳回状态可提交");

        when(reimbursementMapper.selectById(2L)).thenReturn(reimbursement(2L, "APPROVED", null, null, null));
        assertThatThrownBy(() -> service.submit(2L)).hasMessageContaining("仅草稿或已驳回状态可提交");
    }

    @Test
    @DisplayName("submit - 不冲抵备用金：状态置 SUBMITTED 而非 APPROVED（审批通过才生效）")
    void submit_noOffset_setsSubmitted() {
        BizProjectReimbursement r = reimbursement(1L, "DRAFT", 0, null, null);
        when(reimbursementMapper.selectById(1L)).thenReturn(r);
        when(approvalService.startProcess(eq("PROJECT_REIMBURSEMENT"), eq(1L),
                eq("project_reimbursement_approval"), anyMap())).thenReturn("proc-1");

        service.submit(1L);

        assertThat(r.getStatus()).isEqualTo("SUBMITTED");
        assertThat(r.getWorkflowInstanceId()).isEqualTo("proc-1");
        verify(reimbursementMapper).updateById(r);
        verify(reserveFundApplyMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("submit - 已驳回可重新提交")
    void submit_fromRejected_allowed() {
        BizProjectReimbursement r = reimbursement(1L, "REJECTED", 0, null, null);
        when(reimbursementMapper.selectById(1L)).thenReturn(r);
        when(approvalService.startProcess(anyString(), any(), anyString(), anyMap())).thenReturn("proc-2");

        service.submit(1L);

        assertThat(r.getStatus()).isEqualTo("SUBMITTED");
        assertThat(r.getWorkflowInstanceId()).isEqualTo("proc-2");
    }

    @Test
    @DisplayName("submit - 冲抵备用金：提交时只校验，不改备用金余额")
    void submit_withOffset_validatesOnly() {
        BizProjectReimbursement r = reimbursement(1L, "DRAFT", 1, 50L, "300");
        when(reimbursementMapper.selectById(1L)).thenReturn(r);
        when(approvalService.startProcess(anyString(), any(), anyString(), anyMap())).thenReturn("proc-1");
        BizReserveFundApply reserve = new BizReserveFundApply();
        reserve.setId(50L);
        // 申请 1000、已还 0、已冲抵 200 → 待冲抵余额 800 ≥ 本次冲抵 300
        reserve.setApplyAmount(new BigDecimal("1000"));
        reserve.setReturnedAmount(BigDecimal.ZERO);
        reserve.setOffsetAmount(new BigDecimal("200"));
        when(reserveFundApplyMapper.selectById(50L)).thenReturn(reserve);

        service.submit(1L);

        assertThat(r.getStatus()).isEqualTo("SUBMITTED");
        assertThat(reserve.getOffsetAmount()).isEqualByComparingTo("200"); // 提交不改余额
        verify(reserveFundApplyMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("submit - 冲抵额超备用金待冲抵余额拒绝（P0 FIN-PRJ-07）")
    void submit_offsetExceedsPending_rejected() {
        BizProjectReimbursement r = reimbursement(1L, "DRAFT", 1, 50L, "900");
        when(reimbursementMapper.selectById(1L)).thenReturn(r);
        // 申请 1000、已还 500、已冲抵 200 → 待冲抵余额 300 < 本次冲抵 900
        BizReserveFundApply reserve = new BizReserveFundApply();
        reserve.setId(50L);
        reserve.setApplyAmount(new BigDecimal("1000"));
        reserve.setReturnedAmount(new BigDecimal("500"));
        reserve.setOffsetAmount(new BigDecimal("200"));
        when(reserveFundApplyMapper.selectById(50L)).thenReturn(reserve);

        assertThatThrownBy(() -> service.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("冲抵金额超过备用金待冲抵余额");

        verify(approvalService, never()).startProcess(anyString(), any(), anyString(), anyMap());
        verify(reimbursementMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("submit - 冲抵额超报销额拒绝（P0 FIN-PRJ-07）")
    void submit_offsetExceedsReimbursement_rejected() {
        // 报销额 300，冲抵 500 > 300
        BizProjectReimbursement r = reimbursement(1L, "DRAFT", 1, 50L, "500");
        r.setTotalAmount(new BigDecimal("300"));
        when(reimbursementMapper.selectById(1L)).thenReturn(r);
        BizReserveFundApply reserve = new BizReserveFundApply();
        reserve.setId(50L);
        reserve.setApplyAmount(new BigDecimal("10000"));
        when(reserveFundApplyMapper.selectById(50L)).thenReturn(reserve);

        assertThatThrownBy(() -> service.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("冲抵金额不能超过报销金额");
    }

    @Test
    @DisplayName("submit - 报销金额负/零/null 拒绝（P0 FIN-PRJ-06）")
    void submit_invalidAmount_rejected() {
        BizProjectReimbursement neg = reimbursement(1L, "DRAFT", 0, null, null);
        neg.setTotalAmount(new BigDecimal("-100"));
        when(reimbursementMapper.selectById(1L)).thenReturn(neg);
        assertThatThrownBy(() -> service.submit(1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("报销金额必须大于0");

        BizProjectReimbursement nullAmount = reimbursement(2L, "DRAFT", 0, null, null);
        nullAmount.setTotalAmount(null);
        when(reimbursementMapper.selectById(2L)).thenReturn(nullAmount);
        assertThatThrownBy(() -> service.submit(2L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("报销金额必须大于0");

        verify(reimbursementMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("onApproved - 置 APPROVED 并累加备用金冲抵额（null 视 0）")
    void onApproved_accumulatesReserveOffset() {
        BizProjectReimbursement r = reimbursement(1L, "SUBMITTED", 1, 50L, "300");
        when(reimbursementMapper.selectById(1L)).thenReturn(r);
        BizReserveFundApply reserve = new BizReserveFundApply();
        reserve.setId(50L);
        reserve.setApplyAmount(new BigDecimal("1000"));
        reserve.setReturnedAmount(BigDecimal.ZERO);
        reserve.setOffsetAmount(new BigDecimal("200"));
        when(reserveFundApplyMapper.selectById(50L)).thenReturn(reserve);

        service.onApproved(1L);

        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(reserve.getOffsetAmount()).isEqualByComparingTo("500"); // 200+300
        verify(reserveFundApplyMapper).updateById(reserve);
        verify(reimbursementMapper).updateById(r);
    }

    @Test
    @DisplayName("onApproved - 幂等：已 APPROVED 直接返回，不重复冲抵")
    void onApproved_idempotent() {
        BizProjectReimbursement r = reimbursement(1L, "APPROVED", 1, 50L, "300");
        when(reimbursementMapper.selectById(1L)).thenReturn(r);

        service.onApproved(1L);

        verify(reserveFundApplyMapper, never()).selectById(any());
        verify(reserveFundApplyMapper, never()).updateById(any());
        verify(reimbursementMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("onApproved - 记录不存在：跳过不报错")
    void onApproved_missingRecord_skips() {
        when(reimbursementMapper.selectById(9L)).thenReturn(null);

        service.onApproved(9L);

        verify(reimbursementMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("onApproved - 冲抵标记开启但备用金申请不存在：仅置 APPROVED，跳过冲抵")
    void onApproved_withOffsetButReserveMissing_skips() {
        BizProjectReimbursement r = reimbursement(1L, "SUBMITTED", 1, 50L, "300");
        when(reimbursementMapper.selectById(1L)).thenReturn(r);
        when(reserveFundApplyMapper.selectById(50L)).thenReturn(null);

        service.onApproved(1L);

        assertThat(r.getStatus()).isEqualTo("APPROVED");
        verify(reserveFundApplyMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("onApproved - 审批期间待冲抵余额被占用：拒绝生效并回滚")
    void onApproved_offsetNoLongerAffordable_rejected() {
        BizProjectReimbursement r = reimbursement(1L, "SUBMITTED", 1, 50L, "300");
        when(reimbursementMapper.selectById(1L)).thenReturn(r);
        BizReserveFundApply reserve = new BizReserveFundApply();
        reserve.setId(50L);
        // 审批期间已归还 900 → 待冲抵余额仅剩 100 < 300
        reserve.setApplyAmount(new BigDecimal("1000"));
        reserve.setReturnedAmount(new BigDecimal("900"));
        reserve.setOffsetAmount(BigDecimal.ZERO);
        when(reserveFundApplyMapper.selectById(50L)).thenReturn(reserve);

        assertThatThrownBy(() -> service.onApproved(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("冲抵金额超过备用金待冲抵余额");

        assertThat(r.getStatus()).isEqualTo("SUBMITTED");
        verify(reserveFundApplyMapper, never()).updateById(any());
        verify(reimbursementMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("onRejected - SUBMITTED 置 REJECTED；非 SUBMITTED 不改动")
    void onRejected_setsRejectedOnlyFromSubmitted() {
        BizProjectReimbursement submitted = reimbursement(1L, "SUBMITTED", 0, null, null);
        when(reimbursementMapper.selectById(1L)).thenReturn(submitted);

        service.onRejected(1L);

        assertThat(submitted.getStatus()).isEqualTo("REJECTED");
        verify(reimbursementMapper).updateById(submitted);

        BizProjectReimbursement approved = reimbursement(2L, "APPROVED", 0, null, null);
        when(reimbursementMapper.selectById(2L)).thenReturn(approved);

        service.onRejected(2L);

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        verify(reimbursementMapper, never()).updateById(approved);
    }
}
