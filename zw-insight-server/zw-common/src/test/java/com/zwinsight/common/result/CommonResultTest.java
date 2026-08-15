package com.zwinsight.common.result;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.exception.DataPermissionException;
import com.zwinsight.common.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * zw-common 统一响应体与全局异常处理器单元测试（2026-08-15 P3 后端低覆盖补测）
 *
 * 覆盖场景:
 * - PageResult 默认/全参构造 + of(IPage) 转换
 * - R 静态工厂 ok/fail 各重载与构造
 * - GlobalExceptionHandler 业务/数据权限/方法不支持/兜底异常处理
 */
class CommonResultTest {

    @Nested
    @DisplayName("PageResult 分页结果")
    class PageResultTests {

        @Test
        @DisplayName("默认构造 records 为空列表")
        void defaultConstructor_emptyRecords() {
            PageResult<String> result = new PageResult<>();
            assertThat(result.getRecords()).isEmpty();
        }

        @Test
        @DisplayName("全参构造赋值")
        void allArgsConstructor() {
            PageResult<String> result = new PageResult<>(List.of("a", "b"), 2L, 1L, 10L, 1L);
            assertThat(result.getRecords()).containsExactly("a", "b");
            assertThat(result.getTotal()).isEqualTo(2L);
            assertThat(result.getPage()).isEqualTo(1L);
            assertThat(result.getSize()).isEqualTo(10L);
            assertThat(result.getPages()).isEqualTo(1L);
        }

        @Test
        @DisplayName("of(IPage) 转换字段一致")
        void ofIPage_mapsFields() {
            Page<String> page = new Page<>(2, 10);
            page.setRecords(List.of("x"));
            page.setTotal(25L);

            PageResult<String> result = PageResult.of(page);

            assertThat(result.getRecords()).containsExactly("x");
            assertThat(result.getTotal()).isEqualTo(25L);
            assertThat(result.getPage()).isEqualTo(2L);
            assertThat(result.getSize()).isEqualTo(10L);
            assertThat(result.getPages()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("R 统一响应体")
    class RTests {

        @Test
        @DisplayName("ok() 无数据")
        void ok_noData() {
            R<Void> r = R.ok();
            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getMessage()).isEqualTo("操作成功");
            assertThat(r.getData()).isNull();
            assertThat(r.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("ok(data) 带数据")
        void ok_withData() {
            R<String> r = R.ok("hello");
            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData()).isEqualTo("hello");
        }

        @Test
        @DisplayName("ok(message, data) 带消息和数据")
        void ok_withMessageAndData() {
            R<Integer> r = R.ok("自定义消息", 42);
            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getMessage()).isEqualTo("自定义消息");
            assertThat(r.getData()).isEqualTo(42);
        }

        @Test
        @DisplayName("fail(message) 默认 500")
        void fail_withMessage() {
            R<Void> r = R.fail("出错了");
            assertThat(r.getCode()).isEqualTo(500);
            assertThat(r.getMessage()).isEqualTo("出错了");
            assertThat(r.getData()).isNull();
        }

        @Test
        @DisplayName("fail(code, message) 自定义状态码")
        void fail_withCodeAndMessage() {
            R<Void> r = R.fail(404, "未找到");
            assertThat(r.getCode()).isEqualTo(404);
            assertThat(r.getMessage()).isEqualTo("未找到");
        }
    }

    @Nested
    @DisplayName("GlobalExceptionHandler 全局异常处理器")
    class GlobalExceptionHandlerTests {

        private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
        private final HttpServletRequest request = mock(HttpServletRequest.class);

        @Test
        @DisplayName("业务异常返回其 code 与 message")
        void handleBusinessException() {
            when(request.getRequestURI()).thenReturn("/api/v1/test");
            BusinessException e = new BusinessException(600, "业务失败");

            R<Void> r = handler.handleBusinessException(e, request);

            assertThat(r.getCode()).isEqualTo(600);
            assertThat(r.getMessage()).isEqualTo("业务失败");
        }

        @Test
        @DisplayName("数据权限异常返回其 code 与 message")
        void handleDataPermissionException() {
            when(request.getRequestURI()).thenReturn("/api/v1/secure");
            DataPermissionException e = new DataPermissionException("无数据权限");

            R<Void> r = handler.handleDataPermissionException(e, request);

            assertThat(r.getMessage()).isEqualTo("无数据权限");
        }

        @Test
        @DisplayName("请求方法不支持返回提示")
        void handleHttpRequestMethodNotSupported() {
            HttpRequestMethodNotSupportedException e = new HttpRequestMethodNotSupportedException("DELETE");

            R<Void> r = handler.handleHttpRequestMethodNotSupportedException(e);

            assertThat(r.getCode()).isNotEqualTo(200);
            assertThat(r.getMessage()).isNotBlank();
        }

        @Test
        @DisplayName("兜底异常返回 500")
        void handleException_fallback() {
            when(request.getRequestURI()).thenReturn("/api/v1/unknown");
            Exception e = new RuntimeException("意外错误");

            R<Void> r = handler.handleException(e, request);

            assertThat(r.getCode()).isEqualTo(500);
        }
    }
}
