package com.zwinsight.file.service;

import com.zwinsight.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF 转换服务单元测试
 *
 * <p>wkhtmltopdf 为外部进程依赖，真实转换链路由生产验证；
 * 单测覆盖入参守卫与进程不可用时的异常封装分支。</p>
 */
class PdfConvertServiceTest {

    private PdfConvertService pdfConvertService;

    @BeforeEach
    void setUp() {
        pdfConvertService = new PdfConvertService();
        // 指向不存在路径：进程启动即失败，覆盖异常封装分支
        ReflectionTestUtils.setField(pdfConvertService, "wkhtmltopdfPath", "/nonexistent/wkhtmltopdf");
        ReflectionTestUtils.setField(pdfConvertService, "timeoutSeconds", 5);
    }

    @Test
    @DisplayName("HTML 为空：拒绝转换")
    void testConvertHtmlToPdf_emptyHtml() {
        assertThatThrownBy(() -> pdfConvertService.convertHtmlToPdf(""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HTML 内容为空");
        assertThatThrownBy(() -> pdfConvertService.convertHtmlToPdf(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HTML 内容为空");
    }

    @Test
    @DisplayName("wkhtmltopdf 不可用：封装为 BusinessException 500 并提示路径配置")
    void testConvertHtmlToPdf_processUnavailable() {
        assertThatThrownBy(() -> pdfConvertService.convertHtmlToPdf("<html><body>test</body></html>"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法启动 wkhtmltopdf");
    }
}
