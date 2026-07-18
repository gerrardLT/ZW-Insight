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
            when(outputReportMapper.insert(any(BizLaborOutputReport.class))).thenReturn(1);

            laborOutputReportService.save(report);

            assertThat(report.getStatus()).isEqualTo("DRAFT");
            verify(outputReportMapper).insert(report);
        }
    }

    @Nested
    @DisplayName("提交回写")
    class SubmitTests {
        @Test
        @DisplayName("提交：回写的是 cumulativeOutput 而非 cumulativeSettlement")
        void submit_writesBackCumulativeOutput_notSettlement() {
            BizLaborContract contract = new BizLaborContract();
            contract.setId(10L);
            contract.setCumulativeOutput(new BigDecimal("50000"));
            contract.setCumulativeSettlement(new BigDecimal("80000"));
            when(outputReportMapper.selectById(1L)).thenReturn(sampleReport);
            when(laborContractMapper.selectById(10L)).thenReturn(contract);

            laborOutputReportService.submit(1L);

            assertThat(sampleReport.getStatus()).isEqualTo("APPROVED");
            verify(outputReportMapper).updateById(sampleReport);
            // cumulativeOutput: 50000 + 20000 = 70000；cumulativeSettlement 保持 80000 不变
            verify(laborContractMapper).updateById(argThat(c ->
                    c.getCumulativeOutput().compareTo(new BigDecimal("70000")) == 0
                            && c.getCumulativeSettlement().compareTo(new BigDecimal("80000")) == 0));
        }

        @Test
        @DisplayName("提交：合同累计产值为 null 时从零累加")
        void submit_cumulativeNull_initFromZero() {
            BizLaborContract contract = new BizLaborContract();
            contract.setId(10L);
            contract.setCumulativeOutput(null);
            when(outputReportMapper.selectById(1L)).thenReturn(sampleReport);
            when(laborContractMapper.selectById(10L)).thenReturn(contract);

            laborOutputReportService.submit(1L);

            verify(laborContractMapper).updateById(argThat(c ->
                    c.getCumulativeOutput().compareTo(new BigDecimal("20000")) == 0));
        }

        @Test
        @DisplayName("提交：合同不存在时跳过回写")
        void submit_contractNotFound_skipWriteback() {
            when(outputReportMapper.selectById(1L)).thenReturn(sampleReport);
            when(laborContractMapper.selectById(10L)).thenReturn(null);

            laborOutputReportService.submit(1L);

            verify(outputReportMapper).updateById(sampleReport);
            verify(laborContractMapper, never()).updateById(any());
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
