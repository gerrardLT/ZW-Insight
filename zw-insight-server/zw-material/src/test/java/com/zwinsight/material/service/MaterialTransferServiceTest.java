package com.zwinsight.material.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.material.domain.BizMaterialTransfer;
import com.zwinsight.material.domain.BizMaterialTransferDetail;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.mapper.BizMaterialTransferDetailMapper;
import com.zwinsight.material.mapper.BizMaterialTransferMapper;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
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
    @Mock private ApprovalService approvalService;
    
    @InjectMocks
    private MaterialTransferService materialTransferService;
    
    @Test
    @DisplayName("保存调拨：仅落单据不变更库存")
    void testSave_onlyPersist() {
        BizMaterialTransfer transfer = new BizMaterialTransfer();
        transfer.setFromProjectId(1L);
        transfer.setToProjectId(2L);
    
        BizMaterialTransferDetail detail = new BizMaterialTransferDetail();
        detail.setMaterialName("钢筋");
        detail.setSpecification("HRB400");
        detail.setQuantity(new BigDecimal("10"));
        detail.setUnitPrice(new BigDecimal("200"));
    
        materialTransferService.save(transfer, List.of(detail));
    
        assertThat(transfer.getStatus()).isEqualTo("DRAFT");
        verify(transferDetailMapper).insert(any());
        // 库存在保存阶段不变更
        verify(stockMapper, never()).updateById(any());
        verify(stockMapper, never()).insert(any());
    }

    @Test
    @DisplayName("保存调拨：同项目调拨抛异常（2026-08-14 P0 盲点 11a 后端守卫钉住）")
    void testSave_sameProjectRejected() {
        BizMaterialTransfer transfer = new BizMaterialTransfer();
        transfer.setFromProjectId(1L);
        transfer.setToProjectId(1L);

        BizMaterialTransferDetail detail = new BizMaterialTransferDetail();
        detail.setMaterialName("钢筋");
        detail.setQuantity(new BigDecimal("10"));

        assertThatThrownBy(() -> materialTransferService.save(transfer, List.of(detail)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能相同");
        verify(transferMapper, never()).insert(any());
    }

    @Test
    @DisplayName("更新调拨：PUT 体携带同项目抛异常（盲点 11a update 路径钉住）")
    void testUpdate_sameProjectRejected() {
        BizMaterialTransfer existing = new BizMaterialTransfer();
        existing.setId(1L);
        existing.setStatus("DRAFT");
        existing.setFromProjectId(1L);
        existing.setToProjectId(2L);
        when(transferMapper.selectById(1L)).thenReturn(existing);

        // PUT 体只改 toProjectId 为与 from 相同
        BizMaterialTransfer update = new BizMaterialTransfer();
        update.setId(1L);
        update.setToProjectId(1L);

        assertThatThrownBy(() -> materialTransferService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能相同");
        verify(transferMapper, never()).updateById(any());
    }
    
    @Test
    @DisplayName("审批通过：调出减库存+调入新建库存")
    void testOnApproved_transferStock() {
        BizMaterialTransfer transfer = new BizMaterialTransfer();
        transfer.setId(1L);
        transfer.setStatus("SUBMITTED");
        transfer.setFromProjectId(1L);
        transfer.setToProjectId(2L);
        when(transferMapper.selectById(1L)).thenReturn(transfer);
    
        BizMaterialTransferDetail detail = new BizMaterialTransferDetail();
        detail.setMaterialName("钢筋");
        detail.setSpecification("HRB400");
        detail.setQuantity(new BigDecimal("10"));
        detail.setUnitPrice(new BigDecimal("200"));
        when(transferDetailMapper.selectList(any())).thenReturn(List.of(detail));
    
        BizProjectMaterialStock fromStock = new BizProjectMaterialStock();
        fromStock.setStockQuantity(new BigDecimal("50"));
        fromStock.setTotalTransferOut(BigDecimal.ZERO);
        when(stockMapper.selectOne(any())).thenReturn(fromStock).thenReturn(null);
    
        materialTransferService.onApproved(1L);
    
        assertThat(transfer.getStatus()).isEqualTo("APPROVED");
        assertThat(fromStock.getStockQuantity()).isEqualByComparingTo(new BigDecimal("40"));
        verify(stockMapper).insert(any());
    }
    
    @Test
    @DisplayName("审批通过：已生效幂等跳过")
    void testOnApproved_idempotent() {
        BizMaterialTransfer transfer = new BizMaterialTransfer();
        transfer.setId(1L);
        transfer.setStatus("APPROVED");
        when(transferMapper.selectById(1L)).thenReturn(transfer);
    
        materialTransferService.onApproved(1L);
    
        verify(transferDetailMapper, never()).selectList(any());
        verify(stockMapper, never()).updateById(any());
    }
    
    @Test
    @DisplayName("审批通过：调出方库存不足拒绝")
    void testOnApproved_stockInsufficient() {
        BizMaterialTransfer transfer = new BizMaterialTransfer();
        transfer.setId(1L);
        transfer.setStatus("SUBMITTED");
        transfer.setFromProjectId(1L);
        transfer.setToProjectId(2L);
        when(transferMapper.selectById(1L)).thenReturn(transfer);
    
        BizMaterialTransferDetail detail = new BizMaterialTransferDetail();
        detail.setMaterialName("钢筋");
        detail.setSpecification("HRB400");
        detail.setQuantity(new BigDecimal("100"));
        when(transferDetailMapper.selectList(any())).thenReturn(List.of(detail));
    
        BizProjectMaterialStock fromStock = new BizProjectMaterialStock();
        fromStock.setStockQuantity(new BigDecimal("10"));
        when(stockMapper.selectOne(any())).thenReturn(fromStock);
    
        assertThatThrownBy(() -> materialTransferService.onApproved(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存不足");
    }
    
    @Test
    @DisplayName("提交调拨：DRAFT→SUBMITTED并启动流程")
    void testSubmit() {
        BizMaterialTransfer transfer = new BizMaterialTransfer();
        transfer.setId(1L);
        transfer.setStatus("DRAFT");
        when(transferMapper.selectById(1L)).thenReturn(transfer);
        when(approvalService.startProcess(eq("MATERIAL_TRANSFER"), eq(1L), eq("material_transfer_approval"), anyMap()))
                .thenReturn("proc-1");
    
        materialTransferService.submit(1L);
    
        assertThat(transfer.getStatus()).isEqualTo("SUBMITTED");
        assertThat(transfer.getWorkflowInstanceId()).isEqualTo("proc-1");
    }
    
    @Test
    @DisplayName("提交调拨：非DRAFT/REJECTED拒绝")
    void testSubmit_nonDraft() {
        BizMaterialTransfer transfer = new BizMaterialTransfer();
        transfer.setId(1L);
        transfer.setStatus("APPROVED");
        when(transferMapper.selectById(1L)).thenReturn(transfer);
    
        assertThatThrownBy(() -> materialTransferService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿或已驳回状态可提交");
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
    @DisplayName("删除：E2E_TEST_ 标记数据非DRAFT放行（E2eTestGuard）")
    void testDelete_e2eMarkerBypass() {
        BizMaterialTransfer transfer = new BizMaterialTransfer();
        transfer.setId(1L);
        transfer.setStatus("APPROVED");
        transfer.setFromProjectName("E2E_TEST_1723900000000_调出");
        when(transferMapper.selectById(1L)).thenReturn(transfer);

        materialTransferService.delete(1L);

        verify(transferMapper).deleteById(1L);
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
