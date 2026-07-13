package com.zwinsight.common.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 集成测试基类。
 * <p>
 * 封装真实登录获取 JWT token 的逻辑，提供 {@code @BeforeAll} 和 {@code @AfterAll} 模板方法，
 * 子类继承后可直接使用 {@link #getAuthToken()} 获取有效 token 进行接口调用。
 * </p>
 * <p>
 * 登录流程与 verify-base.sh 保持一致：
 * <ol>
 *   <li>GET /api/v1/captcha/image → 获取验证码 UUID</li>
 *   <li>从 Redis captcha:{uuid} 读取验证码答案</li>
 *   <li>POST /api/v1/auth/login 携带 username/password/captchaCode/captchaUuid</li>
 *   <li>从响应中提取 token 字段</li>
 * </ol>
 * </p>
 *
 * @see TestConstants
 * @see TestDataCleaner
 * @see TestAuthenticationException
 */
@SpringBootTest
@ActiveProfiles("integration-test")
public abstract class IntegrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(IntegrationTestBase.class);

    /** 静态 token 缓存，避免重复登录 */
    private static volatile String cachedToken;

    /** 登录重试次数上限 */
    private static final int MAX_LOGIN_RETRY = 3;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    protected TestDataCleaner testDataCleaner;

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    // ==================== 认证相关 ====================

    /**
     * 获取有效的 JWT token。
     * <p>
     * 使用静态缓存避免每个测试类重复登录。首次调用时执行真实登录流程：
     * 获取验证码 → Redis 读取验证码答案 → 登录获取 token。
     * </p>
     *
     * @return 有效的 JWT token 字符串
     * @throws TestAuthenticationException 如果登录失败（含重试耗尽）
     */
    protected static String getAuthToken() {
        if (cachedToken != null && !cachedToken.isBlank()) {
            return cachedToken;
        }
        synchronized (IntegrationTestBase.class) {
            if (cachedToken != null && !cachedToken.isBlank()) {
                return cachedToken;
            }
            cachedToken = doLoginWithRetry();
            return cachedToken;
        }
    }

    /**
     * 带重试的登录逻辑，每次失败使用新验证码重试。
     */
    private static String doLoginWithRetry() {
        for (int i = 1; i <= MAX_LOGIN_RETRY; i++) {
            log.info("登录尝试 {}/{} ...", i, MAX_LOGIN_RETRY);
            try {
                String token = doLogin();
                if (token != null && !token.isBlank()) {
                    log.info("登录成功（token 长度: {}）", token.length());
                    return token;
                }
            } catch (Exception e) {
                log.warn("第 {} 次登录失败: {}", i, e.getMessage());
            }
        }
        throw new TestAuthenticationException(
                "登录在 " + MAX_LOGIN_RETRY + " 次重试内仍失败，请检查服务器连接和测试账号配置");
    }

    /**
     * 执行单次登录流程：获取验证码 → Redis 读取验证码答案 → 登录。
     *
     * @return JWT token，登录失败返回 null
     */
    private static String doLogin() {
        String apiBaseUrl = TestConstants.API_BASE_URL;

        // Step 1: 获取验证码 UUID
        String captchaUrl = apiBaseUrl + "/api/v1/captcha/image";
        ResponseEntity<String> captchaResponse;
        try {
            captchaResponse = restTemplate.getForEntity(captchaUrl, String.class);
        } catch (Exception e) {
            throw new TestAuthenticationException("获取验证码失败: " + captchaUrl, e);
        }

        if (!captchaResponse.getStatusCode().is2xxSuccessful() || captchaResponse.getBody() == null) {
            throw new TestAuthenticationException(
                    "获取验证码失败，HTTP 状态: " + captchaResponse.getStatusCode());
        }

        String uuid;
        try {
            JsonNode captchaJson = objectMapper.readTree(captchaResponse.getBody());
            JsonNode dataNode = captchaJson.get("data");
            if (dataNode == null) {
                throw new TestAuthenticationException("验证码响应缺少 data 字段: " + captchaResponse.getBody());
            }
            uuid = dataNode.get("uuid").asText();
        } catch (TestAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new TestAuthenticationException("解析验证码响应失败", e);
        }

        if (uuid == null || uuid.isBlank()) {
            throw new TestAuthenticationException("验证码响应中 uuid 为空");
        }

        log.debug("获取验证码 UUID: {}", uuid);

        // Step 2: 从 Redis 读取验证码答案
        String captchaCode = readCaptchaFromRedis(uuid);
        if (captchaCode == null || captchaCode.isBlank()) {
            throw new TestAuthenticationException(
                    "Redis 中未找到验证码，key=captcha:" + uuid + "（可能已过期或 Redis 不可达）");
        }

        log.debug("从 Redis 读取验证码答案（key=captcha:{}）", uuid);

        // Step 3: POST 登录
        String loginUrl = apiBaseUrl + TestConstants.LOGIN_PATH;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> loginBody = new HashMap<>();
        loginBody.put("username", TestConstants.TEST_USER);
        loginBody.put("password", TestConstants.TEST_PASS);
        loginBody.put("captchaCode", captchaCode);
        loginBody.put("captchaUuid", uuid);

        HttpEntity<Map<String, String>> loginRequest = new HttpEntity<>(loginBody, headers);
        ResponseEntity<String> loginResponse;
        try {
            loginResponse = restTemplate.postForEntity(loginUrl, loginRequest, String.class);
        } catch (Exception e) {
            throw new TestAuthenticationException("登录接口调用失败: " + loginUrl, e);
        }

        if (!loginResponse.getStatusCode().is2xxSuccessful() || loginResponse.getBody() == null) {
            throw new TestAuthenticationException(
                    "登录失败，HTTP 状态: " + loginResponse.getStatusCode());
        }

        // Step 4: 从响应中提取 token
        try {
            JsonNode loginJson = objectMapper.readTree(loginResponse.getBody());
            int code = loginJson.has("code") ? loginJson.get("code").asInt() : -1;
            if (code != 200) {
                String msg = loginJson.has("msg") ? loginJson.get("msg").asText() : "未知错误";
                throw new TestAuthenticationException("登录业务失败，code=" + code + ", msg=" + msg);
            }
            JsonNode dataNode = loginJson.get("data");
            if (dataNode == null) {
                throw new TestAuthenticationException("登录响应缺少 data 字段");
            }
            // 兼容 token / accessToken 两种字段名
            String token = null;
            if (dataNode.has("token")) {
                token = dataNode.get("token").asText();
            } else if (dataNode.has("accessToken")) {
                token = dataNode.get("accessToken").asText();
            }
            if (token == null || token.isBlank()) {
                throw new TestAuthenticationException("登录响应中未找到 token 字段，data: " + dataNode);
            }
            return token;
        } catch (TestAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new TestAuthenticationException("解析登录响应失败", e);
        }
    }

    /**
     * 从 Redis 读取验证码答案。
     * <p>
     * 使用新建的 StringRedisTemplate 直连服务器 Redis 读取 captcha:{uuid} 的值。
     * 由于此方法在静态上下文中被调用（登录发生在 Spring 容器启动之前或首次调用时），
     * 这里使用独立的 Redis 连接方式。
     * </p>
     *
     * @param uuid 验证码 UUID
     * @return 验证码答案字符串，未找到返回 null
     */
    private static String readCaptchaFromRedis(String uuid) {
        String redisKey = TestConstants.REDIS_CAPTCHA_PREFIX + uuid;
        try {
            // 使用独立 Redis 连接读取验证码
            // 由于是静态方法，直接通过 Lettuce 连接 Redis
            org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory factory =
                    new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(
                            TestConstants.SERVER_HOST, 6379);
            factory.afterPropertiesSet();
            try {
                StringRedisTemplate redisTemplate = new StringRedisTemplate(factory);
                redisTemplate.afterPropertiesSet();
                String value = redisTemplate.opsForValue().get(redisKey);
                // Redis 中的值可能带双引号，需要清理
                if (value != null) {
                    value = value.replace("\"", "").trim();
                }
                return value;
            } finally {
                factory.destroy();
            }
        } catch (Exception e) {
            log.warn("读取 Redis 验证码失败（key={}）: {}", redisKey, e.getMessage());
            return null;
        }
    }

    // ==================== 生命周期模板方法 ====================

    /**
     * 测试类启动前准备认证 token。
     * <p>
     * 子类可通过 {@code @BeforeAll} 调用此方法完成认证准备。
     * 由于 token 有静态缓存，多个测试类共享同一 token 不会重复登录。
     * </p>
     */
    protected static void setupAuthentication() {
        log.info("====== IntegrationTestBase: 准备认证 token ======");
        getAuthToken();
        log.info("====== IntegrationTestBase: 认证准备完成 ======");
    }

    /**
     * 测试类结束后执行数据清理。
     * <p>
     * 子类可通过 {@code @AfterAll} 调用此方法清理测试数据。
     * 调用 {@link TestDataCleaner#cleanAll()} 物理删除 tenant_id=9999 的所有数据。
     * </p>
     *
     * @param cleaner TestDataCleaner 实例（由子类注入后传入）
     */
    protected static void cleanupTestData(TestDataCleaner cleaner) {
        log.info("====== IntegrationTestBase: 开始清理测试数据 ======");
        try {
            if (cleaner != null) {
                cleaner.cleanAll();
            } else {
                log.warn("TestDataCleaner 为 null，跳过数据清理（请确认 Spring 容器已正常初始化）");
            }
        } catch (Exception e) {
            log.error("测试数据清理失败: {}", e.getMessage(), e);
        }
        log.info("====== IntegrationTestBase: 测试数据清理完成 ======");
    }

    /**
     * 清除缓存的 token，下次调用 getAuthToken() 将重新登录。
     */
    protected static void clearTokenCache() {
        cachedToken = null;
        log.info("已清除缓存 token");
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建带有 Authorization 头的 HttpHeaders。
     *
     * @return 包含 Bearer token 的 HttpHeaders
     */
    protected HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getAuthToken());
        return headers;
    }

    /**
     * 获取 RestTemplate 实例，用于子类发起 HTTP 请求。
     *
     * @return RestTemplate 实例
     */
    protected RestTemplate getRestTemplate() {
        return restTemplate;
    }
}
