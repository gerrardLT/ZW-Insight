package com.zwinsight.workflow.service.rollback;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.workflow.domain.BizApprovalRollbackLog;
import com.zwinsight.workflow.domain.BizApprovalSnapshot;
import com.zwinsight.workflow.dto.RollbackLogQuery;
import com.zwinsight.workflow.dto.RollbackLogVO;
import com.zwinsight.workflow.mapper.BizApprovalRollbackLogMapper;
import com.zwinsight.workflow.mapper.BizApprovalSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ApprovalRollbackServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ApprovalRollbackServiceImplTest {

    @Mock private BizApprovalSnapshotMapper snapshotMapper;
    @Mock private BizApprovalRollbackLogMapper rollbackLogMapper;
    @Mock private RollbackStrategyRegistry strategyRegistry;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ApprovalRollbackServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ApprovalRollbackServiceImpl(
                snapshotMapper, rollbackLogMapper, strategyRegistry,
                new ObjectMapper(), eventPublisher);
    }

    // ===== saveSnapshot =====

    @Test
    @DisplayName("保存快照：数据为空时跳过，不落库")
    void testSaveSnapshot_emptySkips() {
        service.saveSnapshot("wf-1", "BUDGET", 1L, Collections.emptyMap());

        verify(snapshotMapper, never()).insert(any());
        verify(snapshotMapper, never()).delete(any());
    }

    @Test
    @DisplayName("保存快照：先删旧快照再逐字段插入")
    void testSaveSnapshot_deletesOldAndInsertsPerField() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", "100");
        data.put("status", "DRAFT");

        service.saveSnapshot("wf-1", "BUDGET", 1L, data);

        verify(snapshotMapper).delete(any());
        verify(snapshotMapper, times(2)).insert(any(BizApprovalSnapshot.class));
    }

    // ===== executeRollback =====

    @Test
    @DisplayName("执行回滚：无快照返回失败")
    void testExecuteRollback_noSnapshot_fails() {
        when(snapshotMapper.selectList(any())).thenReturn(Collections.emptyList());

        RollbackResult result = service.executeRollback("wf-1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(BizApprovalRollbackLog.STATUS_FAILED);
        assertThat(result.getMessage()).contains("未找到快照数据");
    }

    @Test
    @DisplayName("执行回滚：无对应策略返回失败并记日志")
    void testExecuteRollback_noStrategy_fails() {
        BizApprovalSnapshot snapshot = new BizApprovalSnapshot();
        snapshot.setBizType("UNKNOWN_TYPE");
        snapshot.setBizId(1L);
        snapshot.setFieldName("amount");
        snapshot.setOriginalValue("100");
        when(snapshotMapper.selectList(any())).thenReturn(List.of(snapshot));
        when(strategyRegistry.getStrategy("UNKNOWN_TYPE")).thenReturn(null);

        RollbackResult result = service.executeRollback("wf-1");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("未找到业务类型");
        verify(rollbackLogMapper).insert(any(BizApprovalRollbackLog.class));
    }

    @Test
    @DisplayName("执行回滚：策略执行成功返回成功并记日志")
    void testExecuteRollback_success() {
        BizApprovalSnapshot snapshot = new BizApprovalSnapshot();
        snapshot.setBizType("BUDGET");
        snapshot.setBizId(1L);
        snapshot.setFieldName("amount");
        snapshot.setOriginalValue("100");
        when(snapshotMapper.selectList(any())).thenReturn(List.of(snapshot));

        RollbackStrategy strategy = mock(RollbackStrategy.class);
        when(strategyRegistry.getStrategy("BUDGET")).thenReturn(strategy);

        RollbackResult result = service.executeRollback("wf-1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(BizApprovalRollbackLog.STATUS_SUCCESS);
        verify(strategy).rollback(eq(1L), anyMap());
        verify(rollbackLogMapper).insert(any(BizApprovalRollbackLog.class));
    }

    // ===== confirmConflict =====

    @Test
    @DisplayName("确认冲突：日志不存在抛异常")
    void testConfirmConflict_notFound() {
        when(rollbackLogMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.confirmConflict(999L, "已处理"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("回滚日志不存在");
    }

    @Test
    @DisplayName("确认冲突：非冲突状态拒绝")
    void testConfirmConflict_nonConflictStatus_rejected() {
        BizApprovalRollbackLog log = new BizApprovalRollbackLog();
        log.setRollbackStatus(BizApprovalRollbackLog.STATUS_SUCCESS);
        when(rollbackLogMapper.selectById(1L)).thenReturn(log);

        assertThatThrownBy(() -> service.confirmConflict(1L, "已处理"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态不允许确认冲突");
        verify(rollbackLogMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("确认冲突：冲突状态置成功并更新")
    void testConfirmConflict_conflictStatus_updates() {
        BizApprovalRollbackLog log = new BizApprovalRollbackLog();
        log.setRollbackStatus(BizApprovalRollbackLog.STATUS_CONFLICT);
        when(rollbackLogMapper.selectById(1L)).thenReturn(log);
        when(rollbackLogMapper.updateById(log)).thenReturn(1);

        service.confirmConflict(1L, "人工确认无误");

        assertThat(log.getRollbackStatus()).isEqualTo(BizApprovalRollbackLog.STATUS_SUCCESS);
        assertThat(log.getErrorMsg()).contains("人工确认无误");
        verify(rollbackLogMapper).updateById(log);
    }

    // ===== queryRollbackLogs =====

    @Test
    @DisplayName("查询回滚日志：转换为 VO 并返回分页")
    void testQueryRollbackLogs_returnsVOs() {
        BizApprovalRollbackLog log = new BizApprovalRollbackLog();
        log.setId(1L);
        log.setWorkflowInstanceId("wf-1");
        log.setBizType("BUDGET");
        log.setRollbackStatus(BizApprovalRollbackLog.STATUS_SUCCESS);

        Page<BizApprovalRollbackLog> page = new Page<>(1, 10);
        page.setRecords(List.of(log));
        page.setTotal(1);
        when(rollbackLogMapper.selectPage(any(Page.class), any())).thenReturn(page);

        RollbackLogQuery query = new RollbackLogQuery();
        query.setPage(1);
        query.setSize(10);

        PageResult<RollbackLogVO> result = service.queryRollbackLogs(query);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getStatusDesc()).isEqualTo("成功");
    }
}
