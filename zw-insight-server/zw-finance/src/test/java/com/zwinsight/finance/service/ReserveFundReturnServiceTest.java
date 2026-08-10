package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizReserveFundApply;
import com.zwinsight.finance.domain.BizReserveFundReturn;
import com.zwinsight.finance.mapper.BizReserveFundApplyMapper;
import com.zwinsight.finance.mapper.BizReserveFundReturnMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReserveFundReturnService 单元测试（阶段四批 1 补测）
 * <p>备用金归还：待归还金额 = 申请金额 - 已归还 - 已冲抵，归还不得超额。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReserveFundReturnService — 备用金归还")
class ReserveFundReturnServiceTest {

    @Mock
    private BizReserveFundReturnMapper returnMapper;

    @Mock
    private BizReserveFundApplyMapper applyMapper;

    @InjectMocks
    private ReserveFundReturnService service;

    private BizReserveFundApply apply(String applyAmount, String returned, String offset) {
        BizReserveFundApply a = new BizReserveFundApply();
        a.setId(10L);
        a.setApplyAmount(applyAmount == null ? null : new BigDecimal(applyAmount));
        a.setReturnedAmount(returned == null ? null : new BigDecimal(returned));
        a.setOffsetAmount(offset == null ? null : new BigDecimal(offset));
        return a;
    }

    private BizReserveFundReturn fundReturn(String amount) {
        BizReserveFundReturn r = new BizReserveFundReturn();
        r.setReserveApplyId(10L);
        r.setReturnAmount(new BigDecimal(amount));
        return r;
    }

    @Test
    @DisplayName("save - 申请不存在抛异常且不落库")
    void save_applyNotFound() {
        when(applyMapper.selectById(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.save(fundReturn("100")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("备用金申请不存在");
        verify(returnMapper, never()).insert(any());
    }

    @Test
    @DisplayName("save - 归还金额超过待归还（申请1000-已还300-已冲抵200=500）被拒绝")
    void save_exceedsPending_rejected() {
        when(applyMapper.selectById(10L)).thenReturn(apply("1000", "300", "200"));

        assertThatThrownBy(() -> service.save(fundReturn("500.01")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能超过待归还金额");
        verify(returnMapper, never()).insert(any());
        verify(applyMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("save - 恰好等于待归还金额允许（边界）并累加已归还")
    void save_exactlyPending_allowed() {
        BizReserveFundApply a = apply("1000", "300", "200");
        when(applyMapper.selectById(10L)).thenReturn(a);

        service.save(fundReturn("500"));

        verify(returnMapper).insert(any());
        ArgumentCaptor<BizReserveFundApply> captor = ArgumentCaptor.forClass(BizReserveFundApply.class);
        verify(applyMapper).updateById(captor.capture());
        assertThat(captor.getValue().getReturnedAmount()).isEqualByComparingTo("800");
    }

    @Test
    @DisplayName("save - 申请侧金额字段为 null 时按 0 处理（待归还=申请金额）")
    void save_nullAmountsTreatedAsZero() {
        BizReserveFundApply a = apply("2000", null, null);
        when(applyMapper.selectById(10L)).thenReturn(a);

        service.save(fundReturn("2000"));

        ArgumentCaptor<BizReserveFundApply> captor = ArgumentCaptor.forClass(BizReserveFundApply.class);
        verify(applyMapper).updateById(captor.capture());
        assertThat(captor.getValue().getReturnedAmount()).isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("save - 申请金额为 null 时待归还为 0，任何正数归还均拒绝")
    void save_nullApplyAmount_pendingZero() {
        when(applyMapper.selectById(10L)).thenReturn(apply(null, null, null));

        assertThatThrownBy(() -> service.save(fundReturn("0.01")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能超过待归还金额");
    }
}
