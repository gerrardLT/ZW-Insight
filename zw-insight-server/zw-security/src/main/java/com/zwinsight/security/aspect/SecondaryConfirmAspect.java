package com.zwinsight.security.aspect;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.SecondaryConfirmException;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.security.annotation.SecondaryConfirm;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * 操作二次确认 AOP 切面。
 * <p>
 * 拦截标注了 {@link SecondaryConfirm} 注解的方法，对高风险操作执行登录密码二次确认：
 * </p>
 * <ol>
 *   <li>读取请求头 {@code X-Confirm-Password}；缺失/为空/纯空白 → 抛出 449（需要二次确认），不执行目标方法。</li>
 *   <li>检查锁定标记 {@code secondary_confirm:lock:{userId}}；若存在 → 抛出 423（账户临时锁定）。</li>
 *   <li>使用 {@link BCryptPasswordEncoder} 校验确认密码与当前登录用户密码：
 *     <ul>
 *       <li>错误 → 失败计数 {@code secondary_confirm:fail:{userId}}（窗口 15 分钟）+1，抛出 403；
 *           15 分钟内连续达到 5 次 → 设置锁定标记（15 分钟）、清空失败计数，抛出 423。</li>
 *       <li>正确 → 清除失败计数并放行目标方法。</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * @see SecondaryConfirm
 * @see SecondaryConfirmException
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class SecondaryConfirmAspect {

    private final RedisUtils redisUtils;
    private final SysUserMapper sysUserMapper;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /** 确认密码请求头 */
    private static final String CONFIRM_HEADER = "X-Confirm-Password";
    /** 失败计数 Redis key 前缀 */
    private static final String FAIL_KEY_PREFIX = "secondary_confirm:fail:";
    /** 锁定标记 Redis key 前缀 */
    private static final String LOCK_KEY_PREFIX = "secondary_confirm:lock:";
    /** 连续失败上限 */
    private static final int MAX_FAIL_COUNT = 5;
    /** 失败计数窗口（分钟） */
    private static final long WINDOW_MINUTES = 15;
    /** 锁定时长（分钟） */
    private static final long LOCK_MINUTES = 15;

    /** HTTP 449：需要二次确认 */
    private static final int STATUS_CONFIRM_REQUIRED = 449;
    /** HTTP 403：确认密码错误 */
    private static final int STATUS_PASSWORD_ERROR = 403;
    /** HTTP 423：临时锁定 */
    private static final int STATUS_LOCKED = 423;

    @Around("@annotation(secondaryConfirm)")
    public Object around(ProceedingJoinPoint pjp, SecondaryConfirm secondaryConfirm) throws Throwable {
        // 1. 读取确认密码请求头（缺失/空/纯空白 → 449，视为未携带）
        HttpServletRequest request = currentRequest();
        String confirmPassword = request != null ? request.getHeader(CONFIRM_HEADER) : null;
        if (confirmPassword == null || confirmPassword.isBlank()) {
            throw new SecondaryConfirmException(STATUS_CONFIRM_REQUIRED, secondaryConfirm.message());
        }

        // 2. 获取当前登录用户
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new SecondaryConfirmException(STATUS_PASSWORD_ERROR, "无法获取当前登录用户，二次确认失败");
        }

        String lockKey = LOCK_KEY_PREFIX + userId;
        String failKey = FAIL_KEY_PREFIX + userId;

        // 3. 锁定检查
        if (Boolean.TRUE.equals(redisUtils.hasKey(lockKey))) {
            throw new SecondaryConfirmException(STATUS_LOCKED, "账户临时锁定，请" + LOCK_MINUTES + "分钟后重试");
        }

        // 4. 加载用户密码
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getPassword() == null || user.getPassword().isBlank()) {
            throw new SecondaryConfirmException(STATUS_PASSWORD_ERROR, "用户不存在或未设置密码，二次确认失败");
        }

        // 5. BCrypt 校验
        if (!ENCODER.matches(confirmPassword, user.getPassword())) {
            long failCount = recordFailure(failKey);
            if (failCount >= MAX_FAIL_COUNT) {
                // 达到上限：锁定 15 分钟并清空失败计数
                redisUtils.set(lockKey, "1", LOCK_MINUTES, TimeUnit.MINUTES);
                redisUtils.delete(failKey);
                log.warn("用户 {} 二次确认连续失败 {} 次，已临时锁定 {} 分钟", userId, failCount, LOCK_MINUTES);
                throw new SecondaryConfirmException(STATUS_LOCKED,
                        "确认密码连续错误次数过多，账户临时锁定，请" + LOCK_MINUTES + "分钟后重试");
            }
            throw new SecondaryConfirmException(STATUS_PASSWORD_ERROR, "确认密码错误");
        }

        // 6. 校验通过：重置失败计数并放行
        redisUtils.delete(failKey);
        return pjp.proceed();
    }

    /**
     * 累计失败次数，首次失败时设置 15 分钟过期窗口。
     *
     * @return 当前累计失败次数
     */
    private long recordFailure(String failKey) {
        Long count = redisUtils.increment(failKey);
        if (count != null && count == 1L) {
            redisUtils.expire(failKey, WINDOW_MINUTES, TimeUnit.MINUTES);
        }
        return count == null ? 0L : count;
    }

    /**
     * 获取当前请求的 {@link HttpServletRequest}。
     *
     * @return 当前请求；无 Web 请求上下文时返回 {@code null}
     */
    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
