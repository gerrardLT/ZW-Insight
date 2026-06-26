package com.zwinsight.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.security.domain.SysTenant;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.dto.CaptchaVO;
import com.zwinsight.security.dto.DeviceInfo;
import com.zwinsight.security.dto.LoginRequest;
import com.zwinsight.security.dto.LoginResponse;
import com.zwinsight.security.mapper.SysTenantMapper;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.security.util.DeviceInfoResolver;
import com.zwinsight.security.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final SysUserMapper userMapper;
    private final SysTenantMapper tenantMapper;
    private final JwtUtils jwtUtils;
    private final CaptchaService captchaService;
    private final RedisUtils redisUtils;
    private final DeviceManagerService deviceManagerService;
    private final LoginLocationService loginLocationService;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String TOKEN_PREFIX = "token:";
    private static final String LOGIN_FAIL_PREFIX = "login_fail:";

    @Value("${auth.lock-enabled:true}")
    private boolean lockEnabled;
    @Value("${auth.lock-max-attempts:5}")
    private int maxAttempts;
    @Value("${auth.lock-duration:1800}")
    private int lockDuration;

    /**
     * 统一登录方法，支持密码登录和短信验证码登录
     *
     * @param request    登录请求
     * @param clientIp   客户端 IP 地址
     * @param deviceInfo 登录设备信息（用于设备记录 / 异地检测），可为空
     * @return 登录响应（包含 Token 和用户信息）
     */
    public LoginResponse login(LoginRequest request, String clientIp, DeviceInfo deviceInfo) {
        String loginType = request.getEffectiveLoginType();

        if ("SMS".equals(loginType)) {
            return loginBySms(request, clientIp, deviceInfo);
        } else {
            return loginByPassword(request, clientIp, deviceInfo);
        }
    }

    /**
     * 兼容仅带 clientIp 的调用（无显式设备信息）。
     */
    public LoginResponse login(LoginRequest request, String clientIp) {
        return login(request, clientIp, null);
    }

    /**
     * 兼容旧版无 clientIp 参数的调用
     */
    public LoginResponse login(LoginRequest request) {
        return login(request, null, null);
    }

    /**
     * 短信验证码登录
     */
    private LoginResponse loginBySms(LoginRequest request, String clientIp, DeviceInfo deviceInfo) {
        String phone = request.getPhone();
        String smsCode = request.getSmsCode();

        // 1. 参数校验
        if (phone == null || phone.isBlank()) {
            throw new BusinessException("手机号不能为空");
        }
        if (smsCode == null || smsCode.isBlank()) {
            throw new BusinessException("短信验证码不能为空");
        }

        // 2. 校验短信验证码
        if (!captchaService.verifySmsCode(phone, smsCode)) {
            throw new BusinessException("短信验证码错误或已过期");
        }

        // 3. 按手机号查找用户
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getPhone, phone)
                        .eq(SysUser::getDeleted, 0)
        );
        if (user == null) {
            throw new BusinessException("该手机号未注册");
        }

        // 4. 检查状态
        if (user.getStatus() != 1) {
            throw new BusinessException("账号已被停用");
        }

        // 5. 检查租户有效期
        Long tenantId = user.getTenantId();
        SysTenant tenant = checkTenantExpiry(tenantId);

        // 6. 生成 Token 和响应（含设备记录 + 异地检测）
        return buildLoginResponse(user, tenant, clientIp, deviceInfo);
    }

    /**
     * 密码登录（含图形验证码校验和 IP 锁定）
     */
    private LoginResponse loginByPassword(LoginRequest request, String clientIp, DeviceInfo deviceInfo) {
        // 1. 检查 IP 锁定
        captchaService.checkIpLock(clientIp);

        // 2. 图形验证码校验
        if (captchaService.isCaptchaEnabled()) {
            boolean captchaValid = false;

            // 优先使用新版验证码（captchaUuid + captchaCode）
            if (request.getCaptchaUuid() != null && !request.getCaptchaUuid().isBlank()) {
                captchaValid = captchaService.verifyImageCaptcha(
                        request.getCaptchaUuid(), request.getCaptchaCode());
            }
            // 回退到旧版验证码（captchaKey + captcha）
            else if (request.getCaptchaKey() != null && !request.getCaptchaKey().isBlank()) {
                captchaValid = captchaService.validateCaptcha(
                        request.getCaptchaKey(), request.getCaptcha());
            } else {
                // 未提供任何验证码
                throw new CaptchaVerifyException("请输入验证码", captchaService.generateImageCaptcha());
            }

            if (!captchaValid) {
                // R4.3: 验证码校验失败时，在错误响应中返回新验证码
                captchaService.recordIpFailure(clientIp);
                throw new CaptchaVerifyException("验证码错误或已过期", captchaService.generateImageCaptcha());
            }
        }

        // 3. 参数校验
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new BusinessException("用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessException("密码不能为空");
        }

        // 4. 检查账号锁定
        String failKey = LOGIN_FAIL_PREFIX + request.getUsername();
        if (lockEnabled) {
            Object failCount = redisUtils.get(failKey);
            if (failCount != null && Integer.parseInt(failCount.toString()) >= maxAttempts) {
                throw new BusinessException("账号已被锁定，请" + (lockDuration / 60) + "分钟后再试");
            }
        }

        // 5. 查找用户
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
                        .eq(SysUser::getDeleted, 0)
        );
        if (user == null) {
            incrementFailCount(failKey);
            captchaService.recordIpFailure(clientIp);
            throw new BusinessException("用户名或密码错误");
        }

        // 6. 验证密码
        if (!ENCODER.matches(request.getPassword(), user.getPassword())) {
            incrementFailCount(failKey);
            captchaService.recordIpFailure(clientIp);
            throw new BusinessException("用户名或密码错误");
        }

        // 7. 检查状态
        if (user.getStatus() != 1) {
            throw new BusinessException("账号已被停用");
        }

        // 8. 检查租户有效期
        Long tenantId = user.getTenantId();
        SysTenant tenant = checkTenantExpiry(tenantId);

        // 9. 生成 Token 和响应（含设备记录 + 异地检测）
        LoginResponse response = buildLoginResponse(user, tenant, clientIp, deviceInfo);

        // 10. 清除失败计数
        redisUtils.delete(failKey);
        captchaService.clearIpFailure(clientIp);

        log.info("User {} logged in successfully via password", user.getUsername());
        return response;
    }

    public void logout(String token) {
        redisUtils.delete(TOKEN_PREFIX + token);
    }

    public boolean validateToken(String token) {
        return redisUtils.hasKey(TOKEN_PREFIX + token) && !jwtUtils.isTokenExpired(token);
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!ENCODER.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        user.setPassword(ENCODER.encode(newPassword));
        userMapper.updateById(user);
    }

    public void resetPassword(Long userId, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(ENCODER.encode(newPassword));
        userMapper.updateById(user);
    }

    public static String encodePassword(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    // ============ 私有辅助方法 ============

    private SysTenant checkTenantExpiry(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            return null;
        }
        // 检查租户状态
        if (tenant.getStatus() != null) {
            if (tenant.getStatus() == 2) {
                throw new BusinessException("租户已被停用，请联系平台管理员");
            }
            if (tenant.getStatus() == 3) {
                throw new BusinessException("租户已过期，请联系管理员续期");
            }
        }
        // 兼容旧版：按 endDate 或 expireDate 检查过期
        LocalDate effectiveEndDate = tenant.getEndDate() != null ? tenant.getEndDate() : tenant.getExpireDate();
        if (effectiveEndDate != null && effectiveEndDate.isBefore(LocalDate.now())) {
            throw new BusinessException("租户已过期，请联系管理员续期");
        }
        return tenant;
    }

    private LoginResponse buildLoginResponse(SysUser user, SysTenant tenant, String clientIp, DeviceInfo deviceInfo) {
        Long tenantId = user.getTenantId();
        String token = jwtUtils.generateToken(user.getId(), tenantId, user.getUsername());
        redisUtils.set(TOKEN_PREFIX + token, user.getId().toString(), jwtUtils.getExpiration(), TimeUnit.MILLISECONDS);

        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissions = userMapper.selectPermissionsByUserId(user.getId());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());
        response.setTenantId(tenantId);
        response.setTenantName(tenant != null ? tenant.getTenantName() : null);
        response.setRoles(roles);
        response.setPermissions(permissions);

        // 设备记录 + 异地登录检测（安全增强，失败不阻断登录主流程）
        recordDeviceAndDetectLocation(user.getId(), clientIp, deviceInfo, token);

        return response;
    }

    /**
     * 记录登录设备并进行异地登录检测。
     *
     * <p>顺序约定（见 {@link LoginLocationService#detectAndNotify}）：必须先调用
     * {@code detectAndNotify}，再调用 {@code recordLogin}，以保证"上次登录地"查询命中的是
     * 上一次登录记录而非本次。整个过程包裹在 try-catch 中，设备记录/检测失败仅记录日志，
     * 不影响登录主流程。</p>
     */
    private void recordDeviceAndDetectLocation(Long userId, String clientIp, DeviceInfo deviceInfo, String token) {
        try {
            String deviceDesc = DeviceInfoResolver.describe(deviceInfo);
            // 1. 异地登录检测（必须在 recordLogin 之前）
            loginLocationService.detectAndNotify(userId, clientIp, deviceDesc);
            // 2. 记录本次登录设备（含归属地）
            String location = loginLocationService.resolveLocation(clientIp);
            deviceManagerService.recordLogin(userId, deviceInfo, token, clientIp, location);
        } catch (Exception e) {
            log.warn("[LOGIN] 登录设备记录/异地检测异常: userId={}, ip={}", userId, clientIp, e);
        }
    }

    private void incrementFailCount(String key) {
        if (!lockEnabled) return;
        Long count = redisUtils.increment(key);
        if (count != null && count == 1L) {
            redisUtils.expire(key, lockDuration, TimeUnit.SECONDS);
        }
    }

    /**
     * 验证码校验失败异常
     * 携带新的验证码数据以满足 R4.3 要求
     */
    public static class CaptchaVerifyException extends BusinessException {
        private final CaptchaVO newCaptcha;

        public CaptchaVerifyException(String message, CaptchaVO newCaptcha) {
            super(400, message);
            this.newCaptcha = newCaptcha;
        }

        public CaptchaVO getNewCaptcha() {
            return newCaptcha;
        }
    }
}
