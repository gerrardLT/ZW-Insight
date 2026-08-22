package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizOutputReport;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizOutputReportMapper;
import com.zwinsight.contract.vo.ContractAmountSummaryVO;
import com.zwinsight.contract.vo.OutputTrendItemVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ContractStatisticsService 单元测试
 * <p>合同金额汇总与产值完成率趋势：聚合逻辑、空数据异常路径。</p>
 */
@ExtendWith(MockitoExtension.class)
class ContractStatisticsServiceTest {

    @Mock
    private BizConstructionContractMapper contractMapper;

    @Mock
    private BizOutputReportMapper outputReportMapper;

    @InjectMocks
    private ContractStatisticsService service;

    private BizConstructionContract contract(String status, String contractAmount, String received) {
        BizConstructionContract c = new BizConstructionContract();
        c.setProjectId(1L);
        c.setContractType("REGISTER");
        c.setStatus(status);
        c.setContractAmount(new BigDecimal(contractAmount));
        c.setAmountWithoutTax(new BigDecimal(contractAmount).divide(new BigDecimal("1.09"), 2, java.math.RoundingMode.HALF_UP));
        c.setTaxAmount(new BigDecimal("1000"));
        c.setCumulativeChangeAmount(new BigDecimal("5000"));
        c.setCumulativeOutput(new BigDecimal("20000"));
        c.setCumulativeInvoiceAmount(new BigDecimal("15000"));
        c.setCumulativeReceivedAmount(new BigDecimal(received));
        return c;
    }

    private BizOutputReport report(String period, String currentOutput) {
        BizOutputReport r = new BizOutputReport();
        r.setProjectId(1L);
        r.setReportPeriod(period);
        r.setCurrentOutput(new BigDecimal(currentOutput));
        r.setStatus("APPROVED");
        return r;
    }

    @Nested
    @DisplayName("合同金额汇总")
    class AmountSummary {

        @Test
        @DisplayName("多合同聚合：金额合计、回款比例、状态分布")
        void aggregates_contracts() {
            when(contractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(contract("EFFECTIVE", "100000", "30000"),
                            contract("SETTLED", "200000", "200000")));

            ContractAmountSummaryVO vo = service.getAmountSummary(1L);

            assertThat(vo.getContractCount()).isEqualTo(2);
            assertThat(vo.getTotalContractAmount()).isEqualByComparingTo("300000");
            assertThat(vo.getTotalReceivedAmount()).isEqualByComparingTo("230000");
            // 230000 / 300000 = 0.7667
            assertThat(vo.getReceivedRate()).isEqualByComparingTo("0.7667");
            assertThat(vo.getTotalChangeAmount()).isEqualByComparingTo("10000");
            assertThat(vo.getStatusBreakdown()).hasSize(2);
            assertThat(vo.getStatusBreakdown())
                    .extracting(ContractAmountSummaryVO.StatusItem::getStatus)
                    .containsExactly("EFFECTIVE", "SETTLED");
        }

        @Test
        @DisplayName("项目ID为空抛业务异常")
        void rejects_null_project_id() {
            assertThatThrownBy(() -> service.getAmountSummary(null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("项目ID不能为空");
        }

        @Test
        @DisplayName("无生效合同抛业务异常")
        void throws_when_no_contract() {
            when(contractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> service.getAmountSummary(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("暂无生效的施工合同");
        }
    }

    @Nested
    @DisplayName("产值完成率趋势")
    class OutputTrend {

        @Test
        @DisplayName("按期间聚合：本期产值、滚动累计、完成率")
        void aggregates_by_period() {
            when(outputReportMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(report("2026-01", "10000"),
                            report("2026-01", "5000"),
                            report("2026-02", "20000")));
            when(contractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(contract("EFFECTIVE", "100000", "0")));

            List<OutputTrendItemVO> trend = service.getOutputTrend(1L, 12);

            assertThat(trend).hasSize(2);
            assertThat(trend.get(0).getPeriod()).isEqualTo("2026-01");
            assertThat(trend.get(0).getMonthlyOutput()).isEqualByComparingTo("15000");
            assertThat(trend.get(0).getCumulativeOutput()).isEqualByComparingTo("15000");
            assertThat(trend.get(1).getCumulativeOutput()).isEqualByComparingTo("35000");
            // 35000 / 100000 = 0.35
            assertThat(trend.get(1).getCompletionRate()).isEqualByComparingTo("0.3500");
        }

        @Test
        @DisplayName("months 限制仅返回最近期间")
        void limits_recent_periods() {
            when(outputReportMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(report("2026-01", "1000"),
                            report("2026-02", "1000"),
                            report("2026-03", "1000")));
            when(contractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            List<OutputTrendItemVO> trend = service.getOutputTrend(1L, 2);

            assertThat(trend).hasSize(2);
            assertThat(trend.get(0).getPeriod()).isEqualTo("2026-02");
            // 无合同金额时完成率为 null
            assertThat(trend.get(1).getCompletionRate()).isNull();
        }

        @Test
        @DisplayName("无已审批产值上报抛业务异常")
        void throws_when_no_report() {
            when(outputReportMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> service.getOutputTrend(1L, 12))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("暂无已审批的产值上报");
        }
    }
}
