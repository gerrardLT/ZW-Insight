package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizBomItem;
import com.zwinsight.contract.mapper.BizBomItemMapper;
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
import static org.mockito.Mockito.*;

/**
 * BomItemService 单元测试
 * <p>清单项：金额=数量×单价自动计算、排序查询、导入 IO 异常包装。</p>
 */
@ExtendWith(MockitoExtension.class)
class BomItemServiceTest {

    @Mock
    private BizBomItemMapper bomItemMapper;

    @InjectMocks
    private BomItemService service;

    private BizBomItem item(Long id, String quantity, String unitPrice) {
        BizBomItem i = new BizBomItem();
        i.setId(id);
        i.setQuantity(quantity == null ? null : new BigDecimal(quantity));
        i.setUnitPrice(unitPrice == null ? null : new BigDecimal(unitPrice));
        return i;
    }

    @Test
    @DisplayName("list - 按合同查询透传")
    void list_delegates() {
        when(bomItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(item(1L, "1", "2")));

        assertThat(service.list(10L)).hasSize(1);
    }

    @Test
    @DisplayName("save - 数量单价齐全自动计算金额")
    void save_computesAmount() {
        BizBomItem i = item(null, "6", "3.5");

        service.save(i);

        assertThat(i.getAmount()).isEqualByComparingTo("21.0");
        verify(bomItemMapper).insert(i);
    }

    @Test
    @DisplayName("save - 数量或单价缺失时不计算金额")
    void save_missingField_noAmount() {
        BizBomItem i = item(null, null, "3.5");

        service.save(i);

        assertThat(i.getAmount()).isNull();
    }

    @Test
    @DisplayName("update - 不存在抛异常；正常重算金额")
    void update_variants() {
        when(bomItemMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.update(item(99L, "1", "1")))
                .hasMessageContaining("清单项不存在");

        when(bomItemMapper.selectById(1L)).thenReturn(item(1L, "1", "1"));
        BizBomItem patch = item(1L, "10", "2.5");
        service.update(patch);
        assertThat(patch.getAmount()).isEqualByComparingTo("25.0");
        verify(bomItemMapper).updateById(patch);
    }

    @Test
    @DisplayName("delete - 不存在抛异常；正常删除")
    void delete_variants() {
        when(bomItemMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(99L)).hasMessageContaining("清单项不存在");

        when(bomItemMapper.selectById(1L)).thenReturn(item(1L, null, null));
        service.delete(1L);
        verify(bomItemMapper).deleteById(1L);
    }

    @Test
    @DisplayName("batchImport - IO 异常包装为业务异常")
    void batchImport_ioException_wrapped() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("流损坏"));

        assertThatThrownBy(() -> service.batchImport(1L, 2L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件读取失败");
    }
}
