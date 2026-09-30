package com.zwinsight.security.service;

import com.zwinsight.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TencentSmsServiceTest {

    private final TencentSmsService service = new TencentSmsService();

    @Test
    void disabled_doesNotCallRemote() {
        ReflectionTestUtils.setField(service, "smsEnabled", false);
        assertThatCode(() -> service.sendVerificationCode("13800000001", "1234"))
                .doesNotThrowAnyException();
    }

    @Test
    void enabledWithMissingCredentials_failsExplicitly() {
        ReflectionTestUtils.setField(service, "smsEnabled", true);
        ReflectionTestUtils.setField(service, "secretId", "");
        ReflectionTestUtils.setField(service, "secretKey", "");
        ReflectionTestUtils.setField(service, "sdkAppId", "");
        ReflectionTestUtils.setField(service, "signName", "");
        ReflectionTestUtils.setField(service, "templateId", "");

        assertThatThrownBy(() -> service.sendVerificationCode("13800000001", "1234"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("腾讯云短信配置不完整");
    }
}
