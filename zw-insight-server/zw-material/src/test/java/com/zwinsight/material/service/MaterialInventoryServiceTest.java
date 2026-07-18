package com.zwinsight.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.material.domain.BizMaterialInventory;
import com.zwinsight.material.domain.BizMaterialInventoryDetail;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.mapper.BizMaterialInventoryDetailMapper;
import com.zwinsight.material.mapper.BizMaterialInventoryMapper;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MaterialInventoryService 单元测试
 * 覆盖：盘点单 CRUD、库存调整、状态校验、边界条件
 */
@ExtendWith(MockitoExtension.class)
class MaterialInventoryServiceTest {

    @Mock
    private BizMaterialInventoryMapper inventoryMapper;

    @Mock
    private BizMaterialInventoryDetailMapper detailMapper;

    @Mock
    private BizProjectMaterialStockMapper stockMapper;

    @InjectMocks
    private MaterialInventoryService inventoryService;

    private BizMaterialInventory sampleInventory;
    private BizProjectMaterialStock sampleStock;

    @BeforeEach
    void setUp() {
        sampleInventory = new BizMaterialInventory();
        sampleInventory.setId(1L);
        sampleInventory.setProjectId(100L);
        sampleInventory.setInventoryDate(LocalDate.of(2026, 7, 1));
        sampleInventory.setStatus("DRAFT");

        sampleStock = new BizProjectMaterialStock();
        sampleStock.setId(10L);
        sampleStock.setProjectId(100L);
        sampleStock.setMaterialId(200L);
        sampleStock.setMaterialName("钢筋");
        sampleStock.setSpecification("HRB400 Φ16");
        sampleStock.setUnit("吨");
        sampleStock.setStockQuantity(new BigDecimal("50.00"));
        sampleStock.setAvgUnitPrice(new BigDecimal("4500.00"));
        sampleStock.setTotalInbound(new BigDecimal("100.00"));
        sampleStock.setTotalOutbound(new BigDecimal("50.00"));
        sampleStock.setTotalReturn(BigDecimal.ZERO);
        sampleStock.setTotalTransferIn(BigDecimal.ZERO);
        sampleStock.setTotalTransferOut(BigDecimal.ZERO);
    }

    // =====================================================================
    // 分页查询
    // =====================================================================

    @Nested
    @DisplayName("分页查询")
    class PageTests {

