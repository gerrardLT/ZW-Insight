package com.zwinsight.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.basedata.domain.BdMaterial;
import com.zwinsight.basedata.mapper.BdMaterialMapper;
import com.zwinsight.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    @Mock private BdMaterialMapper materialMapper;

    @InjectMocks
    private MaterialService materialService;

    @Test
    @DisplayName("根据ID查询：存在返回材料")
    void testGetById_found() {
        BdMaterial material = new BdMaterial();
        material.setId(1L);
        material.setMaterialName("钢筋");
        when(materialMapper.selectById(1L)).thenReturn(material);

        BdMaterial result = materialService.getById(1L);

        assertThat(result.getMaterialName()).isEqualTo("钢筋");
    }

    @Test
    @DisplayName("根据ID查询：不存在抛异常")
    void testGetById_notFound() {
        when(materialMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> materialService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("材料不存在");
    }

    @Test
    @DisplayName("新增材料：正常保存")
    void testSave() {
        BdMaterial material = new BdMaterial();
        material.setMaterialName("水泥");
        when(materialMapper.insert(any(BdMaterial.class))).thenReturn(1);

        materialService.save(material);

        verify(materialMapper).insert(material);
    }

    @Test
    @DisplayName("更新材料：不存在抛异常")
    void testUpdate_notFound() {
        when(materialMapper.selectById(999L)).thenReturn(null);

        BdMaterial update = new BdMaterial();
        update.setId(999L);

        assertThatThrownBy(() -> materialService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("材料不存在");
    }

    @Test
    @DisplayName("更新材料：存在则更新")
    void testUpdate_found() {
        BdMaterial existing = new BdMaterial();
        existing.setId(1L);
        when(materialMapper.selectById(1L)).thenReturn(existing);

        BdMaterial update = new BdMaterial();
        update.setId(1L);
        update.setMaterialName("更新材料名");
        materialService.update(update);

        verify(materialMapper).updateById(update);
    }

    @Test
    @DisplayName("删除材料：正常删除")
    void testDelete() {
        materialService.delete(1L);

        verify(materialMapper).deleteById(1L);
    }
}
