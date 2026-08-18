package com.zwinsight.material.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.material.domain.BizMaterialOutbound;
import com.zwinsight.material.domain.BizMaterialOutboundDetail;
import com.zwinsight.material.domain.BizMaterialRefund;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.mapper.BizMaterialOutboundDetailMapper;
import com.zwinsight.material.mapper.BizMaterialOutboundMapper;
import com.zwinsight.material.mapper.BizMaterialRefundMapper;
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
    @Mock private BizMaterialRefundMapper refundMapper;
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
    @DisplayName("删除：E2E_TEST_ 标记数据非DRAFT放行（E2eTestGuard）")
    void testDelete_e2eMarkerBypass() {
        BizMaterialOutbound outbound = new BizMaterialOutbound();
        outbound.setId(1L);
        outbound.setStatus("APPROVED");
        outbound.setProjectName("E2E_TEST_1723900000000_项目");
        when(outboundMapper.selectById(1L)).thenReturn(outbound);
        when(outboundDetailMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());

        materialOutboundService.delete(1L);

        verify(outboundMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除：主表无标记、明细 materialName 带 E2E_TEST_ 前缀放行并回填库存")
    void testDelete_detailMarkerBypass_restoreStock() {
        BizMaterialOutbound outbound = new BizMaterialOutbound();
        outbound.setId(2L);
        outbound.setProjectId(10L);
        outbound.setStatus("APPROVED");
        outbound.setOutboundType("PICK");
        when(outboundMapper.selectById(2L)).thenReturn(outbound);

        BizMaterialOutboundDetail detail = new BizMaterialOutboundDetail();
        detail.setId(20L);
        detail.setOutboundId(2L);
        detail.setMaterialName("E2E_TEST_1723900000000_钢筋");
        detail.setSpecification("HRB400");
        detail.setQuantity(new java.math.BigDecimal("5"));
        when(outboundDetailMapper.selectList(any())).thenReturn(java.util.List.of(detail));

        BizProjectMaterialStock stock = new BizProjectMaterialStock();
        stock.setStockQuantity(new java.math.BigDecimal("5"));
        stock.setTotalOutbound(new java.math.BigDecimal("5"));
        when(stockMapper.selectOne(any())).thenReturn(stock);

        materialOutboundService.delete(2L);

        // save 时扣的库存删单后回填
        assertThat(stock.getStockQuantity()).isEqualByComparingTo("10");
        verify(outboundMapper).deleteById(2L);
    }

    @Test
    @DisplayName("查询：不存在抛异常")
    void testGetById_notFound() {
        when(outboundMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> materialOutboundService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("出库单不存在");
    }

    @Test
    @DisplayName("删除草稿出库单：库存对称回填（B3：save 已扣减，删除不回填会永久丢库存）")
    void testDelete_draft_restoresStock() {
        BizMaterialOutbound outbound = new BizMaterialOutbound();
        outbound.setId(1L);
        outbound.setStatus("DRAFT");
        outbound.setProjectId(100L);
        outbound.setOutboundType("PICK");
        when(outboundMapper.selectById(1L)).thenReturn(outbound);

        BizMaterialOutboundDetail detail = new BizMaterialOutboundDetail();
        detail.setId(11L);
        detail.setOutboundId(1L);
        detail.setMaterialName("钢筋");
        detail.setSpecification("HRB400");
        detail.setQuantity(new java.math.BigDecimal("20"));
        when(outboundDetailMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenReturn(java.util.List.of(detail));

        BizProjectMaterialStock stock = new BizProjectMaterialStock();
        stock.setProjectId(100L);
        stock.setStockQuantity(new java.math.BigDecimal("80"));
        stock.setTotalOutbound(new java.math.BigDecimal("120"));
        stock.setTotalReturn(java.math.BigDecimal.ZERO);
        when(stockMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenReturn(stock);

        materialOutboundService.delete(1L);

        verify(outboundMapper).deleteById(1L);
        verify(outboundDetailMapper).deleteById(11L);
        // 库存 80 + 20 = 100，累计出库 120 - 20 = 100
        verify(stockMapper).updateById(argThat(s ->
                s.getStockQuantity().compareTo(new java.math.BigDecimal("100")) == 0
                        && s.getTotalOutbound().compareTo(new java.math.BigDecimal("100")) == 0));
    }

    @Test
    @DisplayName("删除退货草稿出库单：同步作废已生成的 PENDING 退款申请（P1 D1：单删钱退联动断裂）")
    void testDelete_returnDraft_cancelsPendingRefund() {
        BizMaterialOutbound outbound = new BizMaterialOutbound();
        outbound.setId(2L);
        outbound.setStatus("DRAFT");
        outbound.setProjectId(100L);
        outbound.setOutboundType("RETURN");
        when(outboundMapper.selectById(2L)).thenReturn(outbound);
        when(outboundDetailMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenReturn(java.util.List.of());

        BizMaterialRefund refund = new BizMaterialRefund();
        refund.setId(55L);
        refund.setOutboundId(2L);
        refund.setStatus("PENDING");
        when(refundMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenReturn(java.util.List.of(refund));

        materialOutboundService.delete(2L);

        verify(outboundMapper).deleteById(2L);
        assertThat(refund.getStatus()).as("退款申请应同步作废，防继续审批扣款").isEqualTo("CANCELED");
        verify(refundMapper).updateById(refund);
    }

    @Test
    @DisplayName("删除领料出库单：不触发退款作废查询（仅 RETURN 类型联动）")
    void testDelete_pickDraft_noRefundInteraction() {
        BizMaterialOutbound outbound = new BizMaterialOutbound();
        outbound.setId(3L);
        outbound.setStatus("DRAFT");
        outbound.setProjectId(100L);
        outbound.setOutboundType("PICK");
        when(outboundMapper.selectById(3L)).thenReturn(outbound);
        when(outboundDetailMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenReturn(java.util.List.of());

        materialOutboundService.delete(3L);

        verify(outboundMapper).deleteById(3L);
        verify(refundMapper, never()).selectList(any());
    }
}
