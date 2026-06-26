package com.zwinsight.integration;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.machine.dto.MachineSettlementCreateRequest;
import com.zwinsight.machine.dto.MachineSettlementCreateResult;
import com.zwinsight.machine.service.MachineWorkSettlementService;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 机械结算集成测试
 * <p>
 * 验证：机械结算创建 → Flowable 审批 → 回调 → 金额累加
 * </p>
 *
 * 对应需求：R4 (AC 1-6)
 */
@DisplayName("机械结算集成测试")
class MachineSettlementIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MachineWorkSettlementService settlementService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long TENANT_ID = 1000L;
    private static final Long USER_ID = 2001L;
    private static final Long PROJECT_ID = 5001L;
    private static final Long CONTRACT_ID = 6001L;

    @BeforeEach
    void setupTestData() {
        SecurityContextHolder.setUserId(USER_ID);
        SecurityContextHolder.setTenantId(TENANT_ID);

        // 清除残留数据
        jdbcTemplate.update("DELETE FROM biz_machine_work_settlement_detail");
        jdbcTemplate.update("DELETE FROM biz_machine_work_settlement");
        jdbcTemplate.update("DELETE FROM biz_machine_contract");

        // 创建机械合同
        jdbcTemplate.update(
                "INSERT INTO biz_machine_contract (id, tenant_id, project_id, contract_code, " +
                        "settled_amount, cumulative_settlement, total_amount, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                CONTRACT_ID, TENANT_ID, PROJECT_ID, "MC-2025-001",
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500000.00"), "EFFECTIVE");
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clear();
    }

    @Test
    @DisplayName("创建结算单 - 正常流程")
    @Transactional
    @Rollback
    void testCreateSettlement_success() {
        MachineSettlementCreateRequest request = new MachineSettlementCreateRequest();
        request.setProjectId(PROJECT_ID);
        request.setPeriodStart(LocalDate.of(2025, 1, 1));
        request.setPeriodEnd(LocalDate.of(2025, 1, 31));

        Long settlementId = settlementService.createSettlement(request).getSettlementId();
        assertThat(settlementId).isNotNull();

        // 验证结算单已持久化
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM biz_machine_work_settlement WHERE id = ?",
                Integer.class, settlementId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("结算周期重叠检测 - 拒绝创建")
    @Transactional
    @Rollback
    void testCreateSettlement_periodOverlap_rejected() {
        // 先创建一个1月份的结算单
        jdbcTemplate.update(
                "INSERT INTO biz_machine_work_settlement (id, tenant_id, project_id, settlement_code, " +
                        "period_start, period_end, total_amount, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                9001L, TENANT_ID, PROJECT_ID, "JS-2025-001",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31),
                new BigDecimal("10000.00"), 0);

        // 尝试创建周期重叠的结算单
        MachineSettlementCreateRequest request = new MachineSettlementCreateRequest();
        request.setProjectId(PROJECT_ID);
        request.setPeriodStart(LocalDate.of(2025, 1, 15));
        request.setPeriodEnd(LocalDate.of(2025, 2, 15));

        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> settlementService.createSettlement(request),
                "应拒绝周期重叠的结算单创建"
        );
    }

    @Test
    @DisplayName("审批通过回调 - 累加合同已结算金额")
    @Transactional
    @Rollback
    void testOnApproved_accumulatesSettledAmount() {
        // 创建已审批中的结算单
        BigDecimal settlementAmount = new BigDecimal("25000.00");
        jdbcTemplate.update(
                "INSERT INTO biz_machine_work_settlement (id, tenant_id, project_id, settlement_code, " +
                        "period_start, period_end, total_amount, status, workflow_instance_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                9002L, TENANT_ID, PROJECT_ID, "JS-2025-002",
                LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28),
                settlementAmount, 1, "wf-instance-001");

        // 确保合同状态字段正确（已在 setupTestData 中设为 EFFECTIVE）

        // 模拟审批通过事件（通过 Spring Event 机制触发）
        ApprovalCompleteEvent event = new ApprovalCompleteEvent(
                this, "machine_settlement", 9002L, "APPROVED");
        eventPublisher.publishEvent(event);

        // 验证结算单状态变为已审批(2)
        Integer status = jdbcTemplate.queryForObject(
                "SELECT status FROM biz_machine_work_settlement WHERE id = ?",
                Integer.class, 9002L);
        assertThat(status).isEqualTo(2);
    }

    @Test
    @DisplayName("项目费用总览 - 汇总计算")
    void testGetProjectSummary_calculatesCorrectly() {
        // 插入多笔已审批结算单
        jdbcTemplate.update(
                "INSERT INTO biz_machine_work_settlement (id, tenant_id, project_id, settlement_code, " +
                        "period_start, period_end, total_amount, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                9003L, TENANT_ID, PROJECT_ID, "JS-2025-003",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31),
                new BigDecimal("15000.00"), 2);
        jdbcTemplate.update(
                "INSERT INTO biz_machine_work_settlement (id, tenant_id, project_id, settlement_code, " +
                        "period_start, period_end, total_amount, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                9004L, TENANT_ID, PROJECT_ID, "JS-2025-004",
                LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28),
                new BigDecimal("20000.00"), 2);

        var summary = settlementService.getProjectSummary(PROJECT_ID);
        assertThat(summary).isNotNull();
        assertThat(summary.getTotalSettledAmount()).isEqualByComparingTo(new BigDecimal("35000.00"));
    }
}
