package com.zwinsight.security.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.security.dto.CaptchaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CaptchaService {
    private final RedisUtils redisUtils;
    private final SmsService smsService;

    @Value("${auth.captcha-enabled:true}")
    private boolean captchaEnabled;

    // ============ 图形验证码常量 ============
    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final long CAPTCHA_TTL_SECONDS = 300L;

    // ============ 短信验证码常量 ============
    private static final String SMS_KEY_PREFIX = "sms:";
    private static final String SMS_FREQ_PREFIX = "sms:freq:";
    private static final String SMS_DAILY_PREFIX = "sms:daily:";
    private static final long SMS_TTL_SECONDS = 300L;
    private static final long SMS_FREQ_TTL_SECONDS = 60L;
    private static final int SMS_DAILY_LIMIT = 10;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    // ============ 图形验证码方法（原有，保持向后兼容） ============

    /**
     * 生成图形验证码（原有方法，保持向后兼容）
     *
     * @param key 外部传入的 key
     * @return Base64 编码的验证码图片
     */
    public String generateCaptcha(String key) {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40, 4, 20);
        String code = captcha.getCode();
        redisUtils.set(CAPTCHA_PREFIX + key, code, CAPTCHA_TTL_SECONDS, TimeUnit.SECONDS);
        return captcha.getImageBase64Data();
    }

    /**
     * 校验验证码（原有方法，保持向后兼容）
     *
     * @param key  验证码 key
     * @param code 用户输入的验证码
     * @return 校验是否通过
     */
    public boolean validateCaptcha(String key, String code) {
        if (!captchaEnabled) {
            return true;
        }
        Object cached = redisUtils.get(CAPTCHA_PREFIX + key);
        redisUtils.delete(CAPTCHA_PREFIX + key);
        return cached != null && cached.toString().equalsIgnoreCase(code);
    }

    /**
     * 生成图形验证码（新方法）
     * UUID 自动生成，使用 Hutool LineCaptcha 生成 4 位字母数字混合验证码
     * 存入 Redis: key=captcha:{uuid}, value=code, TTL=300s
     *
     * @return CaptchaVO 包含 uuid 和 Base64 图片（带 data:image/png;base64, 前缀）
     */
    public CaptchaVO generateImageCaptcha() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(130, 48, 4, 80);
        String code = captcha.getCode();

        // 存入 Redis，TTL 5 分钟
        redisUtils.set(CAPTCHA_PREFIX + uuid, code, CAPTCHA_TTL_SECONDS, TimeUnit.SECONDS);

        // 获取 Base64 图片并添加前缀
        String imageBase64 = "data:image/png;base64," + captcha.getImageBase64();

        return new CaptchaVO(uuid, imageBase64);
    }

    /**
     * 校验图形验证码（新方法）
     * 大小写不敏感比对，无论成功失败都删除 key（一次性使用）
     *
     * @param uuid      验证码唯一标识
     * @param inputCode 用户输入的验证码
     * @return 校验是否通过
     */
    public boolean verifyImageCaptcha(String uuid, String inputCode) {
        if (uuid == null || inputCode == null) {
            return false;
        }
        String redisKey = CAPTCHA_PREFIX + uuid;
        Object cached = redisUtils.get(redisKey);
        // 无论校验成功失败，都删除 key（一次性使用）
        redisUtils.delete(redisKey);

        if (cached == null) {
            return false;
        }
        return cached.toString().equalsIgnoreCase(inputCode);
    }

    // ============ 短信验证码方法 ============

    /**
     * 发送短信验证码
     * <p>
     * 1. 校验手机号格式
     * 2. 检查 60 秒频率限制
     * 3. 检查每日 10 次限额
     * 4. 生成 6 位数字验证码
     * 5. 存入 Redis 并设置频率限制
     * 6. 调用短信服务发送
     *
     * @param phone 手机号
     */
    public void sendSmsCode(String phone) {
        // 1. 手机号格式校验
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException("手机号格式无效");
        }

        // 2. 频率限制检查（60秒内仅1次）
        String freqKey = SMS_FREQ_PREFIX + phone;
        if (Boolean.TRUE.equals(redisUtils.hasKey(freqKey))) {
            Long ttl = redisUtils.getExpire(freqKey, TimeUnit.SECONDS);
            throw new BusinessException("发送过于频繁，请" + ttl + "秒后重试");
        }

        // 3. 日限额检查（每日不超过10次）
        String dailyKey = SMS_DAILY_PREFIX + phone;
        Object dailyCount = redisUtils.get(dailyKey);
        if (dailyCount != null) {
            int count = Integer.parseInt(dailyCount.toString());
            if (count >= SMS_DAILY_LIMIT) {
                throw new BusinessException("今日短信发送次数已达上限(10次)");
            }
        }

        // 4. 生成6位数字验证码
        String code = String.format("%06d", new Random().nextInt(1000000));

        // 5. 存入 Redis: key=sms:{phone}, value=code, TTL=300s
        String smsKey = SMS_KEY_PREFIX + phone;
        redisUtils.set(smsKey, code, SMS_TTL_SECONDS, TimeUnit.SECONDS);

        // 6. 设置频率限制: key=sms:freq:{phone}, value="1", TTL=60s
        redisUtils.set(freqKey, "1", SMS_FREQ_TTL_SECONDS, TimeUnit.SECONDS);

        // 7. 日计数递增 + 首次设置到当天结束的 EXPIRE
        Long currentCount = redisUtils.increment(dailyKey);
        if (currentCount != null && currentCount == 1L) {
            // 首次发送，设置到当天结束的过期时间
            long secondsUntilEndOfDay = getSecondsUntilEndOfDay();
            redisUtils.expire(dailyKey, secondsUntilEndOfDay, TimeUnit.SECONDS);
        }

        // 8. 调用短信发送服务
        smsService.sendVerificationCode(phone, code);
    }

    /**
     * 校验短信验证码
     * <p>
     * 从 Redis 获取验证码，删除 key（一次性使用），精确匹配比对
     *
     * @param phone     手机号
     * @param inputCode 用户输入的验证码
     * @return 校验是否通过
     */
    public boolean verifySmsCode(String phone, String inputCode) {
        if (phone == null || inputCode == null) {
            return false;
        }
        String smsKey = SMS_KEY_PREFIX + phone;
        Object cached = redisUtils.get(smsKey);
        // 删除 key（一次性使用）
        redisUtils.delete(smsKey);

        if (cached == null) {
            return false;
        }
        // 精确匹配（数字验证码不忽略大小写）
        return cached.toString().equals(inputCode);
    }

    // ============ IP 锁定机制 ============

    private static final String IP_FAIL_PREFIX = "login:ip:fail:";
    private static final String IP_LOCK_PREFIX = "login:ip:lock:";
    private static final int IP_FAIL_WINDOW_SECONDS = 300;   // 5 分钟窗口
    private static final int IP_FAIL_MAX_ATTEMPTS = 5;       // 最多 5 次
    private static final int IP_LOCK_DURATION_SECONDS = 900; // 锁定 15 分钟

    /**
     * 检查 IP 是否被锁定
     *
     * @param clientIp 客户端 IP
     * @throws BusinessException 如果 IP 被锁定
     */
    public void checkIpLock(String clientIp) {
        if (clientIp == null) {
            return;
        }
        String lockKey = IP_LOCK_PREFIX + clientIp;
        if (Boolean.TRUE.equals(redisUtils.hasKey(lockKey))) {
            Long ttl = redisUtils.getExpire(lockKey, TimeUnit.SECONDS);
            throw new BusinessException("登录失败次数过多，请" + (ttl != null ? ttl / 60 + 1 : 15) + "分钟后重试");
        }
    }

    /**
     * 记录 IP 登录失败
     * 5 分钟窗口内连续失败 5 次后锁定 15 分钟
     *
     * @param clientIp 客户端 IP
     */
    public void recordIpFailure(String clientIp) {
        if (clientIp == null) {
            return;
        }
        String failKey = IP_FAIL_PREFIX + clientIp;
        Long count = redisUtils.increment(failKey);
        if (count != null && count == 1L) {
            redisUtils.expire(failKey, IP_FAIL_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        if (count != null && count >= IP_FAIL_MAX_ATTEMPTS) {
            // 达到最大失败次数，设置锁定
            String lockKey = IP_LOCK_PREFIX + clientIp;
            redisUtils.set(lockKey, "1", IP_LOCK_DURATION_SECONDS, TimeUnit.SECONDS);
            // 清除失败计数
            redisUtils.delete(failKey);
        }
    }

    /**
     * 清除 IP 登录失败记录（登录成功时调用）
     *
     * @param clientIp 客户端 IP
     */
    public void clearIpFailure(String clientIp) {
        if (clientIp == null) {
            return;
        }
        redisUtils.delete(IP_FAIL_PREFIX + clientIp);
    }

    // ============ 工具方法 ============

    public boolean isCaptchaEnabled() {
        return captchaEnabled;
    }

    /**
     * 计算当天剩余秒数，用于设置日计数器 TTL
     */
    private long getSecondsUntilEndOfDay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        return ChronoUnit.SECONDS.between(now, endOfDay);
    }
}
