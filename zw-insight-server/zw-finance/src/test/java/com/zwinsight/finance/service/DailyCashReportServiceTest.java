package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizBankFlow;
import com.zwinsight.finance.domain.BizDailyCashReport;
import com.zwinsight.finance.mapper.BizBankAccountMapper;
import com.zwinsight.finance.mapper.BizBankFlowMapper;
import com.zwinsight.finance.mapper.BizDailyCashReportMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DailyCashReportService 单元测试（55_V2026_53 资金日报快照）
 * <p>核心断言：头寸按账户类型分桶汇总、当日收支按方向汇总、大额支出门槛过滤、
 * upsert 幂等（同日覆盖更新）、趋势查询天数边界。</p>
 */
@ExtendWith(MockitoExtension.class)
class DailyCashReportServiceTest {

    @Mock private BizDailyCashReportMapper reportMapper;
    @Mock private BizBankAccountMapper accountMapper;
    @Mock private BizBankFlowMapper flowMapper;
    @Mock private BankFlowService bankFlowService;

    @InjectMocks
    private DailyCashReportService dailyCashReportService;

    private static final LocalDate DATE = LocalDate.of(2026, 9, 20);

    private Map<String, Object> balanceRow(String accountType, String balance) {
        Map<String, Object> row = new HashMap<>();
        row.put("accountType", accountType);
        row.put("balance", new BigDecimal(balance));
        return row;
    }

    private BizBankFlow flow(String direction, String amount) {
        BizBankFlow f = new BizBankFlow();
        f.setDirection(direction);
        f.setAmount(new BigDecimal(amount));
        f.setFlowDate(DATE);
        return f;
    }

    @Nested
    @DisplayName("generate() 日报聚合")
    class GenerateTests {

        @Test
        @DisplayName("正常路径 — 头寸按类型分桶：基本户100万+一般户50万+专户30万，合计180万")
        void generate_balanceBucketed() {
            when(bankFlowService.latestBalances(DATE)).thenReturn(List.of(
                    balanceRow("BASIC", "1000000"),
                    balanceRow("GENERAL", "500000"),
                    balanceRow("SPECIAL", "300000")));
            when(flowMapper.selectList(any())).thenReturn(List.of());
            when(reportMapper.selectOne(any())).thenReturn(null);

            BizDailyCashReport report = dailyCashReportService.generate(DATE, null);

            assertThat(report.getTotalBalance()).isEqualByComparingTo("1800000");
            assertThat(report.getBasicBalance()).isEqualByComparingTo("1000000");
            assertThat(report.getGeneralBalance()).isEqualByComparingTo("500000");
            assertThat(report.getSpecialBalance()).isEqualByComparingTo("300000");
            assertThat(report.getAccountCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("正常路径 — 当日收支按方向汇总，净头寸=流入-流出")
        void generate_inflowOutflow() {
            when(bankFlowService.latestBalances(DATE)).thenReturn(List.of());
            when(flowMapper.selectList(any())).thenReturn(List.of(
                    flow(BizBankFlow.DIRECTION_IN, "200000"),
                    flow(BizBankFlow.DIRECTION_IN, "300000"),
                    flow(BizBankFlow.DIRECTION_OUT, "100000")));
            when(reportMapper.selectOne(any())).thenReturn(null);

            BizDailyCashReport report = dailyCashReportService.generate(DATE, null);

            assertThat(report.getInflowAmount()).isEqualByComparingTo("500000");
            assertThat(report.getOutflowAmount()).isEqualByComparingTo("100000");
            assertThat(report.getNetPosition()).isEqualByComparingTo("400000");
        }

        @Test
        @DisplayName("正常路径 — 大额支出按 50 万门槛过滤（1笔60万计入，20万不计入）")
        void generate_largeOutflowFiltered() {
            when(bankFlowService.latestBalances(DATE)).thenReturn(List.of());
            when(flowMapper.selectList(any())).thenReturn(List.of(
                    flow(BizBankFlow.DIRECTION_OUT, "600000"),
                    flow(BizBankFlow.DIRECTION_OUT, "200000")));
            when(reportMapper.selectOne(any())).thenReturn(null);

            BizDailyCashReport report = dailyCashReportService.generate(DATE, null);

            assertThat(report.getLargeOutflowCount()).isEqualTo(1);
            assertThat(report.getLargeOutflowAmount()).isEqualByComparingTo("600000");
            assertThat(report.getOutflowAmount()).isEqualByComparingTo("800000");
        }

        @Test
        @DisplayName("正常路径 — 自定义大额门槛 10 万（两笔支出均计入）")
        void generate_customThreshold() {
            when(bankFlowService.latestBalances(DATE)).thenReturn(List.of());
            when(flowMapper.selectList(any())).thenReturn(List.of(
                    flow(BizBankFlow.DIRECTION_OUT, "600000"),
                    flow(BizBankFlow.DIRECTION_OUT, "200000")));
            when(reportMapper.selectOne(any())).thenReturn(null);

            BizDailyCashReport report = dailyCashReportService.generate(DATE, new BigDecimal("100000"));

            assertThat(report.getLargeOutflowCount()).isEqualTo(2);
            assertThat(report.getLargeOutflowAmount()).isEqualByComparingTo("800000");
        }

        @Test
        @DisplayName("正常路径 — 同日重复生成执行覆盖更新而非插入（幂等 upsert）")
        void generate_existingUpdates() {
            BizDailyCashReport existing = new BizDailyCashReport();
            existing.setId(888L);
            existing.setReportDate(DATE);
            when(bankFlowService.latestBalances(DATE)).thenReturn(List.of());
            when(flowMapper.selectList(any())).thenReturn(List.of());
            when(reportMapper.selectOne(any())).thenReturn(existing);

            BizDailyCashReport report = dailyCashReportService.generate(DATE, null);

            assertThat(report.getId()).isEqualTo(888L);
            verify(reportMapper).updateById(report);
            verify(reportMapper, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("getReport() 查询日报")
    class GetReportTests {

        @Test
        @DisplayName("异常路径 — 无日报数据抛 404（不静默返回空）")
        void getReport_notFound_throws() {
            when(reportMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> dailyCashReportService.getReport(DATE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("暂无日报数据");
        }
    }

    @Nested
    @DisplayName("trend() 趋势查询边界")
    class TrendTests {

        @Test
        @DisplayName("异常路径 — 天数超 1-90 范围被拦截")
        void trend_invalidDays_rejected() {
            assertThatThrownBy(() -> dailyCashReportService.trend(91))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("1-90");
        }

        @Test
        @DisplayName("正常路径 — 7 日趋势返回对应快照列表")
        void trend_returnsList() {
            BizDailyCashReport r1 = new BizDailyCashReport();
            r1.setReportDate(DATE);
            when(reportMapper.selectList(any())).thenReturn(List.of(r1));

            List<BizDailyCashReport> trend = dailyCashReportService.trend(7);

            assertThat(trend).hasSize(1);
        }
    }

    @Nested
    @DisplayName("largeOutflows() 大额支出下钻")
    class LargeOutflowsTests {

        @Test
        @DisplayName("正常路径 — 按日期+方向+门槛查询大额支出明细")
        void largeOutflows_queryByThreshold() {
            when(flowMapper.selectList(any())).thenReturn(List.of(flow(BizBankFlow.DIRECTION_OUT, "600000")));

            List<BizBankFlow> flows = dailyCashReportService.largeOutflows(DATE, new BigDecimal("500000"));

            assertThat(flows).hasSize(1);
        }
    }
}
