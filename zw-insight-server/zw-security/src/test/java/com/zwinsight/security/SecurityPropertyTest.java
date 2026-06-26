package com.zwinsight.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.exception.SecondaryConfirmException;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.security.annotation.SecondaryConfirm;
import com.zwinsight.security.aspect.SecondaryConfirmAspect;
import com.zwinsight.security.domain.SysLoginDevice;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.dto.DeviceInfo;
import com.zwinsight.security.mapper.SysLoginDeviceMapper;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.security.service.CaptchaService;
import com.zwinsight.security.service.DeviceManagerService;
import com.zwinsight.security.service.PasswordResetService;
import com.zwinsight.security.service.SmsService;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SecurityPropertyTest — 用户安全后端属性测试（jqwik）。
 *
 * <p>覆盖 p2-advanced 设计文档中的安全相关 correctness properties（Property 13-18、20-22），
 * 使用 Mockito + 内存 Redis / Mapper 模拟基础设施，被测对象为真实服务实现
 * （{@link CaptchaService} / {@link PasswordResetService} / {@link DeviceManagerService} /
 * {@link SecondaryConfirmAspect}），不使用 fallback 或伪造业务结果。</p>
 *
 * <p><b>覆盖映射：</b></p>
 * <ul>
 *   <li>Property 13: 短信验证码校验往返（CaptchaService 真实存取，往返一致）</li>
 *   <li>Property 14: 密码复杂度校验（经 {@code PasswordResetService.resetPassword} 公开流程拒绝弱密码）</li>
 *   <li>Property 15: 密码重置后 N 个 Token 全部进入黑名单且会话被删除</li>
 *   <li>Property 16: 验证码连续 5 次失败后第 6 次返回锁定（429）</li>
 *   <li>Property 17: 登录记录设备；注销后 Token 进黑名单且会话删除</li>
 *   <li>Property 18: 活跃设备数超过上限自动淘汰最早登录设备</li>
 *   <li>Property 20: 二次确认缺失/空白 X-Confirm-Password → 449</li>
 *   <li>Property 21: 二次确认密码正确 → 放行并重置失败计数</li>
 *   <li>Property 22: 二次确认连续 5 次错误 → 锁定（423）</li>
 * </ul>
 *
 * <p><b>实现说明：</b>测试用户密码使用 BCrypt 强度 4 编码以加速 {@code matches()}，
 * 这仍是真实的 BCrypt 校验（强度由密文自身携带），仅用于降低测试耗时。</p>
 */
class SecurityPropertyTest {

    /** 测试用低成本 BCrypt 编码器（强度 4），生成的密文 matches() 速度快，但仍为真实 BCrypt。 */
    private static final BCryptPasswordEncoder TEST_ENCODER = new BCryptPasswordEncoder(4);

