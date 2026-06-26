package com.zwinsight.file.service;

import com.zwinsight.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ThymeleafRenderService 单元测试
 *
 * <p>覆盖 Requirement 2.1（变量替换）、2.6（条件/循环语法）、2.4（无效语法错误详情）。</p>
 */
class ThymeleafRenderServiceTest {

    private final ThymeleafRenderService service = new ThymeleafRenderService();

    @Test
    @DisplayName("th:text 变量替换：渲染结果包含变量值且无残留表达式")
    void renderThTextReplacesVariable() {
        String template = "<p th:text=\"${name}\">placeholder</p>";
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "中维智营");

        String html = service.render(template, vars);

        assertTrue(html.contains("中维智营"), "应包含变量实际值");
        assertFalse(html.contains("th:text"), "渲染后不应残留 th:text 属性");
        assertFalse(html.contains("placeholder"), "th:text 应替换占位文本");
    }

    @Test
    @DisplayName("th:each 循环遍历：每个元素都被渲染")
    void renderThEachIteratesList() {
        String template = "<ul><li th:each=\"item : ${items}\" th:text=\"${item}\">x</li></ul>";
        Map<String, Object> vars = new HashMap<>();
        vars.put("items", List.of("钢筋", "水泥", "砂石"));

        String html = service.render(template, vars);

        assertTrue(html.contains("钢筋"));
        assertTrue(html.contains("水泥"));
        assertTrue(html.contains("砂石"));
        assertFalse(html.contains("th:each"));
    }

    @Test
    @DisplayName("th:if 条件判断：条件为真渲染、为假不渲染")
    void renderThIfCondition() {
        String template = "<div><span th:if=\"${show}\">visible</span></div>";

        Map<String, Object> showTrue = new HashMap<>();
        showTrue.put("show", true);
        assertTrue(service.render(template, showTrue).contains("visible"));

        Map<String, Object> showFalse = new HashMap<>();
        showFalse.put("show", false);
        assertFalse(service.render(template, showFalse).contains("visible"));
    }

    @Test
    @DisplayName("无效模板语法：抛出 BusinessException 且 message 含行号和描述")
    void renderInvalidSyntaxReturnsErrorWithLine() {
        // 无法解析的表达式语法
        String template = "<p th:text=\"${ ## invalid ##}\">x</p>";
        Map<String, Object> vars = new HashMap<>();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.render(template, vars));

        assertEquals(500, ex.getCode());
        assertTrue(ex.getMessage().contains("模板渲染失败"), "错误信息应说明渲染失败");
        assertTrue(ex.getMessage().contains("行"), "错误信息应包含行号定位");
    }

    @Test
    @DisplayName("空模板内容：抛出业务异常而非 NPE")
    void renderNullTemplateThrowsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.render(null, new HashMap<>()));
        assertEquals(500, ex.getCode());
    }

    @Test
    @DisplayName("variables 为 null：按空变量处理，不抛异常")
    void renderNullVariablesTreatedAsEmpty() {
        String template = "<p>static content</p>";
        String html = service.render(template, null);
        assertTrue(html.contains("static content"));
    }
}
