package com.zwinsight.contract.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizOutputReport;
import com.zwinsight.contract.domain.BizOutputReportDetail;
import com.zwinsight.contract.mapper.BizBoqItemMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizOutputReportDetailMapper;
import com.zwinsight.contract.mapper.BizOutputReportMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OutputReportService 单元测试
 * <p>审批后生效模式：submit 置 SUBMITTED（不回写），onApproved 回写合同/项目累计产值 + BOQ 已完成工程量。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutputReportServiceTest {

    @Mock private BizOutputReportMapper outputReportMapper;
    @Mock private BizOutputReportDetailMapper reportDetailMapper;
    @Mock private BizConstructionContractMapper contractMapper;
    @Mock private BizBoqItemMapper boqItemMapper;
    @Mock private BizProjectMapper projectMapper;
    @Mock private ApprovalService approvalService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OutputReportService outputReportService;

    @Nested
    @DisplayName("submit() 提交产值上报")
    class SubmitTests {

        @Test
        @DisplayName("正常路径 — 校验通过后状态置 SUBMITTED，不回写累计产值")
        void submit_normalPath_statusSubmitted() {
            Long id = 1L;
            Long contractId = 100L;
            BizOutputReport report = new BizOutputReport();
            report.setId(id);
            report.setContractId(contractId);
            report.setProjectId(10L);
            report.setCurrentOutput(new BigDecimal("20000.00"));
            report.setStatus("DRAFT");

            BizConstructionContract contract = new BizConstructionContract();
            contract.setId(contractId);
            contract.setContractAmount(new BigDecimal("100000.00"));
            contract.setCumulativeOutput(new BigDecimal("50000.00"));

            when(outputReportMapper.selectById(id)).thenReturn(report);
            when(contractMapper.selectById(contractId)).thenReturn(contract);
            when(approvalService.startProcess(eq("OUTPUT_REPORT"), eq(id), eq("output_report_approval"), anyMap()))
                    .thenReturn("proc-1");

            outputReportService.submit(id);

            assertThat(report.getStatus()).isEqualTo("SUBMITTED");
            assertThat(report.getWorkflowInstanceId()).isEqualTo("proc-1");
            verify(contractMapper, never()).addCumulativeOutput(anyLong(), any());
            verify(projectMapper, never()).addCumulativeOutput(anyLong(), any());
        }

        @Test
        @DisplayName("累计产值超合同金额 — 抛 BusinessException")
        void submit_exceedsContract_throws() {
            Long id = 2L;
            Long contractId = 200L;
            BizOutputReport report = new BizOutputReport();
            report.setId(id);
            report.setContractId(contractId);
            report.setCurrentOutput(new BigDecimal("60000.00"));
            report.setStatus("DRAFT");

            BizConstructionContract contract = new BizConstructionContract();
            contract.setId(contractId);
            contract.setContractAmount(new BigDecimal("100000.00"));
            contract.setCumulativeOutput(new BigDecimal("50000.00"));

            when(outputReportMapper.selectById(id)).thenReturn(report);
            when(contractMapper.selectById(contractId)).thenReturn(contract);

            assertThatThrownBy(() -> outputReportService.submit(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("累计产值不能超过合同金额");

            verify(approvalService, never()).startProcess(anyString(), anyLong(), anyString(), anyMap());
        }
    }

    @Nested
    @DisplayName("onApproved() / onRejected() 审批回调")
    class ApprovalCallbackTests {

        @Test
        @DisplayName("审批通过 — 回写合同/项目累计产值 + BOQ 已完成工程量")
        void onApproved_writesBackWithBoq() {
            Long id = 1L;
            Long contractId = 100L;
            Long projectId = 10L;
            BizOutputReport report = new BizOutputReport();
            report.setId(id);
            report.setContractId(contractId);
            report.setProjectId(projectId);
            report.setCurrentOutput(new BigDecimal("20000.00"));
            report.setStatus("SUBMITTED");

            BizConstructionContract contract = new BizConstructionContract();
            contract.setId(contractId);
            contract.setContractAmount(new BigDecimal("100000.00"));
            contract.setCumulativeOutput(new BigDecimal("50000.00"));

            BizOutputReportDetail detail = new BizOutputReportDetail();
            detail.setBoqItemId(9001L);
            detail.setQuantity(new BigDecimal("5"));

            when(outputReportMapper.selectById(id)).thenReturn(report);
            when(contractMapper.selectById(contractId)).thenReturn(contract);
            when(reportDetailMapper.selectList(any())).thenReturn(List.of(detail));

            outputReportService.onApproved(id);

            assertThat(report.getStatus()).isEqualTo("APPROVED");
            verify(contractMapper).addCumulativeOutput(contractId, new BigDecimal("20000.00"));
            verify(projectMapper).addCumulativeOutput(projectId, new BigDecimal("20000.00"));
            verify(boqItemMapper).addCompletedQuantity(9001L, new BigDecimal("5"));
        }

        @Test
        @DisplayName("审批通过 — 已生效幂等跳过")
        void onApproved_idempotent() {
            Long id = 1L;
            BizOutputReport report = new BizOutputReport();
            report.setId(id);
            report.setStatus("APPROVED");
            when(outputReportMapper.selectById(id)).thenReturn(report);

            outputReportService.onApproved(id);

            verify(contractMapper, never()).addCumulativeOutput(anyLong(), any());
            verify(projectMapper, never()).addCumulativeOutput(anyLong(), any());
        }

        @Test
        @DisplayName("审批驳回 — SUBMITTED 置 REJECTED，不回写")
        void onRejected_setsRejected() {
            Long id = 1L;
            BizOutputReport report = new BizOutputReport();
            report.setId(id);
            report.setStatus("SUBMITTED");
            when(outputReportMapper.selectById(id)).thenReturn(report);

            outputReportService.onRejected(id);

            assertThat(report.getStatus()).isEqualTo("REJECTED");
            verify(contractMapper, never()).addCumulativeOutput(anyLong(), any());
        }
    }
}
