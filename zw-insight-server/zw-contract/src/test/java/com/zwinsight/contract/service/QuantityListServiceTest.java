package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizQuantityList;
import com.zwinsight.contract.mapper.BizQuantityListMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * QuantityListService 单元测试
 * <p>工程量清单：金额=数量×单价自动计算，批量导入守卫与 IO 异常包装。</p>
 */
@ExtendWith(MockitoExtension.class)
class QuantityListServiceTest {

    @Mock
    private BizQuantityListMapper quantityListMapper;

    @InjectMocks
    private QuantityListService service;

    private BizQuantityList item(Long id, String quantity, String unitPrice) {
        BizQuantityList q = new BizQuantityList();
        q.setId(id);
        q.setQuantity(quantity == null ? null : new BigDecimal(quantity));
        q.setUnitPrice(unitPrice == null ? null : new BigDecimal(unitPrice));
        return q;
    }

    @Test
    @DisplayName("page - 分页透传")
    void page_delegates() {
        Page<BizQuantityList> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(item(1L, "10", "5")));
        page.setTotal(1L);
        when(quantityListMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BizQuantityList> result = service.page(1, 10, 1L, 2L);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("save - 数量与单价齐全时自动计算金额；缺失则不计算")
    void save_computesAmount() {
        BizQuantityList withBoth = item(null, "12.5", "4");
        service.save(withBoth);
        assertThat(withBoth.getAmount()).isEqualByComparingTo("50.0");
        verify(quantityListMapper).insert(withBoth);

        BizQuantityList missing = item(null, "12.5", null);
        service.save(missing);
        assertThat(missing.getAmount()).isNull();
    }

    @Test
    @DisplayName("update - 不存在抛异常；正常重算金额")
    void update_variants() {
        when(quantityListMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.update(item(99L, "1", "1")))
                .hasMessageContaining("工程量清单不存在");

        when(quantityListMapper.selectById(1L)).thenReturn(item(1L, "1", "1"));
        BizQuantityList patch = item(1L, "3", "7");
        service.update(patch);
        assertThat(patch.getAmount()).isEqualByComparingTo("21");
        verify(quantityListMapper).updateById(patch);
    }

    @Test
    @DisplayName("delete - 不存在抛异常；正常删除")
    void delete_variants() {
        when(quantityListMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(99L)).hasMessageContaining("工程量清单不存在");

        when(quantityListMapper.selectById(1L)).thenReturn(item(1L, null, null));
        service.delete(1L);
        verify(quantityListMapper).deleteById(1L);
    }

    @Test
    @DisplayName("batchImport - 守卫：文件为空/项目合同缺失抛异常")
    void batchImport_guardCases_throws() {
        assertThatThrownBy(() -> service.batchImport(null, 1L, 2L))
                .hasMessageContaining("导入文件不能为空");

        MultipartFile emptyFile = mock(MultipartFile.class);
        when(emptyFile.isEmpty()).thenReturn(true);
        assertThatThrownBy(() -> service.batchImport(emptyFile, 1L, 2L))
                .hasMessageContaining("导入文件不能为空");

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        assertThatThrownBy(() -> service.batchImport(file, null, 2L))
                .hasMessageContaining("项目ID和合同ID不能为空");
    }

    @Test
    @DisplayName("batchImport - IO 异常包装为业务异常")
    void batchImport_ioException_wrapped() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getInputStream()).thenThrow(new IOException("流损坏"));

        assertThatThrownBy(() -> service.batchImport(file, 1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Excel文件读取失败");
    }
}
