package com.zwinsight.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * AssertUtils 工具类单元测试
 */
class AssertUtilsTest {

    @Nested
    @DisplayName("assertApiSuccess 测试")
    class AssertApiSuccessTests {

        @Test
        @DisplayName("标准成功响应 - 通过")
        void successResponse_passes() {
            Map<String, Object> body = Map.of("code", 200, "msg", "success", "data", "test");
            ResponseEntity<Map<String, Object>> response = ResponseEntity.ok(body);

            // 不应抛出异常
            assertThatCode(() -> AssertUtils.assertApiSuccess(response))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("HTTP 4xx 状态码 - 失败")
        void http4xxResponse_fails() {
            Map<String, Object> body = Map.of("code", 400, "msg", "bad request");
            ResponseEntity<Map<String, Object>> response = ResponseEntity.badRequest().body(body);

            assertThatThrownBy(() -> AssertUtils.assertApiSuccess(response))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        @DisplayName("业务码非200 - 失败")
        void businessCodeNot200_fails() {
            Map<String, Object> body = Map.of("code", 500, "msg", "internal error");
            ResponseEntity<Map<String, Object>> response = ResponseEntity.ok(body);

            assertThatThrownBy(() -> AssertUtils.assertApiSuccess(response))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        @DisplayName("null 响应 - 失败")
        void nullResponse_fails() {
            assertThatThrownBy(() -> AssertUtils.assertApiSuccess(null))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        @DisplayName("null body - 失败")
        void nullBody_fails() {
            ResponseEntity<?> response = ResponseEntity.ok(null);

            assertThatThrownBy(() -> AssertUtils.assertApiSuccess(response))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        @DisplayName("body 无 code 字段 - 失败")
        void bodyWithoutCodeField_fails() {
            Map<String, Object> body = Map.of("msg", "success", "data", "test");
            ResponseEntity<Map<String, Object>> response = ResponseEntity.ok(body);

            assertThatThrownBy(() -> AssertUtils.assertApiSuccess(response))
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Nested
    @DisplayName("assertPageResult 测试")
    class AssertPageResultTests {

        @Test
        @DisplayName("标准分页数据 - 通过")
        void validPageResult_passes() {
            Map<String, Object> data = new HashMap<>();
            data.put("records", List.of(Map.of("id", 1), Map.of("id", 2)));
            data.put("total", 10L);

            assertThatCode(() -> AssertUtils.assertPageResult(data, 1))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("records 数量等于 expectedMin - 通过")
        void recordsEqualToMin_passes() {
            Map<String, Object> data = new HashMap<>();
            data.put("records", List.of(Map.of("id", 1)));
            data.put("total", 1);

            assertThatCode(() -> AssertUtils.assertPageResult(data, 1))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("records 数量小于 expectedMin - 失败")
        void recordsLessThanMin_fails() {
            Map<String, Object> data = new HashMap<>();
            data.put("records", List.of());
            data.put("total", 0);

            assertThatThrownBy(() -> AssertUtils.assertPageResult(data, 1))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        @DisplayName("null 数据 - 失败")
        void nullData_fails() {
            assertThatThrownBy(() -> AssertUtils.assertPageResult(null, 1))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        @DisplayName("缺少 records 字段 - 失败")
        void missingRecordsField_fails() {
            Map<String, Object> data = Map.of("total", 10);

            assertThatThrownBy(() -> AssertUtils.assertPageResult(data, 1))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        @DisplayName("缺少 total 字段 - 失败")
        void missingTotalField_fails() {
            Map<String, Object> data = Map.of("records", List.of(Map.of("id", 1)));

            assertThatThrownBy(() -> AssertUtils.assertPageResult(data, 1))
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Nested
    @DisplayName("mask 脱敏函数测试")
    class MaskTests {

        @Test
        @DisplayName("null 输入返回 \"null\"")
        void nullInput_returnsNullString() {
            assertThat(AssertUtils.mask(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("空字符串（长度<=4）返回 ****")
        void emptyString_returnsMasked() {
            assertThat(AssertUtils.mask("")).isEqualTo("****");
        }

        @Test
        @DisplayName("长度 1-4 返回 ****")
        void shortString_returnsFourStars() {
            assertThat(AssertUtils.mask("a")).isEqualTo("****");
            assertThat(AssertUtils.mask("ab")).isEqualTo("****");
            assertThat(AssertUtils.mask("abc")).isEqualTo("****");
            assertThat(AssertUtils.mask("abcd")).isEqualTo("****");
        }

        @Test
        @DisplayName("长度 5 保留前2后2，中间1个*")
        void length5_masksMiddle() {
            assertThat(AssertUtils.mask("abcde")).isEqualTo("ab*de");
        }

        @Test
        @DisplayName("长度 8 保留前2后2，中间4个*")
        void length8_masksMiddle() {
            assertThat(AssertUtils.mask("12345678")).isEqualTo("12****78");
        }

        @Test
        @DisplayName("真实 JWT token 脱敏")
        void jwtToken_masksMiddle() {
            String token = "eyJhbGciOiJIUzI1NiJ9.payload.signature";
            String masked = AssertUtils.mask(token);
            assertThat(masked).startsWith("ey");
            assertThat(masked).endsWith("re");
            // 中间部分全是 *
            String middle = masked.substring(2, masked.length() - 2);
            assertThat(middle).matches("\\*+");
        }

        @Test
        @DisplayName("密码脱敏")
        void password_masksMiddle() {
            assertThat(AssertUtils.mask("123456")).isEqualTo("12**56");
            assertThat(AssertUtils.mask("admin123")).isEqualTo("ad****23");
        }
    }

    @Nested
    @DisplayName("assertNotEmpty 测试")
    class AssertNotEmptyTests {

        @Test
        @DisplayName("非 null 对象 - 通过")
        void nonNullObject_passes() {
            assertThatCode(() -> AssertUtils.assertNotEmpty(123, "id"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("非空字符串 - 通过")
        void nonEmptyString_passes() {
            assertThatCode(() -> AssertUtils.assertNotEmpty("hello", "name"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null 对象 - 失败且包含字段名")
        void nullObject_failsWithFieldName() {
            assertThatThrownBy(() -> AssertUtils.assertNotEmpty(null, "projectName"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("projectName");
        }

        @Test
        @DisplayName("空白字符串 - 失败且包含字段名")
        void blankString_failsWithFieldName() {
            assertThatThrownBy(() -> AssertUtils.assertNotEmpty("   ", "username"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("username");
        }

        @Test
        @DisplayName("空字符串 - 失败")
        void emptyString_fails() {
            assertThatThrownBy(() -> AssertUtils.assertNotEmpty("", "email"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("email");
        }
    }
}
