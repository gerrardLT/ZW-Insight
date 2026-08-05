package com.zwinsight.purchase.portal.service;

import com.zwinsight.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SupplierSmsService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SupplierSmsServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private SupplierSmsService smsService;

    @Test
    @DisplayName("sendCode - 手机号为空抛异常")
    void sendCode_blankPhone_throws() {
        assertThatThrownBy(() -> smsService.sendCode(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手机号不能为空");
        assertThatThrownBy(() -> smsService.sendCode("  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手机号不能为空");
    }

    @Test
    @DisplayName("sendCode - 手机号格式不正确抛异常")
    void sendCode_invalidFormat_throws() {
        assertThatThrownBy(() -> smsService.sendCode("12345678901"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手机号格式不正确");
        assertThatThrownBy(() -> smsService.sendCode("1380013800"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手机号格式不正确");
    }

    @Test
    @DisplayName("sendCode - 60秒内重复发送抛异常")
    void sendCode_withinInterval_throws() {
        when(redisTemplate.hasKey("sms:supplier:13800138000:interval")).thenReturn(true);

        assertThatThrownBy(() -> smsService.sendCode("13800138000"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码发送过于频繁");
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("sendCode - 正常发送：写入验证码（5分钟TTL）+ 间隔标记（60秒）")
    void sendCode_success_writesRedisKeys() {
        when(redisTemplate.hasKey("sms:supplier:13800138000:interval")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        smsService.sendCode("13800138000");

        verify(valueOperations).set(eq("sms:supplier:13800138000"), argThat(code ->
                code != null && code.length() == 6 && code.chars().allMatch(Character::isDigit)
        ), eq(5L), eq(TimeUnit.MINUTES));
        verify(valueOperations).set(eq("sms:supplier:13800138000:interval"), eq("1"),
                eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("verifyCode - 入参为 null 返回 false")
    void verifyCode_nullInput_returnsFalse() {
        assertThat(smsService.verifyCode(null, "123456")).isFalse();
        assertThat(smsService.verifyCode("13800138000", null)).isFalse();
    }

    @Test
    @DisplayName("verifyCode - 验证码匹配：返回 true 并删除（一次性使用）")
    void verifyCode_match_returnsTrueAndDeletes() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("sms:supplier:13800138000")).thenReturn("654321");

        assertThat(smsService.verifyCode("13800138000", "654321")).isTrue();
        verify(redisTemplate).delete("sms:supplier:13800138000");
    }

    @Test
    @DisplayName("verifyCode - 验证码不匹配或已过期：返回 false 不删除")
    void verifyCode_mismatch_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("sms:supplier:13800138000")).thenReturn("654321");

        assertThat(smsService.verifyCode("13800138000", "111111")).isFalse();
        verify(redisTemplate, never()).delete(anyString());

        when(valueOperations.get("sms:supplier:13900139000")).thenReturn(null);
        assertThat(smsService.verifyCode("13900139000", "111111")).isFalse();
    }
}
