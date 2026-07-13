package com.zwinsight.project.integration;

import com.zwinsight.common.base.IntegrationTestBase;
import com.zwinsight.common.base.TestConstants;
import com.zwinsight.common.base.TestDataCleaner;
import com.zwinsight.common.util.AssertUtils;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目模块 L2 集成测试
 * <p>
 * 通过 HTTP 调用真实服务器 API，验证项目 CRUD 完整往返 + 提交状态变更 + 删除约束。
 * 测试数据使用 tenant_id=9999 隔离，测试完成后通过 TestDataCleaner 物理 DELETE 清理。
 * </p>
 * <p>
 * 测试流程：
 * <ol>
 *   <li>创建项目（POST /api/v1/project）→ 获取 projectId</li>
 *   <li>查询项目详情（GET /api/v1/project/{id}）→ 验证字段正确</li>
 *   <li>修改项目（PUT /api/v1/project/{id}）→ 验证修改成功</li>
 *   <li>提交项目（POST /api/v1/project/{id}/submit）→ 验证状态变为 FILED</li>
 *   <li>删除项目（新建 DRAFT 项目再删除，验证仅 DRAFT 可删）</li>
 * </ol>
 * </p>
 *
 * @see IntegrationTestBase
 * @see TestDataCleaner
 */
