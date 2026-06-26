package com.zwinsight.common.desensitize;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.assertj.core.api.Assertions;

/**
 * DesensitizeUtil 属性测试
 * <p>
 * 使用 jqwik 框架验证脱敏工具类的三个核心属性：
 * <ul>
 *   <li>Property 1: 满足最低长度要求时，输出格式严格符合规格</li>
 *   <li>Property 2: 输入长度不足时，全部替换为等长星号</li>
 *   <li>Property 3: null/空字符串原样返回</li>
 * </ul>
 */
class DesensitizeUtilPropertyTest {

    // ==================== Property 1: 脱敏掩码格式正确性 ====================

    /**
     * Property 1 - PHONE: 满足 minLen(7) 的输入，输出为 前3 + "****" + 后4
     * <p>
     * **Validates: Requirements 1.4, 1.5, 1.6, 1.7, 1.8**
     */
    @Property(tries = 100)
    @Label("Property 1 - PHONE: result == value[0:3] + '****' + value[-4:]")
    void phoneMaskFormat(@ForAll("phoneStrings") String value) {
        String result = DesensitizeUtil.desensitize(value, DesensitizeType.PHONE);

        String expectedPrefix = value.substring(0, 3);
        String expectedSuffix = value.substring(value.length() - 4);
        String expected = expectedPrefix + "****" + expectedSuffix;

        Assertions.assertThat(result).isEqualTo(expected);
    }

    /**
     * Property 1 - ID_CARD: 满足 minLen(7) 的输入，输出为 前3 + 星号(len-7) + 后4
     * <p>
     * **Validates: Requirements 1.4, 1.5, 1.6, 1.7, 1.8**
     */
    @Property(tries = 100)
    @Label("Property 1 - ID_CARD: result == value[0:3] + '*'.repeat(len-7) + value[-4:]")
    void idCardMaskFormat(@ForAll("idCardStrings") String value) {
        String result = DesensitizeUtil.desensitize(value, DesensitizeType.ID_CARD);

        String expectedPrefix = value.substring(0, 3);
        String expectedMiddle = "*".repeat(value.length() - 7);
        String expectedSuffix = value.substring(value.length() - 4);
        String expected = expectedPrefix + expectedMiddle + expectedSuffix;

        Assertions.assertThat(result).isEqualTo(expected);
    }

    /**
     * Property 1 - BANK_CARD: 满足 minLen(8) 的输入，输出为 前4 + 星号(len-8) + 后4
     * <p>
     * **Validates: Requirements 1.4, 1.5, 1.6, 1.7, 1.8**
     */
    @Property(tries = 100)
    @Label("Property 1 - BANK_CARD: result == value[0:4] + '*'.repeat(len-8) + value[-4:]")
    void bankCardMaskFormat(@ForAll("bankCardStrings") String value) {
        String result = DesensitizeUtil.desensitize(value, DesensitizeType.BANK_CARD);

        String expectedPrefix = value.substring(0, 4);
        String expectedMiddle = "*".repeat(value.length() - 8);
        String expectedSuffix = value.substring(value.length() - 4);
        String expected = expectedPrefix + expectedMiddle + expectedSuffix;

        Assertions.assertThat(result).isEqualTo(expected);
    }

    /**
     * Property 1 - EMAIL: 合法邮箱（username长度>=2），输出为 首字母 + 星号 + @域名
     * <p>
     * **Validates: Requirements 1.4, 1.5, 1.6, 1.7, 1.8**
     */
    @Property(tries = 100)
    @Label("Property 1 - EMAIL: result == value[0] + '*'.repeat(usernameLen-1) + '@' + domain")
    void emailMaskFormat(@ForAll("validEmails") String value) {
        String result = DesensitizeUtil.desensitize(value, DesensitizeType.EMAIL);

        int atIndex = value.indexOf('@');
        String username = value.substring(0, atIndex);
        String domain = value.substring(atIndex); // 包含 @
        String expected = username.charAt(0) + "*".repeat(username.length() - 1) + domain;

        Assertions.assertThat(result).isEqualTo(expected);
    }

    /**
     * Property 1 - ADDRESS: 满足 minLen(6) 的输入，输出为 前6 + 星号(len-6)
     * <p>
     * **Validates: Requirements 1.4, 1.5, 1.6, 1.7, 1.8**
     */
    @Property(tries = 100)
    @Label("Property 1 - ADDRESS: result == value[0:6] + '*'.repeat(len-6)")
    void addressMaskFormat(@ForAll("addressStrings") String value) {
        String result = DesensitizeUtil.desensitize(value, DesensitizeType.ADDRESS);

        String expectedPrefix = value.substring(0, 6);
        String expectedMask = "*".repeat(value.length() - 6);
        String expected = expectedPrefix + expectedMask;

        Assertions.assertThat(result).isEqualTo(expected);
    }

    // ==================== Property 2: 短输入全星号替代 ====================

    /**
     * Property 2 - PHONE: 长度 < minLen(7) 的非空输入，输出为等长星号
     * <p>
     * **Validates: Requirements 1.10**
     */
    @Property(tries = 100)
    @Label("Property 2 - PHONE: short input → all stars")
    void phoneShortInputAllStars(@ForAll("shortPhoneStrings") String value) {
        String result = DesensitizeUtil.desensitize(value, DesensitizeType.PHONE);

        Assertions.assertThat(result).isEqualTo("*".repeat(value.length()));
    }

    /**
     * Property 2 - ID_CARD: 长度 < minLen(7) 的非空输入，输出为等长星号
     * <p>
     * **Validates: Requirements 1.10**
     */
    @Property(tries = 100)
    @Label("Property 2 - ID_CARD: short input → all stars")
    void idCardShortInputAllStars(@ForAll("shortIdCardStrings") String value) {
        String result = DesensitizeUtil.desensitize(value, DesensitizeType.ID_CARD);

        Assertions.assertThat(result).isEqualTo("*".repeat(value.length()));
    }

