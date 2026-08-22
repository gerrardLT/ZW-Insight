package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizInvoiceApply;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.domain.BizPaymentReceived;
import com.zwinsight.finance.mapper.BizInvoiceApplyMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.BizPaymentReceivedMapper;
import com.zwinsight.finance.vo.CollectionRateVO;
import com.zwinsight.finance.vo.FundPlanItemVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * FinanceStatisticsService 单元测试
 * <p>回款率分析与资金计划：聚合逻辑、比例计算、异常路径。</p>
 */
@ExtendWith(MockitoExtension.class)
class FinanceStatisticsServiceTest {

    @Mock
    private BizPaymentReceivedMapper paymentReceivedMapper;

    @Mock
    private BizInvoiceApplyMapper invoiceApplyMapper;

    @Mock
    private BizPaymentApplyMapper paymentApplyMapper;

    @InjectMocks
    private FinanceStatisticsService service;

    private BizPaymentReceived received(String amount) {
        BizPaymentReceived r = new BizPaymentReceived();
        r.setProjectId(1L);
        r.setStatus("APPROVED");
        r.setReceiveDate(LocalDate.of(2026, 5, 10));
        r.setReceiveAmount(new BigDecimal(amount));
        return r;
    }

    private BizInvoiceApply invoice(String amount) {
        BizInvoiceApply i = new BizInvoiceApply();
        i.setProjectId(1L);
        i.setStatus("APPROVED");
        i.setInvoiceAmount(new BigDecimal(amount));
        return i;
    }

    private BizPaymentApply apply(String paymentDate, String amount) {
        BizPaymentApply a = new BizPaymentApply();
        a.setProjectId(1L);
        a.setStatus("APPROVED");
        a.setPaymentDate(LocalDate.parse(paymentDate));
        a.setPaymentAmount(new BigDecimal(amount));
        return a;
    }

    @Nested
    @DisplayName("回款率分析")
    class CollectionRate {

        @Test
        @DisplayName("回款率与未回款金额计算")
        void computes_rate() {
            when(paymentReceivedMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(received("60000"), received("20000")));
            when(invoiceApplyMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(invoice("100000")));

            CollectionRateVO vo = service.getCollectionRate(1L);

            assertThat(vo.getTotalInvoiced()).isEqualByComparingTo("100000");
            assertThat(vo.getTotalReceived()).isEqualByComparingTo("80000");
            // 80000 / 100000 = 0.8
            assertThat(vo.getCollectionRate()).isEqualByComparingTo("0.8000");
            assertThat(vo.getUncollectedAmount()).isEqualByComparingTo("20000");
        }

        @Test
        @DisplayName("仅有回款无开票时回款率为 null")
        void rate_null_when_no_invoice() {
            when(paymentReceivedMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(received("5000")));
            when(invoiceApplyMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            CollectionRateVO vo = service.getCollectionRate(1L);

            assertThat(vo.getCollectionRate()).isNull();
            assertThat(vo.getUncollectedAmount()).isEqualByComparingTo("-5000");
        }

        @Test
        @DisplayName("开票与回款均为空抛业务异常")
        void throws_when_both_empty() {
            when(paymentReceivedMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());
            when(invoiceApplyMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> service.getCollectionRate(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("暂无已审批的开票或回款数据");
        }
    }

    @Nested
    @DisplayName("资金计划")
    class FundPlan {

        @Test
        @DisplayName("按月聚合应付金额与笔数")
        void aggregates_by_month() {
            when(paymentApplyMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(
                            apply("2026-06-01", "10000"),
                            apply("2026-06-20", "5000"),
                            apply("2026-07-05", "8000")));

            List<FundPlanItemVO> plan = service.getFundPlan(1L, 6);

            assertThat(plan).hasSize(2);
            assertThat(plan.get(0).getMonth()).isEqualTo("2026-06");
            assertThat(plan.get(0).getPlannedAmount()).isEqualByComparingTo("15000");
            assertThat(plan.get(0).getApplyCount()).isEqualTo(2);
            assertThat(plan.get(1).getMonth()).isEqualTo("2026-07");
        }

        @Test
        @DisplayName("months 限制仅返回最近月份")
        void limits_recent_months() {
            when(paymentApplyMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(
                            apply("2026-01-01", "1000"),
                            apply("2026-02-01", "1000"),
                            apply("2026-03-01", "1000")));

            List<FundPlanItemVO> plan = service.getFundPlan(1L, 1);

            assertThat(plan).hasSize(1);
            assertThat(plan.get(0).getMonth()).isEqualTo("2026-03");
        }

        @Test
        @DisplayName("无付款申请抛业务异常")
        void throws_when_empty() {
            when(paymentApplyMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> service.getFundPlan(1L, 6))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("暂无已审批的付款申请");
        }
    }
}
