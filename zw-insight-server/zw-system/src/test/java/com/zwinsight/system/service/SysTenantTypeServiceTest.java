package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.system.domain.SysTenantType;
import com.zwinsight.system.mapper.SysTenantTypeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租户类型管理服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class SysTenantTypeServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                SysTenantType.class);
    }

    @Mock private SysTenantTypeMapper tenantTypeMapper;

    @InjectMocks
    private SysTenantTypeService tenantTypeService;

    @Test
    @DisplayName("分页查询：返回 PageResult 结构")
    void testPage_returnsPageResult() {
        Page<SysTenantType> page = new Page<>(1, 10);
        page.setRecords(List.of(new SysTenantType()));
        page.setTotal(1);
        when(tenantTypeMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<SysTenantType> result = tenantTypeService.page(1, 10, "施工", 1);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("根据ID查询：正常返回")
    void testGetById_ok() {
        SysTenantType type = new SysTenantType();
        type.setId(1L);
        when(tenantTypeMapper.selectById(1L)).thenReturn(type);

        assertThat(tenantTypeService.getById(1L).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("根据ID查询：不存在抛异常")
    void testGetById_notFound() {
        when(tenantTypeMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> tenantTypeService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户类型不存在");
    }

    @Test
    @DisplayName("更新：记录不存在抛异常")
    void testUpdate_notFound() {
        when(tenantTypeMapper.selectById(999L)).thenReturn(null);
        SysTenantType update = new SysTenantType();
        update.setId(999L);

        assertThatThrownBy(() -> tenantTypeService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户类型不存在");
    }

    @Test
    @DisplayName("删除：记录不存在抛异常")
    void testDelete_notFound() {
        when(tenantTypeMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> tenantTypeService.delete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户类型不存在");
    }

    @Test
    @DisplayName("批量删除：空列表抛异常且不执行删除")
    void testBatchDelete_emptyList() {
        assertThatThrownBy(() -> tenantTypeService.batchDelete(Collections.emptyList()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("删除ID列表不能为空");
        verify(tenantTypeMapper, never()).deleteBatchIds(any());
    }

    @Test
    @DisplayName("批量删除：正常按ID集合删除")
    void testBatchDelete_ok() {
        tenantTypeService.batchDelete(List.of(1L, 2L));

        verify(tenantTypeMapper).deleteBatchIds(List.of(1L, 2L));
    }
}
