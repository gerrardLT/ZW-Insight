package com.zwinsight.labor.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.labor.domain.BizLaborContract;
import com.zwinsight.labor.domain.BizLaborOutputReport;
import com.zwinsight.labor.mapper.BizLaborContractMapper;
import com.zwinsight.labor.mapper.BizLaborOutputReportMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LaborOutputReportService 单元测试
 * 覆盖 P0-1：劳务产值 submit 回写合同独立累计产值 cumulativeOutput（修复原误写 cumulativeSettlement 的 bug）
 */
@ExtendWith(MockitoExtension.class)
class LaborOutputReportServiceTest {

    @Mock private BizLaborOutputReportMapper outputReportMapper;
    @Mock private BizLaborContractMapper laborContractMapper;
    @Mock private com.zwinsight.workflow.service.ApprovalService approvalService;

    @InjectMocks
    private LaborOutputReportService laborOutputReportService;

    private BizLaborOutputReport sampleReport;

    @BeforeEach
    void setUp() {
        sampleReport = new BizLaborOutputReport();
        sampleReport.setId(1L);
        sampleReport.setProjectId(100L);
        sampleReport.setContractId(10L);
        sampleReport.setCurrentOutput(new BigDecimal("20000"));
        sampleReport.setStatus("DRAFT");
    }

    @Nested
    @DisplayName("保存")
    class SaveTests {
        @Test
        @DisplayName("保存：状态初始化为 DRAFT")
        void save_draftInitialized() {
            BizLaborOutputReport report = new BizLaborOutputReport();
            report.setCurrentOutput(new BigDecimal("20000"));
            when(outputReportMapper.insert(any(BizLaborOutputReport.class))).thenReturn(1);

            laborOutputReportService.save(report);

            assertThat(report.getStatus()).isEqualTo("DRAFT");
            verify(outputReportMapper).insert(report);
        }

        @Test
        @DisplayName("保存：产值金额为空/非正数拒绝")
        void save_nonPositiveOutput_rejected() {
            BizLaborOutputReport report = new BizLaborOutputReport();

            assertThatThrownBy(() -> laborOutputReportService.save(report))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("本期产值金额必须大于0");

            verify(outputReportMapper, never()).insert(any(BizLaborOutputReport.class));
        }
    }

    @Nested
    @DisplayName("提交回写")
    class SubmitTests {
        @Test
        @DisplayName("提交：发起审批流并将状态置为 SUBMITTED")
        void submit_launchesApprovalAndSetsSubmitted() {
            BizLaborContract contract = new BizLaborContract();
            contract.setId(10L);
            contract.setContractAmount(new BigDecimal("100000"));
            contract.setCumulativeOutput(new BigDecimal("50000"));
            when(outputReportMapper.selectById(1L)).thenReturn(sampleReport);
            when(laborContractMapper.selectById(10L)).thenReturn(contract);
            when(approvalService.startProcess(eq("LABOR_OUTPUT"), eq(1L), eq("labor_output_approval"), any()))
                    .thenReturn("proc-out-123");

            laborOutputReportService.submit(1L);

            assertThat(sampleReport.getStatus()).isEqualTo("SUBMITTED");
            assertThat(sampleReport.getWorkflowInstanceId()).isEqualTo("proc-out-123");
            verify(outputReportMapper).updateById(sampleReport);
        }

        @Test
        @DisplayName("审批通过：状态变更为 APPROVED 并原子累加合同累计产值")
        void onApproved_atomicAddOutput() {
            sampleReport.setStatus("SUBMITTED");
            when(outputReportMapper.selectById(1L)).thenReturn(sampleReport);

            laborOutputReportService.onApproved(1L);

            assertThat(sampleReport.getStatus()).isEqualTo("APPROVED");
            verify(outputReportMapper).updateById(sampleReport);
            verify(laborContractMapper).addOutput(10L, new BigDecimal("20000"));
        }

        @Test
        @DisplayName("提交：报告不存在抛异常")
        void submit_reportNotFound_throws() {
            when(outputReportMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> laborOutputReportService.submit(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("产值报告不存在");
        }

        @Test
        @DisplayName("提交：非 DRAFT 拒绝")
        void submit_nonDraftRejected() {
            sampleReport.setStatus("APPROVED");
            when(outputReportMapper.selectById(1L)).thenReturn(sampleReport);

            assertThatThrownBy(() -> laborOutputReportService.submit(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿状态可提交");
        }
    }
}
