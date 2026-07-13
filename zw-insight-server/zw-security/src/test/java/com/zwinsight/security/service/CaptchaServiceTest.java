package com.zwinsight.security.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.security.dto.CaptchaVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CaptchaService 单元测试
 * <p>
 * 覆盖：验证码生成（UUID+图片）、验证码校验（正确/错误/过期/无记录）、IP锁定机制、Mock RedisUtils 验证 key 格式
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CaptchaService 验证码与 Redis 缓存测试")
class CaptchaServiceTest {

    @Mock
    private RedisUtils redisUtils;

    @Mock
    private SmsService smsService;

    @InjectMocks
    private CaptchaService captchaService;

    // ============ 图形验证码生成测试 ============

    @Nested
    @DisplayName("图形验证码生成 - generateImageCaptcha")
    class GenerateImageCaptchaTest {

        @Test
        @DisplayName("正常生成 - 返回非空 UUID 和 Base64 图片")
        void generateImageCaptcha_returnsUuidAndImage() {
            // When
            CaptchaVO result = captchaService.generateImageCaptcha();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUuid()).isNotNull().isNotBlank();
            assertThat(result.getUuid()).hasSize(32); // UUID 去掉横线后为 32 位
            assertThat(result.getImageBase64()).isNotNull().startsWith("data:image/png;base64,");
        }

        @Test
        @DisplayName("正常生成 - Redis 存储 key 格式为 captcha:{uuid}，TTL=300s")
        void generateImageCaptcha_storesCaptchaInRedisWithCorrectKeyFormat() {
            // When
            CaptchaVO result = captchaService.generateImageCaptcha();

            // Then - 验证 Redis set 被调用，key 格式为 captcha:{uuid}
            verify(redisUtils).set(
                    eq("captcha:" + result.getUuid()),
                    argThat(code -> code instanceof String && ((String) code).length() == 4),
                    eq(300L),
                    eq(TimeUnit.SECONDS)
            );
        }

