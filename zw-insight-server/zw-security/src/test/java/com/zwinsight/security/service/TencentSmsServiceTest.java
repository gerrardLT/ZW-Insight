package com.zwinsight.security.service;

import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;
import com.zwinsight.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TencentSmsServiceTest {

    @Mock
    private SmsClient client;
    private TencentSmsService service;

    @BeforeEach
    void setUp() {
        service = new TencentSmsService() {
            @Override
            protected SmsClient createClient() {
                return client;
            }
        };
    }

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

    @Test
    void enabled_success_sendsChinaPhoneAndCode() throws Exception {
        enableWithCompleteConfig();
        SendStatus status = new SendStatus();
        status.setCode("Ok");
        status.setSerialNo("serial-1");
        SendSmsResponse response = new SendSmsResponse();
        response.setSendStatusSet(new SendStatus[]{status});
        when(client.SendSms(argThat(this::isExpectedRequest))).thenReturn(response);

        assertThatCode(() -> service.sendVerificationCode("13800000001", "1234"))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_providerRejects_throwsBusinessException() throws Exception {
        enableWithCompleteConfig();
        SendStatus status = new SendStatus();
        status.setCode("FailedOperation.TemplateIncorrectOrUnapproved");
        status.setMessage("template not approved");
        SendSmsResponse response = new SendSmsResponse();
        response.setSendStatusSet(new SendStatus[]{status});
        when(client.SendSms(org.mockito.ArgumentMatchers.any(SendSmsRequest.class))).thenReturn(response);

        assertThatThrownBy(() -> service.sendVerificationCode("+8613800000001", "5678"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("template not approved");
    }

    private void enableWithCompleteConfig() {
        ReflectionTestUtils.setField(service, "smsEnabled", true);
        ReflectionTestUtils.setField(service, "secretId", "secret-id");
        ReflectionTestUtils.setField(service, "secretKey", "secret-key");
        ReflectionTestUtils.setField(service, "region", "ap-guangzhou");
        ReflectionTestUtils.setField(service, "sdkAppId", "1400000000");
        ReflectionTestUtils.setField(service, "signName", "徽颍智营");
        ReflectionTestUtils.setField(service, "templateId", "123456");
    }

    private boolean isExpectedRequest(SendSmsRequest request) {
        return "1400000000".equals(request.getSmsSdkAppId())
                && "徽颍智营".equals(request.getSignName())
                && "123456".equals(request.getTemplateId())
                && request.getPhoneNumberSet().length == 1
                && "+8613800000001".equals(request.getPhoneNumberSet()[0])
                && request.getTemplateParamSet().length == 1
                && "1234".equals(request.getTemplateParamSet()[0]);
    }
}
