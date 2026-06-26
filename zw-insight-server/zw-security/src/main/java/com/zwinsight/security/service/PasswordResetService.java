package com.zwinsight.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 密码重置服务（忘记密码）。
 * <p>
 * 三步流程：
 * <ol>
 *   <li>{@link #sendCode(String)} — 校验手机号已注册后，复用 {@link CaptchaService#sendSmsCode(String)}
 *       发送 6 位短信验证码。发送频率限制（60 秒/次、每日 10 次）由 {@code CaptchaService} 内置实现，
 *       底层通过 {@link AliyunSmsService} 真实发送。</li>
 *   <li>{@link #verifyCode(String, String)} — 非消费式校验验证码（保留验证码供重置步骤最终消费）。
 *       连续失败计数存于 Redis {@code pwd_reset:verify_fail:{phone}}，达到 5 次后写入
 *       {@code pwd_reset:lock:{phone}} 锁定 30 分钟。</li>
 *   <li>{@link #resetPassword(String, String, String)} — 复验验证码、校验密码复杂度、BCrypt 更新密码，
 *       并将该用户当前所有有效 Token 加入 Redis 黑名单使其失效。</li>
 * </ol>
 *
 * <h3>Token 失效策略</h3>
 * 登录时 {@link AuthService} 会将 Token 以 {@code token:{token} -> userId} 形式写入 Redis，
 * TTL = Token 剩余有效期，注销时删除该 key（{@code AuthService.logout}）。本服务复用同一机制：
 * 扫描 {@code token:*} 找出 value 等于目标 userId 的会话，删除其 {@code token:{token}} key
 * （等同强制登出，使 {@code AuthService.validateToken} 失效），并按设计写入黑名单
 * {@code token:blacklist:{token_hash}}（token_hash = SHA-256），TTL = 该 Token 的剩余有效期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final SysUserMapper userMapper;
    private final CaptchaService captchaService;
    private final RedisUtils redisUtils;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /** 登录 Token 在 Redis 中的前缀（与 {@link AuthService} 保持一致）。 */
    private static final String TOKEN_PREFIX = "token:";
    /** Token 黑名单前缀（设计文档约定）。 */
    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    /** 验证码连续失败计数 key。 */
    private static final String VERIFY_FAIL_PREFIX = "pwd_reset:verify_fail:";
    /** 验证码锁定标记 key。 */
    private static final String LOCK_PREFIX = "pwd_reset:lock:";

    /** 连续失败达到该次数后锁定。 */
    private static final int MAX_VERIFY_FAIL = 5;
    /** 锁定时长（分钟）。 */
    private static final int LOCK_MINUTES = 30;
    /** 失败计数器存活时长（分钟）。 */
    private static final int FAIL_COUNTER_MINUTES = 30;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    /** 密码复杂度：8-20 个字符，至少包含一个字母和一个数字。 */
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,20}$");

    // ============ 步骤一：发送验证码 ============

    /**
     * 发送密码重置短信验证码。
     *
     * @param phone 手机号
     * @throws BusinessException 手机号格式无效 / 手机号未注册 / 发送频率超限 / 当日次数超限
     */
    public void sendCode(String phone) {
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException(400, "手机号格式无效");
        }
        // 手机号未注册 → 错误
        if (findUserByPhone(phone) == null) {
            throw new BusinessException(400, "手机号未注册");
        }
        // 复用 CaptchaService：内部完成 60 秒频率限制、每日 10 次限额、验证码存储，
        // 并通过 AliyunSmsService 真实发送。频率/日限额超限会抛出 BusinessException。
        captchaService.sendSmsCode(phone);
        log.info("[PWD-RESET] 验证码已发送: phone={}", phone);
    }

    // ============ 步骤二：校验验证码 ============

    /**
     * 校验验证码（非消费式）。用于忘记密码流程的中间校验步骤。
     *
     * @param phone 手机号
     * @param code  用户输入的验证码
     * @throws BusinessException 已锁定 / 验证码无效或已过期
     */
    public void verifyCode(String phone, String code) {
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException(400, "手机号格式无效");
        }
        checkLock(phone);

        // 非消费式校验：保留验证码供重置步骤最终消费
        if (!captchaService.peekSmsCode(phone, code)) {
            recordVerifyFailure(phone);
            throw new BusinessException(400, "验证码无效或已过期");
        }
        // 校验成功，清除失败计数
        redisUtils.delete(VERIFY_FAIL_PREFIX + phone);
    }

    // ============ 步骤三：重置密码 ============

    /**
     * 重置密码。
     *
     * @param phone       手机号
     * @param code        短信验证码
     * @param newPassword 新密码（8-20 字符，含字母和数字）
     * @throws BusinessException 手机号未注册 / 已锁定 / 密码复杂度不符 / 验证码无效或已过期
     */
    public void resetPassword(String phone, String code, String newPassword) {
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException(400, "手机号格式无效");
        }
        SysUser user = findUserByPhone(phone);
        if (user == null) {
            throw new BusinessException(400, "手机号未注册");
        }

        // 锁定检查
        checkLock(phone);

        // 密码复杂度校验（先于消费验证码，校验不过不浪费验证码）
        if (!isPasswordValid(newPassword)) {
            throw new BusinessException(400, "密码复杂度不符合要求：需8-20个字符且同时包含字母和数字");
        }

        // 复验并消费验证码（一次性使用）
        if (!captchaService.verifySmsCode(phone, code)) {
            recordVerifyFailure(phone);
            throw new BusinessException(400, "验证码无效或已过期");
        }
        // 验证码有效，清除失败计数
        redisUtils.delete(VERIFY_FAIL_PREFIX + phone);

        // BCrypt 加密并更新密码
        user.setPassword(ENCODER.encode(newPassword));
        userMapper.updateById(user);

        // 使该用户所有 Token 失效（加入黑名单 + 删除会话）
        int invalidated = invalidateAllUserTokens(user.getId());
        log.info("[PWD-RESET] 密码重置成功: userId={}, 失效Token数={}", user.getId(), invalidated);
    }

    // ============ 私有辅助方法 ============

    private SysUser findUserByPhone(String phone) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getPhone, phone)
                        .eq(SysUser::getDeleted, 0)
        );
    }

    /**
     * 校验密码复杂度：8-20 字符，至少含字母和数字。
     */
    boolean isPasswordValid(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }

    /**
     * 检查手机号是否处于验证码锁定状态。
     */
    private void checkLock(String phone) {
        if (Boolean.TRUE.equals(redisUtils.hasKey(LOCK_PREFIX + phone))) {
            Long ttl = redisUtils.getExpire(LOCK_PREFIX + phone, TimeUnit.SECONDS);
            long minutes = (ttl != null && ttl > 0) ? (ttl / 60 + 1) : LOCK_MINUTES;
            throw new BusinessException(429, "校验次数超限，请" + minutes + "分钟后重试");
        }
    }

    /**
     * 记录一次验证码校验失败；累计达到上限时锁定 30 分钟。
     */
    private void recordVerifyFailure(String phone) {
        String failKey = VERIFY_FAIL_PREFIX + phone;
        Long count = redisUtils.increment(failKey);
        if (count != null && count == 1L) {
            redisUtils.expire(failKey, FAIL_COUNTER_MINUTES, TimeUnit.MINUTES);
        }
        if (count != null && count >= MAX_VERIFY_FAIL) {
            redisUtils.set(LOCK_PREFIX + phone, "1", LOCK_MINUTES, TimeUnit.MINUTES);
            redisUtils.delete(failKey);
        }
    }

    /**
     * 使指定用户当前所有有效 Token 失效。
     * <p>
     * 扫描 {@code token:*}，对 value 等于该 userId 的会话：写入黑名单
     * {@code token:blacklist:{sha256}}（TTL = 剩余有效期），并删除原 {@code token:{token}} key。
     *
     * @return 失效的 Token 数量
     */
    private int invalidateAllUserTokens(Long userId) {
        Set<String> tokenKeys = redisUtils.keys(TOKEN_PREFIX + "*");
        String userIdStr = userId.toString();
        int count = 0;
        for (String key : tokenKeys) {
            // 排除黑名单自身（其 key 同样以 token: 开头）
            if (key.startsWith(TOKEN_BLACKLIST_PREFIX)) {
                continue;
            }
            Object value = redisUtils.get(key);
            if (value == null || !userIdStr.equals(value.toString())) {
                continue;
            }
            String token = key.substring(TOKEN_PREFIX.length());
            Long ttlSeconds = redisUtils.getExpire(key, TimeUnit.SECONDS);
            long blacklistTtl = (ttlSeconds != null && ttlSeconds > 0) ? ttlSeconds : LOCK_MINUTES * 60L;
            redisUtils.set(TOKEN_BLACKLIST_PREFIX + sha256(token), "1", blacklistTtl, TimeUnit.SECONDS);
            // 删除会话 key，等同强制登出（AuthService.validateToken 将失败）
            redisUtils.delete(key);
            count++;
        }
        return count;
    }

    /**
     * 计算 Token 的 SHA-256 十六进制摘要，用作黑名单 key，避免超长 key。
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 标准算法，理论不会发生
            throw new BusinessException(500, "Token 摘要计算失败", e);
        }
    }
}
