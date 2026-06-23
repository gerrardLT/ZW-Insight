package com.zwinsight.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.security.domain.SysTenant;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.dto.LoginRequest;
import com.zwinsight.security.dto.LoginResponse;
import com.zwinsight.security.mapper.SysTenantMapper;
import com.zwinsight.security.mapper.SysUserMapper;
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

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String TOKEN_PREFIX = "token:";
    private static final String LOGIN_FAIL_PREFIX = "login_fail:";

    @Value("${auth.lock-enabled:true}")
    private boolean lockEnabled;
    @Value("${auth.lock-max-attempts:5}")
    private int maxAttempts;
    @Value("${auth.lock-duration:1800}")
    private int lockDuration;

    public LoginResponse login(LoginRequest request) {
        // 1. 验证验证码
        if (captchaService.isCaptchaEnabled()) {
            if (!captchaService.validateCaptcha(request.getCaptchaKey(), request.getCaptcha())) {
                throw new BusinessException("验证码错误或已过期");
            }
        }

        // 2. 检查锁定
        String failKey = LOGIN_FAIL_PREFIX + request.getUsername();
        if (lockEnabled) {
            Object failCount = redisUtils.get(failKey);
            if (failCount != null && Integer.parseInt(failCount.toString()) >= maxAttempts) {
                throw new BusinessException("账号已被锁定，请" + (lockDuration / 60) + "分钟后再试");
            }
        }

        // 3. 查找用户
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
                        .eq(SysUser::getDeleted, 0)
        );
        if (user == null) {
            incrementFailCount(failKey);
            throw new BusinessException("用户名或密码错误");
        }

        // 4. 验证密码
        if (!ENCODER.matches(request.getPassword(), user.getPassword())) {
            incrementFailCount(failKey);
            throw new BusinessException("用户名或密码错误");
        }

        // 5. 检查状态
        if (user.getStatus() != 1) {
            throw new BusinessException("账号已被停用");
        }

        // 6. 检查租户有效期
        Long tenantId = user.getTenantId();
        SysTenant tenant = null;
        if (tenantId != null) {
            tenant = tenantMapper.selectById(tenantId);
            if (tenant != null && tenant.getExpireDate() != null && tenant.getExpireDate().isBefore(LocalDate.now())) {
                throw new BusinessException("租户已过期，请联系管理员续期");
            }
        }

        // 7. 生成Token
        String token = jwtUtils.generateToken(user.getId(), tenantId, user.getUsername());
        redisUtils.set(TOKEN_PREFIX + token, user.getId().toString(), jwtUtils.getExpiration(), TimeUnit.MILLISECONDS);

        // 8. 清除失败计数
        redisUtils.delete(failKey);

        // 9. 查询角色和权限
        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissions = userMapper.selectPermissionsByUserId(user.getId());

        // 10. 构造响应
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());
        response.setTenantId(tenantId);
        response.setTenantName(tenant != null ? tenant.getTenantName() : null);
        response.setRoles(roles);
        response.setPermissions(permissions);

        log.info("User {} logged in successfully", user.getUsername());
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

    private void incrementFailCount(String key) {
        if (!lockEnabled) return;
        Long count = redisUtils.increment(key);
        if (count != null && count == 1L) {
            redisUtils.expire(key, lockDuration, TimeUnit.SECONDS);
        }
    }
}
