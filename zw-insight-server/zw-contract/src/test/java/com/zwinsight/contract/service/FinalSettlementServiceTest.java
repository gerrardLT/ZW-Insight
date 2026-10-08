package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizFinalSettlement;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizFinalSettlementMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * FinalSettlementService 单元测试
 * <p>竣工结算：提交审批即置 APPROVED，合同转 SETTLED，项目结算金额累加。</p>
 */
@ExtendWith(MockitoExtension.class)
class FinalSettlementServiceTest {

    /** 服务内用 LambdaUpdateWrapper.set(...) 做状态 CAS，纯单测环境需预初始化实体列缓存 */
    @org.junit.jupiter.api.BeforeAll
    static void initTableInfo() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), BizFinalSettlement.class);
    }

    @Mock
    private BizFinalSettlementMapper settlementMapper;

    @Mock
    private BizConstructionContractMapper contractMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private FinalSettlementService service;

    private BizFinalSettlement settlement(String status) {
        BizFinalSettlement s = new BizFinalSettlement();
        s.setId(1L);
        s.setProjectId(10L);
        s.setContractId(20L);
        s.setSettlementAmount(new BigDecimal("50000"));
        s.setStatus(status);
        return s;
    }

    @Test
    @DisplayName("page - 分页透传")
    void page_delegates() {
        Page<BizFinalSettlement> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(settlement("DRAFT")));
        page.setTotal(1L);
        when(settlementMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BizFinalSettlement> result = service.page(1, 10, 10L);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("save - 置 DRAFT")
    void save_setsDraft() {
        BizFinalSettlement s = settlement(null);

        service.save(s);

        assertThat(s.getStatus()).isEqualTo("DRAFT");
        verify(settlementMapper).insert(s);
    }

    @Test
    @DisplayName("submit - 守卫：不存在/非草稿抛异常")
    void submit_guardCases_throws() {
        when(settlementMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("竣工结算不存在");

        when(settlementMapper.selectById(2L)).thenReturn(settlement("APPROVED"));
        assertThatThrownBy(() -> service.submit(2L)).hasMessageContaining("仅草稿状态可提交");
    }

    private BizConstructionContract effectiveContract() {
        BizConstructionContract contract = new BizConstructionContract();
        contract.setId(20L);
        contract.setProjectId(10L);
        contract.setStatus("EFFECTIVE");
        return contract;
    }

    @Test
    @DisplayName("submit - 正常：只置 SUBMITTED 并启动流程，不提前回写合同与项目")
    void submit_success_onlySubmitted() {
        BizFinalSettlement s = settlement("DRAFT");
        when(settlementMapper.selectById(1L)).thenReturn(s);
        when(approvalService.startProcess(eq("FINAL_SETTLEMENT"), eq(1L),
                eq("final_settlement_approval"), anyMap())).thenReturn("proc-1");
        when(contractMapper.selectById(20L)).thenReturn(effectiveContract());
        when(projectMapper.selectById(10L)).thenReturn(new BizProject());

        service.submit(1L);

        assertThat(s.getStatus()).isEqualTo("SUBMITTED");
        assertThat(s.getWorkflowInstanceId()).isEqualTo("proc-1");
        verify(contractMapper, never()).updateById(any());
        verify(projectMapper, never()).addSettlementAmount(anyLong(), any());
    }

    @Test
    @DisplayName("submit - 合同/项目引用缺失或不属于该项目一律拒绝，不启动流程")
    void submit_missingOrMismatchedRefs_rejected() {
        BizFinalSettlement s = settlement("DRAFT");
        when(settlementMapper.selectById(1L)).thenReturn(s);

        when(contractMapper.selectById(20L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("施工合同不存在");

        BizConstructionContract other = effectiveContract();
        other.setProjectId(99L);
        when(contractMapper.selectById(20L)).thenReturn(other);
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("不属于该项目");

        when(contractMapper.selectById(20L)).thenReturn(effectiveContract());
        when(projectMapper.selectById(10L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("关联项目不存在");

        verify(approvalService, never()).startProcess(anyString(), anyLong(), anyString(), anyMap());
    }

    @Test
    @DisplayName("submit - 结算金额 null/零/负拒绝（取代原 null 按 0 累加）")
    void submit_nonPositiveAmount_rejected() {
        BizFinalSettlement s = settlement("DRAFT");
        s.setSettlementAmount(null);
        when(settlementMapper.selectById(1L)).thenReturn(s);
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("必须大于0");

        s.setSettlementAmount(BigDecimal.ZERO);
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("必须大于0");
        verify(approvalService, never()).startProcess(anyString(), anyLong(), anyString(), anyMap());
    }

    @Test
    @DisplayName("onApproved - SUBMITTED→APPROVED，合同置 SETTLED，项目结算额原子累加")
    void onApproved_success_writesBack() {
        BizFinalSettlement s = settlement("SUBMITTED");
        when(settlementMapper.selectById(1L)).thenReturn(s);
        when(settlementMapper.update(isNull(), any())).thenReturn(1);
        BizConstructionContract contract = effectiveContract();
        when(contractMapper.selectById(20L)).thenReturn(contract);
        when(projectMapper.addSettlementAmount(10L, new BigDecimal("50000"))).thenReturn(1);
        when(contractMapper.updateById(any(BizConstructionContract.class))).thenReturn(1);

        service.onApproved(1L);

        assertThat(contract.getStatus()).isEqualTo("SETTLED");
        verify(contractMapper).updateById(contract);
        verify(projectMapper).addSettlementAmount(10L, new BigDecimal("50000"));
    }

    @Test
    @DisplayName("onApproved - 已 APPROVED 幂等；并发回调 CAS 落空不重复回写")
    void onApproved_idempotentAndCas() {
        when(settlementMapper.selectById(1L)).thenReturn(settlement("APPROVED"));
        service.onApproved(1L);
        verify(projectMapper, never()).addSettlementAmount(anyLong(), any());

        when(settlementMapper.selectById(2L)).thenReturn(settlement("SUBMITTED"));
        when(settlementMapper.update(isNull(), any())).thenReturn(0);
        service.onApproved(2L);
        verify(projectMapper, never()).addSettlementAmount(anyLong(), any());
        verify(contractMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("onApproved - 合同已失效或项目缺失抛异常（由审批事务整体回滚），不静默跳过")
    void onApproved_invalidRefs_throw() {
        when(settlementMapper.selectById(1L)).thenReturn(settlement("SUBMITTED"));
        when(settlementMapper.update(isNull(), any())).thenReturn(1);

        BizConstructionContract terminated = effectiveContract();
        terminated.setStatus("TERMINATED");
        when(contractMapper.selectById(20L)).thenReturn(terminated);
        assertThatThrownBy(() -> service.onApproved(1L)).hasMessageContaining("无法生效");

        when(contractMapper.selectById(20L)).thenReturn(effectiveContract());
        when(contractMapper.updateById(any(BizConstructionContract.class))).thenReturn(1);
        when(projectMapper.addSettlementAmount(10L, new BigDecimal("50000"))).thenReturn(0);
        assertThatThrownBy(() -> service.onApproved(1L)).hasMessageContaining("项目不存在");
    }

    @Test
    @DisplayName("onApproved - 合同乐观锁冲突（updateById 返回 0）时抛异常，不继续累加项目结算额")
    void onApproved_contractOptimisticLock_throws() {
        when(settlementMapper.selectById(1L)).thenReturn(settlement("SUBMITTED"));
        when(settlementMapper.update(isNull(), any())).thenReturn(1);
        when(contractMapper.selectById(20L)).thenReturn(effectiveContract());
        when(contractMapper.updateById(any(BizConstructionContract.class))).thenReturn(0);

        assertThatThrownBy(() -> service.onApproved(1L)).hasMessageContaining("已被他人修改");
        verify(projectMapper, never()).addSettlementAmount(anyLong(), any());
    }

    @Test
    @DisplayName("onApproved - 状态异常（DRAFT）拒绝；onRejected 仅 SUBMITTED 回退草稿")
    void onApproved_wrongState_and_onRejected() {
        when(settlementMapper.selectById(1L)).thenReturn(settlement("CANCELLED"));
        assertThatThrownBy(() -> service.onApproved(1L)).hasMessageContaining("状态异常");

        service.onRejected(1L);
        verify(settlementMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("submit - 合同非生效状态拒绝提交（D2：DRAFT/SUBMITTED 合同不可竣工结算），不启动流程")
    void submit_contractNotEffective_rejected() {
        BizFinalSettlement s = settlement("DRAFT");
        when(settlementMapper.selectById(1L)).thenReturn(s);
        BizConstructionContract contract = effectiveContract();
        contract.setStatus("DRAFT");
        when(contractMapper.selectById(20L)).thenReturn(contract);

        assertThatThrownBy(() -> service.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅生效状态的合同");

        verify(approvalService, never()).startProcess(anyString(), anyLong(), anyString(), anyMap());
        verify(contractMapper, never()).updateById(any());
    }
}
