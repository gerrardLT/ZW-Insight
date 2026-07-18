package com.zwinsight.security.service;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.zwinsight.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 阿里云短信服务实现
 * <p>
 * 当 sms.enabled=false 时仅打印日志不真实发送（开发/联调环境使用）；
 * 生产环境配置 sms.enabled=true 并填入正确的 accessKeyId/accessKeySecret/signName/templateCode，
 * 即通过阿里云 dysmsapi SDK 真实发送短信验证码。
 * </p>
 */
@Slf4j
@Service
public class AliyunSmsService implements SmsService {

    /** 短信服务接入点（华东1·杭州公共 Endpoint） */
    private static final String ENDPOINT = "dysmsapi.aliyuncs.com";

    @Value("${sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${sms.access-key-id:}")
    private String accessKeyId;

    @Value("${sms.access-key-secret:}")
    private String accessKeySecret;

    @Value("${sms.sign-name:}")
    private String signName;

    @Value("${sms.template-code:}")
    private String templateCode;

    @Override
    public void sendVerificationCode(String phone, String code) {
        if (!smsEnabled) {
            log.info("[SMS-DEV] 短信发送（模拟，未启用真实通道）: phone={}, code={}", phone, code);
            return;
        }

        // 启用真实通道时校验凭证完整性，缺失即失败（不做静默降级）
        if (isBlank(accessKeyId) || isBlank(accessKeySecret) || isBlank(signName) || isBlank(templateCode)) {
            throw new BusinessException("短信服务配置不完整，请检查 sms.access-key-id/access-key-secret/sign-name/template-code");
        }

        try {
            Client client = createClient();
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(phone)
                    .setSignName(signName)
                    .setTemplateCode(templateCode)
                    .setTemplateParam("{\"code\":\"" + code + "\"}");

            SendSmsResponse response = client.sendSms(request);
            String respCode = response.getBody() != null ? response.getBody().getCode() : null;
            if (!"OK".equals(respCode)) {
                String message = response.getBody() != null ? response.getBody().getMessage() : "无响应体";
                log.error("[SMS] 短信发送失败: phone={}, code={}, message={}", phone, respCode, message);
                throw new BusinessException("短信发送失败: " + message);
            }
            log.info("[SMS] 短信验证码发送成功: phone={}, bizId={}", phone, response.getBody().getBizId());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SMS] 短信发送异常: phone={}", phone, e);
            throw new BusinessException("短信发送异常: " + e.getMessage());
        }
    }

    /**
     * 构建阿里云短信客户端。
     */
    private Client createClient() throws Exception {
        Config config = new Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret);
        config.endpoint = ENDPOINT;
        return new Client(config);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
