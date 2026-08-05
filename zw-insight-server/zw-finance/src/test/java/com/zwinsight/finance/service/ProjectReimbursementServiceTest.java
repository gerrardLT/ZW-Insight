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
 * <p>项目报销：提交即审批，冲抵备用金时累加 offsetAmount。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectReimbursementServiceTest {

    @Mock
    private BizProjectReimbursementMapper reimbursementMapper;

    @Mock
    private BizReserveFundApplyMapper reserveFundApplyMapper;

    @Mock
    private ApprovalService approvalService;

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
    @DisplayName("submit - 守卫：不存在/非草稿抛异常")
    void submit_guardCases_throws() {
        when(reimbursementMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(99L)).hasMessageContaining("报销记录不存在");

        when(reimbursementMapper.selectById(1L)).thenReturn(reimbursement(1L, "APPROVED", null, null, null));
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("submit - 不冲抵备用金：仅状态流转")
    void submit_noOffset_statusOnly() {
        BizProjectReimbursement r = reimbursement(1L, "DRAFT", 0, null, null);
        when(reimbursementMapper.selectById(1L)).thenReturn(r);
        when(approvalService.startProcess(eq("PROJECT_REIMBURSEMENT"), eq(1L),
                eq("project_reimbursement_approval"), anyMap())).thenReturn("proc-1");

        service.submit(1L);

        assertThat(r.getStatus()).isEqualTo("APPROVED");
        verify(reserveFundApplyMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("submit - 冲抵备用金：offsetAmount 累加（null 视 0）")
    void submit_withOffset_accumulatesReserveOffset() {
        BizProjectReimbursement r = reimbursement(1L, "DRAFT", 1, 50L, "300");
        when(reimbursementMapper.selectById(1L)).thenReturn(r);
        when(approvalService.startProcess(anyString(), any(), anyString(), anyMap())).thenReturn("proc-1");
        BizReserveFundApply reserve = new BizReserveFundApply();
        reserve.setId(50L);
        reserve.setOffsetAmount(new BigDecimal("200"));
        when(reserveFundApplyMapper.selectById(50L)).thenReturn(reserve);

        service.submit(1L);

        assertThat(reserve.getOffsetAmount()).isEqualByComparingTo("500"); // 200+300
        verify(reserveFundApplyMapper).updateById(reserve);
    }

    @Test
    @DisplayName("submit - 冲抵标记开启但备用金申请不存在：跳过不报错")
    void submit_withOffsetButReserveMissing_skips() {
        BizProjectReimbursement r = reimbursement(1L, "DRAFT", 1, 50L, "300");
        when(reimbursementMapper.selectById(1L)).thenReturn(r);
        when(approvalService.startProcess(anyString(), any(), anyString(), anyMap())).thenReturn("proc-1");
        when(reserveFundApplyMapper.selectById(50L)).thenReturn(null);

        service.submit(1L);

        assertThat(r.getStatus()).isEqualTo("APPROVED");
        verify(reserveFundApplyMapper, never()).updateById(any());
    }
}
