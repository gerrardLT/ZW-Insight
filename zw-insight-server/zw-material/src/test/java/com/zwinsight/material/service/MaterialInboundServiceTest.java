package com.zwinsight.material.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.material.domain.*;
import com.zwinsight.material.mapper.*;
import com.zwinsight.purchase.mapper.BizPurchaseContractMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialInboundServiceTest {

    @Mock private BizMaterialInboundMapper inboundMapper;
    @Mock private BizMaterialInboundDetailMapper inboundDetailMapper;
    @Mock private BizProjectMaterialStockMapper stockMapper;
    @Mock private BizMaterialOutboundMapper outboundMapper;
    @Mock private BizMaterialOutboundDetailMapper outboundDetailMapper;
    @Mock private BizPurchaseContractMapper purchaseContractMapper;

    @InjectMocks
    private MaterialInboundService materialInboundService;

    @Test
    @DisplayName("保存入库单：自动计算明细金额")
    void testSave_autoCalcDetailAmount() {
        BizMaterialInbound inbound = new BizMaterialInbound();
        inbound.setProjectId(1L);

        BizMaterialInboundDetail detail = new BizMaterialInboundDetail();
        detail.setUnitPrice(new BigDecimal("100"));
        detail.setQuantity(new BigDecimal("10"));
        // totalPrice is null → should auto-calc

        materialInboundService.save(inbound, List.of(detail));

        assertThat(inbound.getStatus()).isEqualTo("DRAFT");
        assertThat(detail.getTotalPrice()).isEqualTo(new BigDecimal("1000"));
        assertThat(inbound.getTotalAmount()).isEqualTo(new BigDecimal("1000"));
        verify(inboundMapper).insert(any()); // first insert
        verify(inboundMapper).updateById(any()); // then update amount
    }

    @Test
    @DisplayName("保存入库单：多条明细汇总金额")
    void testSave_multipleDetails() {
        BizMaterialInbound inbound = new BizMaterialInbound();
        inbound.setProjectId(1L);

        BizMaterialInboundDetail d1 = new BizMaterialInboundDetail();
        d1.setTotalPrice(new BigDecimal("500"));

        BizMaterialInboundDetail d2 = new BizMaterialInboundDetail();
        d2.setTotalPrice(new BigDecimal("300"));

        materialInboundService.save(inbound, List.of(d1, d2));

        assertThat(inbound.getTotalAmount()).isEqualTo(new BigDecimal("800"));
        verify(inboundDetailMapper, times(2)).insert(any());
    }

    @Test
    @DisplayName("提交入库：DRAFT→APPROVED并更新库存")
    void testSubmit_updateStock() {
        BizMaterialInbound inbound = new BizMaterialInbound();
        inbound.setId(1L);
        inbound.setProjectId(10L);
        inbound.setStatus("DRAFT");
        inbound.setTotalAmount(new BigDecimal("1000"));
        when(inboundMapper.selectById(1L)).thenReturn(inbound);

        BizMaterialInboundDetail detail = new BizMaterialInboundDetail();
        detail.setMaterialName("钢筋");
        detail.setSpecification("HRB400");
        detail.setQuantity(new BigDecimal("10"));
        detail.setUnitPrice(new BigDecimal("100"));
        when(inboundDetailMapper.selectList(any())).thenReturn(List.of(detail));
        when(stockMapper.selectOne(any())).thenReturn(null); // 新建库存

        materialInboundService.submit(1L);

        assertThat(inbound.getStatus()).isEqualTo("APPROVED");
        verify(stockMapper).insert(any()); // 新创建库存记录
    }

    @Test
    @DisplayName("提交入库：非DRAFT拒绝")
    void testSubmit_nonDraft() {
        BizMaterialInbound inbound = new BizMaterialInbound();
        inbound.setId(1L);
        inbound.setStatus("APPROVED");
        when(inboundMapper.selectById(1L)).thenReturn(inbound);

        assertThatThrownBy(() -> materialInboundService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("删除：非DRAFT拒绝")
    void testDelete_nonDraft() {
        BizMaterialInbound inbound = new BizMaterialInbound();
        inbound.setId(1L);
        inbound.setStatus("APPROVED");
        when(inboundMapper.selectById(1L)).thenReturn(inbound);

        assertThatThrownBy(() -> materialInboundService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("查询：不存在抛异常")
    void testGetById_notFound() {
        when(inboundMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> materialInboundService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("入库单不存在");
    }
}
