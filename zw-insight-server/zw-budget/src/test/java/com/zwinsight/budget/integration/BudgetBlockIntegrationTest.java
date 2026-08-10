package com.zwinsight.budget.integration;

import com.zwinsight.common.base.IntegrationTestBase;
import com.zwinsight.common.base.TestConstants;
import com.zwinsight.common.base.TestDataCleaner;
import com.zwinsight.common.util.AssertUtils;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 预算管控 BLOCK 模式集成测试
 * <p>
 * 测试覆盖：@BudgetCheck 切面 + BudgetControlConfigService.checkBudget 全链路——
 * BLOCK 模式超预算拦截（劳务合同 save 携带实体参数，切面参数提取生效）、
 * 切换 WARN_ONLY 后同金额放行、项目生效配置查询。
 * <p>
 * 前置数据：API 建项目 → JdbcTemplate 直插 biz_budget/biz_budget_detail
 * （biz_budget_detail 无任何写入 API，明细行只来自种子 SQL，故 L2 只能直插 DB）
 * → API 建 BLOCK 控制配置。
 * <p>
 * 金额设计：LABOR 科目预算 100000，阈值 80%。
 * 第一笔劳务合同 50000（执行率 50%，通过）；
 * 第二笔 200000（执行率 (50000+200000)/100000=250% > 100%，BLOCK 拒绝，消息含「已超预算」）；
 * 配置改 WARN_ONLY 后同金额放行。
 * <p>
 * 租户说明：联调服务器 admin 测试账号归属租户 1（与 FinanceIntegrationTest 同基座），
 * API 写入数据均为 tenant_id=1，故直插的预算数据也用 tenant_id=1；
 * 清理按 project_id 维度定向删除（不影响租户其他数据），最后兼容调用 cleaner 清理 9999 残留。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BudgetBlockIntegrationTest extends IntegrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(BudgetBlockIntegrationTest.class);

    private static final String BASE_URL = TestConstants.API_BASE_URL;
    private static final String PROJECT_URL = BASE_URL + "/api/v1/project";
    private static final String LABOR_CONTRACT_URL = BASE_URL + "/api/v1/labor/contract";
    private static final String CONFIG_URL = BASE_URL + "/api/v1/budget-control-configs";

    /** 固定 ID：租户 9999 隔离，测试后由 TestDataCleaner/JDBC 清理，与雪花 ID 无碰撞风险 */
    private static final long BUDGET_ID = 99990001L;
    private static final long BUDGET_DETAIL_ID = 99990002L;

    /** LABOR 科目预算额度 */
    private static final BigDecimal BUDGET_AMOUNT = new BigDecimal("100000.00");

    @Autowired
    private TestDataCleaner cleaner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 前置数据 ID */
    private Long createdProjectId;
    private Long createdConfigId;

    @BeforeAll
    void setup() {
        setupAuthentication();
        log.info("====== BudgetBlockIntegrationTest: 认证完成，开始准备前置数据 ======");
    }

    @AfterAll
    void cleanup() {
        log.info("====== BudgetBlockIntegrationTest: 开始清理测试数据 ======");
        // 按 project_id 维度逆序清理（API 数据归属租户 1，不能用 tenant_id=9999 兼容清理）
        if (createdProjectId != null) {
            try {
                jdbcTemplate.update("DELETE FROM biz_labor_contract WHERE project_id = ?", createdProjectId);
                jdbcTemplate.update("DELETE FROM sys_budget_control_config WHERE project_id = ?", createdProjectId);
                jdbcTemplate.update("DELETE FROM biz_budget_detail WHERE budget_id = ?", BUDGET_ID);
                jdbcTemplate.update("DELETE FROM biz_budget WHERE id = ?", BUDGET_ID);
                jdbcTemplate.update("DELETE FROM biz_project WHERE id = ?", createdProjectId);
                log.info("已按 project_id={} 定向清理本测试创建的数据", createdProjectId);
            } catch (Exception e) {
                log.warn("定向清理失败: {}", e.getMessage());
            }
        }
        cleanupTestData(cleaner);
    }

    // ==================== 前置数据准备 ====================

    @Test
    @Order(1)
    @DisplayName("前置：创建测试项目")
    void step1_createProject() {
        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        Map<String, Object> projectBody = new LinkedHashMap<>();
        projectBody.put("projectName", "集成测试-预算BLOCK项目-" + System.currentTimeMillis());
        projectBody.put("projectNature", "新建");
        projectBody.put("projectType", "房屋建筑");
        projectBody.put("ownerCompanyName", "测试业主单位");
        projectBody.put("projectAddress", "测试地址");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(projectBody, headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                PROJECT_URL, HttpMethod.POST, request,
                new ParameterizedTypeReference<>() {});

        AssertUtils.assertApiSuccess(response);
        log.info("创建测试项目成功");

        // 按名称查回获取 ID
        ResponseEntity<Map<String, Object>> pageResponse = restTemplate.exchange(
                PROJECT_URL + "/page?page=1&size=1&projectName=集成测试-预算BLOCK项目",
                HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        AssertUtils.assertApiSuccess(pageResponse);
        Map<String, Object> data = extractData(pageResponse);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        assertThat(records).isNotEmpty();

        createdProjectId = AssertUtils.asLong(records.get(0).get("id"));
        log.info("测试项目 ID: {}", createdProjectId);
    }

    @Test
    @Order(2)
    @DisplayName("前置：直插预算+预算明细（LABOR 科目 100000）")
    void step2_insertBudgetData() {
        assertThat(createdProjectId).as("前置项目必须已创建").isNotNull();

        jdbcTemplate.update(
                "INSERT INTO biz_budget (id, project_id, budget_type, change_seq, total_amount, status, " +
                        "tenant_id, created_by, created_at, updated_at, deleted, version) " +
                        "VALUES (?, ?, 'ORIGINAL', 0, ?, 'APPROVED', 1, 0, NOW(), NOW(), 0, 0)",
                BUDGET_ID, createdProjectId, BUDGET_AMOUNT);

        jdbcTemplate.update(
                "INSERT INTO biz_budget_detail (id, budget_id, cost_category, item_name, " +
                        "budget_quantity, budget_unit_price, budget_total_price, " +
                        "tenant_id, created_by, created_at, updated_at, deleted, version) " +
                        "VALUES (?, ?, 'LABOR', 'L2集成测试-人工预算', 1, ?, ?, 1, 0, NOW(), NOW(), 0, 0)",
                BUDGET_DETAIL_ID, BUDGET_ID, BUDGET_AMOUNT, BUDGET_AMOUNT);

        log.info("直插预算数据完成: budgetId={}, detailId={}, LABOR 额度={}",
                BUDGET_ID, BUDGET_DETAIL_ID, BUDGET_AMOUNT);
    }

    @Test
    @Order(3)
    @DisplayName("前置：创建 BLOCK 控制配置并验证实时生效")
    void step3_createBlockConfig() {
        assertThat(createdProjectId).as("前置项目必须已创建").isNotNull();

        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        Map<String, Object> configBody = new LinkedHashMap<>();
        configBody.put("projectId", createdProjectId);
        configBody.put("controlMode", "BLOCK");
        configBody.put("warningThreshold", 80);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                CONFIG_URL, HttpMethod.POST, new HttpEntity<>(configBody, headers),
                new ParameterizedTypeReference<>() {});
        AssertUtils.assertApiSuccess(response);

        // save 返回 R<Void> 无 ID，从 DB 查回（按 projectId 定位，本测试专属项目不会歧义）
        createdConfigId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_budget_control_config WHERE project_id = ? " +
                        "ORDER BY created_at DESC LIMIT 1",
                Long.class, createdProjectId);
        assertThat(createdConfigId).as("BLOCK 配置必须创建成功").isNotNull();

        // 项目生效配置查询：确认实时生效（R6.7）
        ResponseEntity<Map<String, Object>> effectiveResponse = restTemplate.exchange(
                CONFIG_URL + "/project/" + createdProjectId,
                HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});
        AssertUtils.assertApiSuccess(effectiveResponse);
        Map<String, Object> body = effectiveResponse.getBody();
        assertThat(body).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> effectiveConfig = (Map<String, Object>) body.get("data");
        assertThat(effectiveConfig).isNotNull();
        assertThat(effectiveConfig.get("controlMode")).isEqualTo("BLOCK");

        log.info("BLOCK 配置创建成功: configId={}", createdConfigId);
    }

    // ==================== 预算控制核心断言 ====================

    @Test
    @Order(4)
    @DisplayName("正向：劳务合同 5 万（执行率 50%）在 BLOCK 模式下通过")
    void step4_laborContractWithinBudget() {
        ResponseEntity<Map<String, Object>> response = createLaborContract(new BigDecimal("50000.00"));
        AssertUtils.assertApiSuccess(response);
        log.info("执行率 50% < 阈值 80%，劳务合同创建成功");
    }

    @Test
    @Order(5)
    @DisplayName("负向：劳务合同 20 万（执行率 250%）被 BLOCK 拦截")
    void step5_laborContractBlockedWhenOverBudget() {
        ResponseEntity<Map<String, Object>> response = createLaborContract(new BigDecimal("200000.00"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(AssertUtils.asLong(body.get("code")))
                .as("BLOCK 模式下超预算必须拒绝（业务码非 200）")
                .isNotEqualTo(200L);
        String msg = String.valueOf(body.getOrDefault("msg", body.get("message")));
        assertThat(msg).as("拦截消息必须包含超预算提示").contains("已超预算");
        log.info("BLOCK 拦截生效，响应消息: {}", msg);
    }

    @Test
    @Order(6)
    @DisplayName("切换控制模式为 WARN_ONLY")
    void step6_switchConfigToWarnOnly() {
        assertThat(createdConfigId).as("前置配置必须已创建").isNotNull();

        Map<String, Object> configBody = new LinkedHashMap<>();
        configBody.put("projectId", createdProjectId);
        configBody.put("controlMode", "WARN_ONLY");
        configBody.put("warningThreshold", 80);

        ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                CONFIG_URL + "/" + createdConfigId, HttpMethod.PUT,
                new HttpEntity<>(configBody, buildAuthHeaders()),
                new ParameterizedTypeReference<>() {});
        AssertUtils.assertApiSuccess(response);
        log.info("控制配置已切换为 WARN_ONLY");
    }

    @Test
    @Order(7)
    @DisplayName("正向：WARN_ONLY 模式下同金额 20 万放行")
    void step7_laborContractPassedInWarnOnlyMode() {
        ResponseEntity<Map<String, Object>> response = createLaborContract(new BigDecimal("200000.00"));
        AssertUtils.assertApiSuccess(response);
        log.info("WARN_ONLY 模式超预算仅预警不拦截，劳务合同创建成功");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建劳务合同（@BudgetCheck(category="LABOR") 切面对实体参数生效）
     */
    private ResponseEntity<Map<String, Object>> createLaborContract(BigDecimal amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", createdProjectId);
        body.put("contractName", "L2预算BLOCK测试劳务合同-" + amount.toPlainString());
        body.put("teamName", "L2测试班组");
        body.put("partyAName", "测试甲方单位");
        body.put("partyBName", "测试劳务公司");
        body.put("signingDate", LocalDate.now().toString());
        body.put("startDate", LocalDate.now().toString());
        body.put("endDate", LocalDate.now().plusMonths(6).toString());
        body.put("contractAmount", amount);

        return getRestTemplate().exchange(
                LABOR_CONTRACT_URL, HttpMethod.POST, new HttpEntity<>(body, buildAuthHeaders()),
                new ParameterizedTypeReference<>() {});
    }

    /**
     * 从响应中提取 data 字段
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(ResponseEntity<Map<String, Object>> response) {
        Map<String, Object> body = response.getBody();
        assertThat(body).as("响应体不应为 null").isNotNull();
        Object data = body.get("data");
        assertThat(data).as("data 字段不应为 null").isNotNull();
        return (Map<String, Object>) data;
    }
}