    /** 与服务实现一致的密码复杂度正则：8-20 字符且至少含一个字母与一个数字。 */
    private static final Pattern VALID_PASSWORD =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,20}$");

    private static final String TOKEN_PREFIX = "token:";
    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    /**
     * 初始化 MyBatis-Plus 实体元数据，使内存 mapper 在解析 LambdaQueryWrapper 的
     * 列名 / 参数时（无 Spring 容器）也能正常渲染 SQL 段。
     */
    @BeforeContainer
    static void initMybatisPlusTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SysLoginDevice.class);
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    // ==================== Property 13: 短信验证码校验往返 ====================

    /**
     * Property 13: 对任意合法手机号，发送验证码后立即用相同验证码校验应成功（peek 与 verify 均通过）。
     *
     * <p>使用真实 {@link CaptchaService} + 内存 Redis：发送时随机生成 6 位码并写入 Redis，
     * 测试从 Redis 读取该码再回放校验，验证"发送-校验"往返一致性。</p>
     *
     * <p><b>Validates: Requirements 7.2</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-advanced, Property 13: 短信验证码校验往返")
    void property13_smsCodeSendVerifyRoundTrip(@ForAll("validPhone") String phone) {
        InMemoryRedis redis = new InMemoryRedis();
        SmsService smsService = Mockito.mock(SmsService.class);
        CaptchaService captchaService = new CaptchaService(redis, smsService);

        // 发送：服务内部随机生成验证码并存入 Redis(sms:{phone})
        captchaService.sendSmsCode(phone);
        Object stored = redis.get("sms:" + phone);
        assertThat(stored).as("发送后 Redis 应存有验证码").isNotNull();
        String code = stored.toString();

        // peek（非消费式）应成功且不删除验证码
        assertThat(captchaService.peekSmsCode(phone, code))
                .as("peekSmsCode 应对相同验证码返回 true").isTrue();
        // verify（消费式）应成功
        assertThat(captchaService.verifySmsCode(phone, code))
                .as("verifySmsCode 应对相同验证码返回 true").isTrue();
        // 消费后再次校验应失败（一次性）
        assertThat(captchaService.verifySmsCode(phone, code))
                .as("验证码消费后再次校验应失败").isFalse();
    }

    // ==================== Property 14: 密码复杂度校验 ====================

    /**
     * Property 14: 任意不满足"8-20 字符且同时含字母与数字"的密码，经密码重置流程应被复杂度校验拒绝。
     *
     * <p>通过公开方法 {@link PasswordResetService#resetPassword} 验证（已注册手机号、未锁定），
     * 复杂度校验先于验证码消费执行，弱密码应抛出含"密码复杂度"的 {@link BusinessException}。</p>
     *
     * <p><b>Validates: Requirements 7.9</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-advanced, Property 14: 密码复杂度校验")
    void property14_weakPasswordRejected(@ForAll("invalidPassword") String weakPassword) {
        String phone = "13800138000";
        String code = "123456";

        InMemoryRedis redis = new InMemoryRedis();
        SysUserMapper userMapper = Mockito.mock(SysUserMapper.class);
        CaptchaService captchaService = Mockito.mock(CaptchaService.class);
        when(userMapper.selectOne(any())).thenReturn(newUser(1L, "$2a$placeholder"));

        PasswordResetService service = new PasswordResetService(userMapper, captchaService, redis);

        assertThatThrownBy(() -> service.resetPassword(phone, code, weakPassword))
                .as("弱密码 [%s] 应被复杂度校验拒绝", weakPassword)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密码复杂度");
    }

    // ==================== Property 15: 密码重置后 Token 全部失效 ====================

    /**
     * Property 15: 对持有 N 个有效会话 Token 的用户，密码重置成功后，N 个 Token 全部进入黑名单，
     * 且其会话 key 被删除；属于其他用户的会话不受影响。
     *
     * <p><b>Validates: Requirements 7.7</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: p2-advanced, Property 15: 密码重置后 Token 全部失效")
    void property15_allTokensBlacklistedAfterReset(@ForAll("uniqueTokens") List<String> tokens) {
        String phone = "13800138000";
        String code = "123456";
        long userId = 42L;
        long otherUserId = 99L;

        InMemoryRedis redis = new InMemoryRedis();
        // 该用户 N 个活跃会话
        for (String t : tokens) {
            redis.set(TOKEN_PREFIX + t, String.valueOf(userId), 3600, TimeUnit.SECONDS);
        }
        // 一个其他用户会话（不应被失效）
        String otherToken = "other-user-token";
        redis.set(TOKEN_PREFIX + otherToken, String.valueOf(otherUserId), 3600, TimeUnit.SECONDS);

        SysUserMapper userMapper = Mockito.mock(SysUserMapper.class);
        CaptchaService captchaService = Mockito.mock(CaptchaService.class);
        when(userMapper.selectOne(any())).thenReturn(newUser(userId, TEST_ENCODER.encode("oldPass123")));
        when(captchaService.verifySmsCode(phone, code)).thenReturn(true);

        PasswordResetService service = new PasswordResetService(userMapper, captchaService, redis);
        service.resetPassword(phone, code, "NewPass2024");

        for (String t : tokens) {
            assertThat(redis.hasKey(TOKEN_BLACKLIST_PREFIX + sha256(t)))
                    .as("Token [%s] 应进入黑名单", t).isTrue();
            assertThat(redis.hasKey(TOKEN_PREFIX + t))
                    .as("Token [%s] 的会话 key 应被删除", t).isFalse();
        }
        // 其他用户会话保持有效，且未被加入黑名单
        assertThat(redis.hasKey(TOKEN_PREFIX + otherToken))
                .as("其他用户会话不应被删除").isTrue();
        assertThat(redis.hasKey(TOKEN_BLACKLIST_PREFIX + sha256(otherToken)))
                .as("其他用户 Token 不应进入黑名单").isFalse();
    }

    // ==================== Property 16: 验证码连续失败锁定 ====================

    /**
     * Property 16: 对任意合法手机号，连续 5 次验证码校验失败后，第 6 次校验无论验证码是否正确，
     * 均应返回锁定错误（HTTP 429）。
     *
     * <p><b>Validates: Requirements 7.8</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: p2-advanced, Property 16: 验证码连续失败锁定")
    void property16_lockoutAfter5Failures(@ForAll("validPhone") String phone) {
        InMemoryRedis redis = new InMemoryRedis();
        SysUserMapper userMapper = Mockito.mock(SysUserMapper.class);
        CaptchaService captchaService = Mockito.mock(CaptchaService.class);
        // 前 5 次：验证码错误（peek 返回 false 触发失败计数）
        when(captchaService.peekSmsCode(any(), any())).thenReturn(false);

        PasswordResetService service = new PasswordResetService(userMapper, captchaService, redis);

        // 连续 5 次失败：每次抛出"验证码无效或已过期"(400)
        for (int i = 1; i <= 5; i++) {
            int attempt = i;
            assertThatThrownBy(() -> service.verifyCode(phone, "000000"))
                    .as("第 %d 次失败应抛出验证码无效", attempt)
                    .isInstanceOf(BusinessException.class);
        }

        // 第 6 次：即便验证码"正确"，也应因锁定返回 429
        when(captchaService.peekSmsCode(any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.verifyCode(phone, "123456"))
                .as("第 6 次应因锁定返回 429")
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(429));
    }

    // ==================== Property 17: 设备登录记录与注销 ====================

    /**
     * Property 17: 任意带设备信息的登录都会持久化一条设备记录；远程注销该设备后，
     * 其 Token 进入黑名单且会话 key 被删除。
     *
     * <p><b>Validates: Requirements 8.1, 8.3, 8.6</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: p2-advanced, Property 17: 设备登录记录与注销")
    void property17_recordLoginAndRevoke(
            @ForAll("deviceToken") String token,
            @ForAll("nonBlank") String deviceName) {
        long userId = 1001L;
        InMemoryRedis redis = new InMemoryRedis();
        DeviceFixture f = newDeviceFixture(redis);
        DeviceManagerService service = f.service;
        // 上限设高，避免本属性触发自动淘汰
        ReflectionTestUtils.setField(service, "maxDevices", 100);

        DeviceInfo device = new DeviceInfo();
        device.setDeviceId("dev-" + token);
        device.setDeviceName(deviceName);
        device.setOs("iOS");

        // 登录前先写入会话 key（模拟 AuthService 登录时写入），TTL=3600s
        redis.set(TOKEN_PREFIX + token, String.valueOf(userId), 3600, TimeUnit.SECONDS);

        service.recordLogin(userId, device, token, "1.2.3.4", "浙江省|杭州市");

        // 8.1 设备记录已持久化（活跃）
        assertThat(f.store).hasSize(1);
        SysLoginDevice record = f.store.get(0);
        assertThat(record.getUserId()).isEqualTo(userId);
        assertThat(record.getToken()).isEqualTo(token);
        assertThat(record.getStatus()).as("登录设备应为活跃状态").isEqualTo(1);

        // 8.3 远程注销该设备（当前 Token 用一个不同值，避免"禁止注销当前设备"限制）
        service.revokeDevice(userId, record.getId(), "another-current-token");

        // 设备状态变为已注销
        assertThat(record.getStatus()).as("注销后设备应为已注销状态").isEqualTo(0);
        // 8.6 Token 进入黑名单
        assertThat(redis.hasKey(TOKEN_BLACKLIST_PREFIX + sha256(token)))
                .as("注销后 Token 应进入黑名单").isTrue();
        // 会话 key 被删除（强制登出）
        assertThat(redis.hasKey(TOKEN_PREFIX + token))
                .as("注销后会话 key 应被删除").isFalse();
    }

    // ==================== Property 18: 最大设备数自动淘汰 ====================

    /**
     * Property 18: 当用户活跃设备数超过上限（5）时，按登录时间升序淘汰最早登录的设备，
     * 直至活跃数等于上限；被淘汰设备的 Token 进入黑名单。
     *
     * <p><b>Validates: Requirements 8.5</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: p2-advanced, Property 18: 最大设备数自动淘汰")
    void property18_autoEvictOldest(@ForAll @IntRange(min = 6, max = 12) int deviceCount) {
        long userId = 2002L;
        int max = 5;
        InMemoryRedis redis = new InMemoryRedis();
        DeviceFixture f = newDeviceFixture(redis);
        DeviceManagerService service = f.service;

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        List<SysLoginDevice> inserted = new ArrayList<>();
        for (int i = 0; i < deviceCount; i++) {
            SysLoginDevice d = new SysLoginDevice();
            d.setUserId(userId);
            d.setToken("tok-" + i);
            d.setStatus(1);
            d.setLoginTime(base.plusMinutes(i)); // i 越小登录越早
            f.insert(d);
            inserted.add(d);
        }

        service.autoEvictOldest(userId, max);

        int expectedEvicted = deviceCount - max;
        // 最早登录的 expectedEvicted 台被注销，其余保持活跃
        for (int i = 0; i < deviceCount; i++) {
            SysLoginDevice d = inserted.get(i);
            if (i < expectedEvicted) {
                assertThat(d.getStatus()).as("最早登录的设备#%d 应被淘汰", i).isEqualTo(0);
                assertThat(redis.hasKey(TOKEN_BLACKLIST_PREFIX + sha256(d.getToken())))
                        .as("被淘汰设备#%d 的 Token 应进入黑名单", i).isTrue();
            } else {
                assertThat(d.getStatus()).as("较新的设备#%d 应保持活跃", i).isEqualTo(1);
            }
        }
        long activeCount = f.store.stream().filter(d -> d.getStatus() == 1).count();
        assertThat(activeCount).as("淘汰后活跃设备数应等于上限").isEqualTo(max);
    }

    // ==================== Property 20: 二次确认缺失密码返回 449 ====================

    /**
     * Property 20: 对标注 {@link SecondaryConfirm} 的方法，当 X-Confirm-Password 请求头
     * 缺失、为空或仅含空白时，应抛出状态码为 449 的 {@link SecondaryConfirmException}，且不执行目标方法。
     *
     * <p><b>Validates: Requirements 10.1, 10.2</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-advanced, Property 20: 二次确认缺失密码返回 449")
    void property20_missingConfirmPasswordReturns449(@ForAll("blankOrNullHeader") String header) throws Throwable {
        RedisUtils redis = new InMemoryRedis();
        SysUserMapper userMapper = Mockito.mock(SysUserMapper.class);
        SecondaryConfirmAspect aspect = new SecondaryConfirmAspect(redis, userMapper);

        ProceedingJoinPoint pjp = Mockito.mock(ProceedingJoinPoint.class);
        SecondaryConfirm annotation = Mockito.mock(SecondaryConfirm.class);
        when(annotation.message()).thenReturn("此操作需要二次确认");

        setRequestHeader(header);
        try {
            assertThatThrownBy(() -> aspect.around(pjp, annotation))
                    .as("空白/缺失确认密码应返回 449")
                    .isInstanceOf(SecondaryConfirmException.class)
                    .satisfies(ex -> assertThat(((SecondaryConfirmException) ex).getStatus()).isEqualTo(449));
            // 目标方法未执行
            Mockito.verify(pjp, Mockito.never()).proceed();
        } finally {
            clearContext();
        }
    }

    // ==================== Property 21: 二次确认密码正确放行并重置计数 ====================

    /**
     * Property 21: 携带正确 X-Confirm-Password 的请求，目标方法应被执行；且用户连续失败计数被清零。
     *
     * <p><b>Validates: Requirements 10.5, 10.7</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: p2-advanced, Property 21: 二次确认密码正确放行并重置计数")
    void property21_correctPasswordProceedsAndResetsCount(
            @ForAll @LongRange(min = 1, max = 1_000_000) long userId,
            @ForAll("password") String password) {
        InMemoryRedis redis = new InMemoryRedis();
        SysUserMapper userMapper = Mockito.mock(SysUserMapper.class);
        SysUser user = newUser(userId, TEST_ENCODER.encode(password));
        when(userMapper.selectById(userId)).thenReturn(user);

        // 预置历史失败计数，验证成功后应被清零
        String failKey = "secondary_confirm:fail:" + userId;
        redis.set(failKey, "3");

        SecondaryConfirmAspect aspect = new SecondaryConfirmAspect(redis, userMapper);
        Object sentinel = new Object();
        ProceedingJoinPoint pjp = Mockito.mock(ProceedingJoinPoint.class);
        SecondaryConfirm annotation = Mockito.mock(SecondaryConfirm.class);

        SecurityContextHolder.setUserId(userId);
        setRequestHeader(password);
        try {
            when(pjp.proceed()).thenReturn(sentinel);
            Object result = aspect.around(pjp, annotation);

            assertThat(result).as("正确确认密码应放行目标方法").isSameAs(sentinel);
            assertThat(redis.hasKey(failKey)).as("成功后失败计数应被清零").isFalse();
        } catch (Throwable t) {
            throw new AssertionError("正确密码不应抛出异常: " + t, t);
        } finally {
            clearContext();
        }
    }

    // ==================== Property 22: 二次确认连续失败锁定 ====================

    /**
     * Property 22: 连续 5 次错误确认密码后，账户被临时锁定（HTTP 423），且后续请求继续返回锁定。
     *
     * <p>前 4 次返回 403（确认密码错误），第 5 次返回 423（锁定），第 6 次因锁定仍返回 423。</p>
     *
     * <p><b>Validates: Requirements 10.6</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: p2-advanced, Property 22: 二次确认连续失败锁定")
    void property22_lockoutAfter5WrongConfirmations(
            @ForAll @IntRange(min = 1, max = 1_000_000) long userId,
            @ForAll("password") String password) {
        InMemoryRedis redis = new InMemoryRedis();
        SysUserMapper userMapper = Mockito.mock(SysUserMapper.class);
        SysUser user = newUser(userId, TEST_ENCODER.encode(password));
        when(userMapper.selectById(userId)).thenReturn(user);

        SecondaryConfirmAspect aspect = new SecondaryConfirmAspect(redis, userMapper);
        ProceedingJoinPoint pjp = Mockito.mock(ProceedingJoinPoint.class);
        SecondaryConfirm annotation = Mockito.mock(SecondaryConfirm.class);

        String wrongPassword = password + "_WRONG"; // 保证与正确密码不同
        SecurityContextHolder.setUserId(userId);
        setRequestHeader(wrongPassword);
        try {
            // 前 4 次：403 确认密码错误
            for (int i = 1; i <= 4; i++) {
                int attempt = i;
                assertThatThrownBy(() -> aspect.around(pjp, annotation))
                        .as("第 %d 次错误应返回 403", attempt)
                        .isInstanceOf(SecondaryConfirmException.class)
                        .satisfies(ex -> assertThat(((SecondaryConfirmException) ex).getStatus()).isEqualTo(403));
            }
            // 第 5 次：触发锁定 423
            assertThatThrownBy(() -> aspect.around(pjp, annotation))
                    .as("第 5 次错误应触发锁定 423")
                    .isInstanceOf(SecondaryConfirmException.class)
                    .satisfies(ex -> assertThat(((SecondaryConfirmException) ex).getStatus()).isEqualTo(423));

            // 锁定标记已写入
            assertThat(redis.hasKey("secondary_confirm:lock:" + userId))
                    .as("第 5 次失败后应写入锁定标记").isTrue();

            // 第 6 次：因锁定仍返回 423
            assertThatThrownBy(() -> aspect.around(pjp, annotation))
                    .as("锁定后再次请求应返回 423")
                    .isInstanceOf(SecondaryConfirmException.class)
                    .satisfies(ex -> assertThat(((SecondaryConfirmException) ex).getStatus()).isEqualTo(423));
        } finally {
            clearContext();
        }
    }

    // ==================== Arbitraries（数据提供器） ====================

    /** 合法手机号：^1[3-9]\d{9}$ */
    @Provide
    Arbitrary<String> validPhone() {
        Arbitrary<Integer> second = Arbitraries.integers().between(3, 9);
        Arbitrary<String> rest = Arbitraries.strings().withChars("0123456789").ofLength(9);
        return Combinators.combine(second, rest).as((s, r) -> "1" + s + r);
    }

    /**
     * 不满足复杂度要求的密码：太短 / 太长 / 纯字母 / 纯数字，
     * 并以与实现一致的正则做最终过滤，确保确实"无效"。
     */
    @Provide
    Arbitrary<String> invalidPassword() {
        Arbitrary<String> tooShort = Arbitraries.strings()
                .withChars("abcDEF123").ofMinLength(0).ofMaxLength(7);
        Arbitrary<String> tooLong = Arbitraries.strings()
                .withChars("abcDEF123").ofMinLength(21).ofMaxLength(30);
        Arbitrary<String> onlyLetters = Arbitraries.strings()
                .withChars("abcdefgHIJKLMN").ofMinLength(8).ofMaxLength(20);
        Arbitrary<String> onlyDigits = Arbitraries.strings()
                .withChars("0123456789").ofMinLength(8).ofMaxLength(20);
        return Arbitraries.oneOf(tooShort, tooLong, onlyLetters, onlyDigits)
                .filter(s -> !VALID_PASSWORD.matcher(s).matches());
    }

    /** 合法密码（仅用于 BCrypt 编码场景，无需满足复杂度规则，长度 1-30）。 */
    @Provide
    Arbitrary<String> password() {
        return Arbitraries.strings()
                .withChars("abcdefghijkLMNOPQR0123456789").ofMinLength(1).ofMaxLength(30);
    }

    /** N 个互不相同的 Token（N=1..10）。 */
    @Provide
    Arbitrary<List<String>> uniqueTokens() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(6).ofMaxLength(16)
                .list().uniqueElements().ofMinSize(1).ofMaxSize(10);
    }

    /** 设备 Token（非空字符串）。 */
    @Provide
    Arbitrary<String> deviceToken() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(6).ofMaxLength(20);
    }

    /** 非空白字符串（设备名等）。 */
    @Provide
    Arbitrary<String> nonBlank() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20);
    }

    /** 空白或缺失的请求头值：null 或仅含空白字符。 */
    @Provide
    Arbitrary<String> blankOrNullHeader() {
        Arbitrary<String> blanks = Arbitraries.of(" ", "  ", "\t", "\n", "", "   \t ", "\r\n");
        return Arbitraries.oneOf(Arbitraries.just(null), blanks);
    }

    // ==================== 辅助：请求上下文 ====================

    /**
     * 设置当前请求的 X-Confirm-Password 请求头；header 为 null 时不添加该头。
     */
    private static void setRequestHeader(String header) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader("X-Confirm-Password", header);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static void clearContext() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clear();
    }

    private static SysUser newUser(Long id, String encodedPassword) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setPhone("13800138000");
        user.setPassword(encodedPassword);
        user.setStatus(1);
        return user;
    }

    /** 计算 SHA-256 十六进制摘要（与服务实现一致），用于推导黑名单 key。 */
    private static String sha256(String input) {
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
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ==================== 内存设备 Mapper 夹具 ====================

    /** 登录设备测试夹具：携带真实 service 及其内存 store。 */
    private static class DeviceFixture {
        DeviceManagerService service;
        List<SysLoginDevice> store;
        AtomicLong idSeq;

        void insert(SysLoginDevice d) {
            d.setId(idSeq.getAndIncrement());
            store.add(d);
        }
    }

    /**
     * 构建一套内存 {@link SysLoginDeviceMapper} + 真实 {@link DeviceManagerService}。
     */
    private DeviceFixture newDeviceFixture(RedisUtils redis) {
        final List<SysLoginDevice> store = new ArrayList<>();
        final AtomicLong idSeq = new AtomicLong(1);

        SysLoginDeviceMapper mapper = Mockito.mock(SysLoginDeviceMapper.class);

        when(mapper.insert(any(SysLoginDevice.class))).thenAnswer(inv -> {
            SysLoginDevice e = inv.getArgument(0);
            e.setId(idSeq.getAndIncrement());
            store.add(e);
            return 1;
        });

        when(mapper.selectById(any())).thenAnswer(inv -> {
            Long id = ((Number) inv.getArgument(0)).longValue();
            return store.stream().filter(d -> id.equals(d.getId())).findFirst().orElse(null);
        });

        // updateById：store 持有的是同一对象引用，service 已就地修改，返回成功即可
        when(mapper.updateById(any(SysLoginDevice.class))).thenReturn(1);

        when(mapper.selectList(any())).thenAnswer(inv -> {
            LambdaQueryWrapper<?> w = (LambdaQueryWrapper<?>) inv.getArgument(0);
            List<Object> params = orderedParams(w);
            Long uid = ((Number) params.get(0)).longValue();
            Integer status = params.size() > 1 ? ((Number) params.get(1)).intValue() : null;
            boolean desc = w.getSqlSegment().toUpperCase().contains("DESC");

            List<SysLoginDevice> list = store.stream()
                    .filter(d -> uid.equals(d.getUserId()))
                    .filter(d -> status == null || status.equals(d.getStatus()))
                    .sorted(Comparator.comparing(SysLoginDevice::getLoginTime))
                    .collect(Collectors.toList());
            if (desc) {
                Collections.reverse(list);
            }
            return list;
        });

        DeviceFixture f = new DeviceFixture();
        f.store = store;
        f.idSeq = idSeq;
        f.service = new DeviceManagerService(mapper, redis);
        return f;
    }

    /**
     * 提取 LambdaQueryWrapper 的有序参数值（按 MyBatis-Plus 生成的 MPGENVALn 序号排序），
     * 顺序与 eq() 调用顺序一致（userId, status）。
     */
    private static List<Object> orderedParams(LambdaQueryWrapper<?> w) {
        w.getSqlSegment(); // 触发参数渲染
        Map<String, Object> pairs = w.getParamNameValuePairs();
        return pairs.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> paramIndex(e.getKey())))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    private static int paramIndex(String key) {
        Matcher m = Pattern.compile("(\\d+)$").matcher(key);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    // ==================== 内存 Redis 实现 ====================

    /**
     * 内存版 {@link RedisUtils}：覆盖被测服务用到的方法，使用 Map 模拟 Redis 行为。
     * 不连接真实 Redis（{@code super(null)}），仅用于属性测试隔离基础设施。
     */
    static class InMemoryRedis extends RedisUtils {
        private final Map<String, Object> store = new ConcurrentHashMap<>();
        private final Map<String, Long> ttlSeconds = new ConcurrentHashMap<>();

        InMemoryRedis() {
            super(null);
        }

        @Override
        public void set(String key, Object value) {
            store.put(key, value);
            ttlSeconds.remove(key);
        }

        @Override
        public void set(String key, Object value, long timeout) {
            store.put(key, value);
            ttlSeconds.put(key, timeout);
        }

        @Override
        public void set(String key, Object value, long timeout, TimeUnit unit) {
            store.put(key, value);
            ttlSeconds.put(key, unit.toSeconds(timeout));
        }

        @Override
        public Object get(String key) {
            return store.get(key);
        }

        @Override
        public Boolean delete(String key) {
            ttlSeconds.remove(key);
            return store.remove(key) != null;
        }

        @Override
        public Boolean hasKey(String key) {
            return store.containsKey(key);
        }

        @Override
        public Long increment(String key) {
            long current = store.containsKey(key) ? Long.parseLong(store.get(key).toString()) : 0L;
            long next = current + 1;
            store.put(key, String.valueOf(next));
            return next;
        }

        @Override
        public Boolean expire(String key, long timeout, TimeUnit unit) {
            if (store.containsKey(key)) {
                ttlSeconds.put(key, unit.toSeconds(timeout));
                return true;
            }
            return false;
        }

        @Override
        public Long getExpire(String key, TimeUnit unit) {
            if (!store.containsKey(key)) {
                return -2L;
            }
            Long secs = ttlSeconds.get(key);
            if (secs == null) {
                return -1L;
            }
            return unit.convert(secs, TimeUnit.SECONDS);
        }

        @Override
        public java.util.Set<String> keys(String pattern) {
            String prefix = pattern.endsWith("*") ? pattern.substring(0, pattern.length() - 1) : pattern;
            return store.keySet().stream()
                    .filter(k -> k.startsWith(prefix))
                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        }
    }
}
