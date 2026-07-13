package com.zwinsight.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.material.domain.BizMaterialInventory;
import com.zwinsight.material.domain.BizProjectMaterialStock;
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
    @DisplayName("保存盘点单")
    class SaveTests {

        @Test
        @DisplayName("保存盘点单 — 正常路径（库存调整为盘点数量）")
        void testSave_happyPath_adjustStock() {
            when(inventoryMapper.insert(any(BizMaterialInventory.class))).thenReturn(1);
            when(stockMapper.selectById(10L)).thenReturn(sampleStock);
            when(stockMapper.updateById(any(BizProjectMaterialStock.class))).thenReturn(1);

            BizMaterialInventory inventory = new BizMaterialInventory();
            inventory.setProjectId(100L);
            inventory.setInventoryDate(LocalDate.now());

            // 盘点数量=45，原库存=50，差异=-5
            Map<Long, BigDecimal> adjustments = new HashMap<>();
            adjustments.put(10L, new BigDecimal("45.00"));

            inventoryService.save(inventory, adjustments);

            // 验证状态被设为 DRAFT
            assertThat(inventory.getStatus()).isEqualTo("DRAFT");
            verify(inventoryMapper).insert(inventory);
            // 验证库存被更新为盘点数量
            verify(stockMapper).updateById(argThat(stock ->
                    stock.getStockQuantity().compareTo(new BigDecimal("45.00")) == 0
            ));
        }

        @Test
        @DisplayName("保存盘点单 — 库存增加（盘点数量 > 当前库存）")
        void testSave_stockIncrease() {
            when(inventoryMapper.insert(any(BizMaterialInventory.class))).thenReturn(1);
            when(stockMapper.selectById(10L)).thenReturn(sampleStock);
            when(stockMapper.updateById(any(BizProjectMaterialStock.class))).thenReturn(1);

            BizMaterialInventory inventory = new BizMaterialInventory();
            inventory.setProjectId(100L);

            // 盘点数量=80，原库存=50，库存增加
            Map<Long, BigDecimal> adjustments = new HashMap<>();
            adjustments.put(10L, new BigDecimal("80.00"));

            inventoryService.save(inventory, adjustments);

            verify(stockMapper).updateById(argThat(stock ->
                    stock.getStockQuantity().compareTo(new BigDecimal("80.00")) == 0
            ));
        }

        @Test
        @DisplayName("保存盘点单 — 库存减少（盘点数量 < 当前库存）")
        void testSave_stockDecrease() {
            when(inventoryMapper.insert(any(BizMaterialInventory.class))).thenReturn(1);
            when(stockMapper.selectById(10L)).thenReturn(sampleStock);
            when(stockMapper.updateById(any(BizProjectMaterialStock.class))).thenReturn(1);

            BizMaterialInventory inventory = new BizMaterialInventory();
            inventory.setProjectId(100L);

            // 盘点数量=20，原库存=50，库存减少
            Map<Long, BigDecimal> adjustments = new HashMap<>();
            adjustments.put(10L, new BigDecimal("20.00"));

            inventoryService.save(inventory, adjustments);

            verify(stockMapper).updateById(argThat(stock ->
                    stock.getStockQuantity().compareTo(new BigDecimal("20.00")) == 0
            ));
        }

        @Test
        @DisplayName("保存盘点单 — 盘点数量为 0")
        void testSave_adjustToZero() {
            when(inventoryMapper.insert(any(BizMaterialInventory.class))).thenReturn(1);
            when(stockMapper.selectById(10L)).thenReturn(sampleStock);
            when(stockMapper.updateById(any(BizProjectMaterialStock.class))).thenReturn(1);

            BizMaterialInventory inventory = new BizMaterialInventory();
            inventory.setProjectId(100L);

            Map<Long, BigDecimal> adjustments = new HashMap<>();
            adjustments.put(10L, BigDecimal.ZERO);

            inventoryService.save(inventory, adjustments);

            verify(stockMapper).updateById(argThat(stock ->
                    stock.getStockQuantity().compareTo(BigDecimal.ZERO) == 0
            ));
        }

        @Test
        @DisplayName("保存盘点单 — stockId 查询不到时不更新")
        void testSave_stockNotFound_skipped() {
            when(inventoryMapper.insert(any(BizMaterialInventory.class))).thenReturn(1);
            when(stockMapper.selectById(999L)).thenReturn(null);

            BizMaterialInventory inventory = new BizMaterialInventory();
            inventory.setProjectId(100L);

            Map<Long, BigDecimal> adjustments = new HashMap<>();
            adjustments.put(999L, new BigDecimal("10.00"));

            inventoryService.save(inventory, adjustments);

            verify(inventoryMapper).insert(inventory);
            verify(stockMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("保存盘点单 — adjustments 为 null 时只保存盘点单")
        void testSave_nullAdjustments() {
            when(inventoryMapper.insert(any(BizMaterialInventory.class))).thenReturn(1);

            BizMaterialInventory inventory = new BizMaterialInventory();
            inventory.setProjectId(100L);

            inventoryService.save(inventory, null);

            verify(inventoryMapper).insert(inventory);
            verify(stockMapper, never()).selectById(anyLong());
            verify(stockMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("保存盘点单 — adjustments 为空 Map 时只保存盘点单")
        void testSave_emptyAdjustments() {
            when(inventoryMapper.insert(any(BizMaterialInventory.class))).thenReturn(1);

            BizMaterialInventory inventory = new BizMaterialInventory();
            inventory.setProjectId(100L);

            inventoryService.save(inventory, new HashMap<>());

            verify(inventoryMapper).insert(inventory);
            verify(stockMapper, never()).selectById(anyLong());
        }

        @Test
        @DisplayName("保存盘点单 — 多条库存调整")
        void testSave_multipleAdjustments() {
            BizProjectMaterialStock stock2 = new BizProjectMaterialStock();
            stock2.setId(11L);
            stock2.setStockQuantity(new BigDecimal("100.00"));

            when(inventoryMapper.insert(any(BizMaterialInventory.class))).thenReturn(1);
            when(stockMapper.selectById(10L)).thenReturn(sampleStock);
            when(stockMapper.selectById(11L)).thenReturn(stock2);
            when(stockMapper.updateById(any(BizProjectMaterialStock.class))).thenReturn(1);

            BizMaterialInventory inventory = new BizMaterialInventory();
            inventory.setProjectId(100L);

            Map<Long, BigDecimal> adjustments = new HashMap<>();
            adjustments.put(10L, new BigDecimal("45.00"));
            adjustments.put(11L, new BigDecimal("90.00"));

            inventoryService.save(inventory, adjustments);

            verify(stockMapper, times(2)).updateById(any(BizProjectMaterialStock.class));
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
