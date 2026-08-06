package com.zwinsight.integration;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.purchase.domain.BizPurchaseContract;
import com.zwinsight.purchase.service.PurchaseContractService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 采购合同集成测试（阶段一收尾 1.6.2）
 * <p>
 * 在 Testcontainers 真实 MySQL/Redis 上验证采购合同真实业务规则：
 * <ul>
 *   <li>save 自动生成合同编号并置 DRAFT（依赖 serial_number_rule 编号规则 + Redis 自增）</li>
 *   <li>仅草稿状态可编辑 / 可删除（状态机保护）</li>
 * </ul>
 * 说明：测试数据不传 contractAmount / partyBId —— 预算切面金额为空时放行、
 * 黑名单切面供应商为空时跳过（均为切面自身的合法放行分支），
 * 从而不依赖 biz_budget_detail / biz_supplier_blacklist 表，聚焦合同状态机本身。
 * </p>
 * <p>租户隔离：全部数据 tenant_id=9999（自动化测试租户），@AfterEach 物理清理。</p>
 */
@DisplayName("采购合同集成测试")
class PurchaseContractIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PurchaseContractService purchaseContractService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long PROJECT_ID = 99990001L;
    private static final Long EFFECTIVE_FIXTURE_ID = 99990101L;

    @BeforeEach
    void setupSerialRule() {
        // save 自动编号的真实依赖：PURCHASE_CONTRACT 编号规则（租户 9999）
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM serial_number_rule WHERE business_type = ? AND tenant_id = ?",
                Integer.class, "PURCHASE_CONTRACT", TEST_TENANT_ID);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO serial_number_rule (id, business_type, rule_prefix, date_format, seq_length, tenant_id) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                    99990901L, "PURCHASE_CONTRACT", "CG", "yyyyMMdd", 4, TEST_TENANT_ID);
        }
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM biz_purchase_contract WHERE tenant_id = ?", TEST_TENANT_ID);
    }

    @Test
    @DisplayName("创建采购合同 - 自动生成编号并置草稿")
    void testSave_generatesCodeAndSetsDraft() {
        BizPurchaseContract contract = newContract("集成测试采购合同-创建");

        purchaseContractService.save(contract);

        assertThat(contract.getId()).isNotNull();
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM biz_purchase_contract WHERE id = ?", String.class, contract.getId());
        String code = jdbcTemplate.queryForObject(
                "SELECT contract_code FROM biz_purchase_contract WHERE id = ?", String.class, contract.getId());
        assertThat(status).isEqualTo("DRAFT");
        assertThat(code).isNotBlank();
        assertThat(code).startsWith("CG");
    }

    @Test
    @DisplayName("编辑采购合同 - 草稿可编辑，非草稿拒绝")
    void testUpdate_draftAllowed_nonDraftRejected() {
        // 草稿可编辑
        BizPurchaseContract draft = newContract("集成测试采购合同-编辑");
        purchaseContractService.save(draft);
        draft.setContractName("集成测试采购合同-已编辑");
        purchaseContractService.update(draft);
        String name = jdbcTemplate.queryForObject(
                "SELECT contract_name FROM biz_purchase_contract WHERE id = ?", String.class, draft.getId());
        assertThat(name).isEqualTo("集成测试采购合同-已编辑");

        // 非草稿（EFFECTIVE 固定夹具）拒绝编辑
        insertEffectiveFixture();
        BizPurchaseContract effective = new BizPurchaseContract();
        effective.setId(EFFECTIVE_FIXTURE_ID);
        effective.setContractName("尝试编辑生效合同");
        assertThatThrownBy(() -> purchaseContractService.update(effective))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可编辑");
    }

    @Test
    @DisplayName("删除采购合同 - 草稿可删除，非草稿拒绝")
    void testDelete_draftAllowed_nonDraftRejected() {
        // 草稿可删除
        BizPurchaseContract draft = newContract("集成测试采购合同-删除");
        purchaseContractService.save(draft);
        purchaseContractService.delete(draft.getId());
        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM biz_purchase_contract WHERE id = ?", Integer.class, draft.getId());
        assertThat(remaining).isZero();

        // 非草稿（EFFECTIVE 固定夹具）拒绝删除
        insertEffectiveFixture();
        assertThatThrownBy(() -> purchaseContractService.delete(EFFECTIVE_FIXTURE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    private BizPurchaseContract newContract(String name) {
        BizPurchaseContract contract = new BizPurchaseContract();
        contract.setProjectId(PROJECT_ID);
        contract.setContractName(name);
        contract.setPartyAName("集成测试甲方");
        return contract;
    }

    /** 直接插入一条 EFFECTIVE 合同夹具（绕过状态机，用于验证编辑/删除保护） */
    private void insertEffectiveFixture() {
        jdbcTemplate.update(
                "INSERT INTO biz_purchase_contract (id, project_id, contract_code, contract_name, " +
                        "contract_amount, cumulative_inbound, cumulative_settlement, cumulative_paid, " +
                        "cumulative_invoice_received, status, tenant_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                EFFECTIVE_FIXTURE_ID, PROJECT_ID, "CG-IT-FIX-001", "集成测试生效合同夹具",
                new BigDecimal("100000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "EFFECTIVE", TEST_TENANT_ID);
    }
}
