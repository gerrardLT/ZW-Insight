package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizChangeVisa;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizChangeVisaMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ChangeVisaService 单元测试
 * <p>变更签证：提交置 SUBMITTED 发起审批，审批通过回调 onApproved 才置 APPROVED 并回写合同累计变更金额。</p>
 */
@ExtendWith(MockitoExtension.class)
class ChangeVisaServiceTest {

    @Mock
    private BizChangeVisaMapper changeVisaMapper;

    @Mock
    private BizConstructionContractMapper contractMapper;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private ChangeVisaService service;

    private BizChangeVisa visa(String status) {
        BizChangeVisa v = new BizChangeVisa();
        v.setId(1L);
        v.setProjectId(10L);
        v.setContractId(20L);
        v.setChangeAmount(new BigDecimal("8000"));
        v.setVisaDate(java.time.LocalDate.of(2026, 10, 15));
        v.setStatus(status);
        return v;
    }

    @Test
    void save_rejectsMissingDateAndNegativeRate() {
        BizChangeVisa v = visa(null);
        v.setVisaDate(null);
        assertThatThrownBy(() -> service.save(v)).hasMessageContaining("签证日期");
        v.setVisaDate(java.time.LocalDate.of(2026, 10, 15));
        v.setTaxRate(new BigDecimal("-1"));
        assertThatThrownBy(() -> service.save(v)).hasMessageContaining("税率");
    }

    @Test
    void taxAmount_preservesUnknownAndNegativeChange() {
        BizChangeVisa v = visa(null);
        assertThat(v.getTaxAmount()).isNull();
        v.setChangeAmount(new BigDecimal("-109"));
        v.setTaxRate(new BigDecimal("9"));
        assertThat(v.getTaxAmount()).isEqualByComparingTo("-9");
    }

