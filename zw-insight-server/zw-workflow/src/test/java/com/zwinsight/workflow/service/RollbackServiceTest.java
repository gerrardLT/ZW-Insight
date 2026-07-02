package com.zwinsight.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.workflow.domain.WfRollbackAction;
import com.zwinsight.workflow.mapper.WfRollbackActionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RollbackService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RollbackServiceTest {

    @Mock
    private WfRollbackActionMapper rollbackActionMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private RollbackService rollbackService;

    // =====================================================================
    // registerAction
    // =====================================================================

    @Test
    @DisplayName("注册回滚操作：已有记录时排序号递增")
    void testRegisterAction_incrementSortOrder() {
        WfRollbackAction lastAction = new WfRollbackAction();
        lastAction.setSortOrder(3);
        when(rollbackActionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(lastAction);
        when(rollbackActionMapper.insert(any(WfRollbackAction.class))).thenReturn(1);

        rollbackService.registerAction("pi-001", "biz_contract", "contract_amount",
                100L, "ADD", new BigDecimal("5000"), new BigDecimal("10000"));

        ArgumentCaptor<WfRollbackAction> captor = ArgumentCaptor.forClass(WfRollbackAction.class);
        verify(rollbackActionMapper).insert(captor.capture());

        WfRollbackAction inserted = captor.getValue();
        assertThat(inserted.getSortOrder()).isEqualTo(4);
        assertThat(inserted.getProcessInstanceId()).isEqualTo("pi-001");
        assertThat(inserted.getTargetTable()).isEqualTo("biz_contract");
        assertThat(inserted.getOperationType()).isEqualTo("ADD");
        assertThat(inserted.getExecuted()).isEqualTo(0);
    }

    @Test
    @DisplayName("注册回滚操作：首条记录排序号为 1")
    void testRegisterAction_firstRecordSortOrderIsOne() {
        when(rollbackActionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(rollbackActionMapper.insert(any(WfRollbackAction.class))).thenReturn(1);

        rollbackService.registerAction("pi-002", "biz_budget", "amount",
                200L, "SET", new BigDecimal("0"), new BigDecimal("8000"));

        ArgumentCaptor<WfRollbackAction> captor = ArgumentCaptor.forClass(WfRollbackAction.class);
        verify(rollbackActionMapper).insert(captor.capture());
        assertThat(captor.getValue().getSortOrder()).isEqualTo(1);
    }

    // =====================================================================
    // rollback — ADD 回滚为减
    // =====================================================================

    @Test
    @DisplayName("回滚 ADD 操作：执行 SQL 减法")
    void testRollback_addOperation_executesSubtract() {
        WfRollbackAction action = buildAction("pi-001", "biz_contract", "contract_amount",
                100L, "ADD", new BigDecimal("3000"), null, 0, 2);

        when(rollbackActionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(action));
        when(jdbcTemplate.update(anyString(), any(BigDecimal.class), anyLong())).thenReturn(1);

        rollbackService.rollback("pi-001");

        verify(jdbcTemplate).update(
                eq("UPDATE biz_contract SET contract_amount = contract_amount - ? WHERE id = ?"),
                eq(new BigDecimal("3000")), eq(100L));
        verify(rollbackActionMapper).updateById(argThat(a -> a.getExecuted() == 1));
    }

    // =====================================================================
    // rollback — SUBTRACT 回滚为加
    // =====================================================================

    @Test
    @DisplayName("回滚 SUBTRACT 操作：执行 SQL 加法")
    void testRollback_subtractOperation_executesAdd() {
        WfRollbackAction action = buildAction("pi-001", "biz_material", "quantity",
                200L, "SUBTRACT", new BigDecimal("50"), null, 0, 1);

        when(rollbackActionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(action));
        when(jdbcTemplate.update(anyString(), any(BigDecimal.class), anyLong())).thenReturn(1);

        rollbackService.rollback("pi-001");

        verify(jdbcTemplate).update(
                eq("UPDATE biz_material SET quantity = quantity + ? WHERE id = ?"),
                eq(new BigDecimal("50")), eq(200L));
    }

    // =====================================================================
    // rollback — SET 恢复原始值
    // =====================================================================

    @Test
    @DisplayName("回滚 SET 操作：恢复原始值")
    void testRollback_setOperation_restoresOriginalValue() {
        WfRollbackAction action = buildAction("pi-001", "biz_project", "status_code",
                300L, "SET", new BigDecimal("1"), new BigDecimal("0"), 0, 1);

        when(rollbackActionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(action));
        when(jdbcTemplate.update(anyString(), any(BigDecimal.class), anyLong())).thenReturn(1);

        rollbackService.rollback("pi-001");

        verify(jdbcTemplate).update(
                eq("UPDATE biz_project SET status_code = ? WHERE id = ?"),
                eq(new BigDecimal("0")), eq(300L));
    }

    // =====================================================================
    // rollback — 无待回滚操作
    // =====================================================================

    @Test
    @DisplayName("回滚：无待回滚操作时跳过")
    void testRollback_noActions_skips() {
        when(rollbackActionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        rollbackService.rollback("pi-empty");

        verifyNoInteractions(jdbcTemplate);
        verify(rollbackActionMapper, never()).updateById(any());
    }

    // =====================================================================
    // rollback — 未知操作类型
    // =====================================================================

    @Test
    @DisplayName("回滚：未知操作类型抛 BusinessException")
    void testRollback_unknownOpType_throwsException() {
        WfRollbackAction action = buildAction("pi-001", "t", "f",
                1L, "UNKNOWN", BigDecimal.ONE, null, 0, 1);

        when(rollbackActionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(action));

        assertThatThrownBy(() -> rollbackService.rollback("pi-001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知的回滚操作类型");
    }

    // =====================================================================
    // getRollbackActions
    // =====================================================================

    @Test
    @DisplayName("查询回滚操作列表")
    void testGetRollbackActions() {
        WfRollbackAction a1 = buildAction("pi-001", "t1", "f1", 1L, "ADD", BigDecimal.ONE, null, 0, 1);
        WfRollbackAction a2 = buildAction("pi-001", "t2", "f2", 2L, "SUBTRACT", BigDecimal.TEN, null, 0, 2);
        when(rollbackActionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(a1, a2));

        List<WfRollbackAction> result = rollbackService.getRollbackActions("pi-001");

        assertThat(result).hasSize(2);
    }

    // ===== 辅助方法 =====

    private WfRollbackAction buildAction(String pi, String table, String field, Long targetId,
                                         String opType, BigDecimal value, BigDecimal originalValue,
                                         int executed, int sortOrder) {
        WfRollbackAction action = new WfRollbackAction();
        action.setProcessInstanceId(pi);
        action.setTargetTable(table);
        action.setTargetField(field);
        action.setTargetId(targetId);
        action.setOperationType(opType);
        action.setOperValue(value);
        action.setOriginalValue(originalValue);
        action.setExecuted(executed);
        action.setSortOrder(sortOrder);
        return action;
    }
}
