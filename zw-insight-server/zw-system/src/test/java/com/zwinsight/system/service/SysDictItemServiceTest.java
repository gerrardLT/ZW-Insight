package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.system.domain.SysDictItem;
import com.zwinsight.system.mapper.SysDictItemMapper;
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
class SysDictItemServiceTest {

    @Mock private SysDictItemMapper dictItemMapper;

    @InjectMocks
    private SysDictItemService dictItemService;

    @Test
    @DisplayName("查询字典值列表：按字典ID查询")
    void testList() {
        SysDictItem item = new SysDictItem();
        item.setLabel("男");
        when(dictItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

        List<SysDictItem> result = dictItemService.list(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("新增字典值：parentId为null时设为0")
    void testSave_nullParentId() {
        SysDictItem item = new SysDictItem();
        item.setLabel("选项");
        item.setParentId(null);

        dictItemService.save(item);

        assertThat(item.getParentId()).isEqualTo(0L);
        verify(dictItemMapper).insert(item);
    }

    @Test
    @DisplayName("更新字典值：不存在抛异常")
    void testUpdate_notFound() {
        when(dictItemMapper.selectById(999L)).thenReturn(null);

        SysDictItem update = new SysDictItem();
        update.setId(999L);

        assertThatThrownBy(() -> dictItemService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字典值不存在");
    }

    @Test
    @DisplayName("删除字典值：存在子项抛异常")
    void testDelete_hasChildren() {
        when(dictItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> dictItemService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在子字典值，无法删除");
    }

    @Test
    @DisplayName("删除字典值：无子项正常删除")
    void testDelete_ok() {
        when(dictItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        dictItemService.delete(1L);

        verify(dictItemMapper).deleteById(1L);
    }
}
