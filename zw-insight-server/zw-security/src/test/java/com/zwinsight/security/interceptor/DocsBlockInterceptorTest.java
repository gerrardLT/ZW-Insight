package com.zwinsight.security.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * API 文档端点拦截器单元测试（生产收敛，tasks.md 2.3.3 豁免条件）。
 * <p>
 * 覆盖：正常路径（拦截生效返回 404 且阻断 handler）、异常路径（handler 参数为 null 不抛异常）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DocsBlockInterceptor — API 文档端点生产收敛")
class DocsBlockInterceptorTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private final DocsBlockInterceptor interceptor = new DocsBlockInterceptor();

    @Test
    @DisplayName("preHandle 返回 false 并置 404，阻断文档端点 handler 执行")
    void preHandle_blocksWith404() throws Exception {
        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        verify(response).setStatus(404);
    }

    @Test
    @DisplayName("handler 为 null（资源处理器未解析场景）同样安全拦截不抛异常")
    void preHandle_nullHandler_stillBlocks() throws Exception {
        boolean proceed = interceptor.preHandle(request, response, null);

        assertThat(proceed).isFalse();
        verify(response).setStatus(404);
    }
}
