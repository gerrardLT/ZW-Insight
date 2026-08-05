package com.zwinsight.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.purchase.domain.BizInquiry;
import com.zwinsight.purchase.domain.BizQuotation;
import com.zwinsight.purchase.domain.BizQuotationDetail;
import com.zwinsight.purchase.mapper.BizInquiryMapper;
import com.zwinsight.purchase.mapper.BizQuotationDetailMapper;
import com.zwinsight.purchase.mapper.BizQuotationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * QuotationService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class QuotationServiceTest {

    @Mock
    private BizQuotationMapper quotationMapper;

    @Mock
    private BizQuotationDetailMapper quotationDetailMapper;

    @Mock
    private BizInquiryMapper inquiryMapper;

    @InjectMocks
    private QuotationService quotationService;

    private BizInquiry inquiry(String status) {
        BizInquiry i = new BizInquiry();
        i.setId(1L);
        i.setStatus(status);
        return i;
    }

    private BizQuotationDetail detail(String price) {
        BizQuotationDetail d = new BizQuotationDetail();
        d.setTotalPrice(price == null ? null : new BigDecimal(price));
        return d;
    }

    @Test
    @DisplayName("submitQuote - 询价单不存在抛异常")
    void submitQuote_inquiryNotFound_throws() {
        BizQuotation q = new BizQuotation();
        q.setInquiryId(1L);
        when(inquiryMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> quotationService.submitQuote(q, Collections.emptyList()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("询价单不存在");
    }

    @Test
    @DisplayName("submitQuote - 询价单状态非 PUBLISHED/QUOTED 不允许报价")
    void submitQuote_invalidStatus_throws() {
        BizQuotation q = new BizQuotation();
        q.setInquiryId(1L);
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry("AWARDED"));

        assertThatThrownBy(() -> quotationService.submitQuote(q, Collections.emptyList()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前询价单状态不允许报价");
    }

    @Test
    @DisplayName("submitQuote - 正常提报：状态置 SUBMITTED、总额汇总、明细回写 quotationId、询价单转 QUOTED")
    void submitQuote_success_fullWriteBack() {
        BizQuotation q = new BizQuotation();
        q.setInquiryId(1L);
        BizInquiry inq = inquiry("PUBLISHED");
        when(inquiryMapper.selectById(1L)).thenReturn(inq);
        // 模拟 insert 回填主键
        doAnswer(inv -> {
            BizQuotation arg = inv.getArgument(0);
            arg.setId(50L);
            return 1;
        }).when(quotationMapper).insert(any(BizQuotation.class));

        List<BizQuotationDetail> details = Arrays.asList(detail("1000.00"), detail("250.50"), detail(null));
        quotationService.submitQuote(q, details);

        assertThat(q.getStatus()).isEqualTo("SUBMITTED");
        assertThat(q.getSubmitTime()).isNotNull();
        // 总额 = 1000 + 250.50（null 跳过）
        assertThat(q.getTotalAmount()).isEqualByComparingTo("1250.50");
        verify(quotationDetailMapper, times(3)).insert(argThat(d -> Long.valueOf(50L).equals(d.getQuotationId())));
        verify(inquiryMapper).updateById(argThat(i -> "QUOTED".equals(i.getStatus())));
    }

    @Test
    @DisplayName("submitQuote - 询价单已是 QUOTED 时不重复更新状态")
    void submitQuote_alreadyQuoted_noStatusUpdate() {
        BizQuotation q = new BizQuotation();
        q.setInquiryId(1L);
        BizInquiry inq = inquiry("QUOTED");
        when(inquiryMapper.selectById(1L)).thenReturn(inq);

        quotationService.submitQuote(q, Collections.emptyList());

        verify(inquiryMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("getRanking - 按总价从低到高排序")
    void getRanking_sortedByTotalAmountAsc() {
        BizQuotation q1 = new BizQuotation();
        q1.setTotalAmount(new BigDecimal("3000"));
        BizQuotation q2 = new BizQuotation();
        q2.setTotalAmount(new BigDecimal("1000"));
        BizQuotation q3 = new BizQuotation();
        q3.setTotalAmount(new BigDecimal("2000"));
        when(quotationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(q1, q2, q3));

        List<BizQuotation> ranking = quotationService.getRanking(1L);

        assertThat(ranking).extracting(BizQuotation::getTotalAmount)
                .containsExactly(new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("3000"));
    }
}
