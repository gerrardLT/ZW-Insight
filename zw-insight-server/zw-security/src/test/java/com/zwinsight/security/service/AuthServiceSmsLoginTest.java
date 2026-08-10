package com.zwinsight.security.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.security.domain.SysTenant;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.dto.LoginRequest;
import com.zwinsight.security.dto.LoginResponse;
import com.zwinsight.security.mapper.SysTenantMapper;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.security.util.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 短信登录与密码重置补测（阶段四批 1）
 * <p>密码登录主路径已由 TokenServiceTest 覆盖，本类补 SMS 登录分支、
 * 租户停用分支与 resetPassword。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — 短信登录与密码重置补测")
class AuthServiceSmsLoginTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysTenantMapper tenantMapper;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private CaptchaService captchaService;
    @Mock
    private RedisUtils redisUtils;
    @Mock
    private DeviceManagerService deviceManagerService;
    @Mock
    private LoginLocationService loginLocationService;

    @InjectMocks
    private AuthService authService;

    private LoginRequest smsRequest(String phone, String code) {
        LoginRequest req = new LoginRequest();
        req.setLoginType("SMS");
        req.setPhone(phone);
        req.setSmsCode(code);
        return req;
    }

    private SysUser validUser() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("u1");
        user.setRealName("用户一");
        user.setPhone("13800000001");
        user.setStatus(1);
        user.setTenantId(1L);
        return user;
    }

    private SysTenant validTenant() {
        SysTenant tenant = new SysTenant();
        tenant.setId(1L);
        tenant.setTenantName("租户A");
        tenant.setStatus(1);
        tenant.setEndDate(LocalDate.now().plusDays(30));
        return tenant;
    }

    @Test
    @DisplayName("SMS 登录 - 验证码正确时签发 Token，且异地检测先于设备记录")
    void smsLogin_success() {
        when(captchaService.verifySmsCode("13800000001", "1234")).thenReturn(true);
        when(userMapper.selectOne(any())).thenReturn(validUser());
        when(tenantMapper.selectById(1L)).thenReturn(validTenant());
        when(jwtUtils.generateToken(1L, 1L, "u1")).thenReturn("tk-sms");
        when(jwtUtils.getExpiration()).thenReturn(3600000L);
        when(userMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
        when(userMapper.selectPermissionsByUserId(1L)).thenReturn(List.of("p1"));

        LoginResponse resp = authService.login(smsRequest("13800000001", "1234"), "1.2.3.4", null);

        assertThat(resp.getToken()).isEqualTo("tk-sms");
        assertThat(resp.getUsername()).isEqualTo("u1");
        assertThat(resp.getTenantName()).isEqualTo("租户A");
        assertThat(resp.getRoles()).containsExactly("ADMIN");
        verify(redisUtils).set(eq("token:tk-sms"), eq("1"), eq(3600000L), eq(TimeUnit.MILLISECONDS));
        // 顺序约定：detectAndNotify 必须在 recordLogin 之前（保证"上次登录地"为历史记录）
        InOrder order = inOrder(loginLocationService, deviceManagerService);
        order.verify(loginLocationService).detectAndNotify(eq(1L), eq("1.2.3.4"), any());
        order.verify(deviceManagerService).recordLogin(eq(1L), any(), eq("tk-sms"), eq("1.2.3.4"), any());
    }

    @Test
    @DisplayName("SMS 登录 - 手机号/验证码为空分别拒绝")
    void smsLogin_missingParams() {
        assertThatThrownBy(() -> authService.login(smsRequest(null, "1234"), null, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("手机号不能为空");
        assertThatThrownBy(() -> authService.login(smsRequest("13800000001", " "), null, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("短信验证码不能为空");
    }

    @Test
    @DisplayName("SMS 登录 - 验证码错误或过期拒绝")
    void smsLogin_invalidCode() {
        when(captchaService.verifySmsCode("13800000001", "9999")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(smsRequest("13800000001", "9999"), null, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("短信验证码错误或已过期");
    }

    @Test
    @DisplayName("SMS 登录 - 手机号未注册拒绝")
    void smsLogin_phoneNotRegistered() {
        when(captchaService.verifySmsCode(anyString(), anyString())).thenReturn(true);
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.login(smsRequest("13800000002", "1234"), null, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("该手机号未注册");
    }

    @Test
    @DisplayName("SMS 登录 - 账号停用拒绝")
    void smsLogin_accountDisabled() {
        SysUser user = validUser();
        user.setStatus(0);
        when(captchaService.verifySmsCode(anyString(), anyString())).thenReturn(true);
        when(userMapper.selectOne(any())).thenReturn(user);

        assertThatThrownBy(() -> authService.login(smsRequest("13800000001", "1234"), null, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("账号已被停用");
    }

    @Test
    @DisplayName("SMS 登录 - 租户停用（status=2）与过期（status=3）分别拒绝")
    void smsLogin_tenantBlockedOrExpired() {
        when(captchaService.verifySmsCode(anyString(), anyString())).thenReturn(true);
        when(userMapper.selectOne(any())).thenReturn(validUser());

        SysTenant disabled = validTenant();
        disabled.setStatus(2);
        when(tenantMapper.selectById(1L)).thenReturn(disabled);
        assertThatThrownBy(() -> authService.login(smsRequest("13800000001", "1234"), null, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("租户已被停用");

        SysTenant expired = validTenant();
        expired.setStatus(3);
        when(tenantMapper.selectById(1L)).thenReturn(expired);
        assertThatThrownBy(() -> authService.login(smsRequest("13800000001", "1234"), null, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("租户已过期");
    }

    @Test
    @DisplayName("resetPassword - 直接重置密码（BCrypt 可验证），用户不存在拒绝")
    void resetPassword() {
        SysUser user = validUser();
        when(userMapper.selectById(1L)).thenReturn(user);

        authService.resetPassword(1L, "newPass123");

        assertThat(user.getPassword()).isNotEqualTo("newPass123"); // BCrypt 密文
        verify(userMapper).updateById(user);

        when(userMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> authService.resetPassword(99L, "x"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("用户不存在");
    }

    @Test
    @DisplayName("login 重载 - 无 clientIp/设备信息参数等价委托不抛异常")
    void login_overloads_delegate() {
        when(captchaService.verifySmsCode(anyString(), anyString())).thenReturn(true);
        when(userMapper.selectOne(any())).thenReturn(validUser());
        when(tenantMapper.selectById(1L)).thenReturn(validTenant());
        when(jwtUtils.generateToken(anyLong(), anyLong(), anyString())).thenReturn("tk2");
        when(jwtUtils.getExpiration()).thenReturn(1000L);
        when(userMapper.selectRoleCodesByUserId(anyLong())).thenReturn(List.of());
        when(userMapper.selectPermissionsByUserId(anyLong())).thenReturn(List.of());

        assertThat(authService.login(smsRequest("13800000001", "1234")).getToken()).isEqualTo("tk2");
        assertThat(authService.login(smsRequest("13800000001", "1234"), "9.9.9.9").getToken()).isEqualTo("tk2");
    }
}
