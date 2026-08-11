package com.zwinsight.message.service;

import cn.hutool.http.HttpUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * 企业微信群机器人服务单元测试
 *
 * <p>真实外呼分支不触达企微服务器：通过 mockStatic 拦截 HttpUtil.post，
 * 覆盖启用/未启用/缺 webhook/成功/业务失败/网络异常全部分支。</p>
 */
class WeChatWorkServiceTest {

    private WeChatWorkService service;

    @BeforeEach
    void setUp() {
        service = new WeChatWorkService();
    }

    private void configure(boolean enabled, String webhook) {
        ReflectionTestUtils.setField(service, "robotEnabled", enabled);
        ReflectionTestUtils.setField(service, "robotWebhook", webhook);
    }

    @Test
    @DisplayName("未启用：直接返回 false 不发起 HTTP 调用")
    void testSendText_disabled() {
        configure(false, "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=k");

        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            assertThat(service.sendText("测试")).isFalse();
            http.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("启用但未配置 webhook：返回 false 不发起 HTTP 调用")
    void testSendText_missingWebhook() {
        configure(true, "");

        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            assertThat(service.sendText("测试")).isFalse();
            http.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("推送成功：errcode=0 返回 true")
    void testSendText_success() {
        configure(true, "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=k");

        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.post(anyString(), anyString()))
                    .thenReturn("{\"errcode\":0,\"errmsg\":\"ok\"}");
            assertThat(service.sendText("测试")).isTrue();
        }
    }

    @Test
    @DisplayName("推送业务失败：errcode 非 0 返回 false")
    void testSendText_businessFailure() {
        configure(true, "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=k");

        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.post(anyString(), anyString()))
                    .thenReturn("{\"errcode\":93000,\"errmsg\":\"invalid webhook url\"}");
            assertThat(service.sendText("测试")).isFalse();
        }
    }

    @Test
    @DisplayName("推送网络异常：捕获并返回 false，不向外抛出")
    void testSendText_networkException() {
        configure(true, "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=k");

        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.post(anyString(), anyString()))
                    .thenThrow(new RuntimeException("connection refused"));
            assertThat(service.sendText("测试")).isFalse();
        }
    }
}
