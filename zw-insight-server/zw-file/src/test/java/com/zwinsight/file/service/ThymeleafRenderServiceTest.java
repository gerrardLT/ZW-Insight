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
    @DisplayName("空 variables：按空变量处理，不抛异常")
    void renderNullVariablesTreatedAsEmpty() {
        String template = "<p>static content</p>";
        String html = service.render(template, null);
        assertTrue(html.contains("static content"));
    }

    @Test
    @DisplayName("嵌套循环 th:each 内部使用：正确渲染多层结构")
    void renderNestedLoops_correctRendering() {
        // Given
        Map<String, Object> vars = new HashMap<>();
        Map<String, List<String>> departments = new HashMap<>();
        departments.put("engineering", List.of("张三", "李四"));
        departments.put("sales", List.of("王五"));
        vars.put("departments", departments);

        String template = "<table><tr th:each\"(dept, users) : ${departments}\"><td>\${dept}</td><td><span th:each=\"user : \${users}\" th:text=\"\${user}\"></span></td></tr></table>";

        // When
        String html = service.render(template, vars);

        // Then
        assertTrue(html.contains("engineering"), "应包含部门名 engineering");
        assertTrue(html.contains("张三"), "应包含用户张三");
        assertTrue(html.contains("李四"), "应包含用户李四");
        assertTrue(html.contains("sales"), "应包含部门名 sales");
        assertTrue(html.contains("王五"), "应包含用户王五");
    }

    @Test
    @DisplayName("th:with 临时变量：正确创建和使用局部变量")
    void renderThWithLocalVariable() {
        String template = "<div th:with=\"total=${price * quantity}\">总计：<span th:text=\"\${total}\">0</span></div>";
        Map<String, Object> vars = new HashMap<>();
        vars.put("price", 100.5);
        vars.put("quantity", 3);

        String html = service.render(template, vars);

        assertTrue(html.contains("总计：301.5"), "应计算并显示正确总价");
    }

    @Test
    @DisplayName("模板注入攻击防护：转义 HTML 标签防止 XSS")
    void renderXssProtection_escapesHtml() {
        String template = "<p th:text=\"${username}\">default</p>";
        Map<String, Object> vars = new HashMap<>();
        vars.put("username", "<script>alert('xss')</script>");

        String html = service.render(template, vars);

        assertFalse(html.contains("<script>"), "应转义脚本标签防止 XSS 攻击");
        assertTrue(html.contains("&lt;script&gt;"), "应使用 HTML 实体编码");
    }

    @Test
    @DisplayName("条件判断 th:unless：条件为假时渲染内容")
    void renderThUnlessCondition() {
        String template = "<div th:unless=\"${hasPermission}\">无权限提示</div>";
        
        // hasPermission = false 时应显示
        Map<String, Object> noPermission = new HashMap<>();
        noPermission.put("hasPermission", false);
        assertTrue(service.render(template, noPermission).contains("无权限提示"));

        // hasPermission = true 时不显示
        Map<String, Object> hasPermission = new HashMap<>();
        hasPermission.put("hasPermission", true);
        assertFalse(service.render(template, hasPermission).contains("无权限提示"));
    }

    @Test
    @DisplayName("多个模板片段组合渲染")
    void renderMultipleTemplates_combination() {
        String template = "<html><head><title th:text=\"${title}\">Default</title></head><body><h1 th:text=\"${heading}\">Hello</h1></body></html>";
        Map<String, Object> vars = new HashMap<>();
        vars.put("title", "项目管理系统");
        vars.put("heading", "欢迎使用");

        String html = service.render(template, vars);

        assertTrue(html.contains("项目管理系统"), "标题应替换");
        assertTrue(html.contains("欢迎使用"), "主标题应替换");
    }

    @Test
    @DisplayName("复杂业务场景：合同审批通知邮件模板")
    void renderContractApprovalEmailTemplate() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("contractNumber", "HT-2024-001234");
        vars.put("applicantName", "张三");
        vars.put("approvalDate", "2024-01-15");
        vars.put("amount", 1250000.00);
        vars.put("status", "APPROVED");

        String template = "<div>\n"
                + "  <h2>合同审批通知</h2>\n"
                + "  <p>合同编号：<span th:text=\"${contractNumber}\"></span></p>\n"
                + "  <p>申请人：<span th:text=\"${applicantName}\"></span></p>\n"
                + "  <p>审批日期：<span th:text=\"${approvalDate}\"></span></p>\n"
                + "  <p>金额：<span th:text=\"${#numbers.formatDecimal(amount, 1, 'COMMA', 2, 'POINT')}"></span>元</p>\n"
                + "  <p th:if=\"${status} == 'APPROVED'\">状态：<span>已批准</span></p>\n"
                + "  <p th:if=\"${status} == 'REJECTED'\">状态：<span>已拒绝</span></p>\n"
                + "</div>";

        String html = service.render(template, vars);

        assertTrue(html.contains("HT-2024-001234"), "应包含合同编号");
        assertTrue(html.contains("张三"), "应包含申请人");
        assertTrue(html.contains("已批准"), "应显示批准状态");
        assertTrue(html.contains("1,250,000.00"), "应格式化金额");
    }
}
