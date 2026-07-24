package com.zwinsight.material.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.material.domain.BizMaterialInbound;
import com.zwinsight.material.domain.BizMaterialInboundDetail;
import com.zwinsight.material.domain.BizMaterialOutbound;
import com.zwinsight.material.domain.BizMaterialOutboundDetail;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.mapper.BizMaterialInboundDetailMapper;
import com.zwinsight.material.mapper.BizMaterialInboundMapper;
import com.zwinsight.material.mapper.BizMaterialOutboundDetailMapper;
import com.zwinsight.material.mapper.BizMaterialOutboundMapper;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.purchase.mapper.BizPurchaseContractMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 材料模块核心服务综合单元测试
 * 覆盖：入库增加库存、出库减少库存、出库超量抛异常、库存查询、首次入库创建记录
 */
@DisplayName("材料模块核心业务测试")
class MaterialServiceTest {

    // ===================================================================
    // 入库增加库存测试
    // ===================================================================

    @Nested
    @DisplayName("入库增加库存")
    @ExtendWith(MockitoExtension.class)
    class InboundStockIncrease {

        @Mock private BizMaterialInboundMapper inboundMapper;
        @Mock private BizMaterialInboundDetailMapper inboundDetailMapper;
        @Mock private BizProjectMaterialStockMapper stockMapper;
        @Mock private BizMaterialOutboundMapper outboundMapper;
        @Mock private BizMaterialOutboundDetailMapper outboundDetailMapper;
        @Mock private BizPurchaseContractMapper purchaseContractMapper;

        @InjectMocks
        private MaterialInboundService inboundService;

        @Test
        @DisplayName("提交入库 - 已有库存记录时累加库存数量")
        void submit_existingStock_increasesQuantity() {
            // Given
            BizMaterialInbound inbound = new BizMaterialInbound();
            inbound.setId(1L);
            inbound.setProjectId(10L);
            inbound.setStatus("DRAFT");
            inbound.setTotalAmount(new BigDecimal("5000"));
            when(inboundMapper.selectById(1L)).thenReturn(inbound);

            BizMaterialInboundDetail detail = new BizMaterialInboundDetail();
            detail.setMaterialName("水泥");
            detail.setSpecification("P.O42.5");
            detail.setUnit("吨");
            detail.setQuantity(new BigDecimal("50"));
            detail.setUnitPrice(new BigDecimal("100"));
            when(inboundDetailMapper.selectList(any())).thenReturn(List.of(detail));

            BizProjectMaterialStock existingStock = new BizProjectMaterialStock();
            existingStock.setProjectId(10L);
            existingStock.setMaterialName("水泥");
            existingStock.setSpecification("P.O42.5");
            existingStock.setStockQuantity(new BigDecimal("100"));
            existingStock.setAvgUnitPrice(new BigDecimal("90"));
            existingStock.setTotalInbound(new BigDecimal("100"));
            existingStock.setTotalOutbound(BigDecimal.ZERO);
            when(stockMapper.selectOne(any())).thenReturn(existingStock);

            // When
            inboundService.submit(1L);

            // Then
            assertThat(inbound.getStatus()).isEqualTo("APPROVED");
            assertThat(existingStock.getStockQuantity()).isEqualByComparingTo(new BigDecimal("150"));
            assertThat(existingStock.getTotalInbound()).isEqualByComparingTo(new BigDecimal("150"));
            verify(stockMapper).updateById(existingStock);
        }

