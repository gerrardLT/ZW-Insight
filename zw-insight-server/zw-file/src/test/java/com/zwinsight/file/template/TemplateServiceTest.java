package com.zwinsight.file.template;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.common.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模板管理服务单元测试（默认模板互斥 + 占位符渲染）
 */
@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                SysTemplate.class);
    }

    @Mock private SysTemplateMapper templateMapper;

    @InjectMocks
    private TemplateService templateService;

    @Test
    @DisplayName("创建默认模板：先取消同模块同类型的其他默认")
    void testCreate_defaultClearsOthers() {
        SysTemplate oldDefault = new SysTemplate();
        oldDefault.setId(10L);
        oldDefault.setIsDefault(1);
        when(templateMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(oldDefault));

        SysTemplate newTemplate = new SysTemplate();
        newTemplate.setModuleCode("CONTRACT");
        newTemplate.setTemplateType("PRINT");
        newTemplate.setIsDefault(1);
        templateService.create(newTemplate);

        ArgumentCaptor<SysTemplate> captor = ArgumentCaptor.forClass(SysTemplate.class);
        verify(templateMapper, atLeastOnce()).updateById(captor.capture());
        assertThat(captor.getValue().getIsDefault()).isZero();
        verify(templateMapper).insert(newTemplate);
    }

    @Test
    @DisplayName("更新/删除：模板不存在抛异常")
    void testUpdateDelete_notFound() {
        when(templateMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> templateService.update(999L, new SysTemplate()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板不存在");
        assertThatThrownBy(() -> templateService.delete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板不存在");
    }

    @Test
    @DisplayName("渲染模板：占位符替换正确")
    void testRenderTemplate_replacesPlaceholders() {
        SysTemplate template = new SysTemplate();
        template.setId(1L);
        template.setTemplateContent("<h1>{{title}}</h1><p>{{amount}}</p>");
        when(templateMapper.selectById(1L)).thenReturn(template);

        String html = templateService.renderTemplate(1L, Map.of("title", "合同", "amount", 100));

        assertThat(html).isEqualTo("<h1>合同</h1><p>100</p>");
    }

    @Test
    @DisplayName("渲染模板：模板不存在/内容为空抛异常")
    void testRenderTemplate_invalid() {
        when(templateMapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> templateService.renderTemplate(999L, Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板不存在");

        SysTemplate empty = new SysTemplate();
        empty.setId(2L);
        empty.setTemplateContent("");
        when(templateMapper.selectById(2L)).thenReturn(empty);
        assertThatThrownBy(() -> templateService.renderTemplate(2L, Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板内容为空");
    }

    @Test
    @DisplayName("列表/默认模板查询：透传 mapper 调用")
    void testListAndDefault() {
        templateService.listByModule("CONTRACT", "PRINT");
        verify(templateMapper).selectList(any(LambdaQueryWrapper.class));

        templateService.getDefault("CONTRACT", "PRINT");
        verify(templateMapper).selectOne(any(LambdaQueryWrapper.class));
    }
}
