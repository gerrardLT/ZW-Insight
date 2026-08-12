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
import static org.mockito.Mockito.*;

/**
 * FinalSettlementService 单元测试
 * <p>竣工结算：提交审批即置 APPROVED，合同转 SETTLED，项目结算金额累加。</p>
 */
@ExtendWith(MockitoExtension.class)
class FinalSettlementServiceTest {

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

    @Test
    @DisplayName("submit - 正常：APPROVED + 合同 SETTLED + 项目结算金额累加")
    void submit_success_fullWriteBack() {
        BizFinalSettlement s = settlement("DRAFT");
        when(settlementMapper.selectById(1L)).thenReturn(s);
        when(approvalService.startProcess(eq("FINAL_SETTLEMENT"), eq(1L),
                eq("final_settlement_approval"), anyMap())).thenReturn("proc-1");
        BizConstructionContract contract = new BizConstructionContract();
        contract.setStatus("EFFECTIVE");
        when(contractMapper.selectById(20L)).thenReturn(contract);
        BizProject project = new BizProject();
        project.setSettlementAmount(new BigDecimal("10000"));
        when(projectMapper.selectById(10L)).thenReturn(project);

        service.submit(1L);

        assertThat(s.getStatus()).isEqualTo("APPROVED");
        assertThat(s.getWorkflowInstanceId()).isEqualTo("proc-1");
        verify(contractMapper).updateById(argThat(c -> "SETTLED".equals(c.getStatus())));
        verify(projectMapper).updateById(argThat(p ->
                p.getSettlementAmount().compareTo(new BigDecimal("60000")) == 0));
    }

    @Test
    @DisplayName("submit - 合同/项目不存在时跳过对应回写但不报错")
    void submit_missingRefs_skipsWriteBack() {
        BizFinalSettlement s = settlement("DRAFT");
        when(settlementMapper.selectById(1L)).thenReturn(s);
        when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap())).thenReturn("proc-1");
        when(contractMapper.selectById(20L)).thenReturn(null);
        when(projectMapper.selectById(10L)).thenReturn(null);

        service.submit(1L);

        assertThat(s.getStatus()).isEqualTo("APPROVED");
        verify(contractMapper, never()).updateById(any());
        verify(projectMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("submit - 合同非生效状态拒绝置已结算（D2：DRAFT/SUBMITTED 合同不可竣工结算）")
    void submit_contractNotEffective_rejected() {
        BizFinalSettlement s = settlement("DRAFT");
        when(settlementMapper.selectById(1L)).thenReturn(s);
        when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap())).thenReturn("proc-1");
        BizConstructionContract contract = new BizConstructionContract();
        contract.setStatus("DRAFT");
        when(contractMapper.selectById(20L)).thenReturn(contract);

        assertThatThrownBy(() -> service.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅生效状态的合同");

        verify(contractMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("submit - 结算金额 null 不抛 NPE 按 0 累加（P0 SET-06）")
    void submit_nullSettlementAmount_noNpe() {
        BizFinalSettlement s = settlement("DRAFT");
        s.setSettlementAmount(null);
        when(settlementMapper.selectById(1L)).thenReturn(s);
        when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap())).thenReturn("proc-1");
        BizConstructionContract contract = new BizConstructionContract();
        contract.setStatus("EFFECTIVE");
        when(contractMapper.selectById(20L)).thenReturn(contract);
        BizProject project = new BizProject();
        project.setSettlementAmount(new BigDecimal("100"));
        when(projectMapper.selectById(10L)).thenReturn(project);

        service.submit(1L);

        // 项目结算额 100 + 0 = 100，无 NPE
        verify(projectMapper).updateById(argThat(p ->
                p.getSettlementAmount().compareTo(new BigDecimal("100")) == 0));
    }
}
