package com.zwinsight.tender.integration;

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
 * 投标全流程集成测试
 * <p>
 * 测试覆盖：投标登记 → 提交 → 开标中标 → 保证金申请，及状态联动与状态守卫：
 * <ul>
 *   <li>登记后 register=REGISTERED 且项目联动为 TENDERING</li>
 *   <li>提交后 register=SUBMITTED</li>
 *   <li>开标 isWon=1 后 register=WON 且项目联动为 WON</li>
 *   <li>删除守卫：非 REGISTERED 状态的登记禁止删除</li>
 *   <li>保证金申请创建为 DRAFT；非 DRAFT 禁止删除（先经 update 置 PAID 验证守卫后还原）</li>
 * </ul>
 * <p>
 * 说明：保证金提交（DepositApplyService.submit）依赖当前租户部署
 * deposit_apply_approval 流程定义，本测试不覆盖该分支（由 L4 生命周期脚本覆盖）。
 * <p>
 * 租户说明：联调服务器 admin 测试账号归属租户 1（与 FinanceIntegrationTest 同基座），
 * API 写入数据均为 tenant_id=1，故清理按 project_id 维度定向删除
 * （不影响租户其他数据），最后兼容调用 cleaner 清理 9999 残留。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenderFlowIntegrationTest extends IntegrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(TenderFlowIntegrationTest.class);

    private static final String BASE_URL = TestConstants.API_BASE_URL;
    private static final String PROJECT_URL = BASE_URL + "/api/v1/project";
    private static final String REGISTER_URL = BASE_URL + "/api/v1/tender/register";
    private static final String OPEN_BID_URL = BASE_URL + "/api/v1/tender/open-bid";
    private static final String DEPOSIT_APPLY_URL = BASE_URL + "/api/v1/tender/deposit/apply";

    /** 投标登记唯一标识（ASCII，避免中文 query 编码问题） */
    private final String ownerCompanySuffix = "L2IT" + System.currentTimeMillis();

    @Autowired
    private TestDataCleaner cleaner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 前置数据 ID */
    private Long createdProjectId;
    private Long createdRegisterId;
    private Long createdDepositApplyId;

    @BeforeAll
    void setup() {
        setupAuthentication();
        log.info("====== TenderFlowIntegrationTest: 认证完成，开始准备前置数据 ======");
    }

    @AfterAll
    void cleanup() {
        log.info("====== TenderFlowIntegrationTest: 开始清理测试数据 ======");
        // 按 project_id 维度逆序定向清理（API 数据归属租户 1，不能用 tenant_id=9999 兼容清理）
        if (createdProjectId != null) {
            try {
                jdbcTemplate.update("DELETE FROM biz_deposit_apply WHERE project_id = ?", createdProjectId);
                jdbcTemplate.update("DELETE FROM biz_open_bid_record WHERE project_id = ?", createdProjectId);
                jdbcTemplate.update("DELETE FROM biz_tender_register WHERE project_id = ?", createdProjectId);
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
        projectBody.put("projectName", "集成测试-投标流转项目-" + System.currentTimeMillis());
        projectBody.put("projectNature", "新建");
        projectBody.put("projectType", "房屋建筑");
        projectBody.put("ownerCompanyName", "测试业主单位");
        projectBody.put("projectAddress", "测试地址");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(projectBody, headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                PROJECT_URL, HttpMethod.POST, request,
                new ParameterizedTypeReference<>() {});

        AssertUtils.assertApiSuccess(response);

        // 按名称查回获取 ID
        ResponseEntity<Map<String, Object>> pageResponse = restTemplate.exchange(
                PROJECT_URL + "/page?page=1&size=1&projectName=集成测试-投标流转项目",
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

    // ==================== 投标流转核心断言 ====================

    @Test
    @Order(2)
    @DisplayName("投标登记：register=REGISTERED 且项目联动 TENDERING")
    void step2_createRegisterAndAssertStatus() {
        assertThat(createdProjectId).as("前置项目必须已创建").isNotNull();

        Map<String, Object> registerBody = new LinkedHashMap<>();
        registerBody.put("projectId", createdProjectId);
        registerBody.put("ownerCompany", ownerCompanySuffix);
        registerBody.put("bidMethod", "L2TEST");
        registerBody.put("registerDate", LocalDate.now().toString());

        ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                REGISTER_URL, HttpMethod.POST, new HttpEntity<>(registerBody, buildAuthHeaders()),
                new ParameterizedTypeReference<>() {});
        AssertUtils.assertApiSuccess(response);

        // save 返回 R<Void> 无 ID，按 projectId + ownerCompany 查回
        createdRegisterId = findRegisterIdByOwnerCompany();
        assertThat(createdRegisterId).as("投标登记必须创建成功").isNotNull();

        // 断言登记状态
        Map<String, Object> register = getRegisterById(createdRegisterId);
        assertThat(register.get("status")).isEqualTo("REGISTERED");

        // 断言项目状态联动为 TENDERING
        assertThat(getProjectStatus(createdProjectId)).isEqualTo("TENDERING");
        log.info("投标登记创建成功: registerId={}, 项目已联动为 TENDERING", createdRegisterId);
    }

    @Test
    @Order(3)
    @DisplayName("提交投标登记：register=SUBMITTED")
    void step3_submitRegister() {
        assertThat(createdRegisterId).as("前置登记必须已创建").isNotNull();

        ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                REGISTER_URL + "/" + createdRegisterId + "/submit", HttpMethod.POST,
                new HttpEntity<>(buildAuthHeaders()),
                new ParameterizedTypeReference<>() {});
        AssertUtils.assertApiSuccess(response);

        Map<String, Object> register = getRegisterById(createdRegisterId);
        assertThat(register.get("status")).isEqualTo("SUBMITTED");
        log.info("投标登记提交成功: registerId={}", createdRegisterId);
    }

    @Test
    @Order(4)
    @DisplayName("开标中标：register=WON 且项目联动 WON")
    void step4_openBidWon() {
        assertThat(createdRegisterId).as("前置登记必须已创建").isNotNull();

        Map<String, Object> openBidBody = new LinkedHashMap<>();
        openBidBody.put("registerId", createdRegisterId);
        openBidBody.put("projectId", createdProjectId);
        openBidBody.put("isWon", 1);
        openBidBody.put("winInfo", "L2集成测试中标");

        ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                OPEN_BID_URL, HttpMethod.POST, new HttpEntity<>(openBidBody, buildAuthHeaders()),
                new ParameterizedTypeReference<>() {});
        AssertUtils.assertApiSuccess(response);

        // 断言登记状态联动为 WON
        Map<String, Object> register = getRegisterById(createdRegisterId);
        assertThat(register.get("status")).isEqualTo("WON");

        // 断言项目状态联动为 WON
        assertThat(getProjectStatus(createdProjectId)).isEqualTo("WON");
        log.info("开标中标联动验证通过: register=WON, project=WON");
    }

    @Test
    @Order(5)
    @DisplayName("负向：非报名状态的投标登记禁止删除")
    void step5_deleteGuardForNonRegisteredStatus() {
        assertThat(createdRegisterId).as("前置登记必须已创建").isNotNull();

        ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                REGISTER_URL + "/" + createdRegisterId, HttpMethod.DELETE,
                new HttpEntity<>(buildAuthHeaders()),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(AssertUtils.asLong(body.get("code")))
                .as("非 REGISTERED 状态删除必须被拒绝")
                .isNotEqualTo(200L);
        String msg = String.valueOf(body.getOrDefault("msg", body.get("message")));
        assertThat(msg).contains("仅报名状态可删除");
        log.info("删除状态守卫验证通过: {}", msg);
    }

    @Test
    @Order(6)
    @DisplayName("保证金申请：创建后状态为 DRAFT")
    void step6_createDepositApply() {
        assertThat(createdRegisterId).as("前置登记必须已创建").isNotNull();

        Map<String, Object> depositBody = new LinkedHashMap<>();
        depositBody.put("registerId", createdRegisterId);
        depositBody.put("projectId", createdProjectId);
        depositBody.put("depositAmount", new BigDecimal("5000.00"));
        depositBody.put("paymentDate", LocalDate.now().toString());

        ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                DEPOSIT_APPLY_URL, HttpMethod.POST, new HttpEntity<>(depositBody, buildAuthHeaders()),
                new ParameterizedTypeReference<>() {});
        AssertUtils.assertApiSuccess(response);

        // 按 projectId 查回断言状态 DRAFT
        Map<String, Object> depositApply = findDepositApplyByProject();
        assertThat(depositApply).as("保证金申请必须创建成功").isNotNull();
        assertThat(depositApply.get("status")).isEqualTo("DRAFT");
        createdDepositApplyId = AssertUtils.asLong(depositApply.get("id"));
        log.info("保证金申请创建成功: depositApplyId={}, status=DRAFT", createdDepositApplyId);
    }

    @Test
    @Order(7)
    @DisplayName("负向：保证金申请 update 不可篡改 status + 非草稿禁止删除")
    void step7_depositDeleteGuardForNonDraftStatus() {
        assertThat(createdDepositApplyId).as("前置保证金申请必须已创建").isNotNull();

        // ① 防篡改钉住（2026-08-12 批次二修复）：PUT 体携带 status=PAID 不得落库
        Map<String, Object> updateBody = new LinkedHashMap<>();
        updateBody.put("registerId", createdRegisterId);
        updateBody.put("projectId", createdProjectId);
        updateBody.put("depositAmount", new BigDecimal("5000.00"));
        updateBody.put("status", "PAID");

        ResponseEntity<Map<String, Object>> updateResponse = getRestTemplate().exchange(
                DEPOSIT_APPLY_URL + "/" + createdDepositApplyId, HttpMethod.PUT,
                new HttpEntity<>(updateBody, buildAuthHeaders()),
                new ParameterizedTypeReference<>() {});
        AssertUtils.assertApiSuccess(updateResponse);

        String statusAfterTamper = jdbcTemplate.queryForObject(
                "SELECT status FROM biz_deposit_apply WHERE id = ?", String.class, createdDepositApplyId);
        assertThat(statusAfterTamper).as("PUT 携带 status 不得落库（防篡改）").isEqualTo("DRAFT");

        // ② 删除守卫：直置 PAID 后删除必须被拒绝（真实 DB 状态构造，不依赖 update 漏洞）
        jdbcTemplate.update("UPDATE biz_deposit_apply SET status = 'PAID' WHERE id = ?", createdDepositApplyId);

        ResponseEntity<Map<String, Object>> deleteResponse = getRestTemplate().exchange(
                DEPOSIT_APPLY_URL + "/" + createdDepositApplyId, HttpMethod.DELETE,
                new HttpEntity<>(buildAuthHeaders()),
                new ParameterizedTypeReference<>() {});

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = deleteResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(AssertUtils.asLong(body.get("code")))
                .as("非 DRAFT 状态删除必须被拒绝")
                .isNotEqualTo(200L);
        String msg = String.valueOf(body.getOrDefault("msg", body.get("message")));
        assertThat(msg).contains("仅草稿状态可删除");
        log.info("保证金删除状态守卫验证通过: {}", msg);

        // 还原为 DRAFT，便于后续清理
        jdbcTemplate.update("UPDATE biz_deposit_apply SET status = 'DRAFT' WHERE id = ?", createdDepositApplyId);
    }

    // ==================== 辅助方法 ====================

    /**
     * 按 projectId 分页查询投标登记并按 ownerCompany 定位记录 ID
     */
    private Long findRegisterIdByOwnerCompany() {
        ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                REGISTER_URL + "/page?page=1&size=50&projectId=" + createdProjectId,
                HttpMethod.GET, new HttpEntity<>(buildAuthHeaders()),
                new ParameterizedTypeReference<>() {});
        AssertUtils.assertApiSuccess(response);

        Map<String, Object> data = extractData(response);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        for (Map<String, Object> record : records) {
            if (ownerCompanySuffix.equals(record.get("ownerCompany"))) {
                return AssertUtils.asLong(record.get("id"));
            }
        }
        return null;
    }

    /**
     * 查询投标登记详情
     */
    private Map<String, Object> getRegisterById(Long registerId) {
        ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                REGISTER_URL + "/" + registerId,
                HttpMethod.GET, new HttpEntity<>(buildAuthHeaders()),
                new ParameterizedTypeReference<>() {});
        AssertUtils.assertApiSuccess(response);
        return extractData(response);
    }

    /**
     * 查询项目当前状态
     */
    private String getProjectStatus(Long projectId) {
        ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                PROJECT_URL + "/" + projectId,
                HttpMethod.GET, new HttpEntity<>(buildAuthHeaders()),
                new ParameterizedTypeReference<>() {});
        AssertUtils.assertApiSuccess(response);
        Map<String, Object> project = extractData(response);
        return String.valueOf(project.get("status"));
    }

    /**
     * 按 projectId 查询保证金申请记录
     */
    private Map<String, Object> findDepositApplyByProject() {
        ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                DEPOSIT_APPLY_URL + "?page=1&size=50&projectId=" + createdProjectId,
                HttpMethod.GET, new HttpEntity<>(buildAuthHeaders()),
                new ParameterizedTypeReference<>() {});
        AssertUtils.assertApiSuccess(response);

        Map<String, Object> data = extractData(response);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        return records.isEmpty() ? null : records.get(0);
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
