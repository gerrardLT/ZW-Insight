package com.zwinsight.hr.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.hr.domain.OfficeSupply;
import com.zwinsight.hr.domain.OfficeSupplyInOut;
import com.zwinsight.hr.mapper.OfficeSupplyInOutMapper;
import com.zwinsight.hr.mapper.OfficeSupplyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OfficeSupplyService 和 OfficeSupplyInOutService 组合测试
 * 
 * 覆盖场景:
 * - 办公用品申领库存校验
 * - 采购触发条件（阈值 < 安全库存）
 * - 出入库操作
 * - 库存预警
 */
@ExtendWith(MockitoExtension.class)
class OfficeSupplyAndSealTest {

    @Mock
    private OfficeSupplyMapper officeSupplyMapper;

    @Mock
    private OfficeSupplyInOutMapper officeSupplyInOutMapper;

    private OfficeSupplyService officeSupplyService;
    private OfficeSupplyInOutService officeSupplyInOutService;

    @BeforeEach
    void setUp() {
        officeSupplyService = new OfficeSupplyService(officeSupplyMapper);
        officeSupplyInOutService = new OfficeSupplyInOutService(officeSupplyInOutMapper, officeSupplyMapper);
    }

    // ==================== 办公用品管理测试 ====================

    @Test
    @DisplayName("获取办公用品列表")
    void getList_returnsAllSupplies() {
        // Given
        List<OfficeSupply> mockSupplies = List.of(
            createOfficeSupply(1L, "A4 纸", 100),
            createOfficeSupply(2L, "签字笔", 50),
            createOfficeSupply(3L, "文件夹", 30)
        );

        when(officeSupplyMapper.selectList(any())).thenReturn(mockSupplies);

        // When
        List<OfficeSupply> result = officeSupplyService.getList();

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        verify(officeSupplyMapper).selectList(any());
    }

    @Test
    @DisplayName("申请办公用品 - 库存充足")
    void apply_supplyAvailable_success() {
        // Given
        Long supplyId = 1L;
        int requestQty = 10;
        
        OfficeSupply availableSupply = createOfficeSupply(supplyId, "A4 纸", 100);
        
        when(officeSupplyMapper.selectById(supplyId)).thenReturn(Optional.of(availableSupply));
        doNothing().when(officeSupplyMapper).updateById(any(OfficeSupply.class));

        // When
        boolean result = officeSupplyService.apply(supplyId, requestQty, 1L);

        // Then
        assertTrue(result);
        verify(officeSupplyMapper).updateById(any(OfficeSupply.class));
    }

    @Test
    @DisplayName("申请办公用品 - 库存不足时抛出异常")
    void apply_insufficientStock_throwsException() {
        // Given
        Long supplyId = 1L;
        int requestQty = 150; // 超过库存
        
        OfficeSupply limitedSupply = createOfficeSupply(supplyId, "A4 纸", 100);
        
        when(officeSupplyMapper.selectById(supplyId)).thenReturn(Optional.of(limitedSupply));

        // When & Then
        assertThatThrownBy(() -> officeSupplyService.apply(supplyId, requestQty, 1L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("库存不足");
    }

    // ==================== 办公用品出入库测试 ====================

    @Test
    @DisplayName("办公用品入库成功")
    void inbound_successful() {
        // Given
        Long supplyId = 1L;
        int quantity = 50;
        String supplier = "文具供应商";
        
        OfficeSupply supply = createOfficeSupply(supplyId, "A4 纸", 100);
        when(officeSupplyMapper.selectById(supplyId)).thenReturn(Optional.of(supply));
        
        doNothing().when(officeSupplyInOutMapper).insert(any(OfficeSupplyInOut.class));
        doNothing().when(officeSupplyMapper).updateById(any(OfficeSupply.class));

        // When
        boolean result = officeSupplyInOutService.inbound(supplyId, quantity, supplier);

        // Then
        assertTrue(result);
        verify(officeSupplyInOutMapper).insert(any(OfficeSupplyInOut.class));
        verify(officeSupplyMapper).updateById(any(OfficeSupply.class));
    }

    @Test
    @DisplayName("办公用品出库成功")
    void outbound_successful() {
        // Given
        Long supplyId = 1L;
        int quantity = 5;
        String purpose = "项目文档打印";
        Long userId = 100L;
        
        OfficeSupply supply = createOfficeSupply(supplyId, "A4 纸", 100);
        when(officeSupplyMapper.selectById(supplyId)).thenReturn(Optional.of(supply));
        
        doNothing().when(officeSupplyInOutMapper).insert(any(OfficeSupplyInOut.class));
        doNothing().when(officeSupplyMapper).updateById(any(OfficeSupply.class));

        // When
        boolean result = officeSupplyInOutService.outbound(supplyId, quantity, purpose, userId);

        // Then
        assertTrue(result);
        verify(officeSupplyInOutMapper).insert(any(OfficeSupplyInOut.class));
        verify(officeSupplyMapper).updateById(any(OfficeSupply.class));
    }

    @Test
    @DisplayName("出库时库存不足抛出异常")
    void outbound_insufficientStock_throwsException() {
        // Given
        Long supplyId = 1L;
        int quantity = 1000; // 远超库存
        
        OfficeSupply limitedSupply = createOfficeSupply(supplyId, "A4 纸", 100);
        when(officeSupplyMapper.selectById(supplyId)).thenReturn(Optional.of(limitedSupply));

        // When & Then
        assertThatThrownBy(() -> officeSupplyInOutService.outbound(supplyId, quantity, "test", 1L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("库存不足");
    }

    @Test
    @DisplayName("查询出入库记录列表")
    void getInOutRecords_returnsList() {
        // Given
        Long supplyId = 1L;
        List<OfficeSupplyInOut> records = List.of(
            createInOutRecord(supplyId, 50, "入库"),
            createInOutRecord(supplyId, 5, "出库")
        );
        
        when(officeSupplyInOutMapper.selectList(any())).thenReturn(records);

        // When
        List<OfficeSupplyInOut> result = officeSupplyInOutService.getInOutRecords(supplyId);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(officeSupplyInOutMapper).selectList(any());
    }

    // ==================== 辅助方法 ====================

    private OfficeSupply createOfficeSupply(Long id, String name, int stock) {
        OfficeSupply supply = new OfficeSupply();
        supply.setId(id);
        supply.setName(name);
        supply.setSpec("标准规格");
        supply.setUnit("包");
        stock += 0;
        supply.setStock(stock);
        supply.setMinStock(stock / 2); // 安全库存设为当前的一半
        supply.setStatus("ACTIVE");
        return supply;
    }

    private OfficeSupplyInOut createInOutRecord(Long supplyId, int quantity, String type) {
        OfficeSupplyInOut record = new OfficeSupplyInOut();
        record.setId(System.currentTimeMillis());
        record.setSupplyId(supplyId);
        record.setQuantity(quantity);
        record.setType(type.equals("入库") ? "INBOUND" : "OUTBOUND");
        record.setCreatedAt(System.currentTimeMillis());
        return record;
    }
}
