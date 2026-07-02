package com.zwinsight.basedata.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.basedata.domain.BdSupplier;
import com.zwinsight.basedata.mapper.BdSupplierMapper;
import com.zwinsight.common.exception.BusinessException;
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
class SupplierServiceTest {

    @Mock private BdSupplierMapper supplierMapper;

    @InjectMocks
    private SupplierService supplierService;

    @Test
    @DisplayName("根据ID查询：存在返回供应商")
    void testGetById_found() {
        BdSupplier supplier = new BdSupplier();
        supplier.setId(1L);
        supplier.setSupplierName("测试供应商");
        when(supplierMapper.selectById(1L)).thenReturn(supplier);

        BdSupplier result = supplierService.getById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getSupplierName()).isEqualTo("测试供应商");
    }

    @Test
    @DisplayName("根据ID查询：不存在抛异常")
    void testGetById_notFound() {
        when(supplierMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> supplierService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商不存在");
    }

    @Test
    @DisplayName("新增供应商：正常保存")
    void testSave() {
        BdSupplier supplier = new BdSupplier();
        supplier.setSupplierName("新供应商");
        when(supplierMapper.insert(any(BdSupplier.class))).thenReturn(1);

        supplierService.save(supplier);

        verify(supplierMapper).insert(supplier);
    }

    @Test
    @DisplayName("更新供应商：存在则更新")
    void testUpdate_found() {
        BdSupplier existing = new BdSupplier();
        existing.setId(1L);
        when(supplierMapper.selectById(1L)).thenReturn(existing);
        when(supplierMapper.updateById(any(BdSupplier.class))).thenReturn(1);

        BdSupplier update = new BdSupplier();
        update.setId(1L);
        update.setSupplierName("更新名称");
        supplierService.update(update);

        verify(supplierMapper).updateById(update);
    }

    @Test
    @DisplayName("更新供应商：不存在抛异常")
    void testUpdate_notFound() {
        when(supplierMapper.selectById(999L)).thenReturn(null);

        BdSupplier update = new BdSupplier();
        update.setId(999L);

        assertThatThrownBy(() -> supplierService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商不存在");
    }

    @Test
    @DisplayName("删除供应商：正常删除")
    void testDelete() {
        when(supplierMapper.deleteById(1L)).thenReturn(1);

        supplierService.delete(1L);

        verify(supplierMapper).deleteById(1L);
    }

    @Test
    @DisplayName("批量删除：调用deleteBatchIds")
    void testBatchDelete() {
        List<Long> ids = List.of(1L, 2L, 3L);
        when(supplierMapper.deleteBatchIds(ids)).thenReturn(3);

        supplierService.batchDelete(ids);

        verify(supplierMapper).deleteBatchIds(ids);
    }
}