        @Test
        @DisplayName("提交入库 - 加权平均单价正确计算")
        void submit_existingStock_weightedAvgPrice() {
            // Given: 库存 100 个 × 90 元，新入库 50 个 × 120 元
            BizMaterialInbound inbound = new BizMaterialInbound();
            inbound.setId(2L);
            inbound.setProjectId(10L);
            inbound.setStatus("DRAFT");
            inbound.setTotalAmount(new BigDecimal("6000"));
            when(inboundMapper.selectById(2L)).thenReturn(inbound);

            BizMaterialInboundDetail detail = new BizMaterialInboundDetail();
            detail.setMaterialName("钢管");
            detail.setSpecification("DN50");
            detail.setUnit("米");
            detail.setQuantity(new BigDecimal("50"));
            detail.setUnitPrice(new BigDecimal("120"));
            when(inboundDetailMapper.selectList(any())).thenReturn(List.of(detail));

            BizProjectMaterialStock existingStock = new BizProjectMaterialStock();
            existingStock.setProjectId(10L);
            existingStock.setMaterialName("钢管");
            existingStock.setSpecification("DN50");
            existingStock.setStockQuantity(new BigDecimal("100"));
            existingStock.setAvgUnitPrice(new BigDecimal("90"));
            existingStock.setTotalInbound(new BigDecimal("100"));
            existingStock.setTotalOutbound(BigDecimal.ZERO);
            when(stockMapper.selectOne(any())).thenReturn(existingStock);

            // When
            inboundService.submit(2L);

            // Then: 加权平均 = (100×90 + 50×120) / 150 = 15000/150 = 100.0000
            assertThat(existingStock.getAvgUnitPrice()).isEqualByComparingTo(new BigDecimal("100.0000"));
            assertThat(existingStock.getStockQuantity()).isEqualByComparingTo(new BigDecimal("150"));
        }
    }

    // ===================================================================
    // 出库减少库存测试
    // ===================================================================

    @Nested
    @DisplayName("出库减少库存")
    @ExtendWith(MockitoExtension.class)
    class OutboundStockDecrease {

        @Mock private BizMaterialOutboundMapper outboundMapper;
        @Mock private BizMaterialOutboundDetailMapper outboundDetailMapper;
        @Mock private BizProjectMaterialStockMapper stockMapper;
        @Mock private ApplicationEventPublisher eventPublisher;
        @Mock private BizProjectMapper projectMapper;

        @InjectMocks
        private MaterialOutboundService outboundService;

        @Test
        @DisplayName("领料出库 - 库存充足时正常扣减")
        void save_pick_sufficientStock_decreasesQuantity() {
            // Given
            BizMaterialOutbound outbound = new BizMaterialOutbound();
            outbound.setProjectId(10L);
            outbound.setOutboundType("PICK");

            BizMaterialOutboundDetail detail = new BizMaterialOutboundDetail();
            detail.setMaterialName("水泥");
            detail.setSpecification("P.O42.5");
            detail.setQuantity(new BigDecimal("30"));

            BizProjectMaterialStock stock = new BizProjectMaterialStock();
            stock.setStockQuantity(new BigDecimal("100"));
            stock.setTotalOutbound(new BigDecimal("20"));
            when(stockMapper.selectOne(any())).thenReturn(stock);

            // When
            outboundService.save(outbound, List.of(detail));

            // Then
            assertThat(stock.getStockQuantity()).isEqualByComparingTo(new BigDecimal("70"));
            assertThat(stock.getTotalOutbound()).isEqualByComparingTo(new BigDecimal("50"));
            verify(stockMapper).updateById(stock);
        }

        @Test
        @DisplayName("退货出库 - 库存充足时扣减并累加退货量")
        void save_return_sufficientStock_decreasesAndTracksReturn() {
            // Given
            BizMaterialOutbound outbound = new BizMaterialOutbound();
            outbound.setProjectId(10L);
            outbound.setOutboundType("RETURN");

            BizMaterialOutboundDetail detail = new BizMaterialOutboundDetail();
            detail.setMaterialName("钢筋");
            detail.setSpecification("HRB400");
            detail.setQuantity(new BigDecimal("10"));

            BizProjectMaterialStock stock = new BizProjectMaterialStock();
            stock.setStockQuantity(new BigDecimal("50"));
            stock.setTotalReturn(BigDecimal.ZERO);
            when(stockMapper.selectOne(any())).thenReturn(stock);

            // When
            outboundService.save(outbound, List.of(detail));

            // Then
            assertThat(stock.getStockQuantity()).isEqualByComparingTo(new BigDecimal("40"));
            assertThat(stock.getTotalReturn()).isEqualByComparingTo(new BigDecimal("10"));
        }
    }

