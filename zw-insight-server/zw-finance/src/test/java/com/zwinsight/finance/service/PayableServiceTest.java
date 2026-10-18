package com.zwinsight.finance.service;

import com.zwinsight.common.event.finance.ExpenseSettlementPayableEvent;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizPayable;
import com.zwinsight.finance.domain.BizPayableWriteOff;
import com.zwinsight.finance.mapper.BizPayableMapper;
import com.zwinsight.finance.mapper.BizPayableWriteOffMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PayableService 单元测试（V2026_115）
 * <p>覆盖：生成（正常/幂等跳过/非正跳过/负载不完整）、付款 FIFO 核销（部分核销/超上限截断/非法金额）、
 * 反冲（超额按实际可反冲额）、结算冲减（正常/超余额截断）。
 * 分层纪律：纯 Service 层行为（mapper 全 mock），真实事务/并发由 e2e-real 与 L4 覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class PayableServiceTest {

    @Mock private BizPayableMapper payableMapper;
    @Mock private BizPayableWriteOffMapper writeOffMapper;
    @Mock private BizProjectMapper projectMapper;

    private PayableService payableService;

    @BeforeEach
    void setUp() {
        payableService = new PayableService(payableMapper, writeOffMapper, projectMapper);
        // 纯单测无 Spring 上下文：@Value 不注入（默认 int=0），显式注入与生产默认一致的账期
        org.springframework.test.util.ReflectionTestUtils.setField(payableService, "defaultCreditDays", 30);
    }

    private ExpenseSettlementPayableEvent event(long sourceId, long contractId, String amount, boolean reversed) {
        ExpenseSettlementPayableEvent e = new ExpenseSettlementPayableEvent();
        e.setSourceType("SUBCONTRACT_SETTLEMENT");
        e.setSourceId(sourceId);
        e.setProjectId(10L);
        e.setContractId(contractId);
        e.setContractCategory("SUBCONTRACT");
        e.setAmount(amount == null ? null : new BigDecimal(amount));
        e.setReversed(reversed);
        return e;
    }

    // ==================== 生成 ====================

    @Test
    @DisplayName("生成：正常路径插入台账并同事务双写项目应付单值")
    void generate_normal_insertsAndDoubleWrites() {
        when(payableMapper.selectCount(any())).thenReturn(0L);
        when(payableMapper.insert(any(BizPayable.class))).thenReturn(1);

        BizPayable r = payableService.generateFromSettlement(event(501L, 11L, "80000.00", false));

        assertThat(r).isNotNull();
        assertThat(r.getPayableAmount()).isEqualByComparingTo("80000.00");
        assertThat(r.getStatus()).isEqualTo("OPEN");
        assertThat(r.getDueDate()).isEqualTo(LocalDate.now().plusDays(30));
        ArgumentCaptor<BizPayable> captor = ArgumentCaptor.forClass(BizPayable.class);
        verify(payableMapper).insert(captor.capture());
        assertThat(captor.getValue().getSourceType()).isEqualTo("SUBCONTRACT_SETTLEMENT");
        verify(projectMapper).addPayableAmount(10L, new BigDecimal("80000.00"));
    }

    @Test
    @DisplayName("生成：同来源已存在则幂等跳过（不重复入账）")
    void generate_duplicate_skips() {
        when(payableMapper.selectCount(any())).thenReturn(1L);

        BizPayable r = payableService.generateFromSettlement(event(501L, 11L, "80000.00", false));

        assertThat(r).isNull();
        verify(payableMapper, never()).insert(any(BizPayable.class));
        verify(projectMapper, never()).addPayableAmount(anyLong(), any());
    }

    @Test
    @DisplayName("生成：金额非正不伪造负债")
    void generate_nonPositiveAmount_skips() {
        when(payableMapper.selectCount(any())).thenReturn(0L);

        assertThat(payableService.generateFromSettlement(event(502L, 11L, "0.00", false))).isNull();
        assertThat(payableService.generateFromSettlement(event(503L, 11L, "-100.00", false))).isNull();
        verify(payableMapper, never()).insert(any(BizPayable.class));
    }

    @Test
    @DisplayName("生成：事件负载不完整抛业务异常（同步消费失败整体回滚的前提）")
    void generate_incompleteEvent_throws() {
        ExpenseSettlementPayableEvent bad = event(504L, 11L, "100.00", false);
        bad.setContractId(null);

        assertThatThrownBy(() -> payableService.generateFromSettlement(bad))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("负载不完整");
    }

    @Test
    @DisplayName("生成接口拒绝冲销事件（防误用：冲销必须走 reduceOnSettlementReverse）")
    void generate_reversedEvent_throws() {
        assertThatThrownBy(() -> payableService.generateFromSettlement(event(505L, 11L, "100.00", true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reduceOnSettlementReverse");
    }

    // ==================== 付款 FIFO 核销 ====================

    private BizPayable open(long id, String payable, String paid, String dueDate) {
        BizPayable p = new BizPayable();
        p.setId(id);
        p.setProjectId(10L);
        p.setContractId(11L);
        p.setPayableAmount(new BigDecimal(payable));
        p.setPaidAmount(new BigDecimal(paid));
        p.setDueDate(LocalDate.parse(dueDate));
        p.setStatus("OPEN");
        return p;
    }

    @Test
    @DisplayName("核销：按到期日 FIFO 部分核销两行并逐笔落明细，项目单值扣减合计")
    void writeOff_fifo_partialAcrossTwoRows() {
        BizPayable first = open(1L, "50000.00", "0", "2026-01-10");
        BizPayable second = open(2L, "60000.00", "10000.00", "2026-02-20");
        when(payableMapper.selectList(any())).thenReturn(List.of(first, second));

        BigDecimal written = payableService.writeOffForContract(901L, 10L, 11L, new BigDecimal("60000.00"));

        assertThat(written).isEqualByComparingTo("60000.00");
        verify(payableMapper).addPaidAmount(1L, new BigDecimal("50000.00"));
        verify(payableMapper).addPaidAmount(2L, new BigDecimal("10000.00"));
        verify(writeOffMapper, times(2)).insert(any(BizPayableWriteOff.class));
        verify(projectMapper).addPayableAmount(10L, new BigDecimal("60000.00").negate());
    }

    @Test
    @DisplayName("核销：付款超过 OPEN 余额合计按余额截断（预付事实由单据体现，不冲减台账）")
    void writeOff_exceedingOpen_cappedAtBalance() {
        when(payableMapper.selectList(any())).thenReturn(List.of(open(1L, "40000.00", "0", "2026-01-10")));

        BigDecimal written = payableService.writeOffForContract(902L, 10L, 11L, new BigDecimal("150000.00"));

        assertThat(written).isEqualByComparingTo("40000.00");
        verify(payableMapper).addPaidAmount(1L, new BigDecimal("40000.00"));
        verify(projectMapper).addPayableAmount(10L, new BigDecimal("40000.00").negate());
    }

    @Test
    @DisplayName("核销：金额非正拒绝（防零额/负额单据扰动台账）")
    void writeOff_nonPositive_throws() {
        assertThatThrownBy(() -> payableService.writeOffForContract(903L, 10L, 11L, BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("大于0");
        verify(payableMapper, never()).addPaidAmount(anyLong(), any());
    }

    // ==================== 反冲 ====================

    @Test
    @DisplayName("反冲：明细金额超过当前已核销时按实际可反冲额维护项目单值")
    void reverseWriteOff_overOffset_usesActualPaid() {
        BizPayable row = open(1L, "50000.00", "30000.00", "2026-01-10");
        when(payableMapper.selectById(1L)).thenReturn(row);
        BizPayableWriteOff detail = new BizPayableWriteOff();
        detail.setPayableId(1L);
        detail.setPaymentApplyId(901L);
        detail.setAmount(new BigDecimal("80000.00"));
        when(writeOffMapper.selectList(any())).thenReturn(List.of(detail));

        BigDecimal total = payableService.reverseWriteOff(901L);

        assertThat(total).isEqualByComparingTo("30000.00");
        verify(payableMapper).addPaidAmount(1L, new BigDecimal("80000.00").negate());
        verify(projectMapper).addPayableAmount(10L, new BigDecimal("30000.00"));
        verify(writeOffMapper).deleteById(detail.getId());
    }

    // ==================== 结算冲减 ====================

    @Test
    @DisplayName("结算删除冲减：按原额冲减并同步项目单值")
    void reduce_normal_reducesRowAndProject() {
        BizPayable row = open(1L, "80000.00", "20000.00", "2026-01-10");
        when(payableMapper.selectList(any())).thenReturn(List.of(row));

        BigDecimal reduced = payableService.reduceOnSettlementReverse(event(501L, 11L, "80000.00", true));

        assertThat(reduced).isEqualByComparingTo("60000.00");
        verify(payableMapper).reducePayableAmount(1L, new BigDecimal("80000.00"));
        verify(projectMapper).addPayableAmount(10L, new BigDecimal("60000.00").negate());
    }

    @Test
    @DisplayName("结算删除冲减：无对应台账行时幂等返回 0（结算先建后删的乱序容错）")
    void reduce_noRow_returnsZero() {
        when(payableMapper.selectList(any())).thenReturn(List.of());

        assertThat(payableService.reduceOnSettlementReverse(event(999L, 11L, "100.00", true)))
                .isEqualByComparingTo(BigDecimal.ZERO);
        verify(projectMapper, never()).addPayableAmount(anyLong(), any());
    }
}
