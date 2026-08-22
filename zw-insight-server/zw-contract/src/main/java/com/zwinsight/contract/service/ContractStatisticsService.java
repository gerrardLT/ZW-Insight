package com.zwinsight.contract.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizOutputReport;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizOutputReportMapper;
import com.zwinsight.contract.vo.ContractAmountSummaryVO;
import com.zwinsight.contract.vo.OutputTrendItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 合同统计服务（金额汇总 + 产值完成率趋势）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractStatisticsService {

    private final BizConstructionContractMapper contractMapper;
    private final BizOutputReportMapper outputReportMapper;

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String CONTRACT_TYPE_REGISTER = "REGISTER";
    private static final int MAX_TREND_MONTHS = 36;

    /**
     * 合同金额汇总（仅统计非草稿的登记合同，变更/补充合同金额已体现在累计变更字段）
     *
     * @param projectId 项目ID
     * @return 金额汇总
     */
    public ContractAmountSummaryVO getAmountSummary(Long projectId) {
        validateProjectId(projectId);

        List<BizConstructionContract> contracts = contractMapper.selectList(
                new LambdaQueryWrapper<BizConstructionContract>()
                        .eq(BizConstructionContract::getProjectId, projectId)
                        .eq(BizConstructionContract::getContractType, CONTRACT_TYPE_REGISTER)
                        .ne(BizConstructionContract::getStatus, STATUS_DRAFT));
        if (contracts.isEmpty()) {
            throw new BusinessException("该项目暂无生效的施工合同");
        }

        ContractAmountSummaryVO vo = new ContractAmountSummaryVO();
        vo.setProjectId(projectId);
        vo.setContractCount(contracts.size());
        vo.setTotalContractAmount(scale2(sumField(contracts, BizConstructionContract::getContractAmount)));
        vo.setTotalAmountWithoutTax(scale2(sumField(contracts, BizConstructionContract::getAmountWithoutTax)));
        vo.setTotalTaxAmount(scale2(sumField(contracts, BizConstructionContract::getTaxAmount)));
        vo.setTotalChangeAmount(scale2(sumField(contracts, BizConstructionContract::getCumulativeChangeAmount)));
        vo.setTotalOutput(scale2(sumField(contracts, BizConstructionContract::getCumulativeOutput)));
        vo.setTotalInvoiceAmount(scale2(sumField(contracts, BizConstructionContract::getCumulativeInvoiceAmount)));
        BigDecimal totalReceived = sumField(contracts, BizConstructionContract::getCumulativeReceivedAmount);
        vo.setTotalReceivedAmount(scale2(totalReceived));
        vo.setReceivedRate(rate(totalReceived, vo.getTotalContractAmount()));

        // 按状态分组（保持状态枚举顺序输出）
        Map<String, List<BizConstructionContract>> byStatus = contracts.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getStatus() != null ? c.getStatus() : "UNKNOWN",
                        TreeMap::new, Collectors.toList()));
        List<ContractAmountSummaryVO.StatusItem> breakdown = new ArrayList<>();
        for (Map.Entry<String, List<BizConstructionContract>> entry : byStatus.entrySet()) {
            ContractAmountSummaryVO.StatusItem item = new ContractAmountSummaryVO.StatusItem();
            item.setStatus(entry.getKey());
            item.setCount(entry.getValue().size());
            item.setAmount(scale2(sumField(entry.getValue(), BizConstructionContract::getContractAmount)));
            breakdown.add(item);
        }
        vo.setStatusBreakdown(breakdown);
        return vo;
    }

    /**
     * 产值完成率趋势（已审批产值上报按期间聚合，累计值为本期产值滚动累加）
     *
     * @param projectId 项目ID
     * @param months    返回的期间数（默认 12，上限 36）
     * @return 趋势列表（按期间升序）
     */
    public List<OutputTrendItemVO> getOutputTrend(Long projectId, Integer months) {
        validateProjectId(projectId);
        int limit = months == null ? 12 : Math.min(Math.max(months, 1), MAX_TREND_MONTHS);

        List<BizOutputReport> reports = outputReportMapper.selectList(
                new LambdaQueryWrapper<BizOutputReport>()
                        .eq(BizOutputReport::getProjectId, projectId)
                        .eq(BizOutputReport::getStatus, STATUS_APPROVED)
                        .isNotNull(BizOutputReport::getReportPeriod));
        if (reports.isEmpty()) {
            throw new BusinessException("该项目暂无已审批的产值上报");
        }

        // 合同金额合计作为完成率分母（无登记合同则完成率为 null）
        BigDecimal contractAmountTotal = sumRegisterContractAmount(projectId);

        // 按期间分组汇总本期产值（TreeMap 保证期间升序）
        Map<String, BigDecimal> outputByPeriod = new TreeMap<>();
        for (BizOutputReport report : reports) {
            if (StrUtil.isBlank(report.getReportPeriod())) {
                continue;
            }
            BigDecimal output = report.getCurrentOutput() != null ? report.getCurrentOutput() : BigDecimal.ZERO;
            outputByPeriod.merge(report.getReportPeriod(), output, BigDecimal::add);
        }

        List<OutputTrendItemVO> trend = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : outputByPeriod.entrySet()) {
            cumulative = cumulative.add(entry.getValue());
            OutputTrendItemVO item = new OutputTrendItemVO();
            item.setPeriod(entry.getKey());
            item.setMonthlyOutput(scale2(entry.getValue()));
            item.setCumulativeOutput(scale2(cumulative));
            item.setCompletionRate(rate(cumulative, contractAmountTotal));
            trend.add(item);
        }

        // 仅返回最近 limit 个期间
        return trend.size() > limit ? trend.subList(trend.size() - limit, trend.size()) : trend;
    }

    /**
     * 项目登记合同（非草稿）金额合计，用于完成率分母
     */
    private BigDecimal sumRegisterContractAmount(Long projectId) {
        List<BizConstructionContract> contracts = contractMapper.selectList(
                new LambdaQueryWrapper<BizConstructionContract>()
                        .eq(BizConstructionContract::getProjectId, projectId)
                        .eq(BizConstructionContract::getContractType, CONTRACT_TYPE_REGISTER)
                        .ne(BizConstructionContract::getStatus, STATUS_DRAFT));
        return sumField(contracts, BizConstructionContract::getContractAmount);
    }

    private void validateProjectId(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
    }

    private BigDecimal sumField(List<BizConstructionContract> list,
                                Function<BizConstructionContract, BigDecimal> getter) {
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
