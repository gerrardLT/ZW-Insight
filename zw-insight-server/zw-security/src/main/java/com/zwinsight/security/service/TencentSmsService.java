package com.zwinsight.security.service;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;
import com.zwinsight.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 腾讯云短信实现；由 {@code sms.provider=tencent} 在徽颖环境启用。 */
@Slf4j
@Service
@ConditionalOnProperty(name = "sms.provider", havingValue = "tencent")
public class TencentSmsService implements SmsService {

    private static final String ENDPOINT = "sms.tencentcloudapi.com";

    @Value("${sms.enabled:false}")
    private boolean smsEnabled;
    @Value("${sms.tencent.secret-id:}")
    private String secretId;
    @Value("${sms.tencent.secret-key:}")
    private String secretKey;
    @Value("${sms.tencent.region:ap-guangzhou}")
    private String region;
    @Value("${sms.tencent.sdk-app-id:}")
    private String sdkAppId;
    @Value("${sms.tencent.sign-name:}")
    private String signName;
    @Value("${sms.tencent.template-id:}")
    private String templateId;

    @Override
    public void sendVerificationCode(String phone, String code) {
        if (!smsEnabled) {
            log.info("[SMS-DEV] 腾讯云短信模拟发送: phone={}, code={}", phone, code);
            return;
        }
        if (isBlank(secretId) || isBlank(secretKey) || isBlank(sdkAppId)
                || isBlank(signName) || isBlank(templateId)) {
            throw new BusinessException("腾讯云短信配置不完整，请检查 secret-id/secret-key/sdk-app-id/sign-name/template-id");
        }

        try {
            SendSmsRequest request = new SendSmsRequest();
            request.setSmsSdkAppId(sdkAppId);
            request.setSignName(signName);
            request.setTemplateId(templateId);
            request.setPhoneNumberSet(new String[]{normalizePhone(phone)});
            request.setTemplateParamSet(new String[]{code});

            SendSmsResponse response = createClient().SendSms(request);
            SendStatus[] statuses = response.getSendStatusSet();
            SendStatus status = statuses != null && statuses.length > 0 ? statuses[0] : null;
            if (status == null || !"Ok".equalsIgnoreCase(status.getCode())) {
                String message = status == null ? "无发送状态" : status.getMessage();
                String responseCode = status == null ? null : status.getCode();
                log.error("[SMS-TENCENT] 发送失败: phone={}, code={}, message={}", phone, responseCode, message);
                throw new BusinessException("短信发送失败: " + message);
            }
            log.info("[SMS-TENCENT] 验证码发送成功: phone={}, serialNo={}", phone, status.getSerialNo());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SMS-TENCENT] 发送异常: phone={}", phone, e);
            throw new BusinessException("短信发送异常: " + e.getMessage());
        }
    }

    protected SmsClient createClient() {
        Credential credential = new Credential(secretId, secretKey);
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(ENDPOINT);
        ClientProfile profile = new ClientProfile();
        profile.setHttpProfile(httpProfile);
        return new SmsClient(credential, region, profile);
    }

    private String normalizePhone(String phone) {
        String value = phone == null ? "" : phone.trim();
        return value.startsWith("+") ? value : "+86" + value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
