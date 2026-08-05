package com.zwinsight.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizCostSubcategory;
import com.zwinsight.budget.mapper.BizCostSubcategoryMapper;
import com.zwinsight.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CostSubcategoryService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class CostSubcategoryServiceTest {

    @Mock
    private BizCostSubcategoryMapper costSubcategoryMapper;

    @InjectMocks
    private CostSubcategoryService service;

    private BizCostSubcategory sub(Long id) {
        BizCostSubcategory s = new BizCostSubcategory();
        s.setId(id);
        s.setCostCategory("MATERIAL");
        return s;
    }

    @Test
    @DisplayName("list - 按类别查询透传")
    void list_delegates() {
        when(costSubcategoryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(sub(1L)));

        assertThat(service.list("MATERIAL")).hasSize(1);
        assertThat(service.list(null)).hasSize(1);
    }

    @Test
    @DisplayName("save - 委托插入")
    void save_delegates() {
        BizCostSubcategory s = sub(null);

        service.save(s);

        verify(costSubcategoryMapper).insert(s);
    }

    @Test
    @DisplayName("update - 不存在抛异常；正常更新")
    void update_variants() {
        when(costSubcategoryMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.update(sub(99L)))
                .hasMessageContaining("费用子类不存在");

        BizCostSubcategory s = sub(1L);
        when(costSubcategoryMapper.selectById(1L)).thenReturn(s);
        service.update(s);
        verify(costSubcategoryMapper).updateById(s);
    }

    @Test
    @DisplayName("delete - 不存在抛异常；正常删除")
    void delete_variants() {
        when(costSubcategoryMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(99L))
                .hasMessageContaining("费用子类不存在");

        when(costSubcategoryMapper.selectById(1L)).thenReturn(sub(1L));
        service.delete(1L);
        verify(costSubcategoryMapper).deleteById(1L);
    }
}
