package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.finance.domain.BizProjectSettlement;
import com.zwinsight.finance.mapper.BizProjectSettlementMapper;
import com.zwinsight.finance.mapper.BizSettlementContractDetailMapper;
import com.zwinsight.finance.mapper.SettlementDataMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ProjectSettlementService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ProjectSettlementServiceTest {

    @Mock
    private BizProjectSettlementMapper settlementMapper;

    @Mock
    private BizSettlementContractDetailMapper detailMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @Mock
    private BizConstructionContractMapper constructionContractMapper;

    @Mock
    private SettlementDataMapper settlementDataMapper;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private ProjectSettlementService projectSettlementService;

    // ============ createSettlement 测试 ============

    @Test
    @DisplayName("testCreateSettlement_rejectWhenNotCompleted — 非竣工项目拒绝创建")
    void testCreateSettlement_rejectWhenNotCompleted() {
        // 准备：项目状态为 CONSTRUCTION（施工中），非 COMPLETED
        Long projectId = 1L;
        BizProject project = new BizProject();
        project.setId(projectId);
        project.setStatus("CONSTRUCTION");

        when(projectMapper.selectById(projectId)).thenReturn(project);

        // 执行 & 验证
        BusinessException exception = assertThrows(BusinessException.class,
                () -> projectSettlementService.createSettlement(projectId));

        assertEquals("项目未竣工，无法进行最终结算", exception.getMessage());

        // 验证不会继续查询结算单
        verify(settlementMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("testCreateSettlement_rejectWhenExistingActive — 已有草稿/审批中结算单时拒绝重复创建")
    void testCreateSettlement_rejectWhenExistingActive() {
        // 准备：项目状态为 COMPLETED（已竣工），但已存在进行中的结算单
        Long projectId = 2L;
        BizProject project = new BizProject();
        project.setId(projectId);
        project.setStatus("COMPLETED");

        when(projectMapper.selectById(projectId)).thenReturn(project);
        // 模拟已存在 DRAFT 或 SUBMITTED 状态的结算单
        when(settlementMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        // 执行 & 验证
        BusinessException exception = assertThrows(BusinessException.class,
                () -> projectSettlementService.createSettlement(projectId));

        assertEquals("该项目已存在进行中的结算单", exception.getMessage());

        // 验证不会继续执行后续创建逻辑
        verify(constructionContractMapper, never()).selectList(any());
    }

    // ============ onApproved 测试 ============

    @Test
    @DisplayName("testOnApproved_updatesProjectStatusToClosed — 审批通过后项目状态变为 CLOSED")
    void testOnApproved_updatesProjectStatusToClosed() {
        // 准备：模拟已存在的结算单
        Long settlementId = 100L;
        Long projectId = 10L;

        BizProjectSettlement settlement = new BizProjectSettlement();
        settlement.setId(settlementId);
        settlement.setProjectId(projectId);
        settlement.setStatus("SUBMITTED");

        when(settlementMapper.selectById(settlementId)).thenReturn(settlement);
        when(settlementMapper.updateById(any(BizProjectSettlement.class))).thenReturn(1);
        when(projectMapper.updateStatus(eq(projectId), eq("CLOSED"))).thenReturn(1);

        // 执行
        projectSettlementService.onApproved(settlementId);

        // 验证：结算单状态更新为 APPROVED
        verify(settlementMapper).updateById(argThat(s -> "APPROVED".equals(s.getStatus())));

        // 验证：项目状态更新为 CLOSED
        verify(projectMapper).updateStatus(projectId, "CLOSED");
    }

    // ============ onRejected 测试 ============

    @Test
    @DisplayName("testOnRejected_keepProjectStatusUnchanged — 驳回后项目状态不变")
    void testOnRejected_keepProjectStatusUnchanged() {
        // 准备：模拟已存在的结算单
        Long settlementId = 200L;
        Long projectId = 20L;

        BizProjectSettlement settlement = new BizProjectSettlement();
        settlement.setId(settlementId);
        settlement.setProjectId(projectId);
        settlement.setStatus("SUBMITTED");

        when(settlementMapper.selectById(settlementId)).thenReturn(settlement);
        when(settlementMapper.updateById(any(BizProjectSettlement.class))).thenReturn(1);

        // 执行
        projectSettlementService.onRejected(settlementId);

        // 验证：结算单状态更新为 REJECTED
        verify(settlementMapper).updateById(argThat(s -> "REJECTED".equals(s.getStatus())));

        // 验证：项目状态未被更新（projectMapper.updateStatus 从未被调用）
        verify(projectMapper, never()).updateStatus(anyLong(), anyString());
    }
}
