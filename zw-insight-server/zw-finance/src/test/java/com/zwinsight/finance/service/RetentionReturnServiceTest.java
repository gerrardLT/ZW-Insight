package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizRetentionMoney;
import com.zwinsight.finance.domain.BizRetentionReturn;
import com.zwinsight.finance.mapper.BizRetentionMoneyMapper;
import com.zwinsight.finance.mapper.BizRetentionReturnMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RetentionReturnService 单元测试
 * <p>质保金返还：提交置 SUBMITTED 并发起审批；金额上限校验在提交与审批通过时各做一次，
 * 审批通过回调 onApproved 才累计已返还、全部返还置 RETURNED 并清理预警 key。</p>
 */
@ExtendWith(MockitoExtension.class)
class RetentionReturnServiceTest {

    @Mock
    private BizRetentionReturnMapper retentionReturnMapper;

    @Mock
    private BizRetentionMoneyMapper retentionMoneyMapper;

    @Mock
    private ApprovalService approvalService;

    @Mock
    private com.zwinsight.finance.task.RetentionWarningTask retentionWarningTask;

    @InjectMocks
    private RetentionReturnService service;

    private BizRetentionReturn ret(Long id, String status, Long retentionId, String amount) {
        BizRetentionReturn r = new BizRetentionReturn();
        r.setId(id);
        r.setStatus(status);
        r.setRetentionId(retentionId);
        r.setReturnAmount(amount == null ? null : new BigDecimal(amount));
        return r;
    }

    private BizRetentionMoney money(String retentionAmount, String returnedAmount) {
        BizRetentionMoney m = new BizRetentionMoney();
        m.setId(5L);
        m.setRetentionAmount(new BigDecimal(retentionAmount));
        m.setReturnedAmount(returnedAmount == null ? null : new BigDecimal(returnedAmount));
        m.setStatus("RETAINED");
        return m;
    }

    @Test
    @DisplayName("save - 置 DRAFT")
    void save_setsDraft() {
        BizRetentionReturn r = ret(null, null, 5L, "1000");

        service.save(r);

        assertThat(r.getStatus()).isEqualTo("DRAFT");
        verify(retentionReturnMapper).insert(r);
    }

