package com.zwinsight.basedata.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.basedata.domain.BdOwner;
import com.zwinsight.basedata.mapper.BdOwnerMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 甲方单位服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class OwnerServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                BdOwner.class);
    }

    @Mock private BdOwnerMapper ownerMapper;

    @InjectMocks
    private OwnerService ownerService;

    @Test
    @DisplayName("分页查询：返回 PageResult 结构")
    void testPage_returnsPageResult() {
        Page<BdOwner> page = new Page<>(1, 10);
        page.setRecords(List.of(new BdOwner()));
        page.setTotal(1);
        when(ownerMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BdOwner> result = ownerService.page(1, 10, "城投", 1);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("下拉列表：透传 mapper 查询")
    void testList_passthrough() {
        when(ownerMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(new BdOwner()));

        assertThat(ownerService.list("城投", 1)).hasSize(1);
    }

    @Test
    @DisplayName("根据ID查询：不存在抛异常")
    void testGetById_notFound() {
        when(ownerMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> ownerService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("甲方单位不存在");
    }

    @Test
    @DisplayName("更新：甲方不存在抛异常")
    void testUpdate_notFound() {
        when(ownerMapper.selectById(999L)).thenReturn(null);
        BdOwner update = new BdOwner();
        update.setId(999L);

        assertThatThrownBy(() -> ownerService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("甲方单位不存在");
    }

    @Test
    @DisplayName("新增/更新存在/删除：正常落库")
    void testSaveUpdateDelete_ok() {
        BdOwner owner = new BdOwner();
        owner.setOwnerName("城市建设投资集团");
        BdOwner existing = new BdOwner();
        existing.setId(1L);
        when(ownerMapper.selectById(1L)).thenReturn(existing);

        ownerService.save(owner);
        owner.setId(1L);
        ownerService.update(owner);
        ownerService.delete(1L);

        verify(ownerMapper).insert(owner);
        verify(ownerMapper).updateById(owner);
        verify(ownerMapper).deleteById(1L);
    }
}
