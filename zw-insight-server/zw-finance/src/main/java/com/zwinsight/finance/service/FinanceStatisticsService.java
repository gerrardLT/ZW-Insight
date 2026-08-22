package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizInvoiceApply;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.domain.BizPaymentReceived;
import com.zwinsight.finance.mapper.BizInvoiceApplyMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.BizPaymentReceivedMapper;
import com.zwinsight.finance.vo.CollectionRateVO;
import com.zwinsight.finance.vo.FundPlanItemVO;
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
 * 财务统计服务（回款率分析 + 资金计划）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceStatisticsService {

    private final BizPaymentReceivedMapper paymentReceivedMapper;
    private final BizInvoiceApplyMapper invoiceApplyMapper;
    private final BizPaymentApplyMapper paymentApplyMapper;

    private static final String STATUS_APPROVED = "APPROVED";
    private static final int MAX_PLAN_MONTHS = 24;
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * 回款率分析（已审批回款对比已审批开票）
     *
     * @param projectId 项目ID
     * @return 回款率
     */
    public CollectionRateVO getCollectionRate(Long projectId) {
        validateProjectId(projectId);

        List<BizPaymentReceived> receivedList = paymentReceivedMapper.selectList(
                new LambdaQueryWrapper<BizPaymentReceived>()
                        .eq(BizPaymentReceived::getProjectId, projectId)
                        .eq(BizPaymentReceived::getStatus, STATUS_APPROVED));
        List<BizInvoiceApply> invoiceList = invoiceApplyMapper.selectList(
                new LambdaQueryWrapper<BizInvoiceApply>()
                        .eq(BizInvoiceApply::getProjectId, projectId)
                        .eq(BizInvoiceApply::getStatus, STATUS_APPROVED));
        if (receivedList.isEmpty() && invoiceList.isEmpty()) {
            throw new BusinessException("该项目暂无已审批的开票或回款数据");
        }

        BigDecimal totalReceived = sumField(receivedList, BizPaymentReceived::getReceiveAmount);
        BigDecimal totalInvoiced = sumField(invoiceList, BizInvoiceApply::getInvoiceAmount);

        CollectionRateVO vo = new CollectionRateVO();
        vo.setProjectId(projectId);
        vo.setTotalInvoiced(scale2(totalInvoiced));
        vo.setTotalReceived(scale2(totalReceived));
        vo.setCollectionRate(rate(totalReceived, totalInvoiced));
        vo.setUncollectedAmount(scale2(totalInvoiced.subtract(totalReceived)));
        return vo;
    }

    /**
     * 资金计划（已审批付款申请按付款日期月份聚合的应付预测）
     *
     * @param projectId 项目ID
     * @param months    返回的月份数（默认 6，上限 24）
     * @return 计划列表（按月份升序）
     */
    public List<FundPlanItemVO> getFundPlan(Long projectId, Integer months) {
        validateProjectId(projectId);
        int limit = months == null ? 6 : Math.min(Math.max(months, 1), MAX_PLAN_MONTHS);

        List<BizPaymentApply> applies = paymentApplyMapper.selectList(
                new LambdaQueryWrapper<BizPaymentApply>()
                        .eq(BizPaymentApply::getProjectId, projectId)
                        .eq(BizPaymentApply::getStatus, STATUS_APPROVED)
                        .isNotNull(BizPaymentApply::getPaymentDate));
        if (applies.isEmpty()) {
            throw new BusinessException("该项目暂无已审批的付款申请");
        }

        // 按付款日期月份分组（TreeMap 保证月份升序）
        Map<YearMonth, List<BizPaymentApply>> byMonth = new TreeMap<>();
        for (BizPaymentApply apply : applies) {
            if (apply.getPaymentDate() == null) {
                continue;
            }
            byMonth.computeIfAbsent(YearMonth.from(apply.getPaymentDate()), k -> new ArrayList<>()).add(apply);
        }

        List<FundPlanItemVO> plan = new ArrayList<>();
        for (Map.Entry<YearMonth, List<BizPaymentApply>> entry : byMonth.entrySet()) {
            FundPlanItemVO item = new FundPlanItemVO();
            item.setMonth(entry.getKey().format(MONTH_FORMATTER));
            item.setPlannedAmount(scale2(sumField(entry.getValue(), BizPaymentApply::getPaymentAmount)));
            item.setApplyCount(entry.getValue().size());
            plan.add(item);
        }

        // 仅返回最近 limit 个月份
        return plan.size() > limit ? plan.subList(plan.size() - limit, plan.size()) : plan;
    }

    private void validateProjectId(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
    }

    private <T> BigDecimal sumField(List<T> list, Function<T, BigDecimal> getter) {
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
