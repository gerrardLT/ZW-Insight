package com.zwinsight.purchase.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.purchase.domain.BizInquiry;
import com.zwinsight.purchase.domain.BizInquiryItem;
import com.zwinsight.purchase.domain.BizInquirySupplier;
import com.zwinsight.purchase.mapper.BizInquiryItemMapper;
import com.zwinsight.purchase.mapper.BizInquiryMapper;
import com.zwinsight.purchase.mapper.BizInquirySupplierMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
    @DisplayName("删除：E2E_TEST_ 标记数据非DRAFT放行（E2eTestGuard）")
    void testDelete_e2eMarkerBypass() {
        BizInquiry inquiry = new BizInquiry();
        inquiry.setId(1L);
        inquiry.setStatus("PUBLISHED");
        inquiry.setTitle("E2E_TEST_1723900000000_询价");
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry);

        inquiryService.delete(1L);

        verify(inquiryMapper).deleteById(1L);
    }

    @Test
    @DisplayName("查询：不存在抛异常")
    void testGetById_notFound() {
        when(inquiryMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> inquiryService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("询价单不存在");
    }

    // ============ 编辑询价单（P1 PUR-INQ-02/03） ============

    private BizInquiry existing(Long id, String status) {
        BizInquiry inquiry = new BizInquiry();
        inquiry.setId(id);
        inquiry.setStatus(status);
        return inquiry;
    }

    @Test
    @DisplayName("编辑：草稿且带明细/供应商 → 先删后插整体替换（P1 PUR-INQ-02）")
    void testUpdate_draft_replacesItemsAndSuppliers() {
        when(inquiryMapper.selectById(1L)).thenReturn(existing(1L, "DRAFT"));

        BizInquiryItem item = new BizInquiryItem();
        item.setId(900L);
        BizInquirySupplier supplier = new BizInquirySupplier();
        supplier.setId(901L);

        BizInquiry update = existing(1L, "DRAFT");
        update.setItems(List.of(item));
        update.setSuppliers(List.of(supplier));

        inquiryService.update(update);

        verify(inquiryMapper).updateById(update);
        verify(inquiryItemMapper).delete(any());
        verify(inquiryItemMapper).insert(item);
        assertThat(item.getId()).as("明细 id 应清空后重挂询价单").isNull();
        assertThat(item.getInquiryId()).isEqualTo(1L);
        verify(inquirySupplierMapper).delete(any());
        verify(inquirySupplierMapper).insert(supplier);
        assertThat(supplier.getId()).isNull();
        assertThat(supplier.getInquiryId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("编辑：草稿但不带明细/供应商 → 仅更新主表不删子表（P1 PUR-INQ-02 边界）")
    void testUpdate_draft_nullChildren_mainOnly() {
        when(inquiryMapper.selectById(1L)).thenReturn(existing(1L, "DRAFT"));

        inquiryService.update(existing(1L, "DRAFT"));

        verify(inquiryMapper).updateById(any());
        verify(inquiryItemMapper, never()).delete(any());
        verify(inquirySupplierMapper, never()).delete(any());
    }

    @Test
    @DisplayName("编辑：非草稿拒绝（P1 PUR-INQ-03）")
    void testUpdate_nonDraft_rejected() {
        when(inquiryMapper.selectById(1L)).thenReturn(existing(1L, "PUBLISHED"));

        assertThatThrownBy(() -> inquiryService.update(existing(1L, "DRAFT")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可编辑");
        verify(inquiryMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("编辑：不存在拒绝（P1 PUR-INQ-03）")
    void testUpdate_notFound_rejected() {
        when(inquiryMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> inquiryService.update(existing(999L, "DRAFT")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("询价单不存在");
        verify(inquiryMapper, never()).updateById(any());
    }
}
