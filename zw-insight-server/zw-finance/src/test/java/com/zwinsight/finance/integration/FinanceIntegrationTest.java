package com.zwinsight.finance.integration;

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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 财务模块（开票申请）集成测试
 * <p>
 * 测试覆盖：开票申请 CRUD + 提交审批（金额校验 + 审批流发起 + 合同累计开票回写）。
 * <p>
 * 前置流程：创建项目 → 创建施工合同 → 提交合同（使合同状态为 EFFECTIVE）。
 * <p>
 * 所有测试数据使用 tenant_id=9999 隔离，测试结束后物理 DELETE 清理。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinanceIntegrationTest extends IntegrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(FinanceIntegrationTest.class);

    private static final String BASE_URL = TestConstants.API_BASE_URL;
    private static final String INVOICE_APPLY_URL = BASE_URL + "/api/v1/finance/invoice-apply";
    private static final String PROJECT_URL = BASE_URL + "/api/v1/project";
    private static final String CONTRACT_URL = BASE_URL + "/api/v1/contract";

    @Autowired
    private TestDataCleaner cleaner;

    /** 前置数据 ID（用于清理） */
    private Long createdProjectId;
    private Long createdContractId;
    private Long createdInvoiceApplyId;

    @BeforeAll
    void setup() {
        setupAuthentication();
        log.info("====== FinanceIntegrationTest: 认证完成，开始准备前置数据 ======");
    }

    @AfterAll
    void cleanup() {
        log.info("====== FinanceIntegrationTest: 开始清理前置数据 ======");
        cleanupCreatedResources();
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
        projectBody.put("projectName", "集成测试-财务模块项目-" + System.currentTimeMillis());
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

        // 查询最新项目获取 ID
        ResponseEntity<Map<String, Object>> pageResponse = restTemplate.exchange(
                PROJECT_URL + "/page?page=1&size=1&projectName=集成测试-财务模块项目",
                HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        AssertUtils.assertApiSuccess(pageResponse);
        Map<String, Object> data = extractData(pageResponse);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        assertThat(records).isNotEmpty();

        createdProjectId = ((Number) records.get(0).get("id")).longValue();
        log.info("测试项目 ID: {}", createdProjectId);
    }

    @Test
    @Order(2)
    @DisplayName("前置：创建施工合同")
    void step2_createContract() {
        assertThat(createdProjectId).as("前置项目必须已创建").isNotNull();

        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        Map<String, Object> contractBody = new LinkedHashMap<>();
        contractBody.put("projectId", createdProjectId);
        contractBody.put("contractType", "REGISTER");
        contractBody.put("partyAName", "测试甲方单位");
        contractBody.put("signingDate", LocalDate.now().toString());
        contractBody.put("startDate", LocalDate.now().toString());
        contractBody.put("endDate", LocalDate.now().plusYears(1).toString());
        contractBody.put("contractAmount", new BigDecimal("5000000.00"));
        contractBody.put("taxRate", new BigDecimal("9"));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(contractBody, headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                CONTRACT_URL, HttpMethod.POST, request,
                new ParameterizedTypeReference<>() {});

        AssertUtils.assertApiSuccess(response);
        log.info("创建施工合同成功");

        // 查询获取合同 ID
        ResponseEntity<Map<String, Object>> pageResponse = restTemplate.exchange(
                CONTRACT_URL + "/page?page=1&size=1&projectId=" + createdProjectId,
                HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        AssertUtils.assertApiSuccess(pageResponse);
        Map<String, Object> data = extractData(pageResponse);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        assertThat(records).isNotEmpty();

        createdContractId = ((Number) records.get(0).get("id")).longValue();
        log.info("施工合同 ID: {}", createdContractId);
    }

    @Test
    @Order(3)
    @DisplayName("前置：提交施工合同（使合同生效）")
    void step3_submitContract() {
        assertThat(createdContractId).as("前置合同必须已创建").isNotNull();

        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                CONTRACT_URL + "/" + createdContractId + "/submit",
                HttpMethod.POST, request,
                new ParameterizedTypeReference<>() {});

        AssertUtils.assertApiSuccess(response);
        log.info("施工合同已提交，状态变为 EFFECTIVE");

        // 验证合同状态已变为 EFFECTIVE
        ResponseEntity<Map<String, Object>> detailResponse = restTemplate.exchange(
                CONTRACT_URL + "/" + createdContractId,
                HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        AssertUtils.assertApiSuccess(detailResponse);
        Map<String, Object> contractData = extractData(detailResponse);
        assertThat(contractData.get("status")).isEqualTo("EFFECTIVE");
        log.info("合同状态确认: EFFECTIVE");
    }

    // ==================== 开票申请 CRUD 测试 ====================

    @Test
    @Order(4)
    @DisplayName("创建开票申请（POST /api/v1/finance/invoice-apply）")
    void step4_createInvoiceApply() {
        assertThat(createdProjectId).as("前置项目必须已创建").isNotNull();
        assertThat(createdContractId).as("前置合同必须已创建").isNotNull();

        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        Map<String, Object> invoiceBody = buildInvoiceApplyRequest(
                new BigDecimal("100000.00"));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(invoiceBody, headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                INVOICE_APPLY_URL, HttpMethod.POST, request,
                new ParameterizedTypeReference<>() {});

        AssertUtils.assertApiSuccess(response);
        log.info("创建开票申请成功");

        // 查询获取开票申请 ID
        ResponseEntity<Map<String, Object>> pageResponse = restTemplate.exchange(
                INVOICE_APPLY_URL + "/page?page=1&size=1&contractId=" + createdContractId,
                HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        AssertUtils.assertApiSuccess(pageResponse);
        Map<String, Object> data = extractData(pageResponse);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        assertThat(records).isNotEmpty();

        createdInvoiceApplyId = ((Number) records.get(0).get("id")).longValue();
        log.info("开票申请 ID: {}", createdInvoiceApplyId);
    }

    @Test
    @Order(5)
    @DisplayName("查询开票申请详情（GET /api/v1/finance/invoice-apply/{id}）")
    void step5_getInvoiceApplyDetail() {
        assertThat(createdInvoiceApplyId).as("开票申请必须已创建").isNotNull();

        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                INVOICE_APPLY_URL + "/" + createdInvoiceApplyId,
                HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        AssertUtils.assertApiSuccess(response);
        Map<String, Object> data = extractData(response);

        // 验证关键字段
        assertThat(data.get("id")).isNotNull();
        assertThat(((Number) data.get("projectId")).longValue()).isEqualTo(createdProjectId);
        assertThat(((Number) data.get("contractId")).longValue()).isEqualTo(createdContractId);
        assertThat(data.get("status")).isEqualTo("DRAFT");

        // 验证开票金额（BigDecimal 精度）
        BigDecimal invoiceAmount = new BigDecimal(data.get("invoiceAmount").toString());
        assertThat(invoiceAmount).isEqualByComparingTo(new BigDecimal("100000.00"));

        log.info("查询开票申请详情成功，状态: DRAFT，金额: {}", invoiceAmount);
    }

    @Test
    @Order(6)
    @DisplayName("修改开票申请（PUT /api/v1/finance/invoice-apply/{id}）")
    void step6_updateInvoiceApply() {
        assertThat(createdInvoiceApplyId).as("开票申请必须已创建").isNotNull();

        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        // 修改开票金额和发票抬头
        Map<String, Object> updateBody = buildInvoiceApplyRequest(
                new BigDecimal("150000.00"));
        updateBody.put("invoiceTitle", "修改后的发票抬头");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(updateBody, headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                INVOICE_APPLY_URL + "/" + createdInvoiceApplyId,
                HttpMethod.PUT, request,
                new ParameterizedTypeReference<>() {});

        AssertUtils.assertApiSuccess(response);
        log.info("修改开票申请成功");

        // 验证修改后的数据
        ResponseEntity<Map<String, Object>> detailResponse = restTemplate.exchange(
                INVOICE_APPLY_URL + "/" + createdInvoiceApplyId,
                HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        AssertUtils.assertApiSuccess(detailResponse);
        Map<String, Object> data = extractData(detailResponse);

        BigDecimal updatedAmount = new BigDecimal(data.get("invoiceAmount").toString());
        assertThat(updatedAmount).isEqualByComparingTo(new BigDecimal("150000.00"));
        assertThat(data.get("invoiceTitle")).isEqualTo("修改后的发票抬头");

        log.info("验证修改后金额: {}，发票抬头: {}", updatedAmount, data.get("invoiceTitle"));
    }

    @Test
    @Order(7)
    @DisplayName("提交开票申请 - 金额校验（开票金额超过可开票金额时应拒绝）")
    void step7_submitInvoiceApply_amountValidation() {
        assertThat(createdInvoiceApplyId).as("开票申请必须已创建").isNotNull();

        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        // 查询合同当前的累计产值和累计开票金额
        ResponseEntity<Map<String, Object>> contractResponse = restTemplate.exchange(
                CONTRACT_URL + "/" + createdContractId,
                HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        AssertUtils.assertApiSuccess(contractResponse);
        Map<String, Object> contractData = extractData(contractResponse);

        BigDecimal cumulativeOutput = contractData.get("cumulativeOutput") != null
                ? new BigDecimal(contractData.get("cumulativeOutput").toString()) : BigDecimal.ZERO;
        BigDecimal cumulativeInvoiced = contractData.get("cumulativeInvoiceAmount") != null
                ? new BigDecimal(contractData.get("cumulativeInvoiceAmount").toString()) : BigDecimal.ZERO;
        BigDecimal maxInvoiceAmount = cumulativeOutput.subtract(cumulativeInvoiced);

        log.info("合同累计产值: {}, 累计已开票: {}, 最大可开票: {}",
                cumulativeOutput, cumulativeInvoiced, maxInvoiceAmount);

        // 当前开票申请金额为 150000.00（step6 已修改）
        // 如果 maxInvoiceAmount < 150000，提交应该失败（金额校验生效）
        BigDecimal currentInvoiceAmount = new BigDecimal("150000.00");

        if (currentInvoiceAmount.compareTo(maxInvoiceAmount) > 0) {
            // 预期提交会因金额校验失败 —— 验证业务规则正确性
            log.info("开票金额 {} > 最大可开票 {}，预期提交被拒绝", currentInvoiceAmount, maxInvoiceAmount);

            HttpEntity<Void> request = new HttpEntity<>(headers);
            try {
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                        INVOICE_APPLY_URL + "/" + createdInvoiceApplyId + "/submit",
                        HttpMethod.POST, request,
                        new ParameterizedTypeReference<>() {});

                // 服务端可能返回 200 但 code!=200（业务异常）
                Map<String, Object> body = response.getBody();
                assertThat(body).isNotNull();
                int code = ((Number) body.get("code")).intValue();
                assertThat(code)
                        .as("提交应返回业务错误码（开票金额超过可开票金额）")
                        .isNotEqualTo(200);
                String message = (String) body.get("message");
                assertThat(message).contains("开票金额");
                log.info("金额校验拒绝成功，错误信息: {}", message);
            } catch (HttpClientErrorException e) {
                // 服务端也可能返回 4xx/5xx
                log.info("金额校验拒绝成功（HTTP {}），响应: {}",
                        e.getStatusCode().value(), e.getResponseBodyAsString());
                assertThat(e.getStatusCode().value()).isBetween(400, 599);
            }
        } else {
            // 如果合同已有足够累计产值，走正常提交流程
            log.info("开票金额 {} <= 最大可开票 {}，执行正常提交", currentInvoiceAmount, maxInvoiceAmount);

            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    INVOICE_APPLY_URL + "/" + createdInvoiceApplyId + "/submit",
                    HttpMethod.POST, request,
                    new ParameterizedTypeReference<>() {});

            AssertUtils.assertApiSuccess(response);
            log.info("开票申请提交成功");

            // 验证提交后状态变更
            ResponseEntity<Map<String, Object>> detailResponse = restTemplate.exchange(
                    INVOICE_APPLY_URL + "/" + createdInvoiceApplyId,
                    HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});

            AssertUtils.assertApiSuccess(detailResponse);
            Map<String, Object> invoiceData = extractData(detailResponse);
            assertThat(invoiceData.get("status")).isEqualTo("APPROVED");
            assertThat(invoiceData.get("workflowInstanceId")).isNotNull();

            // 验证合同累计开票金额回写
            ResponseEntity<Map<String, Object>> contractAfterSubmit = restTemplate.exchange(
                    CONTRACT_URL + "/" + createdContractId,
                    HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});

            AssertUtils.assertApiSuccess(contractAfterSubmit);
            Map<String, Object> contractAfterData = extractData(contractAfterSubmit);
            BigDecimal newCumulativeInvoiced = new BigDecimal(
                    contractAfterData.get("cumulativeInvoiceAmount").toString());
            BigDecimal expectedCumulativeInvoiced = cumulativeInvoiced.add(currentInvoiceAmount);
            assertThat(newCumulativeInvoiced)
                    .as("合同累计开票金额应回写为: 原累计 %s + 本次开票 %s = %s",
                            cumulativeInvoiced, currentInvoiceAmount, expectedCumulativeInvoiced)
                    .isEqualByComparingTo(expectedCumulativeInvoiced);

            log.info("验证合同累计开票回写成功: {}", newCumulativeInvoiced);
        }
    }

    @Test
    @Order(8)
    @DisplayName("分页查询开票申请（按合同ID筛选）")
    void step8_pageQueryByContractId() {
        assertThat(createdContractId).as("前置合同必须已创建").isNotNull();

        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers = buildAuthHeaders();

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                INVOICE_APPLY_URL + "/page?page=1&size=10&contractId=" + createdContractId,
                HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        AssertUtils.assertApiSuccess(response);
        Map<String, Object> data = extractData(response);
        AssertUtils.assertPageResult(data, 1);

        log.info("分页查询成功，记录数 >= 1");
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建开票申请请求体
     */
    private Map<String, Object> buildInvoiceApplyRequest(BigDecimal invoiceAmount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", createdProjectId);
        body.put("contractId", createdContractId);
        body.put("invoiceType", "SPECIAL");
        body.put("invoiceAmount", invoiceAmount);
        body.put("invoiceTitle", "测试发票抬头-集成测试");
        body.put("taxpayerId", "91110000MA0XXXXX");
        body.put("bankAccount", "1234567890123456789");
        body.put("bankName", "中国建设银行测试支行");
        return body;
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

    /**
     * 清理本测试创建的资源（逆序删除避免外键冲突）
     */
    private void cleanupCreatedResources() {
        RestTemplate restTemplate = getRestTemplate();
        HttpHeaders headers;
        try {
            headers = buildAuthHeaders();
        } catch (Exception e) {
            log.warn("清理时获取认证 token 失败，跳过 API 清理: {}", e.getMessage());
            return;
        }

        // 逆序清理：开票申请 → 合同 → 项目
        if (createdInvoiceApplyId != null) {
            try {
                restTemplate.exchange(
                        INVOICE_APPLY_URL + "/" + createdInvoiceApplyId,
                        HttpMethod.DELETE, new HttpEntity<>(headers),
                        new ParameterizedTypeReference<Map<String, Object>>() {});
                log.info("已清理开票申请: {}", createdInvoiceApplyId);
            } catch (Exception e) {
                log.warn("清理开票申请失败（ID={}）: {}", createdInvoiceApplyId, e.getMessage());
            }
        }

        if (createdContractId != null) {
            try {
                restTemplate.exchange(
                        CONTRACT_URL + "/" + createdContractId,
                        HttpMethod.DELETE, new HttpEntity<>(headers),
                        new ParameterizedTypeReference<Map<String, Object>>() {});
                log.info("已清理合同: {}", createdContractId);
            } catch (Exception e) {
                log.warn("清理合同失败（ID={}，可能非 DRAFT 状态）: {}", createdContractId, e.getMessage());
            }
        }

        if (createdProjectId != null) {
            try {
                restTemplate.exchange(
                        PROJECT_URL + "/" + createdProjectId,
                        HttpMethod.DELETE, new HttpEntity<>(headers),
                        new ParameterizedTypeReference<Map<String, Object>>() {});
                log.info("已清理项目: {}", createdProjectId);
            } catch (Exception e) {
                log.warn("清理项目失败（ID={}）: {}", createdProjectId, e.getMessage());
            }
        }
    }
}
