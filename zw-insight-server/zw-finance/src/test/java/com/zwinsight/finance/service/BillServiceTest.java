package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizBill;
import com.zwinsight.finance.mapper.BizBillMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BillService 单元测试（54_V2026_52 票据台账）
 * <p>核心断言：贴现公式（面值×率×剩余天数/360）、状态机（仅 HELD 可流转）。</p>
 */
@ExtendWith(MockitoExtension.class)
class BillServiceTest {

    @Mock private BizBillMapper billMapper;

    @InjectMocks
    private BillService billService;

    private BizBill receivableBill() {
        BizBill bill = new BizBill();
        bill.setBillNo("BA-2026-001");
        bill.setDirection(BizBill.DIRECTION_RECEIVABLE);
        bill.setBillType("BANK_ACCEPTANCE");
        bill.setFaceAmount(new BigDecimal("1000000"));
        bill.setIssueDate(LocalDate.of(2026, 1, 1));
        bill.setDueDate(LocalDate.of(2026, 7, 1));
        bill.setDrawerName("某建设集团");
        bill.setPayeeName("我公司");
        return bill;
    }

    @Nested
    @DisplayName("register() 登记票据")
    class RegisterTests {

        @Test
        @DisplayName("正常路径 — 登记后状态置 HELD")
        void register_normalPath() {
            BizBill bill = receivableBill();

            billService.register(bill);

            assertThat(bill.getStatus()).isEqualTo(BizBill.STATUS_HELD);
            verify(billMapper).insert(bill);
        }

        @Test
        @DisplayName("异常路径 — 到期日不晚于出票日被拦截")
        void register_invalidDates_rejected() {
            BizBill bill = receivableBill();
            bill.setDueDate(LocalDate.of(2026, 1, 1));

            assertThatThrownBy(() -> billService.register(bill))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("到期日期必须晚于出票日期");
        }
    }

    @Nested
    @DisplayName("discount() 贴现（公式与边界）")
    class DiscountTests {

        @Test
        @DisplayName("正常路径 — 面值100万/年率4.8%/剩余90天：利息12000、净额988000")
        void discount_formulaComputed() {
            BizBill bill = receivableBill();
            bill.setId(1L);
            bill.setStatus(BizBill.STATUS_HELD);
            when(billMapper.selectById(1L)).thenReturn(bill);

            // 剩余天数：2026-04-02 → 2026-07-01 = 90 天
            billService.discount(1L, LocalDate.of(2026, 4, 2), new BigDecimal("0.048"));

            assertThat(bill.getStatus()).isEqualTo(BizBill.STATUS_DISCOUNTED);
            assertThat(bill.getDiscountInterest()).isEqualByComparingTo("12000.00");
            assertThat(bill.getDiscountNetAmount()).isEqualByComparingTo("988000.00");
            verify(billMapper).updateById(bill);
        }

        @Test
        @DisplayName("异常路径 — 贴现日期晚于到期日被拦截")
        void discount_afterDueDate_rejected() {
            BizBill bill = receivableBill();
            bill.setId(1L);
            bill.setStatus(BizBill.STATUS_HELD);
            when(billMapper.selectById(1L)).thenReturn(bill);

            assertThatThrownBy(() -> billService.discount(1L, LocalDate.of(2026, 7, 15), new BigDecimal("0.048")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("之间");
        }

        @Test
        @DisplayName("异常路径 — 应付票据不支持贴现")
        void discount_payable_rejected() {
            BizBill bill = receivableBill();
            bill.setId(1L);
            bill.setDirection(BizBill.DIRECTION_PAYABLE);
            bill.setStatus(BizBill.STATUS_HELD);
            when(billMapper.selectById(1L)).thenReturn(bill);

            assertThatThrownBy(() -> billService.discount(1L, LocalDate.of(2026, 4, 2), new BigDecimal("0.048")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅应收票据支持贴现");
        }
    }

    @Nested
    @DisplayName("endorse()/redeem()/payOut() 状态机")
    class StateMachineTests {

        @Test
        @DisplayName("正常路径 — 持有应收票据可背书转让")
        void endorse_normalPath() {
            BizBill bill = receivableBill();
            bill.setId(1L);
            bill.setStatus(BizBill.STATUS_HELD);
            when(billMapper.selectById(1L)).thenReturn(bill);

            billService.endorse(1L, "某供应商", LocalDate.of(2026, 3, 1));

            assertThat(bill.getStatus()).isEqualTo(BizBill.STATUS_ENDORSED);
            assertThat(bill.getEndorseeName()).isEqualTo("某供应商");
        }

        @Test
        @DisplayName("异常路径 — 已贴现票据不可再背书")
        void endorse_discounted_rejected() {
            BizBill bill = receivableBill();
            bill.setId(1L);
            bill.setStatus(BizBill.STATUS_DISCOUNTED);
            when(billMapper.selectById(1L)).thenReturn(bill);

            assertThatThrownBy(() -> billService.endorse(1L, "某供应商", null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅[持有]状态可背书");
        }

        @Test
        @DisplayName("异常路径 — 应收票据不可兑付（只有应付票据可 payOut）")
        void payOut_receivable_rejected() {
            BizBill bill = receivableBill();
            bill.setId(1L);
            bill.setStatus(BizBill.STATUS_HELD);
            when(billMapper.selectById(1L)).thenReturn(bill);

            assertThatThrownBy(() -> billService.payOut(1L, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅应付票据支持兑付");
        }
    }

    @Nested
    @DisplayName("statistics() 票据统计")
    class StatisticsTests {

        @Test
        @DisplayName("正常路径 — 持有应收/应付分别汇总，贴现取净额")
        void statistics_normalPath() {
            BizBill receivable = receivableBill();
            receivable.setStatus(BizBill.STATUS_HELD);
            BizBill payable = receivableBill();
            payable.setDirection(BizBill.DIRECTION_PAYABLE);
            payable.setFaceAmount(new BigDecimal("300000"));
            payable.setStatus(BizBill.STATUS_HELD);
            BizBill discounted = receivableBill();
            discounted.setStatus(BizBill.STATUS_DISCOUNTED);
            discounted.setDiscountNetAmount(new BigDecimal("988000"));
            when(billMapper.selectList(any())).thenReturn(List.of(receivable, payable, discounted));

            java.util.Map<String, Object> stats = billService.statistics(null);

            assertThat((BigDecimal) stats.get("receivableHeld")).isEqualByComparingTo("1000000");
            assertThat((BigDecimal) stats.get("payableHeld")).isEqualByComparingTo("300000");
            assertThat((BigDecimal) stats.get("discountedNet")).isEqualByComparingTo("988000");
        }
    }
}
