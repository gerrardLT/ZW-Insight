package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.event.contract.ProcessSettlementApprovedEvent;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizChangeVisa;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizFinalSettlement;
import com.zwinsight.contract.domain.BizOutputReport;
import com.zwinsight.contract.domain.BizSettlementSource;
import com.zwinsight.contract.mapper.BizChangeVisaMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizFinalSettlementMapper;
import com.zwinsight.contract.mapper.BizOutputReportMapper;
import com.zwinsight.contract.mapper.BizSettlementSourceMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * FinalSettlementService 单元测试（V2026_108 扩展：FINAL 竣工 + PROCESS 过程结算）
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
    private BizSettlementSourceMapper sourceMapper;

    @Mock
    private BizConstructionContractMapper contractMapper;

    @Mock
    private BizOutputReportMapper outputReportMapper;

    @Mock
    private BizChangeVisaMapper changeVisaMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @Mock
    private ApprovalService approvalService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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

    private BizFinalSettlement processSettlement(String status) {
        BizFinalSettlement s = settlement(status);
        s.setSettlementType(BizFinalSettlement.TYPE_PROCESS);
        s.setPeriodStart(LocalDate.of(2026, 9, 1));
        s.setPeriodEnd(LocalDate.of(2026, 9, 30));
        s.setPrepayDeduct(BigDecimal.ZERO);
        s.setRetentionDeduct(BigDecimal.ZERO);
        s.setOtherDeduct(BigDecimal.ZERO);
        return s;
    }

    private BizSettlementSource outputSource() {
        BizSettlementSource src = new BizSettlementSource();
        src.setSourceType(BizSettlementSource.TYPE_OUTPUT_REPORT);
        src.setSourceId(300L);
        src.setDeclaredAmount(new BigDecimal("60000"));
        src.setConfirmedAmount(new BigDecimal("50000"));
        src.setDisputedAmount(BigDecimal.ZERO);
        src.setReduceReason("计量核减");
        return src;
    }

    private BizOutputReport approvedReport() {
        BizOutputReport r = new BizOutputReport();
        r.setId(300L);
        r.setContractId(20L);
        r.setStatus("APPROVED");
        return r;
    }

    @Test
    @DisplayName("page - 分页透传（含 contractId/settlementType 过滤）")
    void page_delegates() {
        Page<BizFinalSettlement> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(settlement("DRAFT")));
        page.setTotal(1L);
        when(settlementMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BizFinalSettlement> result = service.page(1, 10, 10L, 20L, "PROCESS");

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("save - FINAL 置 DRAFT（无来源兼容旧入口）")
    void save_final_setsDraft() {
        BizFinalSettlement s = settlement(null);

        service.save(s);

        assertThat(s.getStatus()).isEqualTo("DRAFT");
        assertThat(s.getSettlementType()).isEqualTo(BizFinalSettlement.TYPE_FINAL);
        verify(settlementMapper).insert(s);
        verifyNoInteractions(sourceMapper);
    }

    @Test
    @DisplayName("save - PROCESS 无来源/无期间拒绝")
    void save_processWithoutSources_rejected() {
        BizFinalSettlement s = processSettlement(null);
        s.setSources(List.of());
        assertThatThrownBy(() -> service.save(s)).hasMessageContaining("来源明细");

        BizFinalSettlement noPeriod = processSettlement(null);
        noPeriod.setSources(List.of(outputSource()));
        noPeriod.setPeriodEnd(null);
        assertThatThrownBy(() -> service.save(noPeriod)).hasMessageContaining("结算期间");
    }

    @Test
    @DisplayName("save - PROCESS 服务端重算净额=Σ审定−Σ扣款，不信任请求体 settlementAmount")
    void save_process_recomputesNetAmount() {
        BizFinalSettlement s = processSettlement(null);
        s.setSettlementAmount(new BigDecimal("999999")); // 伪造值须被重算覆盖
        s.setSources(List.of(outputSource()));
        s.setRetentionDeduct(new BigDecimal("5000"));
        when(outputReportMapper.selectById(300L)).thenReturn(approvedReport());
        when(sourceMapper.countSourceOccupied(eq("OUTPUT_REPORT"), eq(300L), eq(-1L))).thenReturn(0);

        service.save(s);

        assertThat(s.getSettlementAmount()).isEqualByComparingTo("45000");
        assertThat(s.getDeclaredAmount()).isEqualByComparingTo("60000");
        verify(settlementMapper).insert(s);
        verify(sourceMapper).insert(any(BizSettlementSource.class));
    }

    @Test
    @DisplayName("save - 来源产值未审批/不属于本合同拒绝")
    void save_processInvalidSource_rejected() {
        BizFinalSettlement s = processSettlement(null);
        s.setSources(List.of(outputSource()));
        BizOutputReport notApproved = approvedReport();
        notApproved.setStatus("DRAFT");
        when(outputReportMapper.selectById(300L)).thenReturn(notApproved);
        assertThatThrownBy(() -> service.save(s)).hasMessageContaining("未审批");

        BizOutputReport otherContract = approvedReport();
        otherContract.setContractId(999L);
        when(outputReportMapper.selectById(300L)).thenReturn(otherContract);
        assertThatThrownBy(() -> service.save(s)).hasMessageContaining("不属于本合同");
    }

    @Test
    @DisplayName("save - 审定低于申报未填核减原因拒绝；审定+争议超申报拒绝")
    void save_processReduceReasonRequired() {
        BizFinalSettlement s = processSettlement(null);
        BizSettlementSource noReason = outputSource();
        noReason.setReduceReason(" ");
        s.setSources(List.of(noReason));
        when(outputReportMapper.selectById(300L)).thenReturn(approvedReport());
        when(sourceMapper.countSourceOccupied(eq("OUTPUT_REPORT"), eq(300L), eq(-1L))).thenReturn(0);
        assertThatThrownBy(() -> service.save(s)).hasMessageContaining("核减原因");

        BizSettlementSource overflow = outputSource();
        overflow.setDisputedAmount(new BigDecimal("20000"));
        s.setSources(List.of(overflow));
        assertThatThrownBy(() -> service.save(s)).hasMessageContaining("超过申报");
    }

    @Test
    @DisplayName("save - 来源已被其他有效结算占用拒绝（409）")
    void save_processSourceOccupied_rejected() {
        BizFinalSettlement s = processSettlement(null);
        s.setSources(List.of(outputSource()));
        when(outputReportMapper.selectById(300L)).thenReturn(approvedReport());
        when(sourceMapper.countSourceOccupied(eq("OUTPUT_REPORT"), eq(300L), eq(-1L))).thenReturn(1);

        assertThatThrownBy(() -> service.save(s))
                .isInstanceOf(BusinessException.class).hasMessageContaining("占用");
    }

    @Test
    @DisplayName("submit - 守卫：不存在/非草稿抛异常")
    void submit_guardCases_throws() {
        when(settlementMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("结算单不存在");

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
    @DisplayName("submit - FINAL 正常：只置 SUBMITTED 并启动流程，不提前回写")
    void submit_final_success_onlySubmitted() {
        BizFinalSettlement s = settlement("DRAFT");
        when(settlementMapper.selectById(1L)).thenReturn(s);
        when(approvalService.startProcess(eq("FINAL_SETTLEMENT"), eq(1L),
                eq("final_settlement_approval"), anyMap())).thenReturn("proc-1");
        when(contractMapper.selectById(20L)).thenReturn(effectiveContract());
        when(projectMapper.selectById(10L)).thenReturn(new BizProject());

        service.submit(1L);

        assertThat(s.getStatus()).isEqualTo("SUBMITTED");
        verify(contractMapper, never()).updateById(any());
        verify(projectMapper, never()).addSettlementAmount(anyLong(), any());
    }

    @Test
    @DisplayName("submit - PROCESS：重校验来源并重算合计后启动流程")
    void submit_process_revalidatesAndStarts() {
        BizFinalSettlement s = processSettlement("DRAFT");
        when(settlementMapper.selectById(1L)).thenReturn(s);
        when(sourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(outputSource()));
        when(outputReportMapper.selectById(300L)).thenReturn(approvedReport());
        when(sourceMapper.countSourceOccupied(eq("OUTPUT_REPORT"), eq(300L), eq(1L))).thenReturn(0);
        when(approvalService.startProcess(anyString(), eq(1L), anyString(), anyMap())).thenReturn("proc-2");
        when(contractMapper.selectById(20L)).thenReturn(effectiveContract());
        when(projectMapper.selectById(10L)).thenReturn(new BizProject());

        service.submit(1L);

        assertThat(s.getStatus()).isEqualTo("SUBMITTED");
        assertThat(s.getSettlementAmount()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("submit - 引用缺失或不属于该项目一律拒绝，不启动流程")
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
    @DisplayName("submit - 结算金额 null/零/负拒绝")
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
    @DisplayName("onApproved - FINAL：CAS→累计结算回写→合同置 SETTLED→项目结算额累加")
    void onApproved_final_success_writesBack() {
        BizFinalSettlement s = settlement("SUBMITTED");
        when(settlementMapper.selectById(1L)).thenReturn(s);
        when(settlementMapper.update(isNull(), any())).thenReturn(1);
        when(contractMapper.addCumulativeSettlement(20L, new BigDecimal("50000"))).thenReturn(1);
        BizConstructionContract contract = effectiveContract();
        when(contractMapper.selectById(20L)).thenReturn(contract);
        when(projectMapper.addSettlementAmount(10L, new BigDecimal("50000"))).thenReturn(1);
        when(contractMapper.updateById(any(BizConstructionContract.class))).thenReturn(1);

        service.onApproved(1L);

        assertThat(contract.getStatus()).isEqualTo("SETTLED");
        verify(contractMapper).addCumulativeSettlement(20L, new BigDecimal("50000"));
        verify(projectMapper).addSettlementAmount(10L, new BigDecimal("50000"));
    }

    @Test
    @DisplayName("onApproved - PROCESS：CAS→累计结算回写→发布应收事件；不置合同 SETTLED")
    void onApproved_process_publishesEvent() {
        BizFinalSettlement s = processSettlement("SUBMITTED");
        when(settlementMapper.selectById(1L)).thenReturn(s);
        when(sourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(outputSource()));
        when(outputReportMapper.selectById(300L)).thenReturn(approvedReport());
        when(sourceMapper.countSourceOccupied(eq("OUTPUT_REPORT"), eq(300L), eq(1L))).thenReturn(0);
        when(settlementMapper.update(isNull(), any())).thenReturn(1);
        when(contractMapper.addCumulativeSettlement(20L, new BigDecimal("50000"))).thenReturn(1);

        service.onApproved(1L);

        ArgumentCaptor<ProcessSettlementApprovedEvent> captor =
                ArgumentCaptor.forClass(ProcessSettlementApprovedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ProcessSettlementApprovedEvent event = captor.getValue();
        assertThat(event.getSettlementId()).isEqualTo(1L);
        assertThat(event.getNetAmount()).isEqualByComparingTo("50000");
        assertThat(event.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 9, 30));
        verify(contractMapper, never()).updateById(any());
        verify(projectMapper, never()).addSettlementAmount(anyLong(), any());
    }

    @Test
    @DisplayName("onApproved - PROCESS 生效前占用被抢：驳回并不回写")
    void onApproved_processOccupied_throws() {
        BizFinalSettlement s = processSettlement("SUBMITTED");
        when(settlementMapper.selectById(1L)).thenReturn(s);
        when(sourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(outputSource()));
        when(outputReportMapper.selectById(300L)).thenReturn(approvedReport());
        when(sourceMapper.countSourceOccupied(eq("OUTPUT_REPORT"), eq(300L), eq(1L))).thenReturn(1);

        assertThatThrownBy(() -> service.onApproved(1L)).hasMessageContaining("占用");
        verify(settlementMapper, never()).update(isNull(), any());
        verify(contractMapper, never()).addCumulativeSettlement(anyLong(), any());
    }

    @Test
    @DisplayName("onApproved - 累计结算越限（超合同额+变更）回写 0 行：抛异常整体回滚")
    void onApproved_settlementOverLimit_rollsBack() {
        BizFinalSettlement s = settlement("SUBMITTED");
        when(settlementMapper.selectById(1L)).thenReturn(s);
        when(settlementMapper.update(isNull(), any())).thenReturn(1);
        when(contractMapper.addCumulativeSettlement(20L, new BigDecimal("50000"))).thenReturn(0);

        assertThatThrownBy(() -> service.onApproved(1L)).hasMessageContaining("超出合同额");
        verify(projectMapper, never()).addSettlementAmount(anyLong(), any());
    }

    @Test
    @DisplayName("onApproved - 已 APPROVED 幂等；并发回调 CAS 落空不重复回写")
    void onApproved_idempotentAndCas() {
        when(settlementMapper.selectById(1L)).thenReturn(settlement("APPROVED"));
        service.onApproved(1L);
        verify(projectMapper, never()).addSettlementAmount(anyLong(), any());
        verify(contractMapper, never()).addCumulativeSettlement(anyLong(), any());

        when(settlementMapper.selectById(2L)).thenReturn(settlement("SUBMITTED"));
        when(settlementMapper.update(isNull(), any())).thenReturn(0);
        service.onApproved(2L);
        verify(projectMapper, never()).addSettlementAmount(anyLong(), any());
        verify(contractMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("onApproved - FINAL 合同已失效或项目缺失抛异常（整体回滚），不静默跳过")
    void onApproved_finalInvalidRefs_throw() {
        when(settlementMapper.selectById(1L)).thenReturn(settlement("SUBMITTED"));
        when(settlementMapper.update(isNull(), any())).thenReturn(1);
        when(contractMapper.addCumulativeSettlement(20L, new BigDecimal("50000"))).thenReturn(1);

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
    @DisplayName("onApproved - FINAL 合同乐观锁冲突（updateById=0）抛异常，不累加项目结算额")
    void onApproved_contractOptimisticLock_throws() {
        when(settlementMapper.selectById(1L)).thenReturn(settlement("SUBMITTED"));
        when(settlementMapper.update(isNull(), any())).thenReturn(1);
        when(contractMapper.addCumulativeSettlement(20L, new BigDecimal("50000"))).thenReturn(1);
        when(contractMapper.selectById(20L)).thenReturn(effectiveContract());
        when(contractMapper.updateById(any(BizConstructionContract.class))).thenReturn(0);

        assertThatThrownBy(() -> service.onApproved(1L)).hasMessageContaining("已被他人修改");
        verify(projectMapper, never()).addSettlementAmount(anyLong(), any());
    }

    @Test
    @DisplayName("onApproved - 状态异常拒绝；onRejected 仅 SUBMITTED 回退草稿")
    void onApproved_wrongState_and_onRejected() {
        when(settlementMapper.selectById(1L)).thenReturn(settlement("CANCELLED"));
        assertThatThrownBy(() -> service.onApproved(1L)).hasMessageContaining("状态异常");

        service.onRejected(1L);
        verify(settlementMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("submit - 合同非生效状态拒绝提交（D2），不启动流程")
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
    }

    @Test
    @DisplayName("listAvailableSources - 已批产值/签证候选并剔除已占用来源")
    void listAvailableSources_filtersOccupied() {
        BizOutputReport r = approvedReport();
        r.setReportPeriod("2026-09");
        r.setCurrentOutput(new BigDecimal("100000"));
        when(outputReportMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(r));
        when(changeVisaMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(sourceMapper.countSourceOccupied(eq("OUTPUT_REPORT"), eq(300L), eq(-1L))).thenReturn(1); // 已占用

        var result = service.listAvailableSources(20L);

        assertThat(result.get("outputReports")).isEmpty();
        assertThat(result.get("visas")).isEmpty();
    }

    @Test
    @DisplayName("getDetail - 附带来源明细并归一类型")
    void getDetail_attachesSources() {
        BizFinalSettlement s = settlement("DRAFT");
        when(settlementMapper.selectById(1L)).thenReturn(s);
        when(sourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(outputSource()));

        BizFinalSettlement detail = service.getDetail(1L);

        assertThat(detail.getSettlementType()).isEqualTo(BizFinalSettlement.TYPE_FINAL);
        assertThat(detail.getSources()).hasSize(1);
    }
}
