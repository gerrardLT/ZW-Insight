package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.finance.domain.BizFundRollingForecast;
import com.zwinsight.finance.domain.BizSecurityBond;
import com.zwinsight.finance.domain.BizWageSpecialAccount;
import com.zwinsight.finance.mapper.BizFundRollingForecastMapper;
import com.zwinsight.finance.mapper.BizSecurityBondMapper;
import com.zwinsight.finance.mapper.BizWageSpecialAccountMapper;
import com.zwinsight.finance.vo.FundDashboardVO;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 老板资金看板服务（核心三大指标：垫资 / 现金流 / 回款率）
 * <p>口径说明（与 docs/资金流转深度调研报告.md 第七节一致）：
 * 1) 垫资 = 累计产值 - 累计收款（项目表真实回写字段，非另算）；
 * 2) 经营性现金流 = 收付口径（total_income - total_expense，后者仅由付款审批回写）；
 * 3) 回款率 = 累计收款 / (累计收款 + 应收账款)；应收为0时回款率=1（全部收回）。
 * 项目维度取单项目；公司维度聚合全部项目（NULL projectId）。</p>
 */
@Service
@RequiredArgsConstructor
public class FundDashboardService {

    private final BizProjectMapper projectMapper;
    private final BizSecurityBondMapper securityBondMapper;
    private final BizWageSpecialAccountMapper wageAccountMapper;
    private final BizFundRollingForecastMapper rollingForecastMapper;

    /**
     * 获取资金看板（projectId=NULL 时为公司整体聚合）
     */
    public FundDashboardVO getDashboard(Long projectId) {
        List<BizProject> projects = projectMapper.selectList(
                new LambdaQueryWrapper<BizProject>()
                        .eq(projectId != null, BizProject::getId, projectId));
        if (projectId != null && projects.isEmpty()) {
            throw new com.zwinsight.common.exception.BusinessException(404, "项目不存在");
        }

        FundDashboardVO vo = new FundDashboardVO();
        vo.setProjectId(projectId);

        // ===== 聚合项目层真实回写字段 =====
        BigDecimal output = BigDecimal.ZERO;
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        BigDecimal receivable = BigDecimal.ZERO;
        for (BizProject p : projects) {
            output = output.add(nvl(p.getCumulativeOutput()));
            income = income.add(nvl(p.getTotalIncome()));
            expense = expense.add(nvl(p.getTotalExpense()));
            receivable = receivable.add(nvl(p.getReceivableAmount()));
        }

        // 核心一：垫资
        vo.setCumulativeOutput(output);
        vo.setCumulativeReceived(income);
        BigDecimal advance = output.subtract(income).max(BigDecimal.ZERO);
        vo.setAdvanceAmount(advance);
        vo.setAdvanceRate(output.signum() > 0
                ? advance.divide(output, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        // 核心二：经营性现金流（收付口径）
        vo.setCashInflow(income);
        vo.setCashOutflow(expense);
        vo.setOperatingCashFlow(income.subtract(expense));

        // 核心三：回款率 = 收款 / (收款 + 应收)；无应收视为全部收回
        BigDecimal denominator = income.add(receivable);
        vo.setReceivableTotal(receivable);
        vo.setCollectionRate(denominator.signum() > 0
                ? income.divide(denominator, 4, RoundingMode.HALF_UP)
                : (income.signum() > 0 ? BigDecimal.ONE : BigDecimal.ZERO));

        // 辅助：保证金占用与保函替代率
        fillBondStatistics(vo, projectId);

        // 辅助：工资专户合规预警数
        vo.setWageAccountWarnings(countWageWarnings(projectId));

        // 滚动预测缺口（快照）
        vo.setRollingGaps(loadRollingGaps(projectId));
        return vo;
    }

    // ==================== 私有方法 ====================

    private void fillBondStatistics(FundDashboardVO vo, Long projectId) {
        List<BizSecurityBond> occupying = securityBondMapper.selectList(
                new LambdaQueryWrapper<BizSecurityBond>()
                        .eq(projectId != null, BizSecurityBond::getProjectId, projectId)
                        .in(BizSecurityBond::getRefundStatus,
                                BizSecurityBond.STATUS_DEPOSITED, BizSecurityBond.STATUS_USED));
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal guarantee = BigDecimal.ZERO;
        for (BizSecurityBond bond : occupying) {
            if ("CASH".equals(bond.getBondForm())) {
                cash = cash.add(bond.getAmount());
            } else {
                guarantee = guarantee.add(bond.getAmount());
            }
        }
        BigDecimal total = cash.add(guarantee);
        vo.setBondOccupying(total);
        vo.setGuaranteeRatio(total.signum() > 0
                ? guarantee.divide(total, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
    }

    private Integer countWageWarnings(Long projectId) {
        return Math.toIntExact(wageAccountMapper.selectCount(
                new LambdaQueryWrapper<BizWageSpecialAccount>()
                        .eq(projectId != null, BizWageSpecialAccount::getProjectId, projectId)
                        .eq(BizWageSpecialAccount::getStatus, BizWageSpecialAccount.STATUS_ACTIVE)
                        .ne(BizWageSpecialAccount::getComplianceFlag,
                                BizWageSpecialAccount.COMPLIANCE_COMPLIANT)));
    }

    private List<FundDashboardVO.GapItem> loadRollingGaps(Long projectId) {
        List<BizFundRollingForecast> snapshots = rollingForecastMapper.selectList(
                new LambdaQueryWrapper<BizFundRollingForecast>()
                        .eq(projectId != null, BizFundRollingForecast::getProjectId, projectId)
                        .isNull(projectId == null, BizFundRollingForecast::getProjectId)
                        .orderByAsc(BizFundRollingForecast::getForecastMonth));
        List<FundDashboardVO.GapItem> items = new ArrayList<>();
        for (BizFundRollingForecast f : snapshots) {
            FundDashboardVO.GapItem item = new FundDashboardVO.GapItem();
            item.setMonth(f.getForecastMonth());
            item.setExpectedReceipts(nvl(f.getExpectedReceipts()));
            item.setExpectedPayments(nvl(f.getExpectedPayments()));
            item.setNetGap(nvl(f.getNetGap()));
            item.setRiskLevel(f.getRiskLevel());
            items.add(item);
        }
        return items;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
