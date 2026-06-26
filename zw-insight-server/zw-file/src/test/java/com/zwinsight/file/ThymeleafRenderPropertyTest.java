package com.zwinsight.file;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.service.ThymeleafRenderService;
import net.jqwik.api.*;
import org.assertj.core.api.Assertions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ThymeleafRenderPropertyTest — Thymeleaf 字符串模板渲染属性测试
 * （Feature: p2-advanced, Property 4-5）
 *
 * <p>直接实例化 {@link ThymeleafRenderService}（无参构造、纯函数式 render），
 * 对随机变量 Map / 模板内容验证渲染契约，无需 Spring 容器。</p>
 *
 * <p><b>Validates: Requirements 2.1, 2.4, 2.6</b></p>
 */
class ThymeleafRenderPropertyTest {

    private final ThymeleafRenderService service = new ThymeleafRenderService();

    // ==================== Property 4: Thymeleaf 模板渲染变量替换 ====================

    /**
     * Property 4: 对包含 {@code th:text="${var}"} 表达式的模板与对应变量 Map，
     * 渲染结果 SHALL 包含变量的真实值，且不残留任何 {@code th:} 属性或 {@code ${...}} 表达式。
     *
     * <p><b>Feature: p2-advanced, Property 4: Thymeleaf 模板渲染变量替换</b></p>
     * <p><b>Validates: Requirements 2.1, 2.4</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-advanced, Property 4: Thymeleaf 模板渲染变量替换")
    void property4_variableSubstitution(@ForAll("variableValues") List<String> values) {
        // 构造变量 Map：键 v0..vn，值为生成的字母数字串
        Map<String, Object> vars = new LinkedHashMap<>();
        StringBuilder body = new StringBuilder("<div>");
        for (int i = 0; i < values.size(); i++) {
            String key = "v" + i;
            vars.put(key, values.get(i));
            // th:text 会用变量值替换元素文本，占位符 "placeholder" 应被覆盖
            body.append("<span th:text=\"${").append(key).append("}\">placeholder</span>");
        }
        body.append("</div>");

        String rendered = service.render(body.toString(), vars);

        // 每个变量的真实值都应出现在渲染结果中
        for (String value : values) {
            Assertions.assertThat(rendered)
                    .as("渲染结果应包含变量真实值[%s]", value)
                    .contains(value);
        }
        // 不应残留未解析的 th: 属性或 ${...} 表达式
        Assertions.assertThat(rendered)
                .as("渲染结果不应残留 th: 属性")
                .doesNotContain("th:");
        Assertions.assertThat(rendered)
                .as("渲染结果不应残留 ${...} 表达式")
                .doesNotContain("${");
        // 占位符文本应被变量值覆盖
        Assertions.assertThat(rendered)
                .as("渲染结果不应残留占位符")
                .doesNotContain("placeholder");
    }

    // ==================== Property 5: 无效模板语法返回错误详情 ====================

    /**
     * Property 5: 对存在 Thymeleaf 语法错误的模板内容进行渲染，SHALL 抛出
     * {@link BusinessException}，且其消息包含可定位的错误描述信息（"模板渲染失败" 前缀）。
     *
     * <p><b>Feature: p2-advanced, Property 5: 无效模板语法返回错误详情</b></p>
     * <p><b>Validates: Requirements 2.6</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-advanced, Property 5: 无效模板语法返回错误详情")
    void property5_invalidSyntaxReturnsErrorDetail(
            @ForAll("invalidExpressions") String invalidExpr,
            @ForAll("plainPrefixes") String prefix) {

        // 在合法前缀后嵌入非法 Thymeleaf 表达式，整体仍应渲染失败
        String template = "<div><p>" + prefix + "</p>"
                + "<span th:text=\"" + invalidExpr + "\">x</span></div>";

        Map<String, Object> vars = Map.of();

        Throwable thrown = Assertions.catchThrowable(() -> service.render(template, vars));

        Assertions.assertThat(thrown)
                .as("非法模板语法[%s]渲染应抛出 BusinessException", invalidExpr)
                .isInstanceOf(BusinessException.class);
        Assertions.assertThat(thrown.getMessage())
                .as("错误信息应包含可定位的描述信息")
                .isNotBlank()
                .contains("模板渲染失败");
    }

    // ==================== Arbitraries (数据提供器) ====================

    /**
     * 变量值列表：1-5 个，每个为长度 1-12 的字母数字串（避免 HTML 转义带来的断言歧义）。
     */
    @Provide
    Arbitrary<List<String>> variableValues() {
        Arbitrary<String> alnum = Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1).ofMaxLength(12);
        return alnum.list().ofMinSize(1).ofMaxSize(5);
    }

    /**
     * 非法 Thymeleaf 表达式属性值：均会触发解析/求值异常。
     * 覆盖未闭合、未终止字符串、非法 token、不完整三元/运算等情形。
     */
    @Provide
    Arbitrary<String> invalidExpressions() {
        return Arbitraries.of(
                "${unclosed",          // 缺少右花括号
                "${1 +}",              // 不完整算术表达式
                "${'unterminated}",    // 未终止的字符串字面量
                "${a b c}",            // 非法 token 序列
                "${(}",                // 括号不匹配
                "${1 ? 2}",            // 不完整三元表达式
                "${* foo}"             // 非法起始 token
        );
    }

    /** 合法纯文本前缀（不含 th: 与表达式），用于为非法模板增加变化性。 */
    @Provide
    Arbitrary<String> plainPrefixes() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(0).ofMaxLength(8);
    }
}
