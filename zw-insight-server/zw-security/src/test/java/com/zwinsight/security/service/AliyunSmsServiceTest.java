package com.zwinsight.security.service;

import com.zwinsight.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AliyunSmsService 单元测试（阶段四批 1 补测）
 * <p>短信外呼真实发送分支依赖阿里云通道，登记豁免（见 coverage-matrix 豁免区）；
 * 本类覆盖开关分支与启用但配置缺失的显式失败（不静默降级）。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AliyunSmsService — 短信通道开关与配置校验")
class AliyunSmsServiceTest {

    @InjectMocks
    private AliyunSmsService service;

    @Test
    @DisplayName("sms.enabled=false - 模拟模式仅记日志不抛异常")
    void disabled_simulatedSend_noException() {
        ReflectionTestUtils.setField(service, "smsEnabled", false);

        assertThatCode(() -> service.sendVerificationCode("13800000001", "1234"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sms.enabled=true 但凭证缺失 - 显式失败不静默降级")
    void enabled_missingCredentials_throws() {
        ReflectionTestUtils.setField(service, "smsEnabled", true);
        ReflectionTestUtils.setField(service, "accessKeyId", "");
        ReflectionTestUtils.setField(service, "accessKeySecret", "");
        ReflectionTestUtils.setField(service, "signName", "");
        ReflectionTestUtils.setField(service, "templateCode", "");

        assertThatThrownBy(() -> service.sendVerificationCode("13800000001", "1234"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("短信服务配置不完整");
    }

    @Test
    @DisplayName("sms.enabled=true 部分凭证缺失（仅缺 template-code）- 同样显式失败")
    void enabled_partialCredentials_throws() {
        ReflectionTestUtils.setField(service, "smsEnabled", true);
        ReflectionTestUtils.setField(service, "accessKeyId", "ak");
        ReflectionTestUtils.setField(service, "accessKeySecret", "sk");
        ReflectionTestUtils.setField(service, "signName", "中维智营");
        ReflectionTestUtils.setField(service, "templateCode", "");

        assertThatThrownBy(() -> service.sendVerificationCode("13800000001", "1234"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("短信服务配置不完整");
    }
}
