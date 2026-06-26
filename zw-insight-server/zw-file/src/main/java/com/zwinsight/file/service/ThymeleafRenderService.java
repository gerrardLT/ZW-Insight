package com.zwinsight.file.service;

import com.zwinsight.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Collections;
import java.util.Map;

/**
 * Thymeleaf 字符串模板渲染服务
 *
 * <p>使用 Thymeleaf {@link SpringTemplateEngine} 配合 {@link StringTemplateResolver}，
 * 直接渲染数据库中存储的 HTML 模板字符串（而非 classpath 模板文件），
 * 支持 {@code th:text}、{@code th:each}、{@code th:if} 等标准 Thymeleaf 语法。</p>
 *
 * <p>选用 {@code SpringTemplateEngine}（而非裸 {@code org.thymeleaf.TemplateEngine}）的原因：
 * 自 Thymeleaf 3.1 起 OGNL 成为可选依赖，普通 {@code TemplateEngine} 默认的 StandardDialect
 * 在表达式求值时依赖 OGNL（{@code ognl.PropertyAccessor}），而 spring-boot-starter-thymeleaf
 * 的类路径中并不包含 OGNL，运行时会抛出 {@code NoClassDefFoundError}。
 * {@code SpringTemplateEngine} 默认采用 SpringStandardDialect，使用 SpringEL 进行表达式求值，
 * 其依赖（spring-expression）已随 spring-boot-starter-thymeleaf 在类路径中，无需额外引入依赖。</p>
 */
@Slf4j
@Service
public class ThymeleafRenderService {

    /**
     * 共享 SpringTemplateEngine 实例。
     * SpringTemplateEngine 与 StringTemplateResolver 均为线程安全，可在多线程下复用。
     */
    private final SpringTemplateEngine templateEngine;

    public ThymeleafRenderService() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        // 字符串模板按 HTML 模式解析，支持 th:* 属性
        resolver.setTemplateMode(TemplateMode.HTML);
        // 字符串模板内容本身即模板，缓存无意义且会占用内存，关闭缓存
        resolver.setCacheable(false);

        // SpringTemplateEngine 默认即配置 SpringStandardDialect（SpringEL 求值），无需额外引入 OGNL。
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        this.templateEngine = engine;
    }

    /**
     * 使用 Thymeleaf 渲染字符串模板。
     *
     * @param templateContent 模板 HTML 内容（含 th:text、th:each、th:if 等语法）
     * @param variables       业务数据变量 Map，键为模板中引用的变量名
     * @return 渲染后的完整 HTML 字符串
     * @throws BusinessException 当模板语法错误或渲染失败时抛出，message 包含错误行号和描述
     */
    public String render(String templateContent, Map<String, Object> variables) {
        if (templateContent == null) {
            throw new BusinessException(500, "模板渲染失败: 模板内容为空");
        }

        Context context = new Context();
        Map<String, Object> vars = variables != null ? variables : Collections.emptyMap();
        context.setVariables(vars);

        try {
            return templateEngine.process(templateContent, context);
        } catch (TemplateProcessingException e) {
            // TemplateProcessingException（含子类 TemplateInputException）携带行列号信息，
            // 提取后返回可定位的错误详情
            int line = e.getLine() != null ? e.getLine() : -1;
            int col = e.getCol() != null ? e.getCol() : -1;
            String rootMsg = rootCauseMessage(e);
            String detail = String.format("模板渲染失败(行%d,列%d): %s", line, col, rootMsg);
            log.warn("Thymeleaf 模板渲染失败 line={} col={} msg={}", line, col, rootMsg);
            throw new BusinessException(500, detail, e);
        } catch (RuntimeException e) {
            // 其它运行期异常（如表达式求值错误）兜底处理，仍返回描述信息
            String rootMsg = rootCauseMessage(e);
            log.warn("Thymeleaf 模板渲染失败: {}", rootMsg);
            throw new BusinessException(500, "模板渲染失败: " + rootMsg, e);
        }
    }

    /**
     * 提取异常链最底层的原始错误描述，便于排查模板问题。
     */
    private String rootCauseMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String msg = cause.getMessage();
        return msg != null ? msg : cause.getClass().getSimpleName();
    }
}