    /**
     * Property 2 - BANK_CARD: 长度 < minLen(8) 的非空输入，输出为等长星号
     * <p>
     * **Validates: Requirements 1.10**
     */
    @Property(tries = 100)
    @Label("Property 2 - BANK_CARD: short input → all stars")
    void bankCardShortInputAllStars(@ForAll("shortBankCardStrings") String value) {
        String result = DesensitizeUtil.desensitize(value, DesensitizeType.BANK_CARD);

        Assertions.assertThat(result).isEqualTo("*".repeat(value.length()));
    }

    /**
     * Property 2 - EMAIL: 不含 '@' 或 '@' 在首位的输入，输出为等长星号
     * <p>
     * **Validates: Requirements 1.10**
     */
    @Property(tries = 100)
    @Label("Property 2 - EMAIL: invalid email (no @ or @ at start) → all stars")
    void emailShortInputAllStars(@ForAll("shortEmailStrings") String value) {
        String result = DesensitizeUtil.desensitize(value, DesensitizeType.EMAIL);

        Assertions.assertThat(result).isEqualTo("*".repeat(value.length()));
    }

    /**
     * Property 2 - ADDRESS: 长度 < minLen(6) 的非空输入，输出为等长星号
     * <p>
     * **Validates: Requirements 1.10**
     */
    @Property(tries = 100)
    @Label("Property 2 - ADDRESS: short input → all stars")
    void addressShortInputAllStars(@ForAll("shortAddressStrings") String value) {
        String result = DesensitizeUtil.desensitize(value, DesensitizeType.ADDRESS);

        Assertions.assertThat(result).isEqualTo("*".repeat(value.length()));
    }

    // ==================== Property 3: 空值脱敏恒等性 ====================

    /**
     * Property 3: desensitize(null, anyType) == null
     * <p>
     * **Validates: Requirements 1.9**
     */
    @Property(tries = 100)
    @Label("Property 3 - null input → null output for any type")
    void nullInputReturnsNull(@ForAll("desensitizeTypes") DesensitizeType type) {
        String result = DesensitizeUtil.desensitize(null, type);

        Assertions.assertThat(result).isNull();
    }

    /**
     * Property 3: desensitize("", anyType) == ""
     * <p>
     * **Validates: Requirements 1.9**
     */
    @Property(tries = 100)
    @Label("Property 3 - empty input → empty output for any type")
    void emptyInputReturnsEmpty(@ForAll("desensitizeTypes") DesensitizeType type) {
        String result = DesensitizeUtil.desensitize("", type);

        Assertions.assertThat(result).isEmpty();
    }

    // ==================== Providers ====================

    /**
     * 生成满足 PHONE minLen(7) 的随机字符串，长度 7~20
     */
    @Provide
    Arbitrary<String> phoneStrings() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .ofMinLength(7)
                .ofMaxLength(20);
    }

    /**
     * 生成满足 ID_CARD minLen(7) 的随机字符串，长度 7~20
     */
    @Provide
    Arbitrary<String> idCardStrings() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .withCharRange('A', 'Z')
                .ofMinLength(7)
                .ofMaxLength(20);
    }

    /**
     * 生成满足 BANK_CARD minLen(8) 的随机字符串，长度 8~20
     */
    @Provide
    Arbitrary<String> bankCardStrings() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .ofMinLength(8)
                .ofMaxLength(20);
    }

    /**
     * 生成合法邮箱：username(长度>=2) + "@" + domain
     * username 和 domain 均使用字母数字，确保格式可预测
     */
    @Provide
    Arbitrary<String> validEmails() {
        Arbitrary<String> usernames = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(2)
                .ofMaxLength(10);
        Arbitrary<String> domains = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3)
                .ofMaxLength(8);
        return Combinators.combine(usernames, domains)
                .as((user, domain) -> user + "@" + domain + ".com");
    }

    /**
     * 生成满足 ADDRESS minLen(6) 的随机字符串，长度 6~30
     * 使用中文字符模拟真实地址场景
     */
    @Provide
    Arbitrary<String> addressStrings() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('\u4e00', '\u9fff') // 中文字符
                .ofMinLength(6)
                .ofMaxLength(30);
    }

    /**
     * PHONE 短输入：长度 1~6（< minLen 7）
     */
    @Provide
    Arbitrary<String> shortPhoneStrings() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(6);
    }

    /**
     * ID_CARD 短输入：长度 1~6（< minLen 7）
     */
    @Provide
    Arbitrary<String> shortIdCardStrings() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(6);
    }

    /**
     * BANK_CARD 短输入：长度 1~7（< minLen 8）
     */
    @Provide
    Arbitrary<String> shortBankCardStrings() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(7);
    }

    /**
     * EMAIL 短输入：不含 '@' 的字符串或 '@' 在首位的字符串（触发全星号逻辑）
     * 为确保可预测性，生成纯字母数字字符串（不含 @）
     */
    @Provide
    Arbitrary<String> shortEmailStrings() {
        // 生成不含 @ 的非空字符串
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(10);
    }

    /**
     * ADDRESS 短输入：长度 1~5（< minLen 6）
     */
    @Provide
    Arbitrary<String> shortAddressStrings() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('\u4e00', '\u9fff')
                .ofMinLength(1)
                .ofMaxLength(5);
    }

    /**
     * 生成所有脱敏类型
     */
    @Provide
    Arbitrary<DesensitizeType> desensitizeTypes() {
        return Arbitraries.of(DesensitizeType.values());
    }
}
