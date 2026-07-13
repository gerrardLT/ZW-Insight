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
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JWT 认证与权限单元测试
 * <p>
 * 覆盖：token 生成（正常+过期参数）、token 验证（有效+无效+篡改+过期）、登录流程（正常+密码错误+用户不存在）
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    // ============ JWT Token 测试 ============

    @Nested
    @DisplayName("JwtUtils — Token 生成与验证")
    class JwtUtilsTest {

        private JwtUtils jwtUtils;

        private static final String SECRET = "ZwInsight2024SecretKeyForJwtTokenGeneration";
        private static final long DEFAULT_EXPIRATION = 86400000L; // 24小时

        @BeforeEach
        void setUp() {
            jwtUtils = new JwtUtils();
            ReflectionTestUtils.setField(jwtUtils, "secret", SECRET);
            ReflectionTestUtils.setField(jwtUtils, "expiration", DEFAULT_EXPIRATION);
        }

        @Test
        @DisplayName("生成 Token - 正常参数，包含正确的 claims")
        void generateToken_normalParams_containsCorrectClaims() {
            // Given
            Long userId = 1001L;
            Long tenantId = 100L;
            String username = "test_admin";

            // When
            String token = jwtUtils.generateToken(userId, tenantId, username);

            // Then
            assertThat(token).isNotNull().isNotBlank();
            Claims claims = jwtUtils.parseToken(token);
            assertThat(claims.get("userId", Long.class)).isEqualTo(userId);
            assertThat(claims.get("tenantId", Long.class)).isEqualTo(tenantId);
            assertThat(claims.get("username", String.class)).isEqualTo(username);
            assertThat(claims.getSubject()).isEqualTo(username);
        }

        @Test
        @DisplayName("生成 Token - 过期时间正确设置")
        void generateToken_expirationSetCorrectly() {
            // Given
            long customExpiration = 3600000L; // 1小时
            ReflectionTestUtils.setField(jwtUtils, "expiration", customExpiration);

            // When
            String token = jwtUtils.generateToken(1L, 1L, "user1");

            // Then
            Claims claims = jwtUtils.parseToken(token);
            long timeDiff = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
            assertThat(timeDiff).isEqualTo(customExpiration);
        }

        @Test
        @DisplayName("生成 Token - 极短过期时间导致立即过期")
        void generateToken_veryShortExpiration_tokenExpiresImmediately() throws InterruptedException {
            // Given - 设置1毫秒过期
            ReflectionTestUtils.setField(jwtUtils, "expiration", 1L);

            // When
            String token = jwtUtils.generateToken(1L, 1L, "user1");
            Thread.sleep(10); // 等待过期

            // Then
            assertThat(jwtUtils.isTokenExpired(token)).isTrue();
        }

        @Test
        @DisplayName("解析 Token - 有效 Token 返回正确 Claims")
        void parseToken_validToken_returnsCorrectClaims() {
            // Given
            String token = jwtUtils.generateToken(999L, 888L, "admin");

            // When
            Claims claims = jwtUtils.parseToken(token);

            // Then
            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo("admin");
            assertThat(claims.get("userId", Long.class)).isEqualTo(999L);
            assertThat(claims.get("tenantId", Long.class)).isEqualTo(888L);
        }

        @Test
        @DisplayName("解析 Token - 无效 Token 抛出异常")
        void parseToken_invalidToken_throwsException() {
            // Given
            String invalidToken = "this.is.not.a.valid.jwt.token";

            // When & Then
            assertThatThrownBy(() -> jwtUtils.parseToken(invalidToken))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("解析 Token - 篡改 Token 内容后验证失败")
        void parseToken_tamperedToken_throwsException() {
            // Given
            String token = jwtUtils.generateToken(1L, 1L, "user1");
            // 篡改 payload 部分（修改中间段的一个字符）
            String[] parts = token.split("\\.");
            char[] payloadChars = parts[1].toCharArray();
            payloadChars[0] = payloadChars[0] == 'A' ? 'B' : 'A';
            String tamperedToken = parts[0] + "." + new String(payloadChars) + "." + parts[2];

            // When & Then
            assertThatThrownBy(() -> jwtUtils.parseToken(tamperedToken))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("解析 Token - 使用不同密钥签名的 Token 验证失败")
        void parseToken_differentSecret_throwsException() {
            // Given - 用当前实例生成 token
            String token = jwtUtils.generateToken(1L, 1L, "user1");

            // 创建一个使用不同密钥的 JwtUtils 实例
            JwtUtils otherJwtUtils = new JwtUtils();
            ReflectionTestUtils.setField(otherJwtUtils, "secret", "CompletelyDifferentSecretKeyForTesting123");
            ReflectionTestUtils.setField(otherJwtUtils, "expiration", DEFAULT_EXPIRATION);

            // When & Then - 用不同密钥解析应失败
            assertThatThrownBy(() -> otherJwtUtils.parseToken(token))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("isTokenExpired - 未过期的 Token 返回 false")
        void isTokenExpired_validToken_returnsFalse() {
            // Given
            String token = jwtUtils.generateToken(1L, 1L, "user1");

            // When & Then
            assertThat(jwtUtils.isTokenExpired(token)).isFalse();
        }

        @Test
        @DisplayName("isTokenExpired - 已过期的 Token 返回 true")
        void isTokenExpired_expiredToken_returnsTrue() throws InterruptedException {
            // Given - 1ms 过期
            ReflectionTestUtils.setField(jwtUtils, "expiration", 1L);
            String token = jwtUtils.generateToken(1L, 1L, "user1");
            Thread.sleep(10);

            // When & Then
            assertThat(jwtUtils.isTokenExpired(token)).isTrue();
        }

        @Test
        @DisplayName("isTokenExpired - 无效 Token 返回 true")
        void isTokenExpired_invalidToken_returnsTrue() {
            // When & Then
            assertThat(jwtUtils.isTokenExpired("invalid.token.here")).isTrue();
        }

        @Test
        @DisplayName("getUserId - 正确提取用户 ID")
        void getUserId_validToken_returnsCorrectUserId() {
            // Given
            String token = jwtUtils.generateToken(12345L, 1L, "user1");

            // When & Then
            assertThat(jwtUtils.getUserId(token)).isEqualTo(12345L);
        }

        @Test
        @DisplayName("getTenantId - 正确提取租户 ID")
        void getTenantId_validToken_returnsCorrectTenantId() {
            // Given
            String token = jwtUtils.generateToken(1L, 9999L, "user1");

            // When & Then
            assertThat(jwtUtils.getTenantId(token)).isEqualTo(9999L);
        }

        @Test
        @DisplayName("getExpiration - 返回配置的过期时长")
        void getExpiration_returnsConfiguredValue() {
            // When & Then
            assertThat(jwtUtils.getExpiration()).isEqualTo(DEFAULT_EXPIRATION);
        }

        @Test
        @DisplayName("生成 Token - Secret 短于 32 字节时自动补齐")
        void generateToken_shortSecret_paddedCorrectly() {
            // Given - 使用短密钥
            ReflectionTestUtils.setField(jwtUtils, "secret", "short");

            // When
            String token = jwtUtils.generateToken(1L, 1L, "user1");

            // Then - 仍能正确生成和解析
            assertThat(token).isNotBlank();
            Claims claims = jwtUtils.parseToken(token);
            assertThat(claims.get("userId", Long.class)).isEqualTo(1L);
        }
    }

    // ============ AuthService 登录流程测试 ============

    @Nested
    @DisplayName("AuthService — 登录流程")
    class AuthServiceLoginTest {

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

        private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
        private static final String TEST_USERNAME = "test_admin";
        private static final String TEST_PASSWORD = "Test@123456";
        private static final String TEST_IP = "192.168.1.100";

        @BeforeEach
        void setUp() {
            // 设置认证锁定配置
            ReflectionTestUtils.setField(authService, "lockEnabled", true);
            ReflectionTestUtils.setField(authService, "maxAttempts", 5);
            ReflectionTestUtils.setField(authService, "lockDuration", 1800);
        }

        private SysUser buildTestUser() {
            SysUser user = new SysUser();
            user.setId(1001L);
            user.setUsername(TEST_USERNAME);
            user.setPassword(ENCODER.encode(TEST_PASSWORD));
            user.setRealName("测试管理员");
            user.setStatus(1);
            user.setTenantId(9999L);
            return user;
        }

        private SysTenant buildTestTenant() {
            SysTenant tenant = new SysTenant();
            tenant.setId(9999L);
            tenant.setTenantName("自动化测试租户");
            tenant.setStatus(1);
            return tenant;
        }

        private LoginRequest buildPasswordLoginRequest() {
            LoginRequest request = new LoginRequest();
            request.setUsername(TEST_USERNAME);
            request.setPassword(TEST_PASSWORD);
            request.setCaptchaUuid("test-uuid");
            request.setCaptchaCode("abcd");
            return request;
        }

        // --- 正常登录 ---

        @Test
        @DisplayName("密码登录 - 正常流程返回 Token 和用户信息")
        void login_normalPasswordLogin_returnsTokenAndUserInfo() {
            // Given
            LoginRequest request = buildPasswordLoginRequest();
            SysUser user = buildTestUser();
            SysTenant tenant = buildTestTenant();

            // Mock 验证码校验通过
            when(captchaService.isCaptchaEnabled()).thenReturn(true);
            when(captchaService.verifyImageCaptcha("test-uuid", "abcd")).thenReturn(true);
            // Mock 无 IP 锁定
            doNothing().when(captchaService).checkIpLock(TEST_IP);
            // Mock 无账号锁定
            when(redisUtils.get("login_fail:" + TEST_USERNAME)).thenReturn(null);
            // Mock 查询用户
            when(userMapper.selectOne(any())).thenReturn(user);
            // Mock 租户查询
            when(tenantMapper.selectById(9999L)).thenReturn(tenant);
            // Mock Token 生成
            when(jwtUtils.generateToken(1001L, 9999L, TEST_USERNAME)).thenReturn("mock-jwt-token");
            when(jwtUtils.getExpiration()).thenReturn(86400000L);
            // Mock 角色和权限
            when(userMapper.selectRoleCodesByUserId(1001L)).thenReturn(List.of("admin"));
            when(userMapper.selectPermissionsByUserId(1001L)).thenReturn(List.of("system:user:list"));

            // When
            LoginResponse response = authService.login(request, TEST_IP);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getToken()).isEqualTo("mock-jwt-token");
            assertThat(response.getUserId()).isEqualTo(1001L);
            assertThat(response.getUsername()).isEqualTo(TEST_USERNAME);
            assertThat(response.getRealName()).isEqualTo("测试管理员");
            assertThat(response.getTenantId()).isEqualTo(9999L);
            assertThat(response.getTenantName()).isEqualTo("自动化测试租户");
            assertThat(response.getRoles()).containsExactly("admin");
            assertThat(response.getPermissions()).containsExactly("system:user:list");

            // 验证 Token 存入 Redis
            verify(redisUtils).set(eq("token:mock-jwt-token"), eq("1001"), eq(86400000L), eq(TimeUnit.MILLISECONDS));
            // 验证清除失败计数
            verify(redisUtils).delete("login_fail:" + TEST_USERNAME);
        }

        @Test
        @DisplayName("密码登录 - 验证码关闭时跳过验证码校验")
        void login_captchaDisabled_skipsVerification() {
            // Given
            LoginRequest request = buildPasswordLoginRequest();
            SysUser user = buildTestUser();
            SysTenant tenant = buildTestTenant();

            when(captchaService.isCaptchaEnabled()).thenReturn(false);
            doNothing().when(captchaService).checkIpLock(TEST_IP);
            when(redisUtils.get("login_fail:" + TEST_USERNAME)).thenReturn(null);
            when(userMapper.selectOne(any())).thenReturn(user);
            when(tenantMapper.selectById(9999L)).thenReturn(tenant);
            when(jwtUtils.generateToken(anyLong(), anyLong(), anyString())).thenReturn("token-123");
            when(jwtUtils.getExpiration()).thenReturn(86400000L);
            when(userMapper.selectRoleCodesByUserId(anyLong())).thenReturn(List.of());
            when(userMapper.selectPermissionsByUserId(anyLong())).thenReturn(List.of());

            // When
            LoginResponse response = authService.login(request, TEST_IP);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getToken()).isEqualTo("token-123");
            // 验证未调用验证码校验方法
            verify(captchaService, never()).verifyImageCaptcha(anyString(), anyString());
        }

        // --- 密码错误 ---

        @Test
        @DisplayName("密码登录 - 密码错误抛出异常并增加失败计数")
        void login_wrongPassword_throwsExceptionAndIncrementsFailCount() {
            // Given
            LoginRequest request = buildPasswordLoginRequest();
            request.setPassword("WrongPassword!");
            SysUser user = buildTestUser();

            when(captchaService.isCaptchaEnabled()).thenReturn(true);
            when(captchaService.verifyImageCaptcha("test-uuid", "abcd")).thenReturn(true);
            doNothing().when(captchaService).checkIpLock(TEST_IP);
            when(redisUtils.get("login_fail:" + TEST_USERNAME)).thenReturn(null);
            when(userMapper.selectOne(any())).thenReturn(user);
            // Mock increment 返回失败计数
            when(redisUtils.increment("login_fail:" + TEST_USERNAME)).thenReturn(1L);

            // When & Then
            assertThatThrownBy(() -> authService.login(request, TEST_IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户名或密码错误");

            // 验证失败计数增加
            verify(redisUtils).increment("login_fail:" + TEST_USERNAME);
            // 验证记录 IP 失败
            verify(captchaService).recordIpFailure(TEST_IP);
        }

        // --- 用户不存在 ---

        @Test
        @DisplayName("密码登录 - 用户不存在抛出异常")
        void login_userNotFound_throwsException() {
            // Given
            LoginRequest request = buildPasswordLoginRequest();

            when(captchaService.isCaptchaEnabled()).thenReturn(true);
            when(captchaService.verifyImageCaptcha("test-uuid", "abcd")).thenReturn(true);
            doNothing().when(captchaService).checkIpLock(TEST_IP);
            when(redisUtils.get("login_fail:" + TEST_USERNAME)).thenReturn(null);
            // 返回 null 表示用户不存在
            when(userMapper.selectOne(any())).thenReturn(null);
            when(redisUtils.increment("login_fail:" + TEST_USERNAME)).thenReturn(1L);

            // When & Then
            assertThatThrownBy(() -> authService.login(request, TEST_IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户名或密码错误");

            verify(redisUtils).increment("login_fail:" + TEST_USERNAME);
            verify(captchaService).recordIpFailure(TEST_IP);
        }

        // --- 账号锁定 ---

        @Test
        @DisplayName("密码登录 - 账号已锁定时拒绝登录")
        void login_accountLocked_throwsException() {
            // Given
            LoginRequest request = buildPasswordLoginRequest();

            when(captchaService.isCaptchaEnabled()).thenReturn(true);
            when(captchaService.verifyImageCaptcha("test-uuid", "abcd")).thenReturn(true);
            doNothing().when(captchaService).checkIpLock(TEST_IP);
            // 模拟失败次数已达上限
            when(redisUtils.get("login_fail:" + TEST_USERNAME)).thenReturn("5");

            // When & Then
            assertThatThrownBy(() -> authService.login(request, TEST_IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("账号已被锁定");
        }

        // --- 账号被停用 ---

        @Test
        @DisplayName("密码登录 - 账号被停用时拒绝登录")
        void login_accountDisabled_throwsException() {
            // Given
            LoginRequest request = buildPasswordLoginRequest();
            SysUser user = buildTestUser();
            user.setStatus(0); // 停用

            when(captchaService.isCaptchaEnabled()).thenReturn(true);
            when(captchaService.verifyImageCaptcha("test-uuid", "abcd")).thenReturn(true);
            doNothing().when(captchaService).checkIpLock(TEST_IP);
            when(redisUtils.get("login_fail:" + TEST_USERNAME)).thenReturn(null);
            when(userMapper.selectOne(any())).thenReturn(user);

            // When & Then
            assertThatThrownBy(() -> authService.login(request, TEST_IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("账号已被停用");
        }

        // --- Token 验证 ---

        @Test
        @DisplayName("validateToken - Token 有效且 Redis 存在返回 true")
        void validateToken_validAndInRedis_returnsTrue() {
            // Given
            String token = "valid-token-123";
            when(redisUtils.hasKey("token:" + token)).thenReturn(true);
            when(jwtUtils.isTokenExpired(token)).thenReturn(false);

            // When & Then
            assertThat(authService.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("validateToken - Token 不在 Redis 中返回 false")
        void validateToken_notInRedis_returnsFalse() {
            // Given
            String token = "not-in-redis-token";
            when(redisUtils.hasKey("token:" + token)).thenReturn(false);

            // When & Then
            assertThat(authService.validateToken(token)).isFalse();
        }

        @Test
        @DisplayName("validateToken - Token 已过期返回 false")
        void validateToken_expired_returnsFalse() {
            // Given
            String token = "expired-token";
            when(redisUtils.hasKey("token:" + token)).thenReturn(true);
            when(jwtUtils.isTokenExpired(token)).thenReturn(true);

            // When & Then
            assertThat(authService.validateToken(token)).isFalse();
        }

        // --- 登出 ---

        @Test
        @DisplayName("logout - 删除 Redis 中的 Token")
        void logout_deletesTokenFromRedis() {
            // Given
            String token = "token-to-logout";

            // When
            authService.logout(token);

            // Then
            verify(redisUtils).delete("token:" + token);
        }

        // --- 租户过期 ---

        @Test
        @DisplayName("密码登录 - 租户已过期抛出异常")
        void login_tenantExpired_throwsException() {
            // Given
            LoginRequest request = buildPasswordLoginRequest();
            SysUser user = buildTestUser();
            SysTenant tenant = buildTestTenant();
            tenant.setStatus(3); // 已过期

            when(captchaService.isCaptchaEnabled()).thenReturn(true);
            when(captchaService.verifyImageCaptcha("test-uuid", "abcd")).thenReturn(true);
            doNothing().when(captchaService).checkIpLock(TEST_IP);
            when(redisUtils.get("login_fail:" + TEST_USERNAME)).thenReturn(null);
            when(userMapper.selectOne(any())).thenReturn(user);
            when(tenantMapper.selectById(9999L)).thenReturn(tenant);

            // When & Then
            assertThatThrownBy(() -> authService.login(request, TEST_IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("租户已过期");
        }

        // --- 验证码校验失败 ---

        @Test
        @DisplayName("密码登录 - 验证码错误抛出 CaptchaVerifyException")
        void login_wrongCaptcha_throwsCaptchaVerifyException() {
            // Given
            LoginRequest request = buildPasswordLoginRequest();

            when(captchaService.isCaptchaEnabled()).thenReturn(true);
            when(captchaService.verifyImageCaptcha("test-uuid", "abcd")).thenReturn(false);
            doNothing().when(captchaService).checkIpLock(TEST_IP);
            when(captchaService.generateImageCaptcha()).thenReturn(
                    new com.zwinsight.security.dto.CaptchaVO("new-uuid", "data:image/png;base64,xxx"));

            // When & Then
            assertThatThrownBy(() -> authService.login(request, TEST_IP))
                    .isInstanceOf(AuthService.CaptchaVerifyException.class)
                    .hasMessageContaining("验证码错误");
        }
    }

    // ============ AuthService Token 会话管理测试 ============

    @Nested
    @DisplayName("AuthService — 密码管理")
    class AuthServicePasswordTest {

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

        private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(authService, "lockEnabled", true);
            ReflectionTestUtils.setField(authService, "maxAttempts", 5);
            ReflectionTestUtils.setField(authService, "lockDuration", 1800);
        }

        @Test
        @DisplayName("修改密码 - 正常流程成功")
        void changePassword_validOldPassword_success() {
            // Given
            Long userId = 1L;
            String oldPassword = "OldPass123";
            String newPassword = "NewPass456";
            SysUser user = new SysUser();
            user.setId(userId);
            user.setPassword(ENCODER.encode(oldPassword));

            when(userMapper.selectById(userId)).thenReturn(user);
            when(userMapper.updateById(any())).thenReturn(1);

            // When
            authService.changePassword(userId, oldPassword, newPassword);

            // Then
            verify(userMapper).updateById(argThat(u -> {
                // 验证新密码已加密
                SysUser updated = (SysUser) u;
                return ENCODER.matches(newPassword, updated.getPassword());
            }));
        }

        @Test
        @DisplayName("修改密码 - 原密码错误抛出异常")
        void changePassword_wrongOldPassword_throwsException() {
            // Given
            Long userId = 1L;
            SysUser user = new SysUser();
            user.setId(userId);
            user.setPassword(ENCODER.encode("CorrectOldPass"));

            when(userMapper.selectById(userId)).thenReturn(user);

            // When & Then
            assertThatThrownBy(() -> authService.changePassword(userId, "WrongOldPass", "NewPass"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("原密码错误");
        }

        @Test
        @DisplayName("修改密码 - 用户不存在抛出异常")
        void changePassword_userNotFound_throwsException() {
            // Given
            when(userMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> authService.changePassword(999L, "old", "new"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户不存在");
        }

        @Test
        @DisplayName("重置密码 - 正常流程成功")
        void resetPassword_validUser_success() {
            // Given
            Long userId = 1L;
            String newPassword = "ResetPass789";
            SysUser user = new SysUser();
            user.setId(userId);
            user.setPassword("old-encoded-password");

            when(userMapper.selectById(userId)).thenReturn(user);
            when(userMapper.updateById(any())).thenReturn(1);

            // When
            authService.resetPassword(userId, newPassword);

            // Then
            verify(userMapper).updateById(argThat(u -> {
                SysUser updated = (SysUser) u;
                return ENCODER.matches(newPassword, updated.getPassword());
            }));
        }

        @Test
        @DisplayName("重置密码 - 用户不存在抛出异常")
        void resetPassword_userNotFound_throwsException() {
            // Given
            when(userMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> authService.resetPassword(999L, "newPass"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户不存在");
        }

        @Test
        @DisplayName("encodePassword - 静态方法正确加密")
        void encodePassword_returnsValidBcryptHash() {
            // Given
            String raw = "TestPassword123";

            // When
            String encoded = AuthService.encodePassword(raw);

            // Then
            assertThat(encoded).isNotBlank();
            assertThat(encoded).startsWith("$2a$");
            assertThat(ENCODER.matches(raw, encoded)).isTrue();
        }
    }
}
