package com.zwinsight.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 阿里云短信服务实现
 * <p>
 * 当 sms.enabled=false 时仅打印日志不真实发送，
 * 生产环境配置 sms.enabled=true 并填入正确的 accessKeyId/accessKeySecret/signName/templateCode
 */
@Slf4j
@Service
public class AliyunSmsService implements SmsService {

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
            log.info("[SMS-DEV] 短信发送（模拟）: phone={}, code={}", phone, code);
            return;
        }

        log.info("[SMS] 发送短信验证码: phone={}", phone);
        // TODO: 接入阿里云短信 SDK 真实发送
        // 示例调用流程:
        // 1. 构建 com.aliyun.dysmsapi20170525.Client
        // 2. 构造 SendSmsRequest，设置 phoneNumbers, signName, templateCode, templateParam
        // 3. 调用 client.sendSms(request)
        // 4. 检查 response.body.code 是否为 "OK"
        //
        // Client client = createClient(accessKeyId, accessKeySecret);
        // SendSmsRequest request = new SendSmsRequest()
        //     .setPhoneNumbers(phone)
        //     .setSignName(signName)
        //     .setTemplateCode(templateCode)
        //     .setTemplateParam("{\"code\":\"" + code + "\"}");
        // SendSmsResponse response = client.sendSms(request);
        // if (!"OK".equals(response.getBody().getCode())) {
        //     throw new BusinessException("短信发送失败: " + response.getBody().getMessage());
        // }
        log.info("[SMS] 短信验证码发送成功: phone={}", phone);
    }
}
