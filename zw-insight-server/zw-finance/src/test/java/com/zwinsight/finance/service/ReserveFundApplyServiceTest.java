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
 * <p>备用金申请：保存初始化返还/冲抵金额为 0，提交即审批。</p>
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
    @DisplayName("submit - 守卫：不存在/非草稿抛异常")
    void submit_guardCases_throws() {
        when(reserveFundApplyMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(99L)).hasMessageContaining("备用金申请不存在");

        when(reserveFundApplyMapper.selectById(1L)).thenReturn(apply(1L, "APPROVED"));
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("submit - 正常：启动流程置 APPROVED")
    void submit_success() {
        BizReserveFundApply a = apply(1L, "DRAFT");
        when(reserveFundApplyMapper.selectById(1L)).thenReturn(a);
        when(approvalService.startProcess(eq("RESERVE_FUND_APPLY"), eq(1L),
                eq("reserve_fund_apply_approval"), anyMap())).thenReturn("proc-1");

        service.submit(1L);

        assertThat(a.getStatus()).isEqualTo("APPROVED");
        assertThat(a.getWorkflowInstanceId()).isEqualTo("proc-1");
        verify(reserveFundApplyMapper).updateById(a);
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
