package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizPersonalReimbursement;
import com.zwinsight.finance.mapper.BizPersonalReimbursementMapper;
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
 * PersonalReimbursementService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class PersonalReimbursementServiceTest {

    @Mock
    private BizPersonalReimbursementMapper personalReimbursementMapper;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private PersonalReimbursementService service;

    private BizPersonalReimbursement reimbursement(Long id, String status) {
        BizPersonalReimbursement r = new BizPersonalReimbursement();
        r.setId(id);
        r.setStatus(status);
        r.setTotalAmount(new BigDecimal("300"));
        return r;
    }

    @Test
    @DisplayName("page - 分页透传")
    void page_delegates() {
        Page<BizPersonalReimbursement> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(reimbursement(1L, "DRAFT")));
        page.setTotal(1L);
        when(personalReimbursementMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BizPersonalReimbursement> result = service.page(1, 10);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("save - 置 DRAFT")
    void save_setsDraft() {
        BizPersonalReimbursement r = reimbursement(null, null);

        service.save(r);

        assertThat(r.getStatus()).isEqualTo("DRAFT");
        verify(personalReimbursementMapper).insert(r);
    }

    @Test
    @DisplayName("submit - 守卫：不存在/非草稿抛异常")
    void submit_guardCases_throws() {
        when(personalReimbursementMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(99L)).hasMessageContaining("个人报销不存在");

        when(personalReimbursementMapper.selectById(1L)).thenReturn(reimbursement(1L, "APPROVED"));
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("submit - 正常：启动流程置 APPROVED")
    void submit_success() {
        BizPersonalReimbursement r = reimbursement(1L, "DRAFT");
        when(personalReimbursementMapper.selectById(1L)).thenReturn(r);
        when(approvalService.startProcess(eq("PERSONAL_REIMBURSEMENT"), eq(1L),
                eq("personal_reimbursement_approval"), anyMap())).thenReturn("proc-1");

        service.submit(1L);

        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(r.getWorkflowInstanceId()).isEqualTo("proc-1");
        verify(personalReimbursementMapper).updateById(r);
    }

    @Test
    @DisplayName("submit - 报销金额负/零/null 拒绝（P0 FIN-PRB-04）")
    void submit_invalidAmount_rejected() {
        BizPersonalReimbursement neg = reimbursement(1L, "DRAFT");
        neg.setTotalAmount(new java.math.BigDecimal("-100"));
        when(personalReimbursementMapper.selectById(1L)).thenReturn(neg);
        assertThatThrownBy(() -> service.submit(1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("报销金额必须大于0");

        BizPersonalReimbursement nullAmount = reimbursement(2L, "DRAFT");
        nullAmount.setTotalAmount(null);
        when(personalReimbursementMapper.selectById(2L)).thenReturn(nullAmount);
        assertThatThrownBy(() -> service.submit(2L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("报销金额必须大于0");

        verify(personalReimbursementMapper, never()).updateById(any());
    }
}
