package com.zwinsight.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.basedata.domain.BdMaterialCategory;
import com.zwinsight.basedata.mapper.BdMaterialCategoryMapper;
import com.zwinsight.basedata.mapper.BdMaterialMapper;
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
class MaterialCategoryServiceTest {

    @Mock private BdMaterialCategoryMapper categoryMapper;
    @Mock private BdMaterialMapper materialMapper;

    @InjectMocks
    private MaterialCategoryService categoryService;

    @Test
    @DisplayName("获取分类树形列表：返回根节点")
    void testListTree() {
        BdMaterialCategory root = new BdMaterialCategory();
        root.setId(1L);
        root.setParentId(0L);
        root.setCategoryName("钢材");
        when(categoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(root));

        List<BdMaterialCategory> result = categoryService.listTree();

        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("新增分类：正常保存")
    void testSave() {
        BdMaterialCategory category = new BdMaterialCategory();
        category.setCategoryName("新分类");
        when(categoryMapper.insert(any())).thenReturn(1);

        categoryService.save(category);

        verify(categoryMapper).insert(category);
    }

    @Test
    @DisplayName("删除分类：存在子分类抛异常")
    void testDelete_hasChildren() {
        when(categoryMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在子分类，不能删除");
    }

    @Test
    @DisplayName("删除分类：存在关联材料抛异常")
    void testDelete_hasMaterials() {
        when(categoryMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(materialMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分类下存在材料，不能删除");
    }

    @Test
    @DisplayName("删除分类：无子分类无材料正常删除")
    void testDelete_ok() {
        when(categoryMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(materialMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        categoryService.delete(1L);

        verify(categoryMapper).deleteById(1L);
    }

    @Test
    @DisplayName("更新分类：不存在抛异常")
    void testUpdate_notFound() {
        when(categoryMapper.selectById(999L)).thenReturn(null);

        BdMaterialCategory update = new BdMaterialCategory();
        update.setId(999L);

        assertThatThrownBy(() -> categoryService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分类不存在");
    }
}
