package com.zwinsight.budget.advice;

import com.zwinsight.budget.context.BudgetWarningContext;
import com.zwinsight.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 预算警告响应增强
 * <p>
 * 实现 ResponseBodyAdvice，在接口响应返回前检查 BudgetWarningContext 线程变量，
 * 如果存在警告信息，则将其写入响应头 X-Budget-Warning，供前端展示预算超支警告。
 * 最后清除 ThreadLocal 防止内存泄漏。
 * </p>
 */
@RestControllerAdvice
@Slf4j
public class BudgetWarningResponseAdvice implements ResponseBodyAdvice<R<?>> {

    private static final String BUDGET_WARNING_HEADER = "X-Budget-Warning";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 仅处理返回类型为 R 的接口
        return R.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public R<?> beforeBodyWrite(R<?> body, MethodParameter returnType,
                                MediaType selectedContentType,
                                Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                ServerHttpRequest request, ServerHttpResponse response) {
        try {
            String warning = BudgetWarningContext.getWarning();
            if (warning != null && !warning.isEmpty()) {
                // 将预算警告写入响应头
                response.getHeaders().add(BUDGET_WARNING_HEADER, warning);
                log.debug("BudgetWarningResponseAdvice: 设置响应头 {}={}", BUDGET_WARNING_HEADER, warning);
            }
        } finally {
            // 无论如何都清除 ThreadLocal，防止线程复用导致的数据污染
            BudgetWarningContext.clear();
        }
        return body;
    }
}
