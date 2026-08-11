package com.zwinsight.file.template;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.file.service.PdfConvertService;
import com.zwinsight.file.service.ThymeleafRenderService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 打印模板服务单元测试（名称唯一性 + 渲染/PDF 编排）
 */
@ExtendWith(MockitoExtension.class)
class PrintTemplateServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                SysTemplate.class);
    }

    @Mock private PrintTemplateMapper printTemplateMapper;
    @Mock private ThymeleafRenderService thymeleafRenderService;
    @Mock private PdfConvertService pdfConvertService;

    @InjectMocks
    private PrintTemplateService printTemplateService;

    @Test
    @DisplayName("创建：名称为空抛 400")
    void testCreate_blankName() {
        SysTemplate template = new SysTemplate();
        template.setTemplateName(" ");

        assertThatThrownBy(() -> printTemplateService.create(template))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板名称不能为空");
    }

    @Test
    @DisplayName("创建：同业务类型重名抛 409；正常时默认模板类型 PRINT")
    void testCreate_uniquenessAndDefaultType() {
        when(printTemplateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        SysTemplate dup = new SysTemplate();
        dup.setTemplateName("模板A");
        dup.setBusinessType("CONTRACT");
        assertThatThrownBy(() -> printTemplateService.create(dup))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");

        when(printTemplateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        SysTemplate ok = new SysTemplate();
        ok.setTemplateName("模板B");
        ok.setBusinessType("CONTRACT");
        printTemplateService.create(ok);
        assertThat(ok.getTemplateType()).isEqualTo(PrintTemplateService.TEMPLATE_TYPE_PRINT);
        verify(printTemplateMapper).insert(ok);
    }

    @Test
    @DisplayName("更新/删除/详情：模板不存在抛 404")
    void testNotFound() {
        when(printTemplateMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> printTemplateService.update(999L, new SysTemplate()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板不存在");
        assertThatThrownBy(() -> printTemplateService.delete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板不存在");
        assertThatThrownBy(() -> printTemplateService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板不存在");
    }

    @Test
    @DisplayName("渲染：内容为空抛 500；正常时调用 Thymeleaf 渲染")
    void testRender() {
        SysTemplate empty = new SysTemplate();
        empty.setId(1L);
        empty.setTemplateContent("");
        when(printTemplateMapper.selectById(1L)).thenReturn(empty);
        assertThatThrownBy(() -> printTemplateService.render(1L, Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板内容为空");

        SysTemplate ok = new SysTemplate();
        ok.setId(2L);
        ok.setTemplateContent("<p th:text=\"${name}\"></p>");
        when(printTemplateMapper.selectById(2L)).thenReturn(ok);
        when(thymeleafRenderService.render(anyString(), anyMap())).thenReturn("<p>合同</p>");

        assertThat(printTemplateService.render(2L, Map.of("name", "合同"))).isEqualTo("<p>合同</p>");
    }

    @Test
    @DisplayName("导出 PDF：渲染结果交由 PdfConvertService 转换")
    void testExportPdf() {
        SysTemplate ok = new SysTemplate();
        ok.setId(3L);
        ok.setTemplateContent("<p>pdf</p>");
        when(printTemplateMapper.selectById(3L)).thenReturn(ok);
        when(thymeleafRenderService.render(anyString(), anyMap())).thenReturn("<p>pdf</p>");
        when(pdfConvertService.convertHtmlToPdf(anyString())).thenReturn(new byte[]{1, 2, 3});

        byte[] pdf = printTemplateService.exportPdf(3L, Map.of());

        assertThat(pdf).containsExactly(1, 2, 3);
        verify(pdfConvertService).convertHtmlToPdf("<p>pdf</p>");
    }

    @Test
    @DisplayName("分页列表：返回 PageResult 结构")
    void testList() {
        Page<SysTemplate> page = new Page<>(1, 10);
        page.setRecords(List.of(new SysTemplate()));
        page.setTotal(1);
        when(printTemplateMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<SysTemplate> result = printTemplateService.list(1, 10, null, null, "PRINT");

        assertThat(result.getRecords()).hasSize(1);
    }
}
