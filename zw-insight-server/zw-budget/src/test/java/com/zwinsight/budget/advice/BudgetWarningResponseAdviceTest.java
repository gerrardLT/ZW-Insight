package com.zwinsight.budget.advice;

import com.zwinsight.budget.context.BudgetWarningContext;
import com.zwinsight.common.result.R;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BudgetWarningResponseAdvice 单元测试（P1 BUD-ASP-08 补测，2026-08-13）
 * <p>
 * 覆盖 WARN 警告响应头链路：线程变量有警告 → 写入 X-Budget-Warning 响应头并清除；
 * 无警告 → 不写响应头；无论何种情况 finally 均清除 ThreadLocal 防泄漏。
 * </p>
 */
class BudgetWarningResponseAdviceTest {

    private final BudgetWarningResponseAdvice advice = new BudgetWarningResponseAdvice();

    @AfterEach
    void tearDown() {
        BudgetWarningContext.clear();
    }

    private ServerHttpResponse responseWithHeaders(HttpHeaders headers) {
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);
        return response;
    }

    @Test
    @DisplayName("线程变量有警告 → 写入 X-Budget-Warning 响应头且清除变量（P1 BUD-ASP-08）")
    void warningPresent_writesHeaderAndClears() {
        BudgetWarningContext.setWarning("预算执行率已达 95%");
        HttpHeaders headers = new HttpHeaders();

        R<?> body = new R<>();
        R<?> result = advice.beforeBodyWrite(body, null, null, null,
                mock(ServerHttpRequest.class), responseWithHeaders(headers));

        assertThat(result).isSameAs(body);
        assertThat(headers.getFirst("X-Budget-Warning")).isEqualTo("预算执行率已达 95%");
        assertThat(BudgetWarningContext.getWarning()).as("写入后应清除 ThreadLocal").isNull();
    }

    @Test
    @DisplayName("无警告 → 不写响应头")
    void noWarning_noHeader() {
        HttpHeaders headers = new HttpHeaders();

        advice.beforeBodyWrite(new R<>(), null, null, null,
                mock(ServerHttpRequest.class), responseWithHeaders(headers));

        assertThat(headers.containsKey("X-Budget-Warning")).isFalse();
    }

    @Test
    @DisplayName("空字符串警告视为无警告 → 不写响应头")
    void emptyWarning_noHeader() {
        BudgetWarningContext.setWarning("");
        HttpHeaders headers = new HttpHeaders();

        advice.beforeBodyWrite(new R<>(), null, null, null,
                mock(ServerHttpRequest.class), responseWithHeaders(headers));

        assertThat(headers.containsKey("X-Budget-Warning")).isFalse();
        assertThat(BudgetWarningContext.getWarning()).isNull();
    }

    @Test
    @DisplayName("supports：返回类型为 R 才增强")
    void supports_onlyR() {
        MethodParameter rParam = mock(MethodParameter.class);
        when(rParam.getParameterType()).thenReturn((Class) R.class);
        MethodParameter stringParam = mock(MethodParameter.class);
        when(stringParam.getParameterType()).thenReturn((Class) String.class);

        assertThat(advice.supports(rParam, null)).isTrue();
        assertThat(advice.supports(stringParam, null)).isFalse();
    }
}
