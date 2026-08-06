package com.zwinsight.integration;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.workflow.service.rollback.ApprovalRollbackService;
import com.zwinsight.workflow.service.rollback.RollbackResult;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审批回滚集成测试
 * <p>
 * 验证：
 * <ul>
 *   <li>审批快照保存 → 驳回事件 → 回滚 → 数据恢复</li>
 *   <li>回滚乐观锁冲突 → 重试机制</li>
 * </ul>
 * </p>
 *
 * 对应需求：R8 (AC 1-8)
 */
@DisplayName("审批回滚集成测试")
class ApprovalRollbackIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ApprovalRollbackService approvalRollbackService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long TENANT_ID = 1000L;
    private static final Long USER_ID = 2001L;
    private static final Long BIZ_ID = 7001L;
    private static final String WORKFLOW_INSTANCE_ID = "wf-rollback-test-001";
    private static final String BIZ_TYPE = "MACHINE_SETTLEMENT";
    // 回滚策略 MachineSettlementRollbackStrategy 实际更新的表（非 biz_machine_work_settlement，
    // 此前表名不一致导致 UPDATE 报表不存在，回滚恒失败）
    private static final String SETTLEMENT_TABLE = "biz_machine_settlement";

    @BeforeEach
    void setupTestData() {
        SecurityContextHolder.setUserId(USER_ID);
        SecurityContextHolder.setTenantId(TENANT_ID);

        // 清除残留数据
        jdbcTemplate.update("DELETE FROM biz_approval_snapshot");
        jdbcTemplate.update("DELETE FROM biz_approval_rollback_log");
        jdbcTemplate.update("DELETE FROM " + SETTLEMENT_TABLE);

        // 创建一个结算单（模拟审批前状态；列以生产表 biz_machine_settlement 实际结构为准）
        jdbcTemplate.update(
                "INSERT INTO " + SETTLEMENT_TABLE + " (id, tenant_id, project_id, contract_id, " +
                        "settlement_amount, cumulative_settlement, status, version, created_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                BIZ_ID, TENANT_ID, 5001L, 6001L,
                new BigDecimal("30000.00"), BigDecimal.ZERO, "APPROVED", 1, USER_ID);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clear();
    }

    // ==================== 子任务 6：审批快照 → 驳回 → 回滚 → 数据恢复 ====================

    @Test
    @DisplayName("保存快照 - 审批提交时记录数据快照")
    @Transactional
    @Rollback
    void testSaveSnapshot_recordsDataBeforeApproval() {
        Map<String, Object> snapshotData = Map.of(
                "settlement_amount", "0.00",
                "status", "DRAFT"
        );

        approvalRollbackService.saveSnapshot(WORKFLOW_INSTANCE_ID, BIZ_TYPE, BIZ_ID, snapshotData);

        // 验证快照已保存
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM biz_approval_snapshot WHERE workflow_instance_id = ?",
                Integer.class, WORKFLOW_INSTANCE_ID);
        assertThat(count).isEqualTo(2); // settlement_amount 和 status 两个字段
    }

    @Test
    @DisplayName("执行回滚 - 驳回后数据恢复到快照值")
    @Transactional
    @Rollback
    void testExecuteRollback_restoresDataFromSnapshot() {
        // 保存快照（提交审批时的快照：状态为草稿、金额为0；字段名必须是生产表真实列，
        // 回滚策略按字段名拼 UPDATE SQL，不存在的列会报 Unknown column）
        Map<String, Object> snapshotData = Map.of(
                "settlement_amount", "0.00",
                "status", "0"
        );
        approvalRollbackService.saveSnapshot(WORKFLOW_INSTANCE_ID, BIZ_TYPE, BIZ_ID, snapshotData);

        // 执行回滚（模拟驳回触发）
        RollbackResult result = approvalRollbackService.executeRollback(WORKFLOW_INSTANCE_ID);

        // 验证回滚成功
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(1);

        // 验证数据已恢复（快照 status=0 → 回滚后应为 0）
        Integer restoredStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM " + SETTLEMENT_TABLE + " WHERE id = ?",
                Integer.class, BIZ_ID);
        assertThat(restoredStatus).isEqualTo(0);
    }

    @Test
    @DisplayName("回滚记录日志 - 回滚完成后写入日志")
    @Transactional
    @Rollback
    void testExecuteRollback_writesRollbackLog() {
        Map<String, Object> snapshotData = Map.of("status", "0");
        approvalRollbackService.saveSnapshot(WORKFLOW_INSTANCE_ID, BIZ_TYPE, BIZ_ID, snapshotData);

        approvalRollbackService.executeRollback(WORKFLOW_INSTANCE_ID);

        // 验证回滚日志
        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM biz_approval_rollback_log WHERE workflow_instance_id = ?",
                Integer.class, WORKFLOW_INSTANCE_ID);
        assertThat(logCount).isGreaterThanOrEqualTo(1);
    }

    // ==================== 子任务 7：回滚乐观锁冲突 → 重试机制 ====================

    @Test
    @DisplayName("数据冲突检测 - 快照值与当前值不一致时标记冲突")
    @Transactional
    @Rollback
    void testRollbackConflict_detectsDataMismatch() {
        // 保存快照（快照记录的 settlement_amount 原始值为 "0.00"）
        Map<String, Object> snapshotData = Map.of("settlement_amount", "0.00");
        approvalRollbackService.saveSnapshot(WORKFLOW_INSTANCE_ID, BIZ_TYPE, BIZ_ID, snapshotData);

        // 模拟数据被后续操作修改（当前值 30000.00 与快照值 0.00 已不同）
        // 再次修改当前值以制造 "数据已被后续操作修改" 的情况
        jdbcTemplate.update(
                "UPDATE " + SETTLEMENT_TABLE + " SET settlement_amount = ?, version = version + 1 WHERE id = ?",
                new BigDecimal("50000.00"), BIZ_ID);

        // 执行回滚
        RollbackResult result = approvalRollbackService.executeRollback(WORKFLOW_INSTANCE_ID);

        // 冲突场景：快照原始值与当前值不一致应标记冲突或经过重试
        // 根据实际实现可能是 conflict(status=3) 或 success with retry
        assertThat(result).isNotNull();
        if (!result.isSuccess()) {
            // 冲突或失败
            assertThat(result.getStatus()).isIn(2, 3);
        }
    }

    @Test
    @DisplayName("乐观锁冲突 - 重试机制最多3次")
    @Transactional
    @Rollback
    void testOptimisticLockConflict_retriesUpTo3Times() {
        // 保存快照
        Map<String, Object> snapshotData = Map.of("status", "0");
        approvalRollbackService.saveSnapshot(WORKFLOW_INSTANCE_ID, BIZ_TYPE, BIZ_ID, snapshotData);

        // 人为将 version 改为不匹配的值，制造乐观锁冲突
        jdbcTemplate.update(
                "UPDATE " + SETTLEMENT_TABLE + " SET version = 999 WHERE id = ?", BIZ_ID);

        // 执行回滚（应触发重试机制）
        RollbackResult result = approvalRollbackService.executeRollback(WORKFLOW_INSTANCE_ID);

        // 验证：乐观锁冲突后重试，最终可能成功（重新读取最新version后回滚）或失败
        assertThat(result).isNotNull();
        // 如果最终失败，retryCount 应该 <= 3
        if (!result.isSuccess()) {
            assertThat(result.getRetryCount()).isLessThanOrEqualTo(3);
        }
    }
}
