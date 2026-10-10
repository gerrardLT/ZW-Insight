package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizReserveFundApply;
import com.zwinsight.finance.mapper.BizReserveFundApplyMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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
 * ReserveFundApplyService 单元测试
 * <p>备用金申请：保存初始化返还/冲抵金额为 0；提交置 SUBMITTED 发起审批，审批通过回调 onApproved 才置 APPROVED。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReserveFundApplyServiceTest {

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper.getSqlSegment() 断言需要实体 TableInfo
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizReserveFundApply.class);
    }

    @Mock
    private BizReserveFundApplyMapper reserveFundApplyMapper;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private ReserveFundApplyService service;

    private BizReserveFundApply apply(Long id, String status) {
        BizReserveFundApply a = new BizReserveFundApply();
        a.setId(id);
        a.setProjectId(1L);
        a.setStatus(status);
        a.setApplyAmount(new BigDecimal("5000"));
        return a;
    }

    @Test
    @DisplayName("page - 分页透传")
    void page_delegates() {
        Page<BizReserveFundApply> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(apply(1L, "DRAFT")));
        page.setTotal(1L);
        when(reserveFundApplyMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BizReserveFundApply> result = service.page(1, 10, 1L, null);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("page - status 过滤（移动端备用金归还拉 APPROVED 未还清申请）")
    void page_withStatusFilter() {
        Page<BizReserveFundApply> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(apply(2L, "APPROVED")));
        page.setTotal(1L);
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<BizReserveFundApply>> captor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        when(reserveFundApplyMapper.selectPage(any(Page.class), captor.capture())).thenReturn(page);

        PageResult<BizReserveFundApply> result = service.page(1, 10, null, "APPROVED");

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getStatus()).isEqualTo("APPROVED");
        // 强断言：wrapper 确实携带了 status 条件与参数值（删掉过滤条件本用例必须变红）
        LambdaQueryWrapper<BizReserveFundApply> wrapper = captor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("status");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("APPROVED");
    }

    @Test
    @DisplayName("page - status 空串/null 不拼条件")
    void page_blankStatusOmitsCondition() {
        Page<BizReserveFundApply> page = new Page<>(1, 10);
        page.setRecords(Collections.emptyList());
        page.setTotal(0L);
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<BizReserveFundApply>> captor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        when(reserveFundApplyMapper.selectPage(any(Page.class), captor.capture())).thenReturn(page);

        service.page(1, 10, null, "");

        assertThat(captor.getValue().getParamNameValuePairs().values()).doesNotContain("");
    }

    @Test
    @DisplayName("save - 置 DRAFT，null 的返还/冲抵金额初始化为 0，已有值保留")
    void save_initializesAmounts() {
        BizReserveFundApply a = apply(null, null);
        a.setReturnedAmount(null);
        a.setOffsetAmount(new BigDecimal("10"));

        service.save(a);

        assertThat(a.getStatus()).isEqualTo("DRAFT");
        assertThat(a.getReturnedAmount()).isEqualByComparingTo("0");
        assertThat(a.getOffsetAmount()).isEqualByComparingTo("10");
        verify(reserveFundApplyMapper).insert(a);
    }

    @Test
    @DisplayName("submit - 守卫：不存在/非草稿非驳回抛异常")
    void submit_guardCases_throws() {
        when(reserveFundApplyMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(99L)).hasMessageContaining("备用金申请不存在");

        when(reserveFundApplyMapper.selectById(1L)).thenReturn(apply(1L, "APPROVED"));
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("仅草稿或已驳回状态可提交");

        when(reserveFundApplyMapper.selectById(2L)).thenReturn(apply(2L, "SUBMITTED"));
        assertThatThrownBy(() -> service.submit(2L)).hasMessageContaining("仅草稿或已驳回状态可提交");
    }

    @Test
    @DisplayName("submit - 正常：启动流程置 SUBMITTED（审批通过才 APPROVED）")
    void submit_success() {
        BizReserveFundApply a = apply(1L, "DRAFT");
        when(reserveFundApplyMapper.selectById(1L)).thenReturn(a);
        when(approvalService.startProcess(eq("RESERVE_FUND_APPLY"), eq(1L),
                eq("reserve_fund_apply_approval"), anyMap())).thenReturn("proc-1");

        service.submit(1L);

        assertThat(a.getStatus()).isEqualTo("SUBMITTED");
        assertThat(a.getWorkflowInstanceId()).isEqualTo("proc-1");
        verify(reserveFundApplyMapper).updateById(a);
    }

    @Test
    @DisplayName("submit - 已驳回可重新提交")
    void submit_fromRejected_allowed() {
        BizReserveFundApply a = apply(1L, "REJECTED");
        when(reserveFundApplyMapper.selectById(1L)).thenReturn(a);
        when(approvalService.startProcess(anyString(), any(), anyString(), anyMap())).thenReturn("proc-2");

        service.submit(1L);

        assertThat(a.getStatus()).isEqualTo("SUBMITTED");
        assertThat(a.getWorkflowInstanceId()).isEqualTo("proc-2");
    }

    @Test
    @DisplayName("onApproved - SUBMITTED 置 APPROVED；已 APPROVED 幂等跳过")
    void onApproved_approvesAndIsIdempotent() {
        BizReserveFundApply a = apply(1L, "SUBMITTED");
        when(reserveFundApplyMapper.selectById(1L)).thenReturn(a);

        service.onApproved(1L);

        assertThat(a.getStatus()).isEqualTo("APPROVED");
        verify(reserveFundApplyMapper).updateById(a);

        BizReserveFundApply already = apply(2L, "APPROVED");
        when(reserveFundApplyMapper.selectById(2L)).thenReturn(already);

        service.onApproved(2L);

        verify(reserveFundApplyMapper, never()).updateById(already);
    }

    @Test
    @DisplayName("onApproved - 记录不存在：跳过不报错")
    void onApproved_missingRecord_skips() {
        when(reserveFundApplyMapper.selectById(9L)).thenReturn(null);

        service.onApproved(9L);

        verify(reserveFundApplyMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("onRejected - SUBMITTED 置 REJECTED；非 SUBMITTED 不改动")
    void onRejected_setsRejectedOnlyFromSubmitted() {
        BizReserveFundApply submitted = apply(1L, "SUBMITTED");
        when(reserveFundApplyMapper.selectById(1L)).thenReturn(submitted);

        service.onRejected(1L);

        assertThat(submitted.getStatus()).isEqualTo("REJECTED");
        verify(reserveFundApplyMapper).updateById(submitted);

        BizReserveFundApply approved = apply(2L, "APPROVED");
        when(reserveFundApplyMapper.selectById(2L)).thenReturn(approved);

        service.onRejected(2L);

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        verify(reserveFundApplyMapper, never()).updateById(approved);
    }

    @Test
    @DisplayName("save - 申请金额负/零/null 拒绝（P0 FIN-RFA-04）")
    void save_invalidAmount_rejected() {
        BizReserveFundApply neg = apply(1L, "DRAFT");
        neg.setApplyAmount(new java.math.BigDecimal("-500"));
        assertThatThrownBy(() -> service.save(neg))
                .isInstanceOf(BusinessException.class).hasMessageContaining("备用金申请金额必须大于0");

        BizReserveFundApply zero = apply(2L, "DRAFT");
        zero.setApplyAmount(java.math.BigDecimal.ZERO);
        assertThatThrownBy(() -> service.save(zero))
                .isInstanceOf(BusinessException.class).hasMessageContaining("备用金申请金额必须大于0");

        BizReserveFundApply nullAmount = apply(3L, "DRAFT");
        nullAmount.setApplyAmount(null);
        assertThatThrownBy(() -> service.save(nullAmount))
                .isInstanceOf(BusinessException.class).hasMessageContaining("备用金申请金额必须大于0");

        verify(reserveFundApplyMapper, never()).insert(any());
    }
}
