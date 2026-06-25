package com.zwinsight.purchase.portal.service;

import com.zwinsight.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 供应商短信验证码服务
 * <p>
 * 当前为 stub 实现：验证码仅写入 Redis 并输出日志，不实际调用短信网关。
 * 生产环境可替换为真实短信服务（如阿里云 SMS）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierSmsService {

    private final StringRedisTemplate redisTemplate;

    private static final String SMS_CODE_KEY_PREFIX = "sms:supplier:";
    private static final long SMS_CODE_TTL_MINUTES = 5;
    private static final long SMS_CODE_INTERVAL_SECONDS = 60;

    /**
     * 发送验证码
     *
     * @param phone 手机号
     */
    public void sendCode(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new BusinessException("手机号不能为空");
        }
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException("手机号格式不正确");
        }

        // 频率控制：60秒内不允许重复发送
        String intervalKey = SMS_CODE_KEY_PREFIX + phone + ":interval";
        Boolean hasInterval = redisTemplate.hasKey(intervalKey);
        if (Boolean.TRUE.equals(hasInterval)) {
            throw new BusinessException("验证码发送过于频繁，请60秒后再试");
        }

        // 生成6位数字验证码
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999));

        // 存入 Redis（TTL 5分钟）
        String codeKey = SMS_CODE_KEY_PREFIX + phone;
        redisTemplate.opsForValue().set(codeKey, code, SMS_CODE_TTL_MINUTES, TimeUnit.MINUTES);

        // 设置发送间隔标记（60秒）
        redisTemplate.opsForValue().set(intervalKey, "1", SMS_CODE_INTERVAL_SECONDS, TimeUnit.SECONDS);

        // Stub: 仅记录日志，不实际发送短信
        log.info("【供应商门户验证码】手机号: {}, 验证码: {}, 有效期: {}分钟", phone, code, SMS_CODE_TTL_MINUTES);
    }

    /**
     * 验证验证码
     *
     * @param phone 手机号
     * @param code  验证码
     * @return true-验证通过
     */
    public boolean verifyCode(String phone, String code) {
        if (phone == null || code == null) {
            return false;
        }
        String codeKey = SMS_CODE_KEY_PREFIX + phone;
        String storedCode = redisTemplate.opsForValue().get(codeKey);
        if (storedCode != null && storedCode.equals(code)) {
            // 验证成功后删除验证码（一次性使用）
            redisTemplate.delete(codeKey);
            return true;
        }
        return false;
    }
}
