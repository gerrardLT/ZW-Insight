package com.zwinsight.basedata.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.basedata.domain.BdInspectionScheme;
import com.zwinsight.basedata.mapper.BdInspectionSchemeMapper;
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
 * 检查方案服务（basedata）单元测试
 */
@ExtendWith(MockitoExtension.class)
class InspectionSchemeServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                BdInspectionScheme.class);
    }

    @Mock private BdInspectionSchemeMapper schemeMapper;

    @InjectMocks
    private InspectionSchemeService schemeService;

    @Test
    @DisplayName("分页查询：返回 PageResult 结构")
    void testPage_returnsPageResult() {
        Page<BdInspectionScheme> page = new Page<>(1, 10);
        page.setRecords(List.of(new BdInspectionScheme()));
        page.setTotal(1);
        when(schemeMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BdInspectionScheme> result = schemeService.page(1, 10, "质量", "QUALITY", 1);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("根据ID查询：正常返回")
    void testGetById_ok() {
        BdInspectionScheme scheme = new BdInspectionScheme();
        scheme.setId(1L);
        when(schemeMapper.selectById(1L)).thenReturn(scheme);

        assertThat(schemeService.getById(1L).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("根据ID查询：不存在抛异常")
    void testGetById_notFound() {
        when(schemeMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> schemeService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("检查方案不存在");
    }

    @Test
    @DisplayName("更新：方案不存在抛异常")
    void testUpdate_notFound() {
        when(schemeMapper.selectById(999L)).thenReturn(null);
        BdInspectionScheme update = new BdInspectionScheme();
        update.setId(999L);

        assertThatThrownBy(() -> schemeService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("检查方案不存在");
    }

    @Test
    @DisplayName("新增/更新存在/删除：正常落库")
    void testSaveUpdateDelete_ok() {
        BdInspectionScheme scheme = new BdInspectionScheme();
        scheme.setSchemeName("质量检查方案");
        BdInspectionScheme existing = new BdInspectionScheme();
        existing.setId(1L);
        when(schemeMapper.selectById(1L)).thenReturn(existing);

        schemeService.save(scheme);
        scheme.setId(1L);
        schemeService.update(scheme);
        schemeService.delete(1L);

        verify(schemeMapper).insert(scheme);
        verify(schemeMapper).updateById(scheme);
        verify(schemeMapper).deleteById(1L);
    }
}
