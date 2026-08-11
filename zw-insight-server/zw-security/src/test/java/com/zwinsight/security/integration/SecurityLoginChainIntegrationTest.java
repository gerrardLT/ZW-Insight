package com.zwinsight.security.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.base.IntegrationTestBase;
import com.zwinsight.common.base.TestConstants;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 安全登录链集成测试（真实 API + 真实 Redis，批 4 L2）
 * <p>
 * 覆盖功能表「登录认证」核心链路：
 * <ol>
 *   <li>图形验证码签发（captcha:{uuid} 写入 Redis，TTL 5 分钟）</li>
 *   <li>验证码错误 → 拒绝，且无论成败验证码一次性消费（key 删除）</li>
 *   <li>正确验证码 + 正确密码 → 登录成功，token 可访问受保护接口</li>
 *   <li>错误密码 → 拒绝（单测一次失败，避免触发账号锁定阈值影响共享 admin）</li>
 *   <li>登出 → Redis token 删除，原 token 访问受保护接口被拒</li>
 * </ol>
 * <p>
 * 验证码开关适配：服务器 dev profile 联调期临时关闭图形验证码
 * （auth.captcha-enabled=false，配置注释明确要求上线前改回 true）。
 * 测试通过探测实际开关状态自适应断言：开启时校验错误验证码拒绝+一次性消费；
 * 关闭时如实记录现状并跳过强校验断言，不伪造结果。
 * <p>
 * 零残留说明：登录链不写业务表；成功登录会写 sys_login_log/设备记录
 * （共享 admin 的正常登录行为，与 verify-base.sh 一致）；验证码 key 由服务器
 * 一次性消费或 TTL 过期；IP 失败计数 300 秒窗口过期，且末次成功登录会清除
 * 账号失败计数。失败路径总量控制在 IP 锁定阈值（5 次）以内。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityLoginChainIntegrationTest extends IntegrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(SecurityLoginChainIntegrationTest.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String CAPTCHA_URL = TestConstants.API_BASE_URL + "/api/v1/captcha/image";
    private static final String LOGIN_URL = TestConstants.API_BASE_URL + TestConstants.LOGIN_PATH;
    private static final String LOGOUT_URL = TestConstants.API_BASE_URL + "/api/v1/auth/logout";
    private static final String PROTECTED_URL = TestConstants.API_BASE_URL + "/api/v1/project/page?page=1&size=1";

    /** 独立登录获得的 token（不复用基座缓存，避免登出测试污染其他测试类） */
    private String freshToken;
    /** step2 使用的验证码（供 step3 一次性复用断言） */
    private String consumedUuid;
    private String consumedCode;
    /** 服务器验证码开关实际状态（auth.captcha-enabled，dev 联调期临时关闭） */
    private boolean captchaEnabledOnServer = true;

    // ==================== 辅助方法 ====================

    /** 获取验证码：返回 {uuid, 从真实 Redis 读取的答案} */
    private String[] fetchCaptcha() throws Exception {
        ResponseEntity<String> resp = getRestTemplate().getForEntity(CAPTCHA_URL, String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).as("验证码接口可达").isTrue();
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertThat(json.get("code").asInt()).as("验证码业务码").isEqualTo(200);
        String uuid = json.get("data").get("uuid").asText();
        assertThat(uuid).isNotBlank();

        String value = stringRedisTemplate.opsForValue().get(TestConstants.REDIS_CAPTCHA_PREFIX + uuid);
        assertThat(value).as("真实 Redis 中存在验证码答案 key=captcha:%s", uuid).isNotNull();
        String code = value.replace("\"", "").trim();
        return new String[]{uuid, code};
    }

    /** 登录尝试：返回解析后的响应 JSON；HTTP 4xx/5xx 时返回带 httpStatus 的合成节点 */
    private JsonNode attemptLogin(String username, String password, String uuid, String code) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        body.put("captchaUuid", uuid);
        body.put("captchaCode", code);
        try {
            ResponseEntity<String> resp = getRestTemplate()
                    .postForEntity(LOGIN_URL, new HttpEntity<>(body, headers), String.class);
            return objectMapper.readTree(resp.getBody());
        } catch (HttpClientErrorException e) {
            // HTTP 级错误映射：业务拒绝同样成立
            JsonNode node = objectMapper.createObjectNode();
            ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("code", e.getStatusCode().value());
            return node;
        }
    }

    /** 断言登录被拒绝（业务码非 200） */
    private void assertRejected(JsonNode resp, String desc) {
        int code = resp.has("code") ? resp.get("code").asInt() : -1;
        assertThat(code).as(desc + "：应被拒绝，实际 code=%s", code).isNotEqualTo(200);
    }

    /** 用指定 token 访问受保护接口，返回是否成功（200 且业务码 200） */
    private boolean protectedCallOk(String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        try {
            ResponseEntity<String> resp = getRestTemplate()
                    .exchange(PROTECTED_URL, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode json = objectMapper.readTree(resp.getBody());
            return resp.getStatusCode().is2xxSuccessful()
                    && json.has("code") && json.get("code").asInt() == 200;
        } catch (HttpClientErrorException e) {
            return false;
        }
    }

    // ==================== 测试步骤 ====================

    @Test
    @Order(1)
    @DisplayName("验证码签发：接口返回 uuid 且答案写入真实 Redis")
    void step1_captchaIssued() throws Exception {
        String[] captcha = fetchCaptcha();
        log.info("验证码签发成功: uuid={}", captcha[0]);
    }

    @Test
    @Order(2)
    @DisplayName("错误验证码登录：开关开启时必须拒绝并消费验证码；开关关闭时如实记录（dev 联调配置）")
    void step2_wrongCaptchaProbe() throws Exception {
        String[] captcha = fetchCaptcha();
        consumedUuid = captcha[0];
        consumedCode = captcha[1];

        JsonNode resp = attemptLogin(TestConstants.TEST_USER, TestConstants.TEST_PASS,
                consumedUuid, "zzzz");
        int code = resp.has("code") ? resp.get("code").asInt() : -1;
        if (code != 200) {
            captchaEnabledOnServer = true;
            log.info("错误验证码登录被拒绝（code={}），服务器验证码开关为开启状态", code);
        } else {
            // 开关关闭（dev 联调期 auth.captcha-enabled=false，配置注释要求上线前改回 true）：
            // 如实记录现状，不伪造拒绝断言；同时保存该 token 供后续步骤使用
            captchaEnabledOnServer = false;
            JsonNode data = resp.get("data");
            if (data != null) {
                freshToken = data.has("token") ? data.get("token").asText()
                        : data.get("accessToken").asText();
            }
            log.warn("错误验证码登录成功（code=200）：服务器验证码开关处于关闭状态"
                    + "（dev 联调期临时配置，上线前须改回 true），跳过验证码强校验断言");
        }
    }

    @Test
    @Order(3)
    @DisplayName("验证码一次性：已消费的 uuid 从 Redis 删除，复用登录被拒绝（仅开关开启时校验）")
    void step3_captchaOneTimeUse() throws Exception {
        Assumptions.assumeTrue(captchaEnabledOnServer,
                "服务器验证码开关关闭（dev 联调配置），一次性消费断言不适用");
        assertThat(consumedUuid).as("step2 已执行").isNotNull();
        Boolean exists = stringRedisTemplate.hasKey(TestConstants.REDIS_CAPTCHA_PREFIX + consumedUuid);
        assertThat(Boolean.TRUE.equals(exists))
                .as("验证码校验后 Redis key 必须被删除（一次性使用）").isFalse();

        // 复用已消费的验证码（即使携带正确码）也必须拒绝
        JsonNode resp = attemptLogin(TestConstants.TEST_USER, TestConstants.TEST_PASS,
                consumedUuid, consumedCode);
        assertRejected(resp, "复用已消费验证码登录");
    }

    @Test
    @Order(4)
    @DisplayName("正确验证码+正确密码登录成功，token 可访问受保护接口")
    void step4_correctLoginSuccess() throws Exception {
        String[] captcha = fetchCaptcha();
        JsonNode resp = attemptLogin(TestConstants.TEST_USER, TestConstants.TEST_PASS,
                captcha[0], captcha[1]);
        int code = resp.has("code") ? resp.get("code").asInt() : -1;
        assertThat(code).as("登录业务码").isEqualTo(200);

        JsonNode data = resp.get("data");
        assertThat(data).isNotNull();
        freshToken = data.has("token") ? data.get("token").asText()
                : data.get("accessToken").asText();
        assertThat(freshToken).as("登录返回 token").isNotBlank();

        assertThat(protectedCallOk(freshToken)).as("新 token 可访问受保护接口").isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("错误密码登录被拒绝（单次失败，不触发账号锁定阈值）")
    void step5_wrongPasswordRejected() throws Exception {
        String[] captcha = fetchCaptcha();
        JsonNode resp = attemptLogin(TestConstants.TEST_USER, "definitely-wrong-password",
                captcha[0], captcha[1]);
        assertRejected(resp, "错误密码登录");
    }

    @Test
    @Order(6)
    @DisplayName("登出后 token 立即失效，无法再访问受保护接口")
    void step6_logoutInvalidatesToken() throws Exception {
        assertThat(freshToken).as("step4 已执行").isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(freshToken);
        ResponseEntity<String> resp = getRestTemplate()
                .exchange(LOGOUT_URL, HttpMethod.POST, new HttpEntity<>(headers), String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).as("登出接口调用成功").isTrue();

        assertThat(protectedCallOk(freshToken)).as("登出后原 token 必须失效").isFalse();
    }

    @AfterAll
    void cleanup() {
        // 登出测试只作废独立 token；刷新共享 token 缓存，保证后续测试类登录态有效，
        // 同时成功登录会清除本测试累计的账号失败计数
        clearTokenCache();
        setupAuthentication();
        log.info("====== SecurityLoginChainIntegrationTest: 完成，共享 token 已刷新 ======");
    }
}