        @Test
        @DisplayName("正常生成 - 每次调用返回不同的 UUID")
        void generateImageCaptcha_returnsDifferentUuidEachTime() {
            // When
            CaptchaVO first = captchaService.generateImageCaptcha();
            CaptchaVO second = captchaService.generateImageCaptcha();

            // Then
            assertThat(first.getUuid()).isNotEqualTo(second.getUuid());
        }
    }

    // ============ 旧版验证码生成测试 ============

    @Nested
    @DisplayName("旧版验证码生成 - generateCaptcha(key)")
    class GenerateCaptchaLegacyTest {

        @Test
        @DisplayName("正常生成 - 返回 Base64 图片数据")
        void generateCaptcha_returnsBase64ImageData() {
            // Given
            String key = "test-key-001";

            // When
            String imageBase64 = captchaService.generateCaptcha(key);

            // Then
            assertThat(imageBase64).isNotNull().isNotBlank();
            // Hutool LineCaptcha.getImageBase64Data() 返回带前缀的 base64
            assertThat(imageBase64).contains("base64");
        }

        @Test
        @DisplayName("正常生成 - Redis key 格式为 captcha:{key}，TTL=300s")
        void generateCaptcha_storesInRedisWithCorrectKeyFormat() {
            // Given
            String key = "my-captcha-key";

            // When
            captchaService.generateCaptcha(key);

            // Then
            verify(redisUtils).set(
                    eq("captcha:my-captcha-key"),
                    argThat(code -> code instanceof String && ((String) code).length() == 4),
                    eq(300L),
                    eq(TimeUnit.SECONDS)
            );
        }
    }

    // ============ 图形验证码校验测试 ============

    @Nested
    @DisplayName("图形验证码校验 - verifyImageCaptcha")
    class VerifyImageCaptchaTest {

        private static final String UUID = "abc123def456";
        private static final String CORRECT_CODE = "aB3d";

        @Test
        @DisplayName("校验正确 - 大小写不敏感匹配通过")
        void verifyImageCaptcha_correctCodeCaseInsensitive_returnsTrue() {
            // Given - Redis 中存在验证码
            when(redisUtils.get("captcha:" + UUID)).thenReturn(CORRECT_CODE);

            // When - 用户输入不同大小写
            boolean result = captchaService.verifyImageCaptcha(UUID, "AB3D");

            // Then
            assertThat(result).isTrue();
            // 验证一次性删除
            verify(redisUtils).delete("captcha:" + UUID);
        }

        @Test
        @DisplayName("校验错误 - 输入错误验证码返回 false")
        void verifyImageCaptcha_wrongCode_returnsFalse() {
            // Given
            when(redisUtils.get("captcha:" + UUID)).thenReturn(CORRECT_CODE);

            // When
            boolean result = captchaService.verifyImageCaptcha(UUID, "XXXX");

            // Then
            assertThat(result).isFalse();
            // 验证仍然删除 key（一次性使用）
            verify(redisUtils).delete("captcha:" + UUID);
        }

        @Test
        @DisplayName("校验过期 - Redis 中无记录返回 false")
        void verifyImageCaptcha_expired_returnsFalse() {
            // Given - 验证码已过期（Redis 返回 null）
            when(redisUtils.get("captcha:" + UUID)).thenReturn(null);

            // When
            boolean result = captchaService.verifyImageCaptcha(UUID, CORRECT_CODE);

            // Then
            assertThat(result).isFalse();
            verify(redisUtils).delete("captcha:" + UUID);
        }

        @Test
        @DisplayName("无记录 - UUID 不存在返回 false")
        void verifyImageCaptcha_noRecord_returnsFalse() {
            // Given - 从未生成过此 UUID 的验证码
            String nonExistentUuid = "non-existent-uuid";
            when(redisUtils.get("captcha:" + nonExistentUuid)).thenReturn(null);

            // When
            boolean result = captchaService.verifyImageCaptcha(nonExistentUuid, "1234");

            // Then
            assertThat(result).isFalse();
            verify(redisUtils).delete("captcha:" + nonExistentUuid);
        }

        @Test
        @DisplayName("空参数 - uuid 为 null 直接返回 false")
        void verifyImageCaptcha_nullUuid_returnsFalse() {
            // When
            boolean result = captchaService.verifyImageCaptcha(null, "1234");

            // Then
            assertThat(result).isFalse();
            // 不应与 Redis 交互
            verifyNoInteractions(redisUtils);
        }

        @Test
        @DisplayName("空参数 - inputCode 为 null 直接返回 false")
        void verifyImageCaptcha_nullCode_returnsFalse() {
            // When
            boolean result = captchaService.verifyImageCaptcha(UUID, null);

            // Then
            assertThat(result).isFalse();
            verifyNoInteractions(redisUtils);
        }

        @Test
        @DisplayName("Redis key 格式验证 - 使用 captcha:{uuid} 格式")
        void verifyImageCaptcha_usesCorrectRedisKeyFormat() {
            // Given
            String testUuid = "test-uuid-format-check";
            when(redisUtils.get("captcha:" + testUuid)).thenReturn("ABCD");

            // When
            captchaService.verifyImageCaptcha(testUuid, "ABCD");

            // Then - 验证 get 和 delete 都使用正确的 key 格式
            verify(redisUtils).get("captcha:test-uuid-format-check");
            verify(redisUtils).delete("captcha:test-uuid-format-check");
        }
    }

    // ============ 旧版验证码校验测试 ============

    @Nested
    @DisplayName("旧版验证码校验 - validateCaptcha")
    class ValidateCaptchaLegacyTest {

        @BeforeEach
        void enableCaptcha() {
            // captchaEnabled 是 @Value 注入的字段，@InjectMocks 默认为 false
            // 需要手动设置为 true 以测试实际校验逻辑
            ReflectionTestUtils.setField(captchaService, "captchaEnabled", true);
        }

        @Test
        @DisplayName("校验正确 - 大小写不敏感匹配通过")
        void validateCaptcha_correctCode_returnsTrue() {
            // Given
            when(redisUtils.get("captcha:key1")).thenReturn("AbCd");

            // When
            boolean result = captchaService.validateCaptcha("key1", "abcd");

            // Then
            assertThat(result).isTrue();
            verify(redisUtils).delete("captcha:key1");
        }

        @Test
        @DisplayName("校验错误 - 验证码不匹配返回 false")
        void validateCaptcha_wrongCode_returnsFalse() {
            // Given
            when(redisUtils.get("captcha:key1")).thenReturn("AbCd");

            // When
            boolean result = captchaService.validateCaptcha("key1", "WRONG");

            // Then
            assertThat(result).isFalse();
            verify(redisUtils).delete("captcha:key1");
        }

        @Test
        @DisplayName("验证码已过期 - Redis 返回 null 时返回 false")
        void validateCaptcha_expired_returnsFalse() {
            // Given
            when(redisUtils.get("captcha:key1")).thenReturn(null);

            // When
            boolean result = captchaService.validateCaptcha("key1", "1234");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("验证码关闭时 - 无论输入什么都返回 true")
        void validateCaptcha_captchaDisabled_alwaysReturnsTrue() {
            // Given - 关闭验证码开关
            ReflectionTestUtils.setField(captchaService, "captchaEnabled", false);

            // When
            boolean result = captchaService.validateCaptcha("any-key", "any-code");

            // Then - 直接通过，不与 Redis 交互
            assertThat(result).isTrue();
            verifyNoInteractions(redisUtils);
        }
    }

    // ============ IP 锁定机制测试 ============

    @Nested
    @DisplayName("IP 锁定机制 - checkIpLock / recordIpFailure / clearIpFailure")
    class IpLockTest {

        private static final String TEST_IP = "192.168.1.100";

        // --- checkIpLock ---

        @Test
        @DisplayName("checkIpLock - IP 未锁定时正常通过不抛异常")
        void checkIpLock_notLocked_passes() {
            // Given
            when(redisUtils.hasKey("login:ip:lock:" + TEST_IP)).thenReturn(false);

            // When & Then
            assertThatCode(() -> captchaService.checkIpLock(TEST_IP))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("checkIpLock - IP 已锁定时抛出 BusinessException")
        void checkIpLock_locked_throwsException() {
            // Given
            when(redisUtils.hasKey("login:ip:lock:" + TEST_IP)).thenReturn(true);
            when(redisUtils.getExpire("login:ip:lock:" + TEST_IP, TimeUnit.SECONDS)).thenReturn(600L);

            // When & Then
            assertThatThrownBy(() -> captchaService.checkIpLock(TEST_IP))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("登录失败次数过多");
        }

        @Test
        @DisplayName("checkIpLock - IP 为 null 时静默跳过不抛异常")
        void checkIpLock_nullIp_passes() {
            // When & Then
            assertThatCode(() -> captchaService.checkIpLock(null))
                    .doesNotThrowAnyException();
            verifyNoInteractions(redisUtils);
        }

        @Test
        @DisplayName("checkIpLock - Redis key 格式为 login:ip:lock:{ip}")
        void checkIpLock_usesCorrectRedisKeyFormat() {
            // Given
            String ip = "10.0.0.1";
            when(redisUtils.hasKey("login:ip:lock:" + ip)).thenReturn(false);

            // When
            captchaService.checkIpLock(ip);

            // Then
            verify(redisUtils).hasKey("login:ip:lock:10.0.0.1");
        }

        // --- recordIpFailure ---

        @Test
        @DisplayName("recordIpFailure - 首次失败设置 5 分钟窗口过期时间")
        void recordIpFailure_firstFailure_setsExpireWindow() {
            // Given - increment 返回 1 表示首次失败
            when(redisUtils.increment("login:ip:fail:" + TEST_IP)).thenReturn(1L);

            // When
            captchaService.recordIpFailure(TEST_IP);

            // Then - 设置 5 分钟（300秒）窗口
            verify(redisUtils).expire("login:ip:fail:" + TEST_IP, 300, TimeUnit.SECONDS);
            // 未达阈值，不设置锁定
            verify(redisUtils, never()).set(eq("login:ip:lock:" + TEST_IP), any(), anyLong(), any());
        }

        @Test
        @DisplayName("recordIpFailure - 未达阈值（<5次）不触发锁定")
        void recordIpFailure_belowThreshold_doesNotLock() {
            // Given - 第 3 次失败
            when(redisUtils.increment("login:ip:fail:" + TEST_IP)).thenReturn(3L);

            // When
            captchaService.recordIpFailure(TEST_IP);

            // Then - 不触发锁定
            verify(redisUtils, never()).set(eq("login:ip:lock:" + TEST_IP), any(), anyLong(), any());
            // 非首次，不设置 expire
            verify(redisUtils, never()).expire(anyString(), anyLong(), any());
        }

        @Test
        @DisplayName("recordIpFailure - 达到 5 次阈值后锁定 15 分钟")
        void recordIpFailure_reachesThreshold_locksFor15Minutes() {
            // Given - 第 5 次失败
            when(redisUtils.increment("login:ip:fail:" + TEST_IP)).thenReturn(5L);

            // When
            captchaService.recordIpFailure(TEST_IP);

            // Then - 设置锁定 key（900秒 = 15分钟）
            verify(redisUtils).set(
                    eq("login:ip:lock:" + TEST_IP),
                    eq("1"),
                    eq(900L),
                    eq(TimeUnit.SECONDS)
            );
            // 清除失败计数
            verify(redisUtils).delete("login:ip:fail:" + TEST_IP);
        }

        @Test
        @DisplayName("recordIpFailure - 超过 5 次仍触发锁定")
        void recordIpFailure_exceedsThreshold_stillLocks() {
            // Given - 极端情况：第 10 次失败（理论上不该到这，但防御性测试）
            when(redisUtils.increment("login:ip:fail:" + TEST_IP)).thenReturn(10L);

            // When
            captchaService.recordIpFailure(TEST_IP);

            // Then - 仍然设置锁定
            verify(redisUtils).set(
                    eq("login:ip:lock:" + TEST_IP),
                    eq("1"),
                    eq(900L),
                    eq(TimeUnit.SECONDS)
            );
        }

        @Test
        @DisplayName("recordIpFailure - IP 为 null 时静默跳过")
        void recordIpFailure_nullIp_skips() {
            // When
            captchaService.recordIpFailure(null);

            // Then
            verifyNoInteractions(redisUtils);
        }

        @Test
        @DisplayName("recordIpFailure - Redis key 格式为 login:ip:fail:{ip}")
        void recordIpFailure_usesCorrectRedisKeyFormat() {
            // Given
            String ip = "172.16.0.1";
            when(redisUtils.increment("login:ip:fail:" + ip)).thenReturn(2L);

            // When
            captchaService.recordIpFailure(ip);

            // Then
            verify(redisUtils).increment("login:ip:fail:172.16.0.1");
        }

        // --- clearIpFailure ---

        @Test
        @DisplayName("clearIpFailure - 正常清除失败计数")
        void clearIpFailure_deletesFailCountKey() {
            // When
            captchaService.clearIpFailure(TEST_IP);

            // Then
            verify(redisUtils).delete("login:ip:fail:" + TEST_IP);
        }

        @Test
        @DisplayName("clearIpFailure - IP 为 null 时静默跳过")
        void clearIpFailure_nullIp_skips() {
            // When
            captchaService.clearIpFailure(null);

            // Then
            verifyNoInteractions(redisUtils);
        }

        @Test
        @DisplayName("clearIpFailure - Redis key 格式为 login:ip:fail:{ip}")
        void clearIpFailure_usesCorrectRedisKeyFormat() {
            // Given
            String ip = "8.8.8.8";

            // When
            captchaService.clearIpFailure(ip);

            // Then
            verify(redisUtils).delete("login:ip:fail:8.8.8.8");
        }
    }

    // ============ Redis Key 格式汇总验证 ============

    @Nested
    @DisplayName("Redis Key 格式规范验证")
    class RedisKeyFormatTest {

        @Test
        @DisplayName("验证码 key 格式统一为 captcha:{identifier}")
        void captchaKeysFollowCaptchaPrefixPattern() {
            // Given
            String uuid = "test-uuid-123";
            when(redisUtils.get("captcha:" + uuid)).thenReturn("ABCD");

            // When - 新方法
            captchaService.verifyImageCaptcha(uuid, "ABCD");

            // Then - 确认 key 以 captcha: 为前缀
            verify(redisUtils).get(argThat(key -> key.startsWith("captcha:")));
            verify(redisUtils).delete(eq("captcha:" + uuid));
        }

        @Test
        @DisplayName("IP 锁定 key 格式为 login:ip:lock:{ip}")
        void ipLockKeyFormat() {
            // Given
            String ip = "1.2.3.4";
            when(redisUtils.hasKey("login:ip:lock:" + ip)).thenReturn(false);

            // When
            captchaService.checkIpLock(ip);

            // Then
            verify(redisUtils).hasKey(argThat(key ->
                    key.matches("login:ip:lock:\\d+\\.\\d+\\.\\d+\\.\\d+")));
        }

        @Test
        @DisplayName("IP 失败计数 key 格式为 login:ip:fail:{ip}")
        void ipFailKeyFormat() {
            // Given
            String ip = "5.6.7.8";
            when(redisUtils.increment("login:ip:fail:" + ip)).thenReturn(1L);

            // When
            captchaService.recordIpFailure(ip);

            // Then
            verify(redisUtils).increment(argThat(key ->
                    key.matches("login:ip:fail:\\d+\\.\\d+\\.\\d+\\.\\d+")));
        }
    }
}
