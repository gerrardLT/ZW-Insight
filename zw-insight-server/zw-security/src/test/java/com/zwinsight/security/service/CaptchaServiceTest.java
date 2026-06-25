package com.zwinsight.security.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.RedisUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CaptchaService 单元测试
 * <p>
 * 测试验证码生成/校验、短信发送限制、IP 锁定机制
 */
@ExtendWith(MockitoExtension.class)
class CaptchaServiceTest {

    @Mock
    private RedisUtils redisUtils;

    @Mock
    private SmsService smsService;

    @InjectMocks
    private CaptchaService captchaService;

    private static final String TEST_UUID = "abc123";
    private static final String TEST_CODE = "aB3d";
    private static final String TEST_PHONE = "13812345678";
    private static final String TEST_IP = "192.168.1.100";

    // ============ 图形验证码测试 ============

    @Test
    @DisplayName("testVerifyImageCaptcha_success — 正确验证码通过")
    void testVerifyImageCaptcha_success() {
        // 模拟 Redis 中存在验证码
        when(redisUtils.get("captcha:" + TEST_UUID)).thenReturn(TEST_CODE);

        // 执行校验（大小写不敏感）
        boolean result = captchaService.verifyImageCaptcha(TEST_UUID, "Ab3D");

        // 断言通过
        assertTrue(result);
        // 验证删除 key（一次性使用）
        verify(redisUtils).delete("captcha:" + TEST_UUID);
    }

    @Test
    @DisplayName("testVerifyImageCaptcha_failsWhenExpired — 过期返回 false")
    void testVerifyImageCaptcha_failsWhenExpired() {
        // 模拟 Redis 中验证码已过期（返回 null）
        when(redisUtils.get("captcha:" + TEST_UUID)).thenReturn(null);

        // 执行校验
        boolean result = captchaService.verifyImageCaptcha(TEST_UUID, TEST_CODE);

        // 断言失败
        assertFalse(result);
        // 验证仍然调用了 delete
        verify(redisUtils).delete("captcha:" + TEST_UUID);
    }

    // ============ 短信验证码测试 ============

    @Test
    @DisplayName("testSendSmsCode_rejectsInvalidPhone — 手机号无效抛异常")
    void testSendSmsCode_rejectsInvalidPhone() {
        // 无效手机号
        BusinessException ex = assertThrows(BusinessException.class, () ->
                captchaService.sendSmsCode("1234567890")
        );
        assertEquals("手机号格式无效", ex.getMessage());

        // null 手机号
        BusinessException exNull = assertThrows(BusinessException.class, () ->
                captchaService.sendSmsCode(null)
        );
        assertEquals("手机号格式无效", exNull.getMessage());

        // 验证不与 Redis 交互
        verifyNoInteractions(redisUtils);
    }

    @Test
    @DisplayName("testSendSmsCode_rejectsWhenFreqLimited — 60秒内拒绝")
    void testSendSmsCode_rejectsWhenFreqLimited() {
        // 模拟频率限制 key 存在
        when(redisUtils.hasKey("sms:freq:" + TEST_PHONE)).thenReturn(true);
        when(redisUtils.getExpire("sms:freq:" + TEST_PHONE, TimeUnit.SECONDS)).thenReturn(45L);

        // 执行发送
        BusinessException ex = assertThrows(BusinessException.class, () ->
                captchaService.sendSmsCode(TEST_PHONE)
        );

        // 断言包含频率限制信息
        assertTrue(ex.getMessage().contains("发送过于频繁"));
        assertTrue(ex.getMessage().contains("45"));

        // 验证不发送短信
        verifyNoInteractions(smsService);
    }

    @Test
    @DisplayName("testSendSmsCode_rejectsWhenDailyLimitExceeded — 日限额拒绝")
    void testSendSmsCode_rejectsWhenDailyLimitExceeded() {
        // 模拟频率限制未触发
        when(redisUtils.hasKey("sms:freq:" + TEST_PHONE)).thenReturn(false);
        // 模拟日发送次数已达上限（10次）
        when(redisUtils.get("sms:daily:" + TEST_PHONE)).thenReturn("10");

        // 执行发送
        BusinessException ex = assertThrows(BusinessException.class, () ->
                captchaService.sendSmsCode(TEST_PHONE)
        );

        // 断言包含日限额信息
        assertTrue(ex.getMessage().contains("今日短信发送次数已达上限"));

        // 验证不发送短信
        verifyNoInteractions(smsService);
    }

    // ============ IP 锁定机制测试 ============

    @Test
    @DisplayName("testCheckIpLock_throwsWhenLocked — IP 锁定时抛异常")
    void testCheckIpLock_throwsWhenLocked() {
        // 模拟 IP 锁定 key 存在
        when(redisUtils.hasKey("login:ip:lock:" + TEST_IP)).thenReturn(true);
        when(redisUtils.getExpire("login:ip:lock:" + TEST_IP, TimeUnit.SECONDS)).thenReturn(600L);

        // 执行检查
        BusinessException ex = assertThrows(BusinessException.class, () ->
                captchaService.checkIpLock(TEST_IP)
        );

        // 断言包含锁定提示
        assertTrue(ex.getMessage().contains("登录失败次数过多"));
    }

    @Test
    @DisplayName("testRecordIpFailure_locksAfter5Failures — 5次后锁定")
    void testRecordIpFailure_locksAfter5Failures() {
        // 模拟第5次失败（increment 返回 5）
        when(redisUtils.increment("login:ip:fail:" + TEST_IP)).thenReturn(5L);

        // 执行记录失败
        captchaService.recordIpFailure(TEST_IP);

        // 验证设置了锁定 key（900秒 = 15分钟）
        verify(redisUtils).set(
                eq("login:ip:lock:" + TEST_IP),
                eq("1"),
                eq(900L),
                eq(TimeUnit.SECONDS)
        );
        // 验证清除了失败计数
        verify(redisUtils).delete("login:ip:fail:" + TEST_IP);
    }
}