    // ===================================================================
    // 出库超量抛 BusinessException 测试
    // ===================================================================

    @Nested
    @DisplayName("出库超量抛异常")
    @ExtendWith(MockitoExtension.class)
    class OutboundExceedsStock {

        @Mock private BizMaterialOutboundMapper outboundMapper;
        @Mock private BizMaterialOutboundDetailMapper outboundDetailMapper;
        @Mock private BizProjectMaterialStockMapper stockMapper;
        @Mock private ApplicationEventPublisher eventPublisher;
        @Mock private BizProjectMapper projectMapper;

        @InjectMocks
        private MaterialOutboundService outboundService;

        @Test
        @DisplayName("领料出库 - 出库数量超过库存抛BusinessException")
        void save_pick_insufficientStock_throwsBusinessException() {
            // Given
            BizMaterialOutbound outbound = new BizMaterialOutbound();
            outbound.setProjectId(10L);
            outbound.setOutboundType("PICK");

            BizMaterialOutboundDetail detail = new BizMaterialOutboundDetail();
            detail.setMaterialName("钢管");
            detail.setSpecification("DN100");
            detail.setQuantity(new BigDecimal("200")); // 出库 200

            BizProjectMaterialStock stock = new BizProjectMaterialStock();
            stock.setStockQuantity(new BigDecimal("50")); // 仅有 50
            when(stockMapper.selectOne(any())).thenReturn(stock);

            // When & Then
            assertThatThrownBy(() -> outboundService.save(outbound, List.of(detail)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("库存不足");
        }

        @Test
        @DisplayName("领料出库 - 库存记录不存在抛BusinessException")
        void save_pick_noStockRecord_throwsBusinessException() {
            // Given
            BizMaterialOutbound outbound = new BizMaterialOutbound();
            outbound.setProjectId(10L);
            outbound.setOutboundType("PICK");

            BizMaterialOutboundDetail detail = new BizMaterialOutboundDetail();
            detail.setMaterialName("新材料");
            detail.setSpecification("规格A");
            detail.setQuantity(new BigDecimal("5"));

            when(stockMapper.selectOne(any())).thenReturn(null); // 无库存记录

            // When & Then
            assertThatThrownBy(() -> outboundService.save(outbound, List.of(detail)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("库存不足");
        }

        @Test
        @DisplayName("退货出库 - 库存不足抛BusinessException")
        void save_return_insufficientStock_throwsBusinessException() {
            // Given
            BizMaterialOutbound outbound = new BizMaterialOutbound();
            outbound.setProjectId(10L);
            outbound.setOutboundType("RETURN");

            BizMaterialOutboundDetail detail = new BizMaterialOutboundDetail();
            detail.setMaterialName("水泥");
            detail.setSpecification("P.O42.5");
            detail.setQuantity(new BigDecimal("80"));

            BizProjectMaterialStock stock = new BizProjectMaterialStock();
            stock.setStockQuantity(new BigDecimal("30")); // 仅 30
            when(stockMapper.selectOne(any())).thenReturn(stock);

            // When & Then
            assertThatThrownBy(() -> outboundService.save(outbound, List.of(detail)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("库存不足");
        }
    }

    // ===================================================================
    // 库存查询测试
    // ===================================================================

    @Nested
    @DisplayName("库存查询")
    @ExtendWith(MockitoExtension.class)
    class StockQuery {

        @Mock private BizProjectMaterialStockMapper stockMapper;
        @Mock private com.zwinsight.material.mapper.BizStockWarningConfigMapper warningConfigMapper;
        @Mock private com.zwinsight.project.mapper.BizProjectMapper projectMapper;

        @InjectMocks
        private ProjectMaterialStockService stockService;

        @Test
        @DisplayName("分页查询库存 - 返回正确分页结果")
        void page_returnsPageResult() {
            // Given
            BizProjectMaterialStock stock1 = new BizProjectMaterialStock();
            stock1.setProjectId(10L);
            stock1.setMaterialName("水泥");
            stock1.setStockQuantity(new BigDecimal("100"));

            BizProjectMaterialStock stock2 = new BizProjectMaterialStock();
            stock2.setProjectId(10L);
            stock2.setMaterialName("钢筋");
            stock2.setStockQuantity(new BigDecimal("50"));

            Page<BizProjectMaterialStock> mockPage = new Page<>(1, 10);
            mockPage.setRecords(List.of(stock1, stock2));
            mockPage.setTotal(2);

            when(stockMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

            // When
            PageResult<BizProjectMaterialStock> result = stockService.page(1, 10, 10L, null, null, null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getRecords()).hasSize(2);
            assertThat(result.getTotal()).isEqualTo(2);
            assertThat(result.getRecords().get(0).getMaterialName()).isEqualTo("水泥");
        }

        @Test
        @DisplayName("按项目查询库存列表 - 返回该项目所有库存")
        void getByProject_returnsProjectStockList() {
            // Given
            BizProjectMaterialStock stock = new BizProjectMaterialStock();
            stock.setProjectId(10L);
            stock.setMaterialName("砂石");
            stock.setStockQuantity(new BigDecimal("500"));

            when(stockMapper.selectList(any())).thenReturn(List.of(stock));

            // When
            var stockList = stockService.getByProject(10L);

            // Then
            assertThat(stockList).hasSize(1);
            assertThat(stockList.get(0).getMaterialName()).isEqualTo("砂石");
            assertThat(stockList.get(0).getProjectId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("分页查询库存 - projectId为null时查询全部")
        void page_nullProjectId_queriesAll() {
            // Given
            Page<BizProjectMaterialStock> mockPage = new Page<>(1, 10);
            mockPage.setRecords(List.of());
            mockPage.setTotal(0);
            when(stockMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

            // When
            PageResult<BizProjectMaterialStock> result = stockService.page(1, 10, null, null, null, null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getRecords()).isEmpty();
        }
    }

    // ===================================================================
    // 首次入库创建记录测试
    // ===================================================================

    @Nested
    @DisplayName("首次入库创建库存记录")
    @ExtendWith(MockitoExtension.class)
    class FirstInboundCreatesStock {

        @Mock private BizMaterialInboundMapper inboundMapper;
        @Mock private BizMaterialInboundDetailMapper inboundDetailMapper;
        @Mock private BizProjectMaterialStockMapper stockMapper;
        @Mock private BizMaterialOutboundMapper outboundMapper;
        @Mock private BizMaterialOutboundDetailMapper outboundDetailMapper;
        @Mock private BizPurchaseContractMapper purchaseContractMapper;

        @InjectMocks
        private MaterialInboundService inboundService;

        @Test
        @DisplayName("首次入库 - 库存记录不存在时创建新记录")
        void submit_noExistingStock_createsNewStockRecord() {
            // Given
            BizMaterialInbound inbound = new BizMaterialInbound();
            inbound.setId(1L);
            inbound.setProjectId(10L);
            inbound.setStatus("DRAFT");
            inbound.setTotalAmount(new BigDecimal("3000"));
            when(inboundMapper.selectById(1L)).thenReturn(inbound);

            BizMaterialInboundDetail detail = new BizMaterialInboundDetail();
            detail.setMaterialName("混凝土");
            detail.setSpecification("C30");
            detail.setUnit("立方米");
            detail.setQuantity(new BigDecimal("30"));
            detail.setUnitPrice(new BigDecimal("100"));
            when(inboundDetailMapper.selectList(any())).thenReturn(List.of(detail));

            when(stockMapper.selectOne(any())).thenReturn(null); // 无库存记录

            // When
            inboundService.submit(1L);

            // Then
            ArgumentCaptor<BizProjectMaterialStock> captor = ArgumentCaptor.forClass(BizProjectMaterialStock.class);
            verify(stockMapper).insert(captor.capture());

            BizProjectMaterialStock newStock = captor.getValue();
            assertThat(newStock.getProjectId()).isEqualTo(10L);
            assertThat(newStock.getMaterialName()).isEqualTo("混凝土");
            assertThat(newStock.getSpecification()).isEqualTo("C30");
            assertThat(newStock.getUnit()).isEqualTo("立方米");
            assertThat(newStock.getStockQuantity()).isEqualByComparingTo(new BigDecimal("30"));
            assertThat(newStock.getAvgUnitPrice()).isEqualByComparingTo(new BigDecimal("100"));
            assertThat(newStock.getTotalInbound()).isEqualByComparingTo(new BigDecimal("30"));
            assertThat(newStock.getTotalOutbound()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(newStock.getTotalReturn()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("首次入库 - 多条明细分别创建独立库存记录")
        void submit_multipleDetails_createsMultipleStockRecords() {
            // Given
            BizMaterialInbound inbound = new BizMaterialInbound();
            inbound.setId(3L);
            inbound.setProjectId(10L);
            inbound.setStatus("DRAFT");
            inbound.setTotalAmount(new BigDecimal("8000"));
            when(inboundMapper.selectById(3L)).thenReturn(inbound);

            BizMaterialInboundDetail detail1 = new BizMaterialInboundDetail();
            detail1.setMaterialName("水泥");
            detail1.setSpecification("P.O42.5");
            detail1.setUnit("吨");
            detail1.setQuantity(new BigDecimal("50"));
            detail1.setUnitPrice(new BigDecimal("100"));

            BizMaterialInboundDetail detail2 = new BizMaterialInboundDetail();
            detail2.setMaterialName("钢筋");
            detail2.setSpecification("HRB400");
            detail2.setUnit("吨");
            detail2.setQuantity(new BigDecimal("30"));
            detail2.setUnitPrice(new BigDecimal("100"));

            when(inboundDetailMapper.selectList(any())).thenReturn(List.of(detail1, detail2));
            when(stockMapper.selectOne(any())).thenReturn(null); // 均无库存记录

            // When
            inboundService.submit(3L);

            // Then
            verify(stockMapper, times(2)).insert(any(BizProjectMaterialStock.class));
        }

        @Test
        @DisplayName("首次入库 - 新记录的 totalTransferIn 和 totalTransferOut 初始化为零")
        void submit_newStockRecord_transferFieldsInitializedToZero() {
            // Given
            BizMaterialInbound inbound = new BizMaterialInbound();
            inbound.setId(4L);
            inbound.setProjectId(10L);
            inbound.setStatus("DRAFT");
            inbound.setTotalAmount(new BigDecimal("2000"));
            when(inboundMapper.selectById(4L)).thenReturn(inbound);

            BizMaterialInboundDetail detail = new BizMaterialInboundDetail();
            detail.setMaterialName("砂石");
            detail.setSpecification("中砂");
            detail.setUnit("吨");
            detail.setQuantity(new BigDecimal("20"));
            detail.setUnitPrice(new BigDecimal("50"));
            when(inboundDetailMapper.selectList(any())).thenReturn(List.of(detail));

            when(stockMapper.selectOne(any())).thenReturn(null);

            // When
            inboundService.submit(4L);

            // Then
            ArgumentCaptor<BizProjectMaterialStock> captor = ArgumentCaptor.forClass(BizProjectMaterialStock.class);
            verify(stockMapper).insert(captor.capture());

            BizProjectMaterialStock newStock = captor.getValue();
            assertThat(newStock.getTotalTransferIn()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(newStock.getTotalTransferOut()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}