    @Test
    @DisplayName("page - 分页透传")
    void page_delegates() {
        Page<BizChangeVisa> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(visa("DRAFT")));
        page.setTotal(1L);
        when(changeVisaMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BizChangeVisa> result = service.page(1, 10, 10L, 20L, "DESIGN");

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("save - 置 DRAFT")
    void save_setsDraft() {
        BizChangeVisa v = visa(null);

        service.save(v);

        assertThat(v.getStatus()).isEqualTo("DRAFT");
        verify(changeVisaMapper).insert(v);
    }

    @Test
    @DisplayName("submit - 守卫：不存在/非草稿非驳回抛异常")
    void submit_guardCases_throws() {
        when(changeVisaMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("变更签证不存在");

        when(changeVisaMapper.selectById(2L)).thenReturn(visa("APPROVED"));
        assertThatThrownBy(() -> service.submit(2L)).hasMessageContaining("仅草稿或已驳回状态可提交");

        when(changeVisaMapper.selectById(3L)).thenReturn(visa("SUBMITTED"));
        assertThatThrownBy(() -> service.submit(3L)).hasMessageContaining("仅草稿或已驳回状态可提交");
    }

    @Test
    @DisplayName("submit - 正常：置 SUBMITTED，不回写合同累计变更金额")
    void submit_marksSubmittedWithoutWriteBack() {
        BizChangeVisa v = visa("DRAFT");
        when(changeVisaMapper.selectById(1L)).thenReturn(v);
        when(approvalService.startProcess(eq("CHANGE_VISA"), eq(1L),
                eq("change_visa_approval"), anyMap())).thenReturn("proc-1");

        service.submit(1L);

        assertThat(v.getStatus()).isEqualTo("SUBMITTED");
        assertThat(v.getWorkflowInstanceId()).isEqualTo("proc-1");
        verify(contractMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("submit - 已驳回可重新提交")
    void submit_fromRejected_allowed() {
        BizChangeVisa v = visa("REJECTED");
        when(changeVisaMapper.selectById(1L)).thenReturn(v);
        when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap())).thenReturn("proc-2");

        service.submit(1L);

        assertThat(v.getStatus()).isEqualTo("SUBMITTED");
        assertThat(v.getWorkflowInstanceId()).isEqualTo("proc-2");
    }

    @Test
    @DisplayName("onApproved - 置 APPROVED + 合同累计变更金额累加（null 视为 0）")
    void onApproved_writesBackContract() {
        BizChangeVisa v = visa("SUBMITTED");
        when(changeVisaMapper.selectById(1L)).thenReturn(v);
        when(changeVisaMapper.approvePending(1L)).thenReturn(1);
        when(contractMapper.addCumulativeChangeAmount(20L, new BigDecimal("8000"))).thenReturn(1);

        service.onApproved(1L);

        verify(changeVisaMapper).transitionSubmitted(1L, "APPROVED");
        verify(contractMapper).addCumulativeChangeAmount(20L, new BigDecimal("8000"));
    }

    @Test
    @DisplayName("onApproved - 变更金额 null 时不抛 NPE 按 0 累加（P0 VIS-05）")
    void onApproved_nullChangeAmount_noNpe() {
        BizChangeVisa v = visa("SUBMITTED");
        v.setChangeAmount(null);
        when(changeVisaMapper.selectById(1L)).thenReturn(v);
        when(changeVisaMapper.approvePending(1L)).thenReturn(1);
        when(contractMapper.addCumulativeChangeAmount(20L, BigDecimal.ZERO)).thenReturn(1);

        service.onApproved(1L);

        verify(contractMapper).addCumulativeChangeAmount(20L, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("onApproved - 合同不存在时不回写但不报错")
    void onApproved_contractMissing_skipsWriteBack() {
        BizChangeVisa v = visa("SUBMITTED");
        when(changeVisaMapper.selectById(1L)).thenReturn(v);
        when(changeVisaMapper.approvePending(1L)).thenReturn(1);

        assertThatThrownBy(() -> service.onApproved(1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("合同不存在");
        verify(contractMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("onApproved - 幂等：已 APPROVED 直接返回，不重复累加合同累计变更金额")
    void onApproved_idempotent() {
        BizChangeVisa v = visa("APPROVED");
        when(changeVisaMapper.selectById(1L)).thenReturn(v);

        service.onApproved(1L);

        verify(contractMapper, never()).selectById(any());
        verify(contractMapper, never()).updateById(any());
        verify(changeVisaMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("退回发起人后REJECTED重审兼容")
    void onApproved_rejected_canApprove() {
        when(changeVisaMapper.selectById(1L)).thenReturn(visa("REJECTED"));
        when(changeVisaMapper.approvePending(1L)).thenReturn(1);
        when(contractMapper.addCumulativeChangeAmount(20L, new BigDecimal("8000"))).thenReturn(1);
        service.onApproved(1L);
        verify(contractMapper).addCumulativeChangeAmount(20L, new BigDecimal("8000"));
    }

    @Test
    @DisplayName("DRAFT陈旧审批不入账")
    void onApproved_draft_ignored() {
        when(changeVisaMapper.selectById(1L)).thenReturn(visa("DRAFT"));
        service.onApproved(1L);
        verify(changeVisaMapper, never()).approvePending(anyLong());
        verifyNoInteractions(contractMapper);
    }

    @Test
    @DisplayName("CAS后累计SQL异常传播，事务负责回滚")
    void onApproved_atomicWriteThrows_propagates() {
        when(changeVisaMapper.selectById(1L)).thenReturn(visa("SUBMITTED"));
        when(changeVisaMapper.approvePending(1L)).thenReturn(1);
        when(contractMapper.addCumulativeChangeAmount(20L, new BigDecimal("8000")))
                .thenThrow(new IllegalStateException("DB write failed"));
        assertThatThrownBy(() -> service.onApproved(1L)).isInstanceOf(IllegalStateException.class);
        var order = inOrder(changeVisaMapper, contractMapper);
        order.verify(changeVisaMapper).approvePending(1L);
        order.verify(contractMapper).addCumulativeChangeAmount(20L, new BigDecimal("8000"));
    }

    @Test
    @DisplayName("并发审批CAS失败不重复回写")
    void onApproved_lostCas_doesNotWrite() {
        when(changeVisaMapper.selectById(1L)).thenReturn(visa("SUBMITTED"));
        service.onApproved(1L);
        verify(contractMapper, never()).addCumulativeChangeAmount(anyLong(), any());
    }

    @Test
    @DisplayName("onApproved - 记录不存在：跳过不报错")
    void onApproved_missingRecord_skips() {
        when(changeVisaMapper.selectById(9L)).thenReturn(null);

        service.onApproved(9L);

        verify(changeVisaMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("onRejected - SUBMITTED 置 REJECTED；非 SUBMITTED 不改动")
    void onRejected_setsRejectedOnlyFromSubmitted() {
        BizChangeVisa submitted = visa("SUBMITTED");
        when(changeVisaMapper.selectById(1L)).thenReturn(submitted);
        when(changeVisaMapper.transitionSubmitted(1L, "REJECTED")).thenReturn(1);

        service.onRejected(1L);

        assertThat(submitted.getStatus()).isEqualTo("REJECTED");
        verify(changeVisaMapper).transitionSubmitted(1L, "REJECTED");

        BizChangeVisa approved = visa("APPROVED");
        when(changeVisaMapper.selectById(2L)).thenReturn(approved);

        service.onRejected(2L);

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        verify(changeVisaMapper, never()).updateById(approved);
    }
}
