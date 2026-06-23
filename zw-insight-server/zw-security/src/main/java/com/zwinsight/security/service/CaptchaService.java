package com.zwinsight.security.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.zwinsight.common.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CaptchaService {
    private final RedisUtils redisUtils;

    @Value("${auth.captcha-enabled:true}")
    private boolean captchaEnabled;

    private static final String CAPTCHA_PREFIX = "captcha:";

    public String generateCaptcha(String key) {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40, 4, 20);
        String code = captcha.getCode();
        redisUtils.set(CAPTCHA_PREFIX + key, code, 5, TimeUnit.MINUTES);
        return captcha.getImageBase64Data();
    }

    public boolean validateCaptcha(String key, String code) {
        if (!captchaEnabled) {
            return true;
        }
        Object cached = redisUtils.get(CAPTCHA_PREFIX + key);
        redisUtils.delete(CAPTCHA_PREFIX + key);
        return cached != null && cached.toString().equalsIgnoreCase(code);
    }

    public boolean isCaptchaEnabled() {
        return captchaEnabled;
    }
}
