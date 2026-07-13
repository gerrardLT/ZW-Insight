package com.zwinsight.contract.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 施工合同模块集成测试
 * <p>
 * 通过 HTTP 调用真实服务器 API，验证合同 CRUD + 审批流的完整生命周期。
 * 测试数据使用 tenant_id=9999 隔离，测试完成后物理 DELETE 清理。
 * </p>
 * <p>
 * 测试流程：
 * <ol>
 *   <li>创建测试项目（前置依赖）→ 获取 projectId</li>
 *   <li>创建施工合同（POST /api/v1/contract）→ 获取 contractId</li>
 *   <li>查询合同详情（GET /api/v1/contract/{id}）→ 验证字段 + 税金计算</li>
 *   <li>修改合同（PUT /api/v1/contract/{id}）→ 验证更新生效</li>
 *   <li>提交审批（POST /api/v1/contract/{id}/submit）→ 验证状态变更</li>
 * </ol>
 * </p>
 *
 * @see IntegrationTestBase
 * @see TestDataCleaner
 */
@DisplayName("施工合同集成测试 - CRUD + 审批流")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContractIntegrationTest extends IntegrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(ContractIntegrationTest.class);

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /** 测试过程中创建的项目 ID */
    private static Long testProjectId;

    /** 测试过程中创建的合同 ID */
    private static Long testContractId;

    /** TestDataCleaner 实例引用（用于 @AfterAll 静态方法） */
    private static TestDataCleaner cleanerRef;

    @BeforeAll
    static void setup() {
        setupAuthentication();
        log.info("====== ContractIntegrationTest: 认证完成，开始测试 ======");
    }

    @AfterAll
    static void tearDown() {
        cleanupTestData(cleanerRef);
        log.info("====== ContractIntegrationTest: 测试数据清理完成 ======");
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
    @DisplayName("前置条件 - 创建测试项目")
    void step1_createTestProject() {
        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        // 构建项目创建请求
        Map<String, Object> projectRequest = new LinkedHashMap<>();
        projectRequest.put("projectName", "集成测试项目_合同模块_" + System.currentTimeMillis());
        projectRequest.put("projectNature", "新建");
        projectRequest.put("projectType", "房屋建筑");
        projectRequest.put("projectAddress", "自动化测试地址");
        projectRequest.put("contactName", "测试人员");
        projectRequest.put("contactPhone", "13800138000");
        projectRequest.put("budgetAmount", 10000000);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(projectRequest, headers);
        String url = TestConstants.API_BASE_URL + "/api/v1/project";

        log.info("创建测试项目: POST {}", url);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
        AssertUtils.assertApiSuccess(response);

        // 查询刚创建的项目（通过分页查询获取最新项目 ID）
        String pageUrl = TestConstants.API_BASE_URL + "/api/v1/project/page?page=1&size=1";
        HttpEntity<Void> getRequest = new HttpEntity<>(headers);
        ResponseEntity<Map> pageResponse = restTemplate.exchange(pageUrl, HttpMethod.GET, getRequest, Map.class);
        AssertUtils.assertApiSuccess(pageResponse);

        // 从分页结果中提取第一条记录的 ID
        Map<String, Object> body = pageResponse.getBody();
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        assertThat(records).as("项目列表不应为空").isNotEmpty();

        testProjectId = ((Number) records.get(0).get("id")).longValue();
        assertThat(testProjectId).as("测试项目 ID 应有效").isPositive();
        log.info("测试项目创建成功，projectId={}", testProjectId);
    }

    @Test
    @Order(2)
    @DisplayName("创建施工合同 - POST /api/v1/contract")
    void step2_createContract() {
        assertThat(testProjectId).as("前置条件: 测试项目应已创建").isNotNull();

        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        // 构建合同创建请求
        Map<String, Object> contractRequest = new LinkedHashMap<>();
        contractRequest.put("projectId", testProjectId);
        contractRequest.put("contractType", "REGISTER");
        contractRequest.put("partyAName", "测试甲方建设有限公司");
        contractRequest.put("signingDate", LocalDate.now().toString());
        contractRequest.put("startDate", LocalDate.now().toString());
        contractRequest.put("endDate", LocalDate.now().plusMonths(12).toString());
        contractRequest.put("contractAmount", 5000000.00);
        contractRequest.put("taxRate", 9);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(contractRequest, headers);
        String url = TestConstants.API_BASE_URL + "/api/v1/contract";

        log.info("创建施工合同: POST {}", url);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
        AssertUtils.assertApiSuccess(response);

        // 通过分页查询获取刚创建的合同 ID
        String pageUrl = TestConstants.API_BASE_URL + "/api/v1/contract/page?page=1&size=1&projectId=" + testProjectId;
        HttpEntity<Void> getRequest = new HttpEntity<>(headers);
        ResponseEntity<Map> pageResponse = restTemplate.exchange(pageUrl, HttpMethod.GET, getRequest, Map.class);
        AssertUtils.assertApiSuccess(pageResponse);

        Map<String, Object> body = pageResponse.getBody();
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        assertThat(records).as("合同列表不应为空").isNotEmpty();

        testContractId = ((Number) records.get(0).get("id")).longValue();
        assertThat(testContractId).as("测试合同 ID 应有效").isPositive();
        log.info("施工合同创建成功，contractId={}", testContractId);
    }

    @Test
    @Order(3)
    @DisplayName("查询合同详情 - GET /api/v1/contract/{id} + 验证税金计算")
    void step3_getContractDetail() {
        assertThat(testContractId).as("前置条件: 测试合同应已创建").isNotNull();

        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        String url = TestConstants.API_BASE_URL + "/api/v1/contract/" + testContractId;
        HttpEntity<Void> request = new HttpEntity<>(headers);

        log.info("查询合同详情: GET {}", url);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
        AssertUtils.assertApiSuccess(response);

        Map<String, Object> body = response.getBody();
        Map<String, Object> contract = (Map<String, Object>) body.get("data");

        // 验证基本字段
        assertThat(contract.get("projectId")).as("projectId 应匹配")
                .isEqualTo(testProjectId.intValue());
        assertThat(contract.get("contractType")).as("合同类型应为 REGISTER")
                .isEqualTo("REGISTER");
        assertThat(contract.get("partyAName")).as("甲方名称应匹配")
                .isEqualTo("测试甲方建设有限公司");
        assertThat(contract.get("status")).as("初始状态应为 DRAFT")
                .isEqualTo("DRAFT");
        assertThat(contract.get("contractCode")).as("合同编号应自动生成")
                .isNotNull();

        // 验证税金计算: amountWithoutTax = contractAmount / (1 + taxRate/100)
        BigDecimal contractAmount = new BigDecimal(contract.get("contractAmount").toString());
        BigDecimal taxRate = new BigDecimal(contract.get("taxRate").toString());
        BigDecimal amountWithoutTax = new BigDecimal(contract.get("amountWithoutTax").toString());
        BigDecimal taxAmount = new BigDecimal(contract.get("taxAmount").toString());

        // 计算期望值
        BigDecimal rate = taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        BigDecimal divisor = BigDecimal.ONE.add(rate);
        BigDecimal expectedWithoutTax = contractAmount.divide(divisor, 2, RoundingMode.HALF_UP);
        BigDecimal expectedTaxAmount = contractAmount.subtract(expectedWithoutTax);

        assertThat(amountWithoutTax).as("不含税金额计算应正确")
                .isEqualByComparingTo(expectedWithoutTax);
        assertThat(taxAmount).as("税额计算应正确")
                .isEqualByComparingTo(expectedTaxAmount);

        log.info("合同详情验证通过: 金额={}, 不含税={}, 税额={}",
                contractAmount, amountWithoutTax, taxAmount);
    }

    @Test
    @Order(4)
    @DisplayName("修改合同 - PUT /api/v1/contract/{id}")
    void step4_updateContract() {
        assertThat(testContractId).as("前置条件: 测试合同应已创建").isNotNull();

        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        // 修改合同金额和甲方名称
        Map<String, Object> updateRequest = new LinkedHashMap<>();
        updateRequest.put("projectId", testProjectId);
        updateRequest.put("contractType", "REGISTER");
        updateRequest.put("partyAName", "测试甲方建设有限公司（已更新）");
        updateRequest.put("signingDate", LocalDate.now().toString());
        updateRequest.put("startDate", LocalDate.now().toString());
        updateRequest.put("endDate", LocalDate.now().plusMonths(18).toString());
        updateRequest.put("contractAmount", 6000000.00);
        updateRequest.put("taxRate", 13);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(updateRequest, headers);
        String url = TestConstants.API_BASE_URL + "/api/v1/contract/" + testContractId;

        log.info("修改合同: PUT {}", url);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PUT, request, Map.class);
        AssertUtils.assertApiSuccess(response);

        // 验证修改生效：重新查询合同
        HttpEntity<Void> getRequest = new HttpEntity<>(headers);
        ResponseEntity<Map> detailResponse = restTemplate.exchange(url, HttpMethod.GET, getRequest, Map.class);
        AssertUtils.assertApiSuccess(detailResponse);

        Map<String, Object> body = detailResponse.getBody();
        Map<String, Object> contract = (Map<String, Object>) body.get("data");

        assertThat(contract.get("partyAName")).as("甲方名称应已更新")
                .isEqualTo("测试甲方建设有限公司（已更新）");

        BigDecimal updatedAmount = new BigDecimal(contract.get("contractAmount").toString());
        assertThat(updatedAmount).as("合同金额应已更新为 6000000")
                .isEqualByComparingTo(new BigDecimal("6000000"));

        BigDecimal updatedTaxRate = new BigDecimal(contract.get("taxRate").toString());
        assertThat(updatedTaxRate).as("税率应已更新为 13")
                .isEqualByComparingTo(new BigDecimal("13"));

        // 验证税金重新计算
        BigDecimal newAmountWithoutTax = new BigDecimal(contract.get("amountWithoutTax").toString());
        BigDecimal rate = updatedTaxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        BigDecimal divisor = BigDecimal.ONE.add(rate);
        BigDecimal expectedWithoutTax = updatedAmount.divide(divisor, 2, RoundingMode.HALF_UP);

        assertThat(newAmountWithoutTax).as("修改后不含税金额应重新计算")
                .isEqualByComparingTo(expectedWithoutTax);

        log.info("合同修改验证通过: 新金额={}, 新税率={}%, 新不含税金额={}",
                updatedAmount, updatedTaxRate, newAmountWithoutTax);
    }

    @Test
    @Order(5)
    @DisplayName("提交审批 - POST /api/v1/contract/{id}/submit + 状态变更验证")
    void step5_submitForApproval() {
        assertThat(testContractId).as("前置条件: 测试合同应已创建").isNotNull();

        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        // 提交审批
        String submitUrl = TestConstants.API_BASE_URL + "/api/v1/contract/" + testContractId + "/submit";
        HttpEntity<Void> submitRequest = new HttpEntity<>(headers);

        log.info("提交审批: POST {}", submitUrl);
        ResponseEntity<Map> submitResponse = restTemplate.exchange(
                submitUrl, HttpMethod.POST, submitRequest, Map.class);
        AssertUtils.assertApiSuccess(submitResponse);

        // 验证状态变更：查询合同详情确认状态
        String detailUrl = TestConstants.API_BASE_URL + "/api/v1/contract/" + testContractId;
        HttpEntity<Void> getRequest = new HttpEntity<>(headers);
        ResponseEntity<Map> detailResponse = restTemplate.exchange(
                detailUrl, HttpMethod.GET, getRequest, Map.class);
        AssertUtils.assertApiSuccess(detailResponse);

        Map<String, Object> body = detailResponse.getBody();
        Map<String, Object> contract = (Map<String, Object>) body.get("data");

        // 提交审批后状态应变为 EFFECTIVE
        assertThat(contract.get("status")).as("提交审批后状态应变更为 EFFECTIVE")
                .isEqualTo("EFFECTIVE");

        log.info("审批提交验证通过: contractId={}, 状态已变更为 EFFECTIVE", testContractId);
    }
}