@DisplayName("项目模块集成测试 - CRUD + 提交 + 删除")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectIntegrationTest extends IntegrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(ProjectIntegrationTest.class);

    private static final String PROJECT_API = TestConstants.API_BASE_URL + "/api/v1/project";

    /** 测试过程中创建的项目 ID（用于 CRUD 往返测试） */
    private static Long testProjectId;

    /** 用于删除测试的独立项目 ID */
    private static Long deleteTestProjectId;

    /** TestDataCleaner 实例引用（用于 @AfterAll 静态方法） */
    private static TestDataCleaner cleanerRef;

    @BeforeAll
    static void setup() {
        setupAuthentication();
        log.info("====== ProjectIntegrationTest: 认证完成，开始项目 CRUD 集成测试 ======");
    }

    @AfterAll
    static void tearDown() {
        cleanupTestData(cleanerRef);
        log.info("====== ProjectIntegrationTest: 测试数据清理完成 ======");
    }

    /**
     * 在非静态上下文中捕获 Spring 注入的 cleaner 实例
     */
    @BeforeEach
    void captureCleanerRef() {
        if (cleanerRef == null) {
            cleanerRef = testDataCleaner;
        }
    }

    // ==================== 测试用例 ====================

    @Test
    @Order(1)
    @DisplayName("创建项目 - POST /api/v1/project → 返回成功")
    void step1_createProject() throws Exception {
        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        // 构建项目创建请求
        Map<String, Object> requestBody = buildCreateProjectRequest("集成测试项目_CRUD_" + System.currentTimeMillis());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        log.info("创建项目: POST {}", PROJECT_API);
        ResponseEntity<Map> response = restTemplate.exchange(PROJECT_API, HttpMethod.POST, request, Map.class);
        AssertUtils.assertApiSuccess(response);

        // 通过分页查询获取刚创建的项目 ID
        String pageUrl = PROJECT_API + "/page?page=1&size=1";
        HttpEntity<Void> getRequest = new HttpEntity<>(headers);
        ResponseEntity<Map> pageResponse = restTemplate.exchange(pageUrl, HttpMethod.GET, getRequest, Map.class);
        AssertUtils.assertApiSuccess(pageResponse);

        Map<String, Object> body = pageResponse.getBody();
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        assertThat(records).as("项目列表不应为空").isNotEmpty();

        testProjectId = ((Number) records.get(0).get("id")).longValue();
        assertThat(testProjectId).as("测试项目 ID 应有效").isPositive();

        log.info("[创建项目] 成功，projectId={}", testProjectId);
    }

    @Test
    @Order(2)
    @DisplayName("查询项目详情 - GET /api/v1/project/{id} → 验证字段正确")
    void step2_getProjectDetail() throws Exception {
        assertThat(testProjectId).as("前置条件：项目 ID 应已获取").isNotNull();

        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();
        String url = PROJECT_API + "/" + testProjectId;

        HttpEntity<Void> request = new HttpEntity<>(headers);

        log.info("查询项目详情: GET {}", url);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
        AssertUtils.assertApiSuccess(response);

        Map<String, Object> body = response.getBody();
        Map<String, Object> project = (Map<String, Object>) body.get("data");

        // 验证基本字段
        assertThat(((Number) project.get("id")).longValue())
                .as("id 应匹配").isEqualTo(testProjectId);
        assertThat((String) project.get("projectName"))
                .as("项目名称应包含测试标识").contains("集成测试项目_CRUD_");
        assertThat(project.get("projectNature"))
                .as("项目性质应为新建").isEqualTo("新建");
        assertThat(project.get("projectType"))
                .as("项目类型应为房建工程").isEqualTo("房建工程");
        assertThat(project.get("status"))
                .as("初始状态应为 DRAFT").isEqualTo("DRAFT");
        assertThat(project.get("projectCode"))
                .as("项目编号应自动生成").isNotNull();
        assertThat(project.get("projectAddress"))
                .as("项目地址应匹配").isEqualTo("测试地址-集成测试");

        log.info("[查询详情] 项目名称: {}, 状态: {}, 编号: {}",
                project.get("projectName"), project.get("status"), project.get("projectCode"));
    }

    @Test
    @Order(3)
    @DisplayName("修改项目 - PUT /api/v1/project/{id} → 验证修改成功")
    void step3_updateProject() throws Exception {
        assertThat(testProjectId).as("前置条件：项目 ID 应已获取").isNotNull();

        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        // 修改项目名称、项目性质、联系人
        Map<String, Object> updateBody = buildCreateProjectRequest("集成测试项目_已修改");
        updateBody.put("projectNature", "改扩建");
        updateBody.put("contactName", "测试联系人");
        updateBody.put("contactPhone", "13800138000");
        updateBody.put("budgetAmount", 2000000.00);

        String url = PROJECT_API + "/" + testProjectId;
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(updateBody, headers);

        log.info("修改项目: PUT {}", url);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PUT, request, Map.class);
        AssertUtils.assertApiSuccess(response);

        // 重新查询验证修改结果
        HttpEntity<Void> getRequest = new HttpEntity<>(headers);
        ResponseEntity<Map> detailResponse = restTemplate.exchange(url, HttpMethod.GET, getRequest, Map.class);
        AssertUtils.assertApiSuccess(detailResponse);

        Map<String, Object> body = detailResponse.getBody();
        Map<String, Object> project = (Map<String, Object>) body.get("data");

        assertThat(project.get("projectName"))
                .as("修改后项目名称应匹配").isEqualTo("集成测试项目_已修改");
        assertThat(project.get("projectNature"))
                .as("修改后项目性质应匹配").isEqualTo("改扩建");
        assertThat(project.get("contactName"))
                .as("修改后联系人应匹配").isEqualTo("测试联系人");
        assertThat(project.get("contactPhone"))
                .as("修改后联系电话应匹配").isEqualTo("13800138000");

        log.info("[修改项目] 成功，新名称: {}, 新性质: {}", project.get("projectName"), project.get("projectNature"));
    }

    @Test
    @Order(4)
    @DisplayName("提交项目 - POST /api/v1/project/{id}/submit → 状态变为 FILED")
    void step4_submitProject() throws Exception {
        assertThat(testProjectId).as("前置条件：项目 ID 应已获取").isNotNull();

        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        // 提交项目
        String submitUrl = PROJECT_API + "/" + testProjectId + "/submit";
        HttpEntity<Void> submitRequest = new HttpEntity<>(headers);

        log.info("提交项目: POST {}", submitUrl);
        ResponseEntity<Map> submitResponse = restTemplate.exchange(
                submitUrl, HttpMethod.POST, submitRequest, Map.class);
        AssertUtils.assertApiSuccess(submitResponse);

        // 验证状态已变为 FILED
        String detailUrl = PROJECT_API + "/" + testProjectId;
        HttpEntity<Void> getRequest = new HttpEntity<>(headers);
        ResponseEntity<Map> detailResponse = restTemplate.exchange(
                detailUrl, HttpMethod.GET, getRequest, Map.class);
        AssertUtils.assertApiSuccess(detailResponse);

        Map<String, Object> body = detailResponse.getBody();
        Map<String, Object> project = (Map<String, Object>) body.get("data");

        assertThat(project.get("status"))
                .as("提交后状态应变为 FILED")
                .isEqualTo("FILED");

        log.info("[提交项目] 成功，状态已变为 FILED");
    }

    @Test
    @Order(5)
    @DisplayName("删除已提交项目 - 仅 DRAFT 状态可删除，FILED 状态应被拒绝")
    void step5_deleteSubmittedProject_shouldFail() throws Exception {
        assertThat(testProjectId).as("前置条件：项目 ID 应已获取").isNotNull();

        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        // 尝试删除已提交（FILED）状态的项目
        String deleteUrl = PROJECT_API + "/" + testProjectId;
        HttpEntity<Void> deleteRequest = new HttpEntity<>(headers);

        log.info("尝试删除 FILED 项目: DELETE {}", deleteUrl);
        ResponseEntity<Map> deleteResponse = restTemplate.exchange(
                deleteUrl, HttpMethod.DELETE, deleteRequest, Map.class);

        // 非 DRAFT 状态删除应失败（业务码不应为 200）
        Map<String, Object> body = deleteResponse.getBody();
        int code = ((Number) body.get("code")).intValue();
        assertThat(code)
                .as("删除非 DRAFT 项目应失败，业务码不应为 200")
                .isNotEqualTo(200);

        log.info("[删除约束] FILED 状态项目删除被正确拒绝，code={}", code);
    }

    @Test
    @Order(6)
    @DisplayName("删除 DRAFT 项目 - 新建项目再删除，验证删除成功")
    void step6_deleteDraftProject() throws Exception {
        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        // Step 1: 创建一个新的 DRAFT 项目用于删除测试
        Map<String, Object> requestBody = buildCreateProjectRequest("集成测试项目_待删除_" + System.currentTimeMillis());
        HttpEntity<Map<String, Object>> createRequest = new HttpEntity<>(requestBody, headers);

        log.info("创建待删除项目: POST {}", PROJECT_API);
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                PROJECT_API, HttpMethod.POST, createRequest, Map.class);
        AssertUtils.assertApiSuccess(createResponse);

        // Step 2: 查询获取新项目 ID（分页排序取最新）
        String pageUrl = PROJECT_API + "/page?page=1&size=1";
        HttpEntity<Void> getRequest = new HttpEntity<>(headers);
        ResponseEntity<Map> pageResponse = restTemplate.exchange(pageUrl, HttpMethod.GET, getRequest, Map.class);
        AssertUtils.assertApiSuccess(pageResponse);

        Map<String, Object> body = pageResponse.getBody();
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        assertThat(records).as("项目列表不应为空").isNotEmpty();

        deleteTestProjectId = ((Number) records.get(0).get("id")).longValue();
        assertThat(deleteTestProjectId).as("待删除项目 ID 应有效").isPositive();
        log.info("待删除项目 ID: {}", deleteTestProjectId);

        // Step 3: 删除 DRAFT 状态的项目
        String deleteUrl = PROJECT_API + "/" + deleteTestProjectId;
        HttpEntity<Void> deleteRequest = new HttpEntity<>(headers);

        log.info("删除 DRAFT 项目: DELETE {}", deleteUrl);
        ResponseEntity<Map> deleteResponse = restTemplate.exchange(
                deleteUrl, HttpMethod.DELETE, deleteRequest, Map.class);
        AssertUtils.assertApiSuccess(deleteResponse);

        // Step 4: 验证项目已被删除 - 查询详情应返回"项目不存在"
        ResponseEntity<Map> detailResponse = restTemplate.exchange(
                deleteUrl, HttpMethod.GET, getRequest, Map.class);
        Map<String, Object> detailBody = detailResponse.getBody();
        int detailCode = ((Number) detailBody.get("code")).intValue();

        assertThat(detailCode)
                .as("已删除项目查询应返回错误（非 200）")
                .isNotEqualTo(200);

        deleteTestProjectId = null; // 标记已删除，避免 @AfterAll 重复清理
        log.info("[删除项目] DRAFT 项目删除成功并验证通过");
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建项目创建请求 body
     * <p>
     * 所有测试数据通过 tenant_id=9999 隔离（由服务端 SecurityContextHolder 自动填充）
     * </p>
     */
    private Map<String, Object> buildCreateProjectRequest(String projectName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectName", projectName);
        body.put("projectNature", "新建");
        body.put("projectType", "房建工程");
        body.put("ownerCompanyName", "测试业主单位");
        body.put("signingCompanyName", "测试签约公司");
        body.put("projectAddress", "测试地址-集成测试");
        body.put("contactName", "自动化测试");
        body.put("contactPhone", "13900139000");
        body.put("budgetAmount", new BigDecimal("1000000.00"));
        return body;
    }
}
