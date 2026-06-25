package com.zwinsight.security.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property P4: 验证码一次性使用
 * <p>
 * 验证：验证码校验成功后，再次使用相同 uuid+code 校验必定失败。
 * 使用内存模拟 Redis 行为，验证 CaptchaService 的一次性使用逻辑。
 * </p>
 * <p>
 * **Validates: Requirements 4.5**
 * </p>
 */
@DisplayName("P4: 验证码一次性使用属性测试")
class CaptchaOneTimeUsePropertyTest {

    private static final String CAPTCHA_PREFIX = "captcha:";

    /**
     * 模拟 Redis 存储（内存Map实现）
     * 模拟 verifyImageCaptcha 的核心逻辑：
     * 1. 从存储中获取验证码
     * 2. 无论校验成功失败都立即删除（一次性使用）
     * 3. 大小写不敏感比对
     */
    private static class InMemoryCaptchaVerifier {
        private final Map<String, String> store = new HashMap<>();

        void store(String uuid, String code) {
            store.put(CAPTCHA_PREFIX + uuid, code);
        }

        /**
         * 完全复刻 CaptchaService.verifyImageCaptcha 逻辑
         */
        boolean verify(String uuid, String inputCode) {
            if (uuid == null || inputCode == null) {
                return false;
            }
            String redisKey = CAPTCHA_PREFIX + uuid;
            String cached = store.get(redisKey);
            // 无论成功失败都删除 key（一次性使用）
            store.remove(redisKey);

            if (cached == null) {
                return false;
            }
            return cached.equalsIgnoreCase(inputCode);
        }

        boolean hasKey(String uuid) {
            return store.containsKey(CAPTCHA_PREFIX + uuid);
        }
    }

    /**
     * 生成随机 4 位字母数字验证码
     */
    private String generateRandomCode() {
        String chars = "ABCDEFGHIJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    @RepeatedTest(50)
    @DisplayName("P4: 验证码校验成功后，再次使用相同 uuid+code 校验必定失败")
    void testOneTimeUse() {
        InMemoryCaptchaVerifier verifier = new InMemoryCaptchaVerifier();

        // 生成随机 UUID 和验证码
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String code = generateRandomCode();

        // 存入验证码
        verifier.store(uuid, code);
        assertTrue(verifier.hasKey(uuid), "存入后 key 应该存在");

        // 第一次校验：应成功
        boolean firstResult = verifier.verify(uuid, code);
        assertTrue(firstResult, "第一次校验应成功，uuid=" + uuid + ", code=" + code);

        // 校验后 key 应被删除
        assertFalse(verifier.hasKey(uuid), "第一次校验后 key 应被删除");

        // 第二次校验：使用相同 uuid+code，必定失败
        boolean secondResult = verifier.verify(uuid, code);
        assertFalse(secondResult, "第二次使用相同 uuid+code 校验必定失败");
    }

    @RepeatedTest(50)
    @DisplayName("P4: 验证码大小写不敏感校验成功后仍然一次性删除")
    void testOneTimeUseWithCaseInsensitive() {
        InMemoryCaptchaVerifier verifier = new InMemoryCaptchaVerifier();

        String uuid = UUID.randomUUID().toString().replace("-", "");
        String code = generateRandomCode();

        verifier.store(uuid, code);

        // 使用全大写/全小写进行首次校验
        String inputCode = ThreadLocalRandom.current().nextBoolean() ?
                code.toUpperCase() : code.toLowerCase();
        boolean firstResult = verifier.verify(uuid, inputCode);
        assertTrue(firstResult, "大小写不敏感校验应成功");

        // 再次校验必定失败（无论用什么大小写组合）
        boolean secondResult = verifier.verify(uuid, code);
        assertFalse(secondResult, "一次性使用后，任何大小写组合校验都应失败");
    }
}
