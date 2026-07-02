package com.zwinsight.material.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.material.domain.BizMaterialOutbound;
import com.zwinsight.material.domain.BizMaterialOutboundDetail;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.mapper.BizMaterialOutboundDetailMapper;
import com.zwinsight.material.mapper.BizMaterialOutboundMapper;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialOutboundServiceTest {

    @Mock private BizMaterialOutboundMapper outboundMapper;
    @Mock private BizMaterialOutboundDetailMapper outboundDetailMapper;
    @Mock private BizProjectMaterialStockMapper stockMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MaterialOutboundService materialOutboundService;

    @Test
    @DisplayName("保存领料出库：库存充足扣减库存")
    void testSave_pick_stockSufficient() {
        BizMaterialOutbound outbound = new BizMaterialOutbound();
        outbound.setProjectId(1L);
        outbound.setOutboundType("PICK");

        BizMaterialOutboundDetail detail = new BizMaterialOutboundDetail();
        detail.setMaterialName("钢筋");
        detail.setSpecification("HRB400");
        detail.setQuantity(new BigDecimal("5"));

        BizProjectMaterialStock stock = new BizProjectMaterialStock();
        stock.setStockQuantity(new BigDecimal("20"));
        stock.setTotalOutbound(BigDecimal.ZERO);
        when(stockMapper.selectOne(any())).thenReturn(stock);

        materialOutboundService.save(outbound, List.of(detail));

        assertThat(outbound.getStatus()).isEqualTo("DRAFT");
        assertThat(stock.getStockQuantity()).isEqualTo(new BigDecimal("15"));
        assertThat(stock.getTotalOutbound()).isEqualTo(new BigDecimal("5"));
    }

    @Test
    @DisplayName("保存领料出库：库存不足抛异常")
    void testSave_pick_stockInsufficient() {
        BizMaterialOutbound outbound = new BizMaterialOutbound();
        outbound.setProjectId(1L);
        outbound.setOutboundType("PICK");

        BizMaterialOutboundDetail detail = new BizMaterialOutboundDetail();
        detail.setMaterialName("钢筋");
        detail.setSpecification("HRB400");
        detail.setQuantity(new BigDecimal("50"));

        BizProjectMaterialStock stock = new BizProjectMaterialStock();
        stock.setStockQuantity(new BigDecimal("10"));
        when(stockMapper.selectOne(any())).thenReturn(stock);

        assertThatThrownBy(() -> materialOutboundService.save(outbound, List.of(detail)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存不足");
    }

    @Test
    @DisplayName("提交出库：DRAFT→APPROVED")
    void testSubmit() {
        BizMaterialOutbound outbound = new BizMaterialOutbound();
        outbound.setId(1L);
        outbound.setStatus("DRAFT");
        when(outboundMapper.selectById(1L)).thenReturn(outbound);

        materialOutboundService.submit(1L);

        assertThat(outbound.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("提交出库：非DRAFT拒绝")
    void testSubmit_nonDraft() {
        BizMaterialOutbound outbound = new BizMaterialOutbound();
        outbound.setId(1L);
        outbound.setStatus("APPROVED");
        when(outboundMapper.selectById(1L)).thenReturn(outbound);

        assertThatThrownBy(() -> materialOutboundService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("删除：非DRAFT拒绝")
    void testDelete_nonDraft() {
        BizMaterialOutbound outbound = new BizMaterialOutbound();
        outbound.setId(1L);
        outbound.setStatus("APPROVED");
        when(outboundMapper.selectById(1L)).thenReturn(outbound);

        assertThatThrownBy(() -> materialOutboundService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("查询：不存在抛异常")
    void testGetById_notFound() {
        when(outboundMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> materialOutboundService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("出库单不存在");
    }
}
