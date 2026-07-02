package com.zwinsight.integration.mapper;

import com.zwinsight.budget.mapper.BudgetOccupiedMapper;
import com.zwinsight.integration.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BudgetOccupiedMapper 集成测试（Testcontainers MySQL）
 * <p>
 * 测试跨 4 类合同表的聚合查询，验证预算占用金额计算逻辑。
 * </p>
 */
class BudgetOccupiedMapperTest extends BaseIntegrationTest {

    @Autowired
    private BudgetOccupiedMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    private static final Long PROJECT_ID = 1L;
    private static final Long OTHER_PROJECT_ID = 2L;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM biz_subcontract");
        jdbc.execute("DELETE FROM biz_labor_contract");
        jdbc.execute("DELETE FROM biz_machine_contract");
        jdbc.execute("DELETE FROM biz_purchase_contract");
        jdbc.execute("DELETE FROM biz_payment_apply");

        // 分包合同
        jdbc.update("INSERT INTO biz_subcontract (id, project_id, contract_code, contract_amount, status, deleted, version) "
                + "VALUES (1, ?, 'SUB-001', 100000.00, 'EFFECTIVE', 0, 0)", PROJECT_ID);
        jdbc.update("INSERT INTO biz_subcontract (id, project_id, contract_code, contract_amount, status, deleted, version) "
                + "VALUES (2, ?, 'SUB-002', 50000.00, 'EFFECTIVE', 0, 0)", PROJECT_ID);
        // 草稿状态（不应计入）
        jdbc.update("INSERT INTO biz_subcontract (id, project_id, contract_code, contract_amount, status, deleted, version) "
                + "VALUES (3, ?, 'SUB-003', 99999.00, 'DRAFT', 0, 0)", PROJECT_ID);
        // 已删除（不应计入）
        jdbc.update("INSERT INTO biz_subcontract (id, project_id, contract_code, contract_amount, status, deleted, version) "
                + "VALUES (4, ?, 'SUB-004', 88888.00, 'EFFECTIVE', 1, 0)", PROJECT_ID);
        // 其他项目
        jdbc.update("INSERT INTO biz_subcontract (id, project_id, contract_code, contract_amount, status, deleted, version) "
                + "VALUES (5, ?, 'SUB-005', 200000.00, 'EFFECTIVE', 0, 0)", OTHER_PROJECT_ID);

        // 劳务合同
        jdbc.update("INSERT INTO biz_labor_contract (id, project_id, contract_code, contract_amount, status, deleted, version) "
                + "VALUES (1, ?, 'LAB-001', 80000.00, 'EFFECTIVE', 0, 0)", PROJECT_ID);
        jdbc.update("INSERT INTO biz_labor_contract (id, project_id, contract_code, contract_amount, status, deleted, version) "
                + "VALUES (2, ?, 'LAB-002', 30000.00, 'EFFECTIVE', 0, 0)", PROJECT_ID);

        // 机械合同
        jdbc.update("INSERT INTO biz_machine_contract (id, project_id, contract_code, contract_amount, status, deleted, version) "
                + "VALUES (1, ?, 'MAC-001', 60000.00, 'EFFECTIVE', 0, 0)", PROJECT_ID);

        // 采购合同
        jdbc.update("INSERT INTO biz_purchase_contract (id, project_id, contract_code, contract_amount, status, deleted, version) "
                + "VALUES (1, ?, 'PUR-001', 120000.00, 'EFFECTIVE', 0, 0)", PROJECT_ID);
        jdbc.update("INSERT INTO biz_purchase_contract (id, project_id, contract_code, contract_amount, status, deleted, version) "
                + "VALUES (2, ?, 'PUR-002', 40000.00, 'EFFECTIVE', 0, 0)", PROJECT_ID);

