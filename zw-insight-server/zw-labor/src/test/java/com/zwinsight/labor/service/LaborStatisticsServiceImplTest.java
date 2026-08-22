package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.labor.domain.BizLaborContract;
import com.zwinsight.labor.domain.BizLaborPayroll;
import com.zwinsight.labor.mapper.BizLaborContractMapper;
import com.zwinsight.labor.mapper.BizLaborPayrollMapper;
import com.zwinsight.labor.vo.LaborCostRatioVO;
import com.zwinsight.labor.vo.PayrollTrendItemVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * LaborStatisticsServiceImpl 单元测试
 * <p>工资发放趋势按月聚合、劳务成本占比计算、异常路径。</p>
 */
@ExtendWith(MockitoExtension.class)
class LaborStatisticsServiceImplTest {

    @Mock
    private BizLaborPayrollMapper payrollMapper;

    @Mock
    private BizLaborContractMapper laborContractMapper;

    @InjectMocks
    private LaborStatisticsServiceImpl service;

    private BizLaborPayroll payroll(String periodStart, String settlement, String paid, String unpaid) {
        BizLaborPayroll p = new BizLaborPayroll();
        p.setProjectId(1L);
        p.setStatus("APPROVED");
        p.setPeriodStart(LocalDate.parse(periodStart));
        p.setTotalSettlement(new BigDecimal(settlement));
        p.setTotalPaid(new BigDecimal(paid));
        p.setUnpaid(new BigDecimal(unpaid));
        return p;
    }

    private BizLaborContract laborContract(String amount) {
        BizLaborContract c = new BizLaborContract();
        c.setProjectId(1L);
        c.setStatus("EFFECTIVE");
        c.setContractAmount(new BigDecimal(amount));
        return c;
    }

    @Nested
    @DisplayName("工资发放趋势")
    class PayrollTrend {

        @Test
        @DisplayName("按月聚合结算/已付/未付")
        void aggregates_by_month() {
            when(payrollMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(
                            payroll("2026-01-01", "10000", "8000", "2000"),
                            payroll("2026-01-15", "5000", "5000", "0"),
                            payroll("2026-02-01", "6000", "3000", "3000")));

            List<PayrollTrendItemVO> trend = service.getPayrollTrend(1L, 12);

            assertThat(trend).hasSize(2);
            assertThat(trend.get(0).getMonth()).isEqualTo("2026-01");
            assertThat(trend.get(0).getTotalSettlement()).isEqualByComparingTo("15000");
            assertThat(trend.get(0).getTotalPaid()).isEqualByComparingTo("13000");
            assertThat(trend.get(1).getMonth()).isEqualTo("2026-02");
            assertThat(trend.get(1).getTotalUnpaid()).isEqualByComparingTo("3000");
        }

        @Test
        @DisplayName("months 限制仅返回最近月份")
        void limits_recent_months() {
            when(payrollMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(
                            payroll("2026-01-01", "1000", "1000", "0"),
                            payroll("2026-02-01", "1000", "1000", "0"),
                            payroll("2026-03-01", "1000", "1000", "0")));

            List<PayrollTrendItemVO> trend = service.getPayrollTrend(1L, 2);

            assertThat(trend).hasSize(2);
            assertThat(trend.get(0).getMonth()).isEqualTo("2026-02");
        }

        @Test
        @DisplayName("无工资单数据抛业务异常")
        void throws_when_empty() {
            when(payrollMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> service.getPayrollTrend(1L, 12))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("暂无已审批的工资单");
        }
    }

    @Nested
    @DisplayName("劳务成本占比")
    class CostRatio {

        @Test
        @DisplayName("成本占比与付款比例计算")
        void computes_ratios() {
            when(laborContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(laborContract("100000"), laborContract("50000")));
            when(payrollMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(payroll("2026-01-01", "90000", "60000", "30000")));

            LaborCostRatioVO vo = service.getCostRatio(1L);

            assertThat(vo.getContractAmountTotal()).isEqualByComparingTo("150000");
            assertThat(vo.getSettlementTotal()).isEqualByComparingTo("90000");
            // 90000 / 150000 = 0.6
            assertThat(vo.getCostRatio()).isEqualByComparingTo("0.6000");
            // 60000 / 90000 = 0.6667
            assertThat(vo.getPaymentRatio()).isEqualByComparingTo("0.6667");
        }

        @Test
        @DisplayName("无生效劳务合同抛业务异常")
        void throws_when_no_effective_contract() {
            when(laborContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> service.getCostRatio(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("暂无生效的劳务合同");
        }

        @Test
        @DisplayName("项目ID为空抛业务异常")
        void rejects_null_project_id() {
            assertThatThrownBy(() -> service.getCostRatio(null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("项目ID不能为空");
        }
    }
}
