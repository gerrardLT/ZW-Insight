package com.zwinsight.subcontract.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.subcontract.domain.BizSubcontract;
import com.zwinsight.subcontract.domain.BizSubcontractOutputReport;
import com.zwinsight.subcontract.mapper.BizSubcontractMapper;
import com.zwinsight.subcontract.mapper.BizSubcontractOutputReportMapper;
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
 * SubcontractOutputService 单元测试
 * 覆盖 P0-1：分包产值 submit 回写合同累计产值 cumulativeOutput
 */
@ExtendWith(MockitoExtension.class)
class SubcontractOutputServiceTest {

    @Mock private BizSubcontractOutputReportMapper outputReportMapper;
    @Mock private BizSubcontractMapper subcontractMapper;

    @InjectMocks
    private SubcontractOutputService subcontractOutputService;

    private BizSubcontractOutputReport sampleReport;

    @BeforeEach
    void setUp() {
        sampleReport = new BizSubcontractOutputReport();
        sampleReport.setId(1L);
        sampleReport.setProjectId(100L);
        sampleReport.setContractId(10L);
        sampleReport.setCurrentOutput(new BigDecimal("30000"));
        sampleReport.setStatus("DRAFT");
    }

    @Nested
    @DisplayName("保存")
    class SaveTests {
        @Test
        @DisplayName("保存：状态初始化为 DRAFT")
        void save_draftInitialized() {
            BizSubcontractOutputReport report = new BizSubcontractOutputReport();
            when(outputReportMapper.insert(any(BizSubcontractOutputReport.class))).thenReturn(1);

            subcontractOutputService.save(report);

            assertThat(report.getStatus()).isEqualTo("DRAFT");
            verify(outputReportMapper).insert(report);
        }
    }

    @Nested
    @DisplayName("提交回写")
    class SubmitTests {
        @Test
        @DisplayName("提交：状态变更 APPROVED + 回写合同累计产值")
        void submit_writesBackCumulativeOutput() {
            BizSubcontract contract = new BizSubcontract();
            contract.setId(10L);
            contract.setCumulativeOutput(new BigDecimal("100000"));
            when(outputReportMapper.selectById(1L)).thenReturn(sampleReport);
            when(subcontractMapper.selectById(10L)).thenReturn(contract);

            subcontractOutputService.submit(1L);

            assertThat(sampleReport.getStatus()).isEqualTo("APPROVED");
            verify(outputReportMapper).updateById(sampleReport);
            // 100000 + 30000 = 130000
            verify(subcontractMapper).updateById(argThat(c ->
                    c.getCumulativeOutput().compareTo(new BigDecimal("130000")) == 0));
        }

        @Test
        @DisplayName("提交：合同累计产值为 null 时从零累加")
        void submit_cumulativeNull_initFromZero() {
            BizSubcontract contract = new BizSubcontract();
            contract.setId(10L);
            contract.setCumulativeOutput(null);
            when(outputReportMapper.selectById(1L)).thenReturn(sampleReport);
            when(subcontractMapper.selectById(10L)).thenReturn(contract);

            subcontractOutputService.submit(1L);

            verify(subcontractMapper).updateById(argThat(c ->
                    c.getCumulativeOutput().compareTo(new BigDecimal("30000")) == 0));
        }

        @Test
        @DisplayName("提交：contractId 为 null 时不回写合同")
        void submit_contractIdNull_skipWriteback() {
            sampleReport.setContractId(null);
            when(outputReportMapper.selectById(1L)).thenReturn(sampleReport);

            subcontractOutputService.submit(1L);

            assertThat(sampleReport.getStatus()).isEqualTo("APPROVED");
            verify(outputReportMapper).updateById(sampleReport);
            verify(subcontractMapper, never()).selectById(any());
            verify(subcontractMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("提交：合同不存在时跳过回写")
        void submit_contractNotFound_skipWriteback() {
            when(outputReportMapper.selectById(1L)).thenReturn(sampleReport);
            when(subcontractMapper.selectById(10L)).thenReturn(null);

            subcontractOutputService.submit(1L);

            verify(outputReportMapper).updateById(sampleReport);
            verify(subcontractMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("提交：报告不存在抛异常")
        void submit_reportNotFound_throws() {
            when(outputReportMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> subcontractOutputService.submit(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("产值报告不存在");
        }

        @Test
        @DisplayName("提交：非 DRAFT 拒绝")
        void submit_nonDraftRejected() {
            sampleReport.setStatus("APPROVED");
            when(outputReportMapper.selectById(1L)).thenReturn(sampleReport);

            assertThatThrownBy(() -> subcontractOutputService.submit(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿状态可提交");
        }
    }
}
