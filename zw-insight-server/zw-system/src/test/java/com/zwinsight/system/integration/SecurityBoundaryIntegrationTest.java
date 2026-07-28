package com.zwinsight.system.integration;

import com.zwinsight.common.base.IntegrationTestBase;
import com.zwinsight.common.base.TestConstants;
import com.zwinsight.common.base.TestDataCleaner;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 安全边界集成测试（对应审计报告 intended-vs-implemented-audit-2026-07-27.md 第七章 TS-1~TS-5）
 * <p>
 * 通过 HTTP 黑盒方式验证服务端安全强制逻辑，测试数据使用 tenant_id=9999 隔离，
 * 完成后经 {@link TestDataCleaner} 物理清理。所有前置条件不满足时以
 * {@link Assumptions#assumeTrue} 优雅跳过，避免因环境问题误报失败。
 * </p>
 * <p>
 * 用例状态说明（与审计发现严重级别一致）：
 * <ul>
 *   <li>TS-2（Redis 令牌白名单）— <b>启用</b>：当前即应通过，作为正向回归守卫。</li>
 *   <li>TS-5（审批处理人校验）— <b>启用</b>：守卫已修复的越权（原 Critical #2）防止倒退。</li>
 *   <li>TS-1（接口级功能权限）— <b>@Disabled</b>：Critical #1 未修复，编码期望的安全行为，修复后移除注解即转为守卫。</li>
 *   <li>TS-3（数据权限覆盖）— <b>@Disabled</b>：Major #4 未修复。</li>
 *   <li>TS-4（登录验证码）— <b>@Disabled</b>：Minor #5，dev 档验证码关闭。</li>
 * </ul>
 * </p>
 *
 * @see IntegrationTestBase
 * @see TestDataCleaner
 */
@DisplayName("安全边界集成测试 - 越权/令牌/权限强制")
@TestMethodOrder(OrderAnnotation.class)
class SecurityBoundaryIntegrationTest extends IntegrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(SecurityBoundaryIntegrationTest.class);

    // ==================== API 路径 ====================
    private static final String PROJECT_URL = TestConstants.API_BASE_URL + "/api/v1/project";
    private static final String CONTRACT_URL = TestConstants.API_BASE_URL + "/api/v1/contract";
    private static final String USER_URL = TestConstants.API_BASE_URL + "/api/v1/system/user";
    private static final String ROLE_URL = TestConstants.API_BASE_URL + "/api/v1/system/role";
    private static final String APPROVAL_URL = TestConstants.API_BASE_URL + "/api/v1/workflow/approval";

    /** 源码中公开的硬编码 JWT 密钥（JwtUtils L16 / application-dev.yml L65），用于模拟"离线伪造"。 */
    private static final String PUBLIC_JWT_SECRET = "ZwInsight2024SecretKeyForJwtTokenGeneration";

    /** 低权测试用户登录口令（满足常见复杂度）。 */
    private static final String LOW_PRIV_PASSWORD = "Test@123456";

    // ==================== 跨方法共享状态 ====================
    private static TestDataCleaner cleanerRef;
    private static Long createdUserId;
    private static String createdUsername;

    // ==================== 生命周期 ====================

    @BeforeAll
    static void setup() {
        setupAuthentication();
        log.info("====== SecurityBoundaryIntegrationTest: 认证完成，开始安全边界测试 ======");
    }

    @AfterAll
    static void tearDown() {
        // 兜底删除测试期创建的低权用户（防止 admin 非 9999 租户时残留）
        if (createdUserId != null) {
            try {
                HttpHeaders h = new HttpHeaders();
                h.setBearerAuth(getAuthToken());
                new RestTemplate().exchange(USER_URL + "/" + createdUserId,
                        HttpMethod.DELETE, new HttpEntity<>(h), Map.class);
                log.info("已删除测试低权用户 userId={}", createdUserId);
            } catch (Exception e) {
                log.warn("删除测试低权用户失败（将由 TestDataCleaner 兜底）: {}", e.getMessage());
            }
        }
        cleanupTestData(cleanerRef);
        log.info("====== SecurityBoundaryIntegrationTest: 测试数据清理完成 ======");
    }

    @BeforeEach
    void captureCleanerRef() {
        if (cleanerRef == null) {
            cleanerRef = testDataCleaner;
        }
    }

    // ==================== TS-2：Redis 令牌白名单（启用） ====================

    @Test
    @Order(2)
    @DisplayName("TS-2 离线伪造 Token 应被拒 - 验签合法但不在 Redis 白名单 → 401")
    void ts2_forgedTokenShouldBeRejected() {
        // 用公开密钥离线签发一枚"签名合法"但从未经过登录（不在 Redis）的 Token
        String forged = forgeToken(999001L, TestConstants.TEST_TENANT_ID, "attacker");

        int code = effectiveCode(PROJECT_URL + "/page?page=1&size=1", HttpMethod.GET, null, forged);

        // 期望：AuthInterceptor.validateToken 因 hasKey(token:{token})=false 短路拒绝 → HTTP 401
        assertThat(code)
                .as("离线伪造 Token（不在 Redis 白名单）必须被拒为 401，实际=%d。"
                        + "若返回 200 说明退化为仅验签、未校验白名单，需立即修复", code)
                .isEqualTo(401);
        log.info("TS-2 通过：离线伪造 Token 被拒 (code={})", code);
    }

    // ==================== TS-5：审批处理人校验（启用，防倒退回归） ====================

    @Test
    @Order(5)
    @DisplayName("TS-5 审批越权防倒退 - 非处理人 complete 他人任务 → 业务码 403")
    void ts5_nonAssigneeCannotCompleteOthersTask() {
        // 1) admin 创建项目 + 施工合同并提交审批，触发 Flowable 流程
        Long projectId = createProjectAsAdmin();
        Assumptions.assumeTrue(projectId != null, "前置：创建测试项目失败，跳过");

        Long contractId = createContractAsAdmin(projectId);
        Assumptions.assumeTrue(contractId != null, "前置：创建施工合同失败，跳过");

        boolean submitted = submitContractAsAdmin(contractId);
        Assumptions.assumeTrue(submitted, "前置：合同提交审批失败（服务器可能未部署流程定义），跳过");

        // 2) 从 admin 待办中取得任务 ID（该任务处理人为 admin）
        String taskId = findFirstTodoTaskId(contractId);
        Assumptions.assumeTrue(taskId != null, "前置：未获取到人工审批任务（流程可能自动通过），跳过");

        // 3) 创建低权用户并登录，模拟"拿到 taskId 的非处理人"
        Long lowUserId = ensureLowPrivUser();
        Assumptions.assumeTrue(lowUserId != null, "前置：创建低权用户失败，跳过");
        String lowToken = loginAs(createdUsername, LOW_PRIV_PASSWORD);
        Assumptions.assumeTrue(lowToken != null, "前置：低权用户登录失败，跳过");

        // 4) 低权用户尝试 complete admin 的任务 —— 应被 assertTaskAssignee 拒绝
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", taskId);
        body.put("comment", "越权测试（应被拒绝）");

        BizResult r = call(APPROVAL_URL + "/complete", HttpMethod.POST, body, lowToken);

        // BusinessException(403) 经 GlobalExceptionHandler 返回 HTTP 200 + body.code=403
        assertThat(r.code)
                .as("非处理人 complete 他人审批任务应返回业务码 403，实际=%d，message=%s", r.code, r.message)
                .isEqualTo(403);
        if (r.message != null) {
            // 任务可能已指定处理人（“无权操作他人审批任务”）或为候选组未签收（“尚未签收”），
            // 两者都是 assertTaskAssignee 的正确安全拒绝，放宽以避免误报。
            assertThat(r.message)
                    .as("拒绝原因应指向处理人校验（无权/未签收）")
                    .containsAnyOf("无权操作他人审批任务", "尚未签收");
        }
        log.info("TS-5 通过：非处理人越权被拒 (code={}, msg={})", r.code, r.message);
    }

    // ==================== TS-1：接口级功能权限（禁用，待 Critical #1 修复后启用） ====================

    @Test
    @Order(1)
    @DisplayName("TS-1 垂直越权 - 低权用户调用 system/* 与项目删除高危接口应 403")
    void ts1_lowPrivUserCannotCallHighRiskEndpoints() {
        Long lowUserId = ensureLowPrivUser();
        Assumptions.assumeTrue(lowUserId != null, "前置：创建低权用户失败，跳过");
        String lowToken = loginAs(createdUsername, LOW_PRIV_PASSWORD);
        Assumptions.assumeTrue(lowToken != null, "前置：低权用户登录失败，跳过");

        // 1) 重置他人密码
        Map<String, Object> pwd = Map.of("newPassword", "Hacked@123456");
        int c1 = effectiveCode(USER_URL + "/" + lowUserId + "/reset-password", HttpMethod.PUT, pwd, lowToken);
        // 2) 篡改角色数据范围为 ALL（roleId=1 作为示例）
        Map<String, Object> scope = Map.of("dataScope", "ALL");
        int c2 = effectiveCode(ROLE_URL + "/1/data-scope", HttpMethod.PUT, scope, lowToken);
        // 3) 给自己追加角色（提权）
        int c3 = effectiveCode(USER_URL + "/" + lowUserId + "/roles", HttpMethod.PUT, List.of(1), lowToken);

        assertThat(c1).as("低权用户重置他人密码应 403").isEqualTo(403);
        assertThat(c2).as("低权用户改数据范围应 403").isEqualTo(403);
        assertThat(c3).as("低权用户自我提权应 403").isEqualTo(403);
    }

    // ==================== TS-3：行级数据权限覆盖（禁用，待 Major #4 修复后启用） ====================

    @Test
    @Order(3)
    @Disabled("Major #4 未修复：machine/labor 等模块 Mapper 未标注 @DataPermission。补注解后移除此注解并补两项目业务数据后启用。")
    @DisplayName("TS-3 跨项目数据泄露 - PROJECT 范围用户经未标注模块不应看到他项目数据")
    void ts3_unannotatedModuleShouldRespectProjectScope() {
        // 完整断言需在 P_A、P_B 两项目下预置未标注模块（如机械台账）业务数据，
        // 并将低权用户数据范围设为 PROJECT 且仅归属 P_A。补注解修复后启用本用例：
        // 断言：机械模块列表接口返回结果不含 P_B 的记录（projectId 全部等于 P_A）。
        Assumptions.assumeTrue(false, "TS-3 需先补齐两项目业务数据与 PROJECT 范围用户，详见审计报告 Major #4");
    }

    // ==================== TS-4：登录验证码（禁用，待 Minor #5 修复后启用） ====================

    @Test
    @Order(4)
    @Disabled("Minor #5 未修复：dev 档 captcha-enabled=false。生产 profile 置 true 后移除此注解启用。")
    @DisplayName("TS-4 登录验证码强制 - 不带验证码的登录应失败")
    void ts4_loginWithoutCaptchaShouldFail() {
        Map<String, String> body = new HashMap<>();
        body.put("username", TestConstants.TEST_USER);
        body.put("password", TestConstants.TEST_PASS);
        // 故意不带 captchaCode / captchaUuid
        BizResult r = call(TestConstants.API_BASE_URL + TestConstants.LOGIN_PATH, HttpMethod.POST, body, null);
        assertThat(r.code)
                .as("验证码开启后，缺少验证码的登录应校验失败（非 200）")
                .isNotEqualTo(200);
    }

    // ==================== 辅助方法 ====================

    /** 用公开密钥离线签发一枚合法签名的 HS256 JWT（模拟攻击者伪造，不写入 Redis）。 */
    private static String forgeToken(long userId, long tenantId, String username) {
        try {
            long now = System.currentTimeMillis();
            String header = "{\"alg\":\"HS256\"}";
            String payload = String.format(Locale.ROOT,
                    "{\"userId\":%d,\"tenantId\":%d,\"username\":\"%s\",\"sub\":\"%s\",\"iat\":%d,\"exp\":%d}",
                    userId, tenantId, username, username, now / 1000L, (now + 3600_000L) / 1000L);
            Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
            String signingInput = enc.encodeToString(header.getBytes(StandardCharsets.UTF_8))
                    + "." + enc.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(PUBLIC_JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + enc.encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException("离线伪造 Token 失败", e);
        }
    }

    /** 以指定账号密码走真实验证码登录流程，成功返回 token，失败返回 null。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private String loginAs(String username, String password) {
        try {
            RestTemplate rt = getRestTemplate();
            ResponseEntity<Map> cap = rt.getForEntity(
                    TestConstants.API_BASE_URL + "/api/v1/captcha/image", Map.class);
            if (!cap.getStatusCode().is2xxSuccessful() || cap.getBody() == null) {
                return null;
            }
            Object dataObj = cap.getBody().get("data");
            if (!(dataObj instanceof Map)) {
                return null;
            }
            String uuid = String.valueOf(((Map) dataObj).get("uuid"));
            String captchaCode = stringRedisTemplate.opsForValue()
                    .get(TestConstants.REDIS_CAPTCHA_PREFIX + uuid);
            if (captchaCode != null) {
                captchaCode = captchaCode.replace("\"", "").trim();
            }

            Map<String, String> loginBody = new HashMap<>();
            loginBody.put("username", username);
            loginBody.put("password", password);
            loginBody.put("captchaCode", captchaCode);
            loginBody.put("captchaUuid", uuid);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> resp = rt.postForEntity(
                    TestConstants.API_BASE_URL + TestConstants.LOGIN_PATH,
                    new HttpEntity<>(loginBody, h), Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return null;
            }
            Object codeVal = resp.getBody().get("code");
            if (!(codeVal instanceof Number) || ((Number) codeVal).intValue() != 200) {
                return null;
            }
            Object d = resp.getBody().get("data");
            if (!(d instanceof Map)) {
                return null;
            }
            Object token = ((Map) d).get("token");
            if (token == null) {
                token = ((Map) d).get("accessToken");
            }
            return token == null ? null : token.toString();
        } catch (Exception e) {
            log.warn("loginAs({}) 失败: {}", username, e.getMessage());
            return null;
        }
    }

    /** 幂等地创建一个无角色的低权用户（tenant 由租户拦截器按 admin 上下文填充），返回其 userId。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Long ensureLowPrivUser() {
        if (createdUserId != null) {
            return createdUserId;
        }
        try {
            RestTemplate rt = getRestTemplate();
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            h.setBearerAuth(getAuthToken());

            String username = "sec_lowpriv_" + System.currentTimeMillis();
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("username", username);
            user.put("password", LOW_PRIV_PASSWORD);
            user.put("realName", "安全测试低权用户");
            user.put("status", 1);

            ResponseEntity<Map> save = rt.exchange(USER_URL, HttpMethod.POST,
                    new HttpEntity<>(user, h), Map.class);
            if (!save.getStatusCode().is2xxSuccessful()) {
                return null;
            }

            // 通过用户名分页查询回捞 userId
            ResponseEntity<Map> page = rt.exchange(USER_URL + "?page=1&size=1&username=" + username,
                    HttpMethod.GET, new HttpEntity<>(h), Map.class);
            if (!page.getStatusCode().is2xxSuccessful() || page.getBody() == null) {
                return null;
            }
            Object data = page.getBody().get("data");
            if (!(data instanceof Map)) {
                return null;
            }
            Object records = ((Map) data).get("records");
            if (!(records instanceof List) || ((List) records).isEmpty()) {
                return null;
            }
            Map first = (Map) ((List) records).get(0);
            createdUserId = ((Number) first.get("id")).longValue();
            createdUsername = username;
            log.info("已创建低权测试用户 username={}, userId={}", username, createdUserId);
            return createdUserId;
        } catch (Exception e) {
            log.warn("创建低权用户失败: {}", e.getMessage());
            return null;
        }
    }

    private Long createProjectAsAdmin() {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("projectName", "安全测试项目_" + System.currentTimeMillis());
        req.put("projectNature", "新建");
        req.put("projectType", "房屋建筑");
        req.put("projectAddress", "安全测试地址");
        req.put("contactName", "测试");
        req.put("contactPhone", "13800138000");
        req.put("budgetAmount", 5000000);
        BizResult r = call(PROJECT_URL, HttpMethod.POST, req, getAuthToken());
        if (r.code != 200) {
            return null;
        }
        return latestId(PROJECT_URL + "/page?page=1&size=1");
    }

    private Long createContractAsAdmin(Long projectId) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("projectId", projectId);
        req.put("contractType", "REGISTER");
        req.put("partyAName", "安全测试甲方有限公司");
        req.put("signingDate", LocalDate.now().toString());
        req.put("startDate", LocalDate.now().toString());
        req.put("endDate", LocalDate.now().plusMonths(12).toString());
        req.put("contractAmount", 2000000.00);
        req.put("taxRate", 9);
        BizResult r = call(CONTRACT_URL, HttpMethod.POST, req, getAuthToken());
        if (r.code != 200) {
            return null;
        }
        return latestId(CONTRACT_URL + "/page?page=1&size=1&projectId=" + projectId);
    }

    private boolean submitContractAsAdmin(Long contractId) {
        BizResult r = call(CONTRACT_URL + "/" + contractId + "/submit", HttpMethod.POST, null, getAuthToken());
        return r.code == 200;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String findFirstTodoTaskId(Long contractId) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setBearerAuth(getAuthToken());
            ResponseEntity<Map> resp = getRestTemplate().exchange(
                    APPROVAL_URL + "/todo?page=1&size=20", HttpMethod.GET, new HttpEntity<>(h), Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return null;
            }
            Object data = resp.getBody().get("data");
            if (!(data instanceof Map)) {
                return null;
            }
            Object records = ((Map) data).get("records");
            if (!(records instanceof List) || ((List) records).isEmpty()) {
                return null;
            }
            List<Map> list = (List<Map>) records;
            // 优先匹配当前合同的任务，回退到第一条
            for (Map t : list) {
                Object bizId = t.get("businessId");
                if (bizId instanceof Number && contractId.equals(((Number) bizId).longValue())) {
                    return taskIdOf(t);
                }
            }
            return taskIdOf(list.get(0));
        } catch (Exception e) {
            log.warn("查询待办任务失败: {}", e.getMessage());
            return null;
        }
    }

    private static String taskIdOf(Map<?, ?> task) {
        Object id = task.get("taskId");
        if (id == null) {
            id = task.get("id");
        }
        return id == null ? null : id.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Long latestId(String pageUrl) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setBearerAuth(getAuthToken());
            ResponseEntity<Map> resp = getRestTemplate().exchange(
                    pageUrl, HttpMethod.GET, new HttpEntity<>(h), Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return null;
            }
            Object data = resp.getBody().get("data");
            if (!(data instanceof Map)) {
                return null;
            }
            Object records = ((Map) data).get("records");
            if (!(records instanceof List) || ((List) records).isEmpty()) {
                return null;
            }
            Map first = (Map) ((List) records).get(0);
            return ((Number) first.get("id")).longValue();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 统一返回"有效状态码"：HTTP 2xx 时取响应体 code；4xx/5xx（如拦截器 401）时取 HTTP 状态码。
     * 这样可同时覆盖两种拒绝层：AuthInterceptor 直写 HTTP 401、GlobalExceptionHandler 返回 200+code。
     */
    private int effectiveCode(String url, HttpMethod method, Object body, String token) {
        return call(url, method, body, token).code;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private BizResult call(String url, HttpMethod method, Object body, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            h.setBearerAuth(token);
        }
        try {
            ResponseEntity<Map> r = getRestTemplate().exchange(url, method, new HttpEntity<>(body, h), Map.class);
            Object code = r.getBody() != null ? r.getBody().get("code") : null;
            Object msg = r.getBody() != null ? r.getBody().get("message") : null;
            if (msg == null && r.getBody() != null) {
                msg = r.getBody().get("msg");
            }
            int effective = (code instanceof Number) ? ((Number) code).intValue() : r.getStatusCode().value();
            return new BizResult(effective, msg == null ? null : msg.toString());
        } catch (HttpStatusCodeException e) {
            return new BizResult(e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    /** 简单承载"有效状态码 + 消息"的结果对象。 */
    private static final class BizResult {
        final int code;
        final String message;

        BizResult(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}
