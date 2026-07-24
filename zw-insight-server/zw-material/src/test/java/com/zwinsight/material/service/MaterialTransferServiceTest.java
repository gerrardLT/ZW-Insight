package com.zwinsight.material.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.material.domain.BizMaterialTransfer;
import com.zwinsight.material.domain.BizMaterialTransferDetail;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.mapper.BizMaterialTransferDetailMapper;
import com.zwinsight.material.mapper.BizMaterialTransferMapper;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
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
class MaterialTransferServiceTest {

    @Mock private BizMaterialTransferMapper transferMapper;
    @Mock private BizMaterialTransferDetailMapper transferDetailMapper;
    @Mock private BizProjectMaterialStockMapper stockMapper;
    @Mock private BizProjectMapper projectMapper;

    @InjectMocks
    private MaterialTransferService materialTransferService;

    @Test
    @DisplayName("保存调拨：调出减库存+调入增库存")
    void testSave_transferStock() {
        BizMaterialTransfer transfer = new BizMaterialTransfer();
        transfer.setFromProjectId(1L);
        transfer.setToProjectId(2L);

        BizMaterialTransferDetail detail = new BizMaterialTransferDetail();
        detail.setMaterialName("钢筋");
        detail.setSpecification("HRB400");
        detail.setQuantity(new BigDecimal("10"));
        detail.setUnitPrice(new BigDecimal("200"));

        BizProjectMaterialStock fromStock = new BizProjectMaterialStock();
        fromStock.setStockQuantity(new BigDecimal("50"));
        fromStock.setTotalTransferOut(BigDecimal.ZERO);

        // 第一次 selectOne 返回调出方库存，第二次返回 null（调入方无库存，新建）
        when(stockMapper.selectOne(any())).thenReturn(fromStock).thenReturn(null);

        materialTransferService.save(transfer, List.of(detail));

        assertThat(transfer.getStatus()).isEqualTo("DRAFT");
        assertThat(fromStock.getStockQuantity()).isEqualTo(new BigDecimal("40"));
        assertThat(fromStock.getTotalTransferOut()).isEqualTo(new BigDecimal("10"));
        verify(stockMapper).insert(any()); // 调入方新建库存
    }

    @Test
    @DisplayName("保存调拨：调出方库存不足抛异常")
    void testSave_stockInsufficient() {
        BizMaterialTransfer transfer = new BizMaterialTransfer();
        transfer.setFromProjectId(1L);
        transfer.setToProjectId(2L);

        BizMaterialTransferDetail detail = new BizMaterialTransferDetail();
        detail.setMaterialName("钢筋");
        detail.setSpecification("HRB400");
        detail.setQuantity(new BigDecimal("100"));

        BizProjectMaterialStock fromStock = new BizProjectMaterialStock();
        fromStock.setStockQuantity(new BigDecimal("10"));
        when(stockMapper.selectOne(any())).thenReturn(fromStock);

        assertThatThrownBy(() -> materialTransferService.save(transfer, List.of(detail)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存不足");
    }

    @Test
    @DisplayName("提交调拨：DRAFT→APPROVED")
    void testSubmit() {
        BizMaterialTransfer transfer = new BizMaterialTransfer();
        transfer.setId(1L);
        transfer.setStatus("DRAFT");
        when(transferMapper.selectById(1L)).thenReturn(transfer);

        materialTransferService.submit(1L);

        assertThat(transfer.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("提交调拨：非DRAFT拒绝")
    void testSubmit_nonDraft() {
        BizMaterialTransfer transfer = new BizMaterialTransfer();
        transfer.setId(1L);
        transfer.setStatus("APPROVED");
        when(transferMapper.selectById(1L)).thenReturn(transfer);

        assertThatThrownBy(() -> materialTransferService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("删除：非DRAFT拒绝")
    void testDelete_nonDraft() {
        BizMaterialTransfer transfer = new BizMaterialTransfer();
        transfer.setId(1L);
        transfer.setStatus("APPROVED");
        when(transferMapper.selectById(1L)).thenReturn(transfer);

        assertThatThrownBy(() -> materialTransferService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("查询：不存在抛异常")
    void testGetById_notFound() {
        when(transferMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> materialTransferService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("调拨单不存在");
    }
}
