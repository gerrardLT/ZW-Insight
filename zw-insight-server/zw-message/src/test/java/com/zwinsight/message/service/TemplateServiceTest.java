package com.zwinsight.message.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.message.domain.MsgTemplate;
import com.zwinsight.message.mapper.MsgTemplateMapper;
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
 * 消息模板服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                MsgTemplate.class);
    }

    @Mock private MsgTemplateMapper templateMapper;

    @InjectMocks
    private TemplateService templateService;

    @Test
    @DisplayName("分页查询：返回 PageResult 结构")
    void testPage_returnsPageResult() {
        Page<MsgTemplate> page = new Page<>(1, 10);
        page.setRecords(List.of(new MsgTemplate()));
        page.setTotal(1);
        when(templateMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<MsgTemplate> result = templateService.page(1, 10, "审批");

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("根据ID查询：不存在抛异常")
    void testGetById_notFound() {
        when(templateMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> templateService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板不存在");
    }

    @Test
    @DisplayName("更新：模板不存在抛异常")
    void testUpdate_notFound() {
        when(templateMapper.selectById(999L)).thenReturn(null);
        MsgTemplate update = new MsgTemplate();
        update.setId(999L);

        assertThatThrownBy(() -> templateService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板不存在");
    }

    @Test
    @DisplayName("新增/更新存在/删除：正常落库")
    void testSaveUpdateDelete_ok() {
        MsgTemplate template = new MsgTemplate();
        template.setTemplateName("审批提醒");
        MsgTemplate existing = new MsgTemplate();
        existing.setId(1L);
        when(templateMapper.selectById(1L)).thenReturn(existing);

        templateService.save(template);
        template.setId(1L);
        templateService.update(template);
        templateService.delete(1L);

        verify(templateMapper).insert(template);
        verify(templateMapper).updateById(template);
        verify(templateMapper).deleteById(1L);
    }
}
