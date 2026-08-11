package com.zwinsight.basedata.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.basedata.domain.BdCompany;
import com.zwinsight.basedata.mapper.BdCompanyMapper;
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
 * 自持公司服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                BdCompany.class);
    }

    @Mock private BdCompanyMapper companyMapper;

    @InjectMocks
    private CompanyService companyService;

    @Test
    @DisplayName("分页查询：返回 PageResult 结构")
    void testPage_returnsPageResult() {
        Page<BdCompany> page = new Page<>(1, 10);
        page.setRecords(List.of(new BdCompany()));
        page.setTotal(1);
        when(companyMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BdCompany> result = companyService.page(1, 10, "中维", 1);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("下拉列表：透传 mapper 查询")
    void testList_passthrough() {
        when(companyMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(new BdCompany()));

        assertThat(companyService.list("中维", 1)).hasSize(1);
    }

    @Test
    @DisplayName("根据ID查询：不存在抛异常")
    void testGetById_notFound() {
        when(companyMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> companyService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("公司不存在");
    }

    @Test
    @DisplayName("更新：公司不存在抛异常")
    void testUpdate_notFound() {
        when(companyMapper.selectById(999L)).thenReturn(null);
        BdCompany update = new BdCompany();
        update.setId(999L);

        assertThatThrownBy(() -> companyService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("公司不存在");
    }

    @Test
    @DisplayName("新增/更新存在/删除：正常落库")
    void testSaveUpdateDelete_ok() {
        BdCompany company = new BdCompany();
        company.setCompanyName("中维建设有限公司");
        BdCompany existing = new BdCompany();
        existing.setId(1L);
        when(companyMapper.selectById(1L)).thenReturn(existing);

        companyService.save(company);
        company.setId(1L);
        companyService.update(company);
        companyService.delete(1L);

        verify(companyMapper).insert(company);
        verify(companyMapper).updateById(company);
        verify(companyMapper).deleteById(1L);
    }
}
