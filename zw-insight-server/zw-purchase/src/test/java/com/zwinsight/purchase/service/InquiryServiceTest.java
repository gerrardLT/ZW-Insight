package com.zwinsight.purchase.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.purchase.domain.BizInquiry;
import com.zwinsight.purchase.mapper.BizInquiryItemMapper;
import com.zwinsight.purchase.mapper.BizInquiryMapper;
import com.zwinsight.purchase.mapper.BizInquirySupplierMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @Mock private BizInquiryMapper inquiryMapper;
    @Mock private BizInquiryItemMapper inquiryItemMapper;
    @Mock private BizInquirySupplierMapper inquirySupplierMapper;

    @InjectMocks
    private InquiryService inquiryService;

    @Test
    @DisplayName("保存询价单：默认DRAFT状态")
    void testSave() {
        BizInquiry inquiry = new BizInquiry();
        inquiry.setTitle("钢材询价");
        when(inquiryMapper.insert(any())).thenReturn(1);

        inquiryService.save(inquiry);

        assertThat(inquiry.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("发布询价单：DRAFT→PUBLISHED（有物料明细）")
    void testPublish_success() {
        BizInquiry inquiry = new BizInquiry();
        inquiry.setId(1L);
        inquiry.setStatus("DRAFT");
        inquiry.setInviteMode("PUBLIC");
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry);
        when(inquiryItemMapper.selectCount(any())).thenReturn(3L);

        inquiryService.publish(1L);

        assertThat(inquiry.getStatus()).isEqualTo("PUBLISHED");
        assertThat(inquiry.getPublishTime()).isNotNull();
    }

    @Test
    @DisplayName("发布询价单：无物料明细抛异常")
    void testPublish_noItems() {
        BizInquiry inquiry = new BizInquiry();
        inquiry.setId(1L);
        inquiry.setStatus("DRAFT");
        inquiry.setInviteMode("PUBLIC");
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry);
        when(inquiryItemMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> inquiryService.publish(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少添加一个询价物料");
    }

    @Test
    @DisplayName("发布询价单：定向模式无供应商抛异常")
    void testPublish_directed_noSupplier() {
        BizInquiry inquiry = new BizInquiry();
        inquiry.setId(1L);
        inquiry.setStatus("DRAFT");
        inquiry.setInviteMode("DIRECTED");
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry);
        when(inquiryItemMapper.selectCount(any())).thenReturn(2L);
        when(inquirySupplierMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> inquiryService.publish(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少指定一个供应商");
    }

    @Test
    @DisplayName("删除：非DRAFT拒绝")
    void testDelete_nonDraft() {
        BizInquiry inquiry = new BizInquiry();
        inquiry.setId(1L);
        inquiry.setStatus("PUBLISHED");
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry);

        assertThatThrownBy(() -> inquiryService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("查询：不存在抛异常")
    void testGetById_notFound() {
        when(inquiryMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> inquiryService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("询价单不存在");
    }
}
