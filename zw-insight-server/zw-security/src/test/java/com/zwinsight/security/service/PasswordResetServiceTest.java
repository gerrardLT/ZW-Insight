package com.zwinsight.security.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PasswordResetService 单元测试。
 * <p>
 * 校验密码复杂度（纯逻辑）、手机号未注册拒绝、验证码锁定、验证码无效拒绝，
 * 以及密码重置后将该用户全部 Token 加入黑名单。
 * 依赖（Redis / Mapper / CaptchaService）使用 Mockito 隔离，被测对象为真实服务。
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private CaptchaService captchaService;
    @Mock
    private RedisUtils redisUtils;

    @InjectMocks
    private PasswordResetService service;

    private static final String PHONE = "13812345678";
    private static final String CODE = "123456";

    // ============ 密码复杂度（纯逻辑，无 mock 交互） ============

    @Test
    @DisplayName("isPasswordValid — 合法/非法密码判定")
    void testPasswordComplexity() {
        // 合法：8-20 字符，含字母和数字
        assertTrue(service.isPasswordValid("abc12345"));
        assertTrue(service.isPasswordValid("Passw0rd2024"));
        assertTrue(service.isPasswordValid("a1b2c3d4e5f6g7h8i9j0")); // 20 位

        // 非法：太短
        assertFalse(service.isPasswordValid("ab12"));
        // 非法：太长（21 位）
        assertFalse(service.isPasswordValid("a1b2c3d4e5f6g7h8i9j0k"));
        // 非法：纯数字
        assertFalse(service.isPasswordValid("12345678"));
        // 非法：纯字母
        assertFalse(service.isPasswordValid("abcdefgh"));
        // 非法：null
        assertFalse(service.isPasswordValid(null));
    }

    // ============ sendCode ============

    @Test
    @DisplayName("sendCode — 手机号未注册抛错")
    void testSendCode_unregisteredPhone() {
        when(userMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendCode(PHONE));
        assertEquals("手机号未注册", ex.getMessage());
        verify(captchaService, never()).sendSmsCode(anyString());
    }

    @Test
    @DisplayName("sendCode — 已注册则复用 CaptchaService 发送")
    void testSendCode_registeredPhone() {
        when(userMapper.selectOne(any())).thenReturn(newUser(1L));

        service.sendCode(PHONE);

        verify(captchaService).sendSmsCode(PHONE);
    }

    // ============ verifyCode ============

    @Test
    @DisplayName("verifyCode — 已锁定时抛 429")
    void testVerifyCode_locked() {
        when(redisUtils.hasKey("pwd_reset:lock:" + PHONE)).thenReturn(true);
        when(redisUtils.getExpire(eq("pwd_reset:lock:" + PHONE), eq(TimeUnit.SECONDS)))
                .thenReturn(1800L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifyCode(PHONE, CODE));
        assertEquals(429, ex.getCode());
        assertTrue(ex.getMessage().contains("校验次数超限"));
    }

    @Test
    @DisplayName("verifyCode — 验证码无效则计数失败")
    void testVerifyCode_invalid() {
        when(redisUtils.hasKey("pwd_reset:lock:" + PHONE)).thenReturn(false);
        when(captchaService.peekSmsCode(PHONE, CODE)).thenReturn(false);
        when(redisUtils.increment("pwd_reset:verify_fail:" + PHONE)).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifyCode(PHONE, CODE));
        assertEquals("验证码无效或已过期", ex.getMessage());
        verify(redisUtils).increment("pwd_reset:verify_fail:" + PHONE);
    }

    @Test
    @DisplayName("verifyCode — 第5次失败触发锁定")
    void testVerifyCode_lockAfter5Failures() {
        when(redisUtils.hasKey("pwd_reset:lock:" + PHONE)).thenReturn(false);
        when(captchaService.peekSmsCode(PHONE, CODE)).thenReturn(false);
        when(redisUtils.increment("pwd_reset:verify_fail:" + PHONE)).thenReturn(5L);

        assertThrows(BusinessException.class, () -> service.verifyCode(PHONE, CODE));

        verify(redisUtils).set(eq("pwd_reset:lock:" + PHONE), eq("1"),
                eq(30L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("verifyCode — 成功清除失败计数")
    void testVerifyCode_success() {
        when(redisUtils.hasKey("pwd_reset:lock:" + PHONE)).thenReturn(false);
        when(captchaService.peekSmsCode(PHONE, CODE)).thenReturn(true);

        assertDoesNotThrow(() -> service.verifyCode(PHONE, CODE));
        verify(redisUtils).delete("pwd_reset:verify_fail:" + PHONE);
    }

    // ============ resetPassword ============

    @Test
    @DisplayName("resetPassword — 密码复杂度不符抛错且不消费验证码")
    void testResetPassword_weakPassword() {
        when(userMapper.selectOne(any())).thenReturn(newUser(1L));
        when(redisUtils.hasKey("pwd_reset:lock:" + PHONE)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resetPassword(PHONE, CODE, "weak"));
        assertTrue(ex.getMessage().contains("密码复杂度"));
        verify(captchaService, never()).verifySmsCode(anyString(), anyString());
    }

    @Test
    @DisplayName("resetPassword — 成功后所有 Token 进入黑名单并更新密码")
    void testResetPassword_invalidatesAllTokens() {
        SysUser user = newUser(42L);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(redisUtils.hasKey("pwd_reset:lock:" + PHONE)).thenReturn(false);
        when(captchaService.verifySmsCode(PHONE, CODE)).thenReturn(true);

        // 该用户的两个会话 + 一个其他用户会话 + 一个已有黑名单 key
        Set<String> tokenKeys = new HashSet<>();
        tokenKeys.add("token:tokenA");
        tokenKeys.add("token:tokenB");
        tokenKeys.add("token:tokenC");
        tokenKeys.add("token:blacklist:oldhash");
        when(redisUtils.keys("token:*")).thenReturn(tokenKeys);
        when(redisUtils.get("token:tokenA")).thenReturn("42");
        when(redisUtils.get("token:tokenB")).thenReturn("42");
        when(redisUtils.get("token:tokenC")).thenReturn("99"); // 其他用户
        when(redisUtils.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(3600L);

        service.resetPassword(PHONE, CODE, "Passw0rd2024");

        // 密码已加密更新
        assertNotEquals("$2a$encoded", user.getPassword());
        verify(userMapper).updateById(user);

        // 该用户两个会话被删除
        verify(redisUtils).delete("token:tokenA");
        verify(redisUtils).delete("token:tokenB");
        // 其他用户会话未被删除
        verify(redisUtils, never()).delete("token:tokenC");
        // 黑名单写入两次（仅该用户两个 Token），key 以 token:blacklist: 开头
        verify(redisUtils, times(2)).set(
                startsWith("token:blacklist:"), eq("1"), eq(3600L), eq(TimeUnit.SECONDS));
        // 验证码被消费
        verify(captchaService).verifySmsCode(PHONE, CODE);
    }

    private SysUser newUser(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setPhone(PHONE);
        user.setPassword("$2a$encoded");
        user.setStatus(1);
        return user;
    }
}
