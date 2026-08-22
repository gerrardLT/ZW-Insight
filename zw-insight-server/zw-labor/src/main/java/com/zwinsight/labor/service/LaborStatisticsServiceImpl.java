package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.labor.domain.BizLaborContract;
import com.zwinsight.labor.domain.BizLaborPayroll;
import com.zwinsight.labor.mapper.BizLaborContractMapper;
import com.zwinsight.labor.mapper.BizLaborPayrollMapper;
import com.zwinsight.labor.vo.LaborCostRatioVO;
import com.zwinsight.labor.vo.PayrollTrendItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * 劳务统计服务实现（工资发放趋势 + 劳务成本占比）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LaborStatisticsServiceImpl implements LaborStatisticsService {

    private final BizLaborPayrollMapper payrollMapper;
    private final BizLaborContractMapper laborContractMapper;

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String CONTRACT_STATUS_EFFECTIVE = "EFFECTIVE";
    private static final int MAX_TREND_MONTHS = 36;
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    public List<PayrollTrendItemVO> getPayrollTrend(Long projectId, Integer months) {
        validateProjectId(projectId);
        int limit = months == null ? 12 : Math.min(Math.max(months, 1), MAX_TREND_MONTHS);

        List<BizLaborPayroll> payrolls = queryEffectivePayrolls(projectId);
        if (payrolls.isEmpty()) {
            throw new BusinessException("该项目暂无已审批的工资单");
        }

        // 按周期起始月份分组（TreeMap 保证月份升序）
        Map<YearMonth, List<BizLaborPayroll>> byMonth = new TreeMap<>();
        for (BizLaborPayroll payroll : payrolls) {
            if (payroll.getPeriodStart() == null) {
                continue;
            }
            byMonth.computeIfAbsent(YearMonth.from(payroll.getPeriodStart()), k -> new ArrayList<>()).add(payroll);
        }

        List<PayrollTrendItemVO> trend = new ArrayList<>();
        for (Map.Entry<YearMonth, List<BizLaborPayroll>> entry : byMonth.entrySet()) {
            PayrollTrendItemVO item = new PayrollTrendItemVO();
            item.setMonth(entry.getKey().format(MONTH_FORMATTER));
            item.setTotalSettlement(scale2(sumField(entry.getValue(), BizLaborPayroll::getTotalSettlement)));
            item.setTotalPaid(scale2(sumField(entry.getValue(), BizLaborPayroll::getTotalPaid)));
            item.setTotalUnpaid(scale2(sumField(entry.getValue(), BizLaborPayroll::getUnpaid)));
            trend.add(item);
        }

        // 仅返回最近 limit 个月份
        return trend.size() > limit ? trend.subList(trend.size() - limit, trend.size()) : trend;
    }

    @Override
    public LaborCostRatioVO getCostRatio(Long projectId) {
        validateProjectId(projectId);

        List<BizLaborContract> contracts = laborContractMapper.selectList(
                new LambdaQueryWrapper<BizLaborContract>()
                        .eq(BizLaborContract::getProjectId, projectId)
                        .eq(BizLaborContract::getStatus, CONTRACT_STATUS_EFFECTIVE));
        BigDecimal contractAmountTotal = sumContractAmount(contracts);
        if (contractAmountTotal.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("该项目暂无生效的劳务合同，无法计算成本占比");
        }

        List<BizLaborPayroll> payrolls = queryEffectivePayrolls(projectId);
        BigDecimal settlementTotal = sumField(payrolls, BizLaborPayroll::getTotalSettlement);
        BigDecimal paidTotal = sumField(payrolls, BizLaborPayroll::getTotalPaid);
        BigDecimal unpaidTotal = sumField(payrolls, BizLaborPayroll::getUnpaid);

        LaborCostRatioVO vo = new LaborCostRatioVO();
        vo.setProjectId(projectId);
        vo.setContractAmountTotal(scale2(contractAmountTotal));
        vo.setSettlementTotal(scale2(settlementTotal));
        vo.setPaidTotal(scale2(paidTotal));
        vo.setUnpaidTotal(scale2(unpaidTotal));
        vo.setCostRatio(rate(settlementTotal, contractAmountTotal));
        vo.setPaymentRatio(rate(paidTotal, settlementTotal));
        return vo;
    }

    /**
     * 已审批/已结算工资单（排除草稿）
     */
    private List<BizLaborPayroll> queryEffectivePayrolls(Long projectId) {
        return payrollMapper.selectList(
                new LambdaQueryWrapper<BizLaborPayroll>()
                        .eq(BizLaborPayroll::getProjectId, projectId)
                        .ne(BizLaborPayroll::getStatus, STATUS_DRAFT));
    }

    private BigDecimal sumContractAmount(List<BizLaborContract> contracts) {
        return contracts.stream()
                .map(BizLaborContract::getContractAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateProjectId(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
    }

    private BigDecimal sumField(List<BizLaborPayroll> list, Function<BizLaborPayroll, BigDecimal> getter) {
        return list.stream().map(getter).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 计算比例（保留 4 位小数），分母为 0 或 null 时返回 null
     */
    private BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
