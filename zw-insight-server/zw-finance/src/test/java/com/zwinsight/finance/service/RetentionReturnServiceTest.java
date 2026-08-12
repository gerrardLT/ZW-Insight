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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RetentionReturnService 单元测试
 * <p>质保金返还：金额上限校验、累计已返还、全部返还标记 RETURNED。</p>
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
    @DisplayName("submit - 守卫：不存在/非草稿/质保金记录不存在")
    void submit_guardCases_throws() {
        when(retentionReturnMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(99L)).hasMessageContaining("返还记录不存在");

        when(retentionReturnMapper.selectById(1L)).thenReturn(ret(1L, "APPROVED", 5L, "100"));
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("仅草稿状态可提交");

        when(retentionReturnMapper.selectById(2L)).thenReturn(ret(2L, "DRAFT", 5L, "100"));
        when(retentionMoneyMapper.selectById(5L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(2L)).hasMessageContaining("关联质保金记录不存在");
    }

    @Test
    @DisplayName("submit - 返还金额超过剩余可返还金额抛异常")
    void submit_exceedsMaxReturn_throws() {
        // 质保金 10000，已返还 8000 → 最多再返 2000
        when(retentionReturnMapper.selectById(1L)).thenReturn(ret(1L, "DRAFT", 5L, "3000"));
        when(retentionMoneyMapper.selectById(5L)).thenReturn(money("10000", "8000"));

        assertThatThrownBy(() -> service.submit(1L))
                .hasMessageContaining("返还金额不能超过剩余可返还金额");
        verify(approvalService, never()).startProcess(anyString(), any(), anyString(), anyMap());
    }

    @Test
    @DisplayName("submit - 部分返还：累计已返还增加，状态不变")
    void submit_partialReturn_updatesAccumulation() {
        when(retentionReturnMapper.selectById(1L)).thenReturn(ret(1L, "DRAFT", 5L, "2000"));
        BizRetentionMoney m = money("10000", "3000");
        when(retentionMoneyMapper.selectById(5L)).thenReturn(m);
        when(approvalService.startProcess(eq("RETENTION_RETURN"), eq(1L),
                eq("retention_return_approval"), anyMap())).thenReturn("proc-1");

        service.submit(1L);

        assertThat(m.getReturnedAmount()).isEqualByComparingTo("5000");
        assertThat(m.getStatus()).isEqualTo("RETAINED"); // 未全部返还
        verify(retentionReturnMapper).updateById(argThat(r -> "APPROVED".equals(r.getStatus())));
    }

    @Test
    @DisplayName("submit - 全部返还（含 returnedAmount 为 null 视 0）：标记 RETURNED 并联动清理预警 key（P0 FIN-RTR-07）")
    void submit_fullReturn_marksReturned() {
        when(retentionReturnMapper.selectById(1L)).thenReturn(ret(1L, "DRAFT", 5L, "10000"));
        BizRetentionMoney m = money("10000", null);
        m.setId(5L);
        when(retentionMoneyMapper.selectById(5L)).thenReturn(m);
        when(approvalService.startProcess(anyString(), any(), anyString(), anyMap())).thenReturn("proc-1");

        service.submit(1L);

        assertThat(m.getReturnedAmount()).isEqualByComparingTo("10000");
        assertThat(m.getStatus()).isEqualTo("RETURNED");
        // P0 联动断言：全额退还后清理预警去重 key
        verify(retentionWarningTask).onRetentionReturned(5L);
    }

    @Test
    @DisplayName("submit - 部分返还不触发预警 key 清理")
    void submit_partialReturn_noWarningCleanup() {
        when(retentionReturnMapper.selectById(1L)).thenReturn(ret(1L, "DRAFT", 5L, "3000"));
        BizRetentionMoney m = money("10000", null);
        m.setId(5L);
        when(retentionMoneyMapper.selectById(5L)).thenReturn(m);
        when(approvalService.startProcess(anyString(), any(), anyString(), anyMap())).thenReturn("proc-1");

        service.submit(1L);

        assertThat(m.getStatus()).isNotEqualTo("RETURNED");
        verify(retentionWarningTask, never()).onRetentionReturned(any());
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
}
