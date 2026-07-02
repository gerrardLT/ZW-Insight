package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.system.domain.SysDict;
import com.zwinsight.system.domain.SysDictItem;
import com.zwinsight.system.mapper.SysDictItemMapper;
import com.zwinsight.system.mapper.SysDictMapper;
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
class SysDictServiceTest {

    @Mock private SysDictMapper dictMapper;
    @Mock private SysDictItemMapper dictItemMapper;

    @InjectMocks
    private SysDictService dictService;

    @Test
    @DisplayName("新增字典：编码重复抛异常")
    void testSave_duplicateCode() {
        SysDict dict = new SysDict();
        dict.setDictCode("GENDER");
        when(dictMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> dictService.save(dict))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字典编码已存在");
    }

    @Test
    @DisplayName("新增字典：正常保存")
    void testSave_ok() {
        SysDict dict = new SysDict();
        dict.setDictCode("GENDER");
        when(dictMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        dictService.save(dict);

        verify(dictMapper).insert(dict);
    }

    @Test
    @DisplayName("更新字典：不存在抛异常")
    void testUpdate_notFound() {
        when(dictMapper.selectById(999L)).thenReturn(null);

        SysDict update = new SysDict();
        update.setId(999L);

        assertThatThrownBy(() -> dictService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字典不存在");
    }

    @Test
    @DisplayName("删除字典：同时删除字典值")
    void testDelete() {
        dictService.delete(1L);

        verify(dictMapper).deleteById(1L);
        verify(dictItemMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("根据编码获取字典值：字典不存在抛异常")
    void testGetDictItemsByCode_notFound() {
        when(dictMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> dictService.getDictItemsByCode("NOT_EXIST"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字典不存在");
    }

    @Test
    @DisplayName("根据编码获取字典值：正常返回")
    void testGetDictItemsByCode_ok() {
        SysDict dict = new SysDict();
        dict.setId(1L);
        dict.setDictCode("GENDER");
        when(dictMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(dict);

        SysDictItem item = new SysDictItem();
        item.setLabel("男");
        when(dictItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

        List<SysDictItem> result = dictService.getDictItemsByCode("GENDER");

        assertThat(result).hasSize(1);
    }
}
