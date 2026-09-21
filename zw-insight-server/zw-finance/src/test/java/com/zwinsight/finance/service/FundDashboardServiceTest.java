package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizFundRollingForecast;
import com.zwinsight.finance.domain.BizSecurityBond;
import com.zwinsight.finance.mapper.BizFundRollingForecastMapper;
import com.zwinsight.finance.mapper.BizSecurityBondMapper;
import com.zwinsight.finance.mapper.BizWageSpecialAccountMapper;
import com.zwinsight.finance.vo.FundDashboardVO;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * FundDashboardService 单元测试（53_V2026_51 老板资金看板）
 * <p>核心断言三大指标口径（docs/资金流转深度调研报告.md 第七节）：
 * 垫资=产值-收款（下限0）、经营现金流=收款-付款、回款率=收款/(收款+应收)。</p>
 */
@ExtendWith(MockitoExtension.class)
class FundDashboardServiceTest {

    @Mock private BizProjectMapper projectMapper;
    @Mock private BizSecurityBondMapper securityBondMapper;
    @Mock private BizWageSpecialAccountMapper wageAccountMapper;
    @Mock private BizFundRollingForecastMapper rollingForecastMapper;

    @InjectMocks
    private FundDashboardService fundDashboardService;

    private BizProject project(String output, String income, String expense, String receivable) {
        BizProject p = new BizProject();
        p.setCumulativeOutput(new BigDecimal(output));
        p.setTotalIncome(new BigDecimal(income));
        p.setTotalExpense(new BigDecimal(expense));
        p.setReceivableAmount(new BigDecimal(receivable));
        return p;
    }

    private BizSecurityBond bond(String form, String amount) {
        BizSecurityBond b = new BizSecurityBond();
        b.setBondForm(form);
        b.setAmount(new BigDecimal(amount));
        return b;
    }

    @Test
    @DisplayName("公司整体聚合 — 三大核心指标按口径计算（产值100万/收款60万/付款40万/应收20万）")
    void getDashboard_companyAggregate_computesCoreMetrics() {
        when(projectMapper.selectList(any()))
                .thenReturn(List.of(project("1000000", "600000", "400000", "200000")));
        when(securityBondMapper.selectList(any()))
                .thenReturn(List.of(bond("CASH", "50000"), bond("BANK_GUARANTEE", "30000")));
        when(wageAccountMapper.selectCount(any())).thenReturn(2L);
        BizFundRollingForecast forecast = new BizFundRollingForecast();
        forecast.setForecastMonth("2026-10");
        forecast.setExpectedReceipts(new BigDecimal("100000"));
        forecast.setExpectedPayments(new BigDecimal("150000"));
        forecast.setNetGap(new BigDecimal("-50000"));
        forecast.setRiskLevel("HIGH");
        when(rollingForecastMapper.selectList(any())).thenReturn(List.of(forecast));

        FundDashboardVO vo = fundDashboardService.getDashboard(null);

        // 核心一：垫资 = 100万 - 60万 = 40万；垫资率 = 40/100 = 0.4000
        assertThat(vo.getCumulativeOutput()).isEqualByComparingTo("1000000");
        assertThat(vo.getCumulativeReceived()).isEqualByComparingTo("600000");
        assertThat(vo.getAdvanceAmount()).isEqualByComparingTo("400000");
        assertThat(vo.getAdvanceRate()).isEqualByComparingTo("0.4000");
        // 核心二：经营现金流净额 = 收款60万 - 付款40万 = 20万
        assertThat(vo.getOperatingCashFlow()).isEqualByComparingTo("200000");
        // 核心三：回款率 = 60万 /(60万+20万) = 0.7500
        assertThat(vo.getCollectionRate()).isEqualByComparingTo("0.7500");
        // 辅助：保证金占用 8万，保函替代率 = 3/8 = 0.3750
        assertThat(vo.getBondOccupying()).isEqualByComparingTo("80000");
        assertThat(vo.getGuaranteeRatio()).isEqualByComparingTo("0.3750");
        // 辅助：工资专户预警数
        assertThat(vo.getWageAccountWarnings()).isEqualTo(2);
        // 滚动预测缺口快照
        assertThat(vo.getRollingGaps()).hasSize(1);
        assertThat(vo.getRollingGaps().get(0).getNetGap()).isEqualByComparingTo("-50000");
    }

    @Test
    @DisplayName("垫资下限 — 收款超过产值时垫资为0不回负")
    void getDashboard_incomeExceedsOutput_advanceFloorsAtZero() {
        when(projectMapper.selectList(any()))
                .thenReturn(List.of(project("500000", "800000", "300000", "0")));
        when(securityBondMapper.selectList(any())).thenReturn(List.of());
        when(wageAccountMapper.selectCount(any())).thenReturn(0L);
        when(rollingForecastMapper.selectList(any())).thenReturn(List.of());

        FundDashboardVO vo = fundDashboardService.getDashboard(null);

        assertThat(vo.getAdvanceAmount()).isEqualByComparingTo("0");
        assertThat(vo.getAdvanceRate()).isEqualByComparingTo("0");
        // 应收为0但有收款 → 视为全部收回，回款率=1
        assertThat(vo.getCollectionRate()).isEqualByComparingTo("1.0000");
        // 无保证金 → 占用0、替代率0（分母0不除）
        assertThat(vo.getBondOccupying()).isEqualByComparingTo("0");
        assertThat(vo.getGuaranteeRatio()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("指定项目不存在 — 抛 BusinessException(404 项目不存在)")
    void getDashboard_projectNotFound_throws() {
        when(projectMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> fundDashboardService.getDashboard(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
    }
}
