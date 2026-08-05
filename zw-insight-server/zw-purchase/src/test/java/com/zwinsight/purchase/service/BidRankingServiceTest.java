package com.zwinsight.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.purchase.domain.BizBidResult;
import com.zwinsight.purchase.domain.BizInquiry;
import com.zwinsight.purchase.domain.BizQuotation;
import com.zwinsight.purchase.mapper.BizBidResultMapper;
import com.zwinsight.purchase.mapper.BizInquiryMapper;
import com.zwinsight.purchase.mapper.BizQuotationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * BidRankingService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class BidRankingServiceTest {

    @Mock
    private BizQuotationMapper quotationMapper;

    @Mock
    private BizBidResultMapper bidResultMapper;

    @Mock
    private BizInquiryMapper inquiryMapper;

    @InjectMocks
    private BidRankingService bidRankingService;

    private BizInquiry inquiry(String status) {
        BizInquiry i = new BizInquiry();
        i.setId(1L);
        i.setStatus(status);
        return i;
    }

    private BizQuotation quote(Long supplierId, String name, String amount) {
        BizQuotation q = new BizQuotation();
        q.setSupplierId(supplierId);
        q.setSupplierName(name);
        q.setTotalAmount(new BigDecimal(amount));
        return q;
    }

    @Test
    @DisplayName("calculateRanking - 询价单不存在抛异常")
    void calculateRanking_inquiryNotFound_throws() {
        when(inquiryMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> bidRankingService.calculateRanking(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("询价单不存在");
    }

    @Test
    @DisplayName("calculateRanking - 状态非 QUOTED/PUBLISHED 不允许计算")
    void calculateRanking_invalidStatus_throws() {
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry("AWARDED"));

        assertThatThrownBy(() -> bidRankingService.calculateRanking(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态不允许计算排名");
    }

    @Test
    @DisplayName("calculateRanking - 无报价抛异常")
    void calculateRanking_noQuotations_throws() {
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry("QUOTED"));
        when(quotationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> bidRankingService.calculateRanking(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂无供应商报价");
    }

    @Test
    @DisplayName("calculateRanking - 正常计算：清旧排名、按最低价升序生成 1..N 名、均非中标")
    void calculateRanking_success_lowestFirst() {
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry("QUOTED"));
        when(quotationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(
                        quote(11L, "供应商A", "5000"),
                        quote(12L, "供应商B", "3000"),
                        quote(13L, "供应商C", "4000")));
        when(bidResultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        bidRankingService.calculateRanking(1L);

        // 先清旧排名
        verify(bidResultMapper).delete(any(LambdaQueryWrapper.class));
        // 按总价升序插入：B(3000)=1, C(4000)=2, A(5000)=3
        ArgumentCaptor<BizBidResult> captor = ArgumentCaptor.forClass(BizBidResult.class);
        verify(bidResultMapper, times(3)).insert(captor.capture());
        List<BizBidResult> inserted = captor.getAllValues();
        assertThat(inserted).extracting(BizBidResult::getSupplierId, BizBidResult::getRanking)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(12L, 1),
                        org.assertj.core.groups.Tuple.tuple(13L, 2),
                        org.assertj.core.groups.Tuple.tuple(11L, 3));
        assertThat(inserted).allSatisfy(r -> {
            assertThat(r.getIsWinner()).isZero();
            assertThat(r.getInquiryId()).isEqualTo(1L);
        });
    }

    @Test
    @DisplayName("confirmWinner - 询价单不存在/供应商未参与排名抛异常")
    void confirmWinner_notFoundCases_throws() {
        when(inquiryMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> bidRankingService.confirmWinner(1L, 11L))
                .hasMessageContaining("询价单不存在");

        when(inquiryMapper.selectById(2L)).thenReturn(inquiry("QUOTED"));
        when(bidResultMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> bidRankingService.confirmWinner(2L, 11L))
                .hasMessageContaining("该供应商未参与排名");
    }

    @Test
    @DisplayName("confirmWinner - 正常确认：全员清 winner、目标置 1、询价单转 AWARDED")
    void confirmWinner_success_resetsAndAwards() {
        BizInquiry inq = inquiry("QUOTED");
        BizBidResult target = new BizBidResult();
        target.setSupplierId(12L);
        BizBidResult other = new BizBidResult();
        other.setSupplierId(11L);
        other.setIsWinner(1); // 旧中标方

        when(inquiryMapper.selectById(1L)).thenReturn(inq);
        when(bidResultMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(target);
        when(bidResultMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(target, other));

        bidRankingService.confirmWinner(1L, 12L);

        assertThat(target.getIsWinner()).isEqualTo(1);
        assertThat(other.getIsWinner()).isZero();
        verify(bidResultMapper, times(3)).updateById(any(BizBidResult.class)); // 2 清 + 1 设
        verify(inquiryMapper).updateById(argThat(i -> "AWARDED".equals(i.getStatus())));
    }

    @Test
    @DisplayName("getByInquiry - 透传查询结果")
    void getByInquiry_delegates() {
        BizBidResult r = new BizBidResult();
        when(bidResultMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(r));

        assertThat(bidRankingService.getByInquiry(1L)).hasSize(1);
    }
}