        // 付款申请
        jdbc.update("INSERT INTO biz_payment_apply (id, project_id, contract_id, contract_category, payment_amount, status, deleted, version) "
                + "VALUES (1, ?, 1, 'MATERIAL', 50000.00, 'APPROVED', 0, 0)", PROJECT_ID);
        jdbc.update("INSERT INTO biz_payment_apply (id, project_id, contract_id, contract_category, payment_amount, status, deleted, version) "
                + "VALUES (2, ?, 1, 'MATERIAL', 30000.00, 'APPROVED', 0, 0)", PROJECT_ID);
        // 草稿状态（不应计入）
        jdbc.update("INSERT INTO biz_payment_apply (id, project_id, contract_id, contract_category, payment_amount, status, deleted, version) "
                + "VALUES (3, ?, 1, 'MATERIAL', 99999.00, 'DRAFT', 0, 0)", PROJECT_ID);
        // 其他科目
        jdbc.update("INSERT INTO biz_payment_apply (id, project_id, contract_id, contract_category, payment_amount, status, deleted, version) "
                + "VALUES (4, ?, 1, 'LABOR', 20000.00, 'APPROVED', 0, 0)", PROJECT_ID);
    }

    @Test
    @DisplayName("sumSubcontractAmount — 汇总分包合同已签金额")
    void should_sum_subcontract_amount() {
        BigDecimal total = mapper.sumSubcontractAmount(PROJECT_ID);

        // 100000 + 50000 = 150000（草稿、已删除、其他项目不计入）
        assertThat(total).isEqualByComparingTo(new BigDecimal("150000.00"));
    }

    @Test
    @DisplayName("sumLaborContractAmount — 汇总劳务合同已签金额")
    void should_sum_labor_contract_amount() {
        BigDecimal total = mapper.sumLaborContractAmount(PROJECT_ID);

        // 80000 + 30000 = 110000
        assertThat(total).isEqualByComparingTo(new BigDecimal("110000.00"));
    }

    @Test
    @DisplayName("sumMachineContractAmount — 汇总机械合同已签金额")
    void should_sum_machine_contract_amount() {
        BigDecimal total = mapper.sumMachineContractAmount(PROJECT_ID);

        assertThat(total).isEqualByComparingTo(new BigDecimal("60000.00"));
    }

    @Test
    @DisplayName("sumPurchaseContractAmount — 汇总采购合同已签金额")
    void should_sum_purchase_contract_amount() {
        BigDecimal total = mapper.sumPurchaseContractAmount(PROJECT_ID);

        // 120000 + 40000 = 160000
        assertThat(total).isEqualByComparingTo(new BigDecimal("160000.00"));
    }

    @Test
    @DisplayName("sumContractAmountForMaterial — 按科目查询材料合同金额")
    void should_sum_contract_amount_for_material() {
        BigDecimal total = mapper.sumContractAmountForMaterial(PROJECT_ID);

        assertThat(total).isEqualByComparingTo(new BigDecimal("160000.00"));
    }

    @Test
    @DisplayName("sumApprovedPaymentByCategory — 按科目汇总已审批付款")
    void should_sum_approved_payment_by_category() {
        BigDecimal material = mapper.sumApprovedPaymentByCategory(PROJECT_ID, "MATERIAL");

        // 50000 + 30000 = 80000（草稿和其他科目不计入）
        assertThat(material).isEqualByComparingTo(new BigDecimal("80000.00"));
    }

    @Test
    @DisplayName("sumApprovedPaymentByCategory — 劳务科目")
    void should_sum_approved_payment_for_labor() {
        BigDecimal labor = mapper.sumApprovedPaymentByCategory(PROJECT_ID, "LABOR");

        assertThat(labor).isEqualByComparingTo(new BigDecimal("20000.00"));
    }

    @Test
    @DisplayName("无数据时返回 0 而非 null")
    void should_return_zero_for_empty_project() {
        BigDecimal total = mapper.sumSubcontractAmount(999L);

        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("不同项目数据隔离")
    void should_isolate_by_project() {
        BigDecimal otherTotal = mapper.sumSubcontractAmount(OTHER_PROJECT_ID);

        assertThat(otherTotal).isEqualByComparingTo(new BigDecimal("200000.00"));
    }
}
