package com.zwinsight.common.util;

import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * 公共断言工具类
 * <p>
 * 提供测试中常用的断言方法和日志脱敏函数。
 * 全 static 方法，不需要 Spring 注入。
 * </p>
 */
public final class AssertUtils {

    private AssertUtils() {
        // 防止实例化
    }

    /**
     * 断言 API 响应成功
     * <p>
     * 验证规则：
     * <ul>
     *   <li>HTTP 状态码为 2xx</li>
     *   <li>响应体非 null 且可解析</li>
     *   <li>响应体中 code 字段为 200</li>
     * </ul>
     * </p>
     *
     * @param response Spring ResponseEntity 响应对象
     * @throws AssertionError 当任何断言条件不满足时，包含完整上下文信息
     */
    @SuppressWarnings("unchecked")
    public static void assertApiSuccess(ResponseEntity<?> response) {
        assertThat(response)
                .as("API 响应不应为 null")
                .isNotNull();

        int httpStatus = response.getStatusCode().value();
        assertThat(httpStatus)
                .as("HTTP 状态码应为 2xx，实际为 %d，响应体: %s", httpStatus, response.getBody())
                .isBetween(200, 299);

        Object body = response.getBody();
        assertThat(body)
                .as("响应体不应为 null，HTTP 状态码: %d", httpStatus)
                .isNotNull();

        // 响应体应为 Map（JSON 解析后的结构）
        assertThat(body)
                .as("响应体应为 JSON 对象（Map），HTTP 状态码: %d，实际类型: %s",
                        httpStatus, body.getClass().getSimpleName())
                .isInstanceOf(Map.class);

        Map<String, Object> bodyMap = (Map<String, Object>) body;
        assertThat(bodyMap)
                .as("响应体 JSON 应包含 code 字段，HTTP 状态码: %d，响应体: %s", httpStatus, bodyMap)
                .containsKey("code");

        Object codeValue = bodyMap.get("code");
        assertThat(codeValue)
                .as("响应体 code 字段不应为 null，HTTP 状态码: %d，响应体: %s", httpStatus, bodyMap)
                .isNotNull();

        int code;
        if (codeValue instanceof Number) {
            code = ((Number) codeValue).intValue();
        } else {
            fail("响应体 code 字段应为数值类型，实际为: %s（值: %s），HTTP 状态码: %d，响应体: %s",
                    codeValue.getClass().getSimpleName(), codeValue, httpStatus, bodyMap);
            return; // unreachable，但编译器需要
        }

        assertThat(code)
                .as("响应体业务状态码应为 200，实际为 %d，HTTP 状态码: %d，响应体: %s",
                        code, httpStatus, bodyMap)
                .isEqualTo(200);
    }

    /**
     * 断言分页查询结果结构正确且记录数满足最低要求
     * <p>
     * 验证规则：
     * <ul>
     *   <li>data 为 Map 类型</li>
     *   <li>包含 records 字段（List 类型）且 size >= expectedMin</li>
     *   <li>包含 total 字段（数值类型）且 >= expectedMin</li>
     * </ul>
     * </p>
     *
     * @param data        分页数据对象（通常为响应体中的 data 字段）
     * @param expectedMin 期望的最小记录数
     * @throws AssertionError 当断言条件不满足时
     */
    @SuppressWarnings("unchecked")
    public static void assertPageResult(Object data, int expectedMin) {
        assertThat(data)
                .as("分页数据不应为 null")
                .isNotNull();

        assertThat(data)
                .as("分页数据应为 Map 类型，实际为: %s", data.getClass().getSimpleName())
                .isInstanceOf(Map.class);

        Map<String, Object> pageMap = (Map<String, Object>) data;

        // 验证 records 字段
        assertThat(pageMap)
                .as("分页数据应包含 records 字段，实际字段: %s", pageMap.keySet())
                .containsKey("records");

        Object records = pageMap.get("records");
        assertThat(records)
                .as("records 字段不应为 null")
                .isNotNull();
        assertThat(records)
                .as("records 字段应为 List 类型，实际为: %s", records.getClass().getSimpleName())
                .isInstanceOf(List.class);

        List<?> recordList = (List<?>) records;
        assertThat(recordList.size())
                .as("records 列表大小应 >= %d，实际为 %d", expectedMin, recordList.size())
                .isGreaterThanOrEqualTo(expectedMin);

        // 验证 total 字段
        assertThat(pageMap)
                .as("分页数据应包含 total 字段，实际字段: %s", pageMap.keySet())
                .containsKey("total");

        Object totalValue = pageMap.get("total");
        assertThat(totalValue)
                .as("total 字段不应为 null")
                .isNotNull();

        long total;
        if (totalValue instanceof Number) {
            total = ((Number) totalValue).longValue();
        } else {
            fail("total 字段应为数值类型，实际为: %s（值: %s）",
                    totalValue.getClass().getSimpleName(), totalValue);
            return; // unreachable
        }

        assertThat(total)
                .as("total 应 >= %d，实际为 %d", expectedMin, total)
                .isGreaterThanOrEqualTo(expectedMin);
    }

    /**
     * 日志脱敏函数
     * <p>
     * 规则：
     * <ul>
     *   <li>null 输入返回 "null"</li>
     *   <li>长度 <= 4 返回 "****"</li>
     *   <li>否则保留前2字符和后2字符，中间用 * 替代</li>
     * </ul>
     * </p>
     *
     * @param sensitive 需要脱敏的敏感字符串
     * @return 脱敏后的字符串
     */
    public static String mask(String sensitive) {
        if (sensitive == null) {
            return "null";
        }
        if (sensitive.length() <= 4) {
            return "****";
        }
        int midLen = sensitive.length() - 4; // 中间需要遮盖的字符数
        return sensitive.substring(0, 2) + "*".repeat(midLen) + sensitive.substring(sensitive.length() - 2);
    }

    /**
     * 断言对象非空
     * <p>
     * 验证规则：
     * <ul>
     *   <li>对象非 null</li>
     *   <li>如果是字符串，则非空白（trimmed 后 length > 0）</li>
     * </ul>
     * </p>
     *
     * @param obj       待检查对象
     * @param fieldName 字段名称（用于失败消息）
     * @throws AssertionError 当对象为 null 或为空白字符串时，message 包含 fieldName
     */
    public static void assertNotEmpty(Object obj, String fieldName) {
        assertThat(obj)
                .as("字段 [%s] 不应为 null", fieldName)
                .isNotNull();

        if (obj instanceof String str) {
            assertThat(str.trim())
                    .as("字段 [%s] 不应为空白字符串", fieldName)
                    .isNotEmpty();
        }
    }
}