    @Test
    @DisplayName("submit - 守卫：不存在/非草稿非驳回/质保金记录不存在")
    void submit_guardCases_throws() {
        when(retentionReturnMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(99L)).hasMessageContaining("返还记录不存在");

        when(retentionReturnMapper.selectById(1L)).thenReturn(ret(1L, "APPROVED", 5L, "100"));
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("仅草稿或已驳回状态可提交");

        when(retentionReturnMapper.selectById(3L)).thenReturn(ret(3L, "SUBMITTED", 5L, "100"));
        assertThatThrownBy(() -> service.submit(3L)).hasMessageContaining("仅草稿或已驳回状态可提交");

        when(retentionReturnMapper.selectById(2L)).thenReturn(ret(2L, "DRAFT", 5L, "100"));
        when(retentionMoneyMapper.selectById(5L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(2L)).hasMessageContaining("关联质保金记录不存在");
    }

    @Test
    @DisplayName("submit - 返还金额超过剩余可返还金额抛异常（提交时即拦截）")
    void submit_exceedsMaxReturn_throws() {
        // 质保金 10000，已返还 8000 → 最多再返 2000
        when(retentionReturnMapper.selectById(1L)).thenReturn(ret(1L, "DRAFT", 5L, "3000"));
        when(retentionMoneyMapper.selectById(5L)).thenReturn(money("10000", "8000"));

        assertThatThrownBy(() -> service.submit(1L))
                .hasMessageContaining("返还金额不能超过剩余可返还金额");
        verify(approvalService, never()).startProcess(anyString(), any(), anyString(), anyMap());
    }

    @Test
    @DisplayName("submit - 正常：置 SUBMITTED，不回写质保金已返还金额")
    void submit_marksSubmittedWithoutWriteBack() {
        when(retentionReturnMapper.selectById(1L)).thenReturn(ret(1L, "DRAFT", 5L, "2000"));
        BizRetentionMoney m = money("10000", "3000");
        when(retentionMoneyMapper.selectById(5L)).thenReturn(m);
        when(approvalService.startProcess(eq("RETENTION_RETURN"), eq(1L),
                eq("retention_return_approval"), anyMap())).thenReturn("proc-1");

        service.submit(1L);

        assertThat(m.getReturnedAmount()).isEqualByComparingTo("3000"); // 提交不改余额
        verify(retentionMoneyMapper, never()).updateById(any());
        verify(retentionReturnMapper).updateById(argThat(r -> "SUBMITTED".equals(r.getStatus())));
    }

    @Test
    @DisplayName("submit - 已驳回可重新提交")
    void submit_fromRejected_allowed() {
        when(retentionReturnMapper.selectById(1L)).thenReturn(ret(1L, "REJECTED", 5L, "2000"));
        when(retentionMoneyMapper.selectById(5L)).thenReturn(money("10000", "3000"));
        when(approvalService.startProcess(anyString(), any(), anyString(), anyMap())).thenReturn("proc-2");

        service.submit(1L);

        verify(retentionReturnMapper).updateById(argThat(r -> "SUBMITTED".equals(r.getStatus())));
    }

    @Test
    @DisplayName("submit - 返还金额负/零/null 拒绝（P0 FIN-RTR-08）")
    void submit_invalidReturnAmount_rejected() {
        BizRetentionMoney m = money("10000", null);
        when(retentionMoneyMapper.selectById(5L)).thenReturn(m);

        when(retentionReturnMapper.selectById(1L)).thenReturn(ret(1L, "DRAFT", 5L, "-100"));
        assertThatThrownBy(() -> service.submit(1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("返还金额必须大于0");

        when(retentionReturnMapper.selectById(2L)).thenReturn(ret(2L, "DRAFT", 5L, null));
        assertThatThrownBy(() -> service.submit(2L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("返还金额必须大于0");

        verify(retentionReturnMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("onApproved - 部分返还：置 APPROVED，累计已返还增加，状态不变")
    void onApproved_partialReturn_updatesAccumulation() {
        when(retentionReturnMapper.selectById(1L)).thenReturn(ret(1L, "SUBMITTED", 5L, "2000"));
        BizRetentionMoney m = money("10000", "3000");
        when(retentionMoneyMapper.selectById(5L)).thenReturn(m);

        service.onApproved(1L);

        assertThat(m.getReturnedAmount()).isEqualByComparingTo("5000");
        assertThat(m.getStatus()).isEqualTo("RETAINED"); // 未全部返还
        verify(retentionReturnMapper).updateById(argThat(r -> "APPROVED".equals(r.getStatus())));
    }

    @Test
    @DisplayName("onApproved - 全部返还（含 returnedAmount 为 null 视 0）：标记 RETURNED 并联动清理预警 key（P0 FIN-RTR-07）")
    void onApproved_fullReturn_marksReturned() {
        when(retentionReturnMapper.selectById(1L)).thenReturn(ret(1L, "SUBMITTED", 5L, "10000"));
        BizRetentionMoney m = money("10000", null);
        m.setId(5L);
        when(retentionMoneyMapper.selectById(5L)).thenReturn(m);

        service.onApproved(1L);

        assertThat(m.getReturnedAmount()).isEqualByComparingTo("10000");
        assertThat(m.getStatus()).isEqualTo("RETURNED");
        // P0 联动断言：全额退还后清理预警去重 key
        verify(retentionWarningTask).onRetentionReturned(5L);
    }

    @Test
    @DisplayName("onApproved - 部分返还不触发预警 key 清理")
    void onApproved_partialReturn_noWarningCleanup() {
        when(retentionReturnMapper.selectById(1L)).thenReturn(ret(1L, "SUBMITTED", 5L, "3000"));
        BizRetentionMoney m = money("10000", null);
        m.setId(5L);
        when(retentionMoneyMapper.selectById(5L)).thenReturn(m);

        service.onApproved(1L);

        assertThat(m.getStatus()).isNotEqualTo("RETURNED");
        verify(retentionWarningTask, never()).onRetentionReturned(any());
    }

    @Test
    @DisplayName("onApproved - 幂等：已 APPROVED 直接返回，不重复累计")
    void onApproved_idempotent() {
        when(retentionReturnMapper.selectById(1L)).thenReturn(ret(1L, "APPROVED", 5L, "2000"));

        service.onApproved(1L);

        verify(retentionMoneyMapper, never()).selectById(any());
        verify(retentionMoneyMapper, never()).updateById(any());
        verify(retentionReturnMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("onApproved - 审批期间剩余可返还金额被其他单占用：拒绝生效并回滚")
    void onApproved_balanceChangedDuringApproval_rejected() {
        when(retentionReturnMapper.selectById(1L)).thenReturn(ret(1L, "SUBMITTED", 5L, "3000"));
        // 审批期间已返还 9000 → 仅剩 1000 < 3000
        BizRetentionMoney m = money("10000", "9000");
        when(retentionMoneyMapper.selectById(5L)).thenReturn(m);

        assertThatThrownBy(() -> service.onApproved(1L))
                .hasMessageContaining("返还金额不能超过剩余可返还金额");

        verify(retentionReturnMapper, never()).updateById(any());
        verify(retentionMoneyMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("onApproved - 记录不存在：跳过不报错")
    void onApproved_missingRecord_skips() {
        when(retentionReturnMapper.selectById(9L)).thenReturn(null);

        service.onApproved(9L);

        verify(retentionReturnMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("onRejected - SUBMITTED 置 REJECTED；非 SUBMITTED 不改动")
    void onRejected_setsRejectedOnlyFromSubmitted() {
        BizRetentionReturn submitted = ret(1L, "SUBMITTED", 5L, "1000");
        when(retentionReturnMapper.selectById(1L)).thenReturn(submitted);

        service.onRejected(1L);

        assertThat(submitted.getStatus()).isEqualTo("REJECTED");
        verify(retentionReturnMapper).updateById(submitted);

        BizRetentionReturn approved = ret(2L, "APPROVED", 5L, "1000");
        when(retentionReturnMapper.selectById(2L)).thenReturn(approved);

        service.onRejected(2L);

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        verify(retentionReturnMapper, never()).updateById(approved);
    }
}