        @Test
        @DisplayName("按项目ID分页查询 — 正常返回")
        void testPage_withProjectId() {
            Page<BizMaterialInventory> mockPage = new Page<>(1, 10);
            mockPage.setTotal(1);
            mockPage.setRecords(java.util.List.of(sampleInventory));

            when(inventoryMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            var result = inventoryService.page(1, 10, 100L);

            assertThat(result).isNotNull();
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).getProjectId()).isEqualTo(100L);
            verify(inventoryMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("projectId 为 null 时查询全部")
        void testPage_withoutProjectId() {
            Page<BizMaterialInventory> mockPage = new Page<>(1, 10);
            mockPage.setTotal(0);
            mockPage.setRecords(java.util.List.of());

            when(inventoryMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            var result = inventoryService.page(1, 10, null);

            assertThat(result).isNotNull();
            assertThat(result.getRecords()).isEmpty();
        }
    }

    // =====================================================================
    // 保存盘点单（差异调整库存）
    // =====================================================================

    @Nested
    @DisplayName("登记盘点单（第一阶段）")
    class SaveTests {

        @Test
        @DisplayName("登记盘点单 — 只落单据+明细，不调库存")
        void testSave_happyPath_persistDetailNotStock() {
            when(inventoryMapper.insert(any(BizMaterialInventory.class))).thenReturn(1);
            when(stockMapper.selectById(10L)).thenReturn(sampleStock);
            when(detailMapper.insert(any(BizMaterialInventoryDetail.class))).thenReturn(1);

            BizMaterialInventory inventory = new BizMaterialInventory();
            inventory.setProjectId(100L);
            inventory.setInventoryDate(LocalDate.now());

            // 实盘数量=45，账面=50，差异=-5
            Map<Long, BigDecimal> adjustments = new HashMap<>();
            adjustments.put(10L, new BigDecimal("45.00"));

            inventoryService.save(inventory, adjustments);

            // 状态为 DRAFT
            assertThat(inventory.getStatus()).isEqualTo("DRAFT");
            verify(inventoryMapper).insert(inventory);
            // 关键：登记阶段不调整库存
            verify(stockMapper, never()).updateById(any());
            // 明细记录账面/实盘/差异
            verify(detailMapper).insert(argThat(d ->
                    d.getStockId().equals(10L)
                            && d.getBookQuantity().compareTo(new BigDecimal("50.00")) == 0
                            && d.getActualQuantity().compareTo(new BigDecimal("45.00")) == 0
                            && d.getDiffQuantity().compareTo(new BigDecimal("-5.00")) == 0
            ));
        }

        @Test
        @DisplayName("登记盘点单 — 盘盈（实盘 > 账面，差异为正）")
        void testSave_stockSurplus() {
            when(inventoryMapper.insert(any(BizMaterialInventory.class))).thenReturn(1);
            when(stockMapper.selectById(10L)).thenReturn(sampleStock);
            when(detailMapper.insert(any(BizMaterialInventoryDetail.class))).thenReturn(1);

            BizMaterialInventory inventory = new BizMaterialInventory();
            inventory.setProjectId(100L);

            Map<Long, BigDecimal> adjustments = new HashMap<>();
            adjustments.put(10L, new BigDecimal("80.00"));

            inventoryService.save(inventory, adjustments);

            verify(detailMapper).insert(argThat(d ->
                    d.getDiffQuantity().compareTo(new BigDecimal("30.00")) == 0
            ));
            verify(stockMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("登记盘点单 — stockId 查不到时跳过，不插明细")
        void testSave_stockNotFound_skipped() {
            when(inventoryMapper.insert(any(BizMaterialInventory.class))).thenReturn(1);
            when(stockMapper.selectById(999L)).thenReturn(null);

            BizMaterialInventory inventory = new BizMaterialInventory();
            inventory.setProjectId(100L);

            Map<Long, BigDecimal> adjustments = new HashMap<>();
            adjustments.put(999L, new BigDecimal("10.00"));

            inventoryService.save(inventory, adjustments);

            verify(inventoryMapper).insert(inventory);
            verify(detailMapper, never()).insert(any());
            verify(stockMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("登记盘点单 — adjustments 为 null 时只保存盘点单")
        void testSave_nullAdjustments() {
            when(inventoryMapper.insert(any(BizMaterialInventory.class))).thenReturn(1);

            BizMaterialInventory inventory = new BizMaterialInventory();
            inventory.setProjectId(100L);

            inventoryService.save(inventory, null);

            verify(inventoryMapper).insert(inventory);
            verify(stockMapper, never()).selectById(anyLong());
            verify(detailMapper, never()).insert(any());
        }

        @Test
        @DisplayName("登记盘点单 — 多条盘点明细")
        void testSave_multipleDetails() {
            BizProjectMaterialStock stock2 = new BizProjectMaterialStock();
            stock2.setId(11L);
            stock2.setStockQuantity(new BigDecimal("100.00"));

            when(inventoryMapper.insert(any(BizMaterialInventory.class))).thenReturn(1);
            when(stockMapper.selectById(10L)).thenReturn(sampleStock);
            when(stockMapper.selectById(11L)).thenReturn(stock2);
            when(detailMapper.insert(any(BizMaterialInventoryDetail.class))).thenReturn(1);

            BizMaterialInventory inventory = new BizMaterialInventory();
            inventory.setProjectId(100L);

            Map<Long, BigDecimal> adjustments = new HashMap<>();
            adjustments.put(10L, new BigDecimal("45.00"));
            adjustments.put(11L, new BigDecimal("90.00"));

            inventoryService.save(inventory, adjustments);

            verify(detailMapper, times(2)).insert(any(BizMaterialInventoryDetail.class));
            verify(stockMapper, never()).updateById(any());
        }
    }

    // =====================================================================
    // 审批盘点单（第二阶段：据实盘数量调整库存）
    // =====================================================================

    @Nested
    @DisplayName("审批盘点单")
    class SubmitTests {

        @Test
        @DisplayName("审批盘点单 — 据实盘数量调整库存并置 APPROVED")
        void testSubmit_happyPath_adjustStock() {
            sampleInventory.setStatus("DRAFT");
            when(inventoryMapper.selectById(1L)).thenReturn(sampleInventory);

            BizMaterialInventoryDetail detail = new BizMaterialInventoryDetail();
            detail.setInventoryId(1L);
            detail.setStockId(10L);
            detail.setBookQuantity(new BigDecimal("50.00"));
            detail.setActualQuantity(new BigDecimal("45.00"));
            detail.setDiffQuantity(new BigDecimal("-5.00"));
            when(detailMapper.selectList(any())).thenReturn(List.of(detail));
            when(stockMapper.selectById(10L)).thenReturn(sampleStock);
            when(stockMapper.updateById(any(BizProjectMaterialStock.class))).thenReturn(1);
            when(inventoryMapper.updateById(any(BizMaterialInventory.class))).thenReturn(1);

            inventoryService.submit(1L);

            // 库存被调为实盘数量
            verify(stockMapper).updateById(argThat(stock ->
                    stock.getStockQuantity().compareTo(new BigDecimal("45.00")) == 0
            ));
            // 状态置 APPROVED
            verify(inventoryMapper).updateById(argThat(inv -> "APPROVED".equals(inv.getStatus())));
        }

        @Test
        @DisplayName("审批盘点单 — 单据不存在抛异常")
        void testSubmit_notFound() {
            when(inventoryMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> inventoryService.submit(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("盘点单不存在");

            verify(stockMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("审批盘点单 — 非 DRAFT 状态拒绝审批")
        void testSubmit_nonDraftRejected() {
            sampleInventory.setStatus("APPROVED");
            when(inventoryMapper.selectById(1L)).thenReturn(sampleInventory);

            assertThatThrownBy(() -> inventoryService.submit(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿状态可审批");

            verify(stockMapper, never()).updateById(any());
            verify(inventoryMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("审批盘点单 — 明细对应库存不存在时跳过，仍置 APPROVED")
        void testSubmit_stockNotFound_skipButApprove() {
            sampleInventory.setStatus("DRAFT");
            when(inventoryMapper.selectById(1L)).thenReturn(sampleInventory);

            BizMaterialInventoryDetail detail = new BizMaterialInventoryDetail();
            detail.setStockId(999L);
            detail.setActualQuantity(new BigDecimal("10.00"));
            when(detailMapper.selectList(any())).thenReturn(List.of(detail));
            when(stockMapper.selectById(999L)).thenReturn(null);
            when(inventoryMapper.updateById(any(BizMaterialInventory.class))).thenReturn(1);

            inventoryService.submit(1L);

            verify(stockMapper, never()).updateById(any());
            verify(inventoryMapper).updateById(argThat(inv -> "APPROVED".equals(inv.getStatus())));
        }
    }

    // =====================================================================
    // getById
    // =====================================================================

    @Nested
    @DisplayName("查询盘点单")
    class GetByIdTests {

        @Test
        @DisplayName("查询盘点单 — 存在则返回")
        void testGetById_found() {
            when(inventoryMapper.selectById(1L)).thenReturn(sampleInventory);

            BizMaterialInventory result = inventoryService.getById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getProjectId()).isEqualTo(100L);
            assertThat(result.getStatus()).isEqualTo("DRAFT");
        }

        @Test
        @DisplayName("查询盘点单 — 不存在抛 BusinessException")
        void testGetById_notFound() {
            when(inventoryMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> inventoryService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("盘点单不存在");
        }
    }

    // =====================================================================
    // update
    // =====================================================================

    @Nested
    @DisplayName("更新盘点单")
    class UpdateTests {

        @Test
        @DisplayName("更新盘点单 — DRAFT 状态可编辑")
        void testUpdate_draftAllowed() {
            when(inventoryMapper.selectById(1L)).thenReturn(sampleInventory);

            BizMaterialInventory update = new BizMaterialInventory();
            update.setId(1L);
            update.setInventoryDate(LocalDate.of(2026, 7, 5));

            inventoryService.update(update);

            verify(inventoryMapper).updateById(update);
        }

        @Test
        @DisplayName("更新盘点单 — 不存在抛异常")
        void testUpdate_notFound() {
            when(inventoryMapper.selectById(999L)).thenReturn(null);

            BizMaterialInventory update = new BizMaterialInventory();
            update.setId(999L);

            assertThatThrownBy(() -> inventoryService.update(update))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("盘点单不存在");
        }

        @Test
        @DisplayName("更新盘点单 — 非 DRAFT 状态拒绝编辑")
        void testUpdate_nonDraftRejected() {
            sampleInventory.setStatus("APPROVED");
            when(inventoryMapper.selectById(1L)).thenReturn(sampleInventory);

            BizMaterialInventory update = new BizMaterialInventory();
            update.setId(1L);

            assertThatThrownBy(() -> inventoryService.update(update))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿状态可编辑");
        }
    }

    // =====================================================================
    // delete
    // =====================================================================

    @Nested
    @DisplayName("删除盘点单")
    class DeleteTests {

        @Test
        @DisplayName("删除盘点单 — DRAFT 状态可删除")
        void testDelete_draftAllowed() {
            when(inventoryMapper.selectById(1L)).thenReturn(sampleInventory);

            inventoryService.delete(1L);

            verify(inventoryMapper).deleteById(1L);
        }

        @Test
        @DisplayName("删除盘点单 — 不存在抛异常")
        void testDelete_notFound() {
            when(inventoryMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> inventoryService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("盘点单不存在");
        }

        @Test
        @DisplayName("删除盘点单 — 非 DRAFT 状态拒绝删除")
        void testDelete_nonDraftRejected() {
            sampleInventory.setStatus("APPROVED");
            when(inventoryMapper.selectById(1L)).thenReturn(sampleInventory);

            assertThatThrownBy(() -> inventoryService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿状态可删除");
        }
    }
}
