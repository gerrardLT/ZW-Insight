package com.zwinsight.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.basedata.domain.BizSupplierEvaluation;
import com.zwinsight.basedata.mapper.BizSupplierEvaluationMapper;
import com.zwinsight.material.domain.BizMaterialInbound;
import com.zwinsight.material.domain.BizMaterialRefund;
import com.zwinsight.material.mapper.BizMaterialInboundMapper;
import com.zwinsight.material.mapper.BizMaterialRefundMapper;
import com.zwinsight.purchase.domain.BizPurchaseContract;
import com.zwinsight.purchase.domain.BizPurchaseSettlement;
import com.zwinsight.purchase.mapper.BizPurchaseContractMapper;
import com.zwinsight.purchase.mapper.BizPurchaseSettlementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 供应商自动评分服务
 * <p>
 * 每月1日 02:00 自动执行，基于上月交付数据对所有活跃供应商进行自动评分。
 * </p>
 * <p>
 * 评分维度（各20分，满分100分）：
 * <ul>
 *   <li>交付质量（qualityScore）：退货率越低分越高。退货率=退货金额/入库金额</li>
 *   <li>交付及时性（timelinessScore）：入库单数量/合同数量比率，越高说明履约越积极</li>
 *   <li>价格合理性（priceScore）：实际结算金额/合同金额偏差，偏差越小分越高</li>
 *   <li>服务配合度（serviceScore）：有退货记录扣分，无退货满分</li>
 *   <li>合作频率（cooperationScore）：合同数量越多分越高</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierAutoScoreService {

    private final BizPurchaseContractMapper purchaseContractMapper;
    private final BizPurchaseSettlementMapper settlementMapper;
    private final BizMaterialInboundMapper inboundMapper;
    private final BizMaterialRefundMapper refundMapper;
    private final BizSupplierEvaluationMapper evaluationMapper;

    /** 评分满分 */
    private static final int MAX_SCORE = 20;

    /**
     * 每月1日 02:00 自动执行供应商评分
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void executeMonthlyAutoScore() {
        log.info("供应商自动评分定时任务开始执行");
        LocalDate today = LocalDate.now();
        LocalDate lastMonthStart = today.minusMonths(1).withDayOfMonth(1);
        LocalDate lastMonthEnd = today.withDayOfMonth(1).minusDays(1);

        // 1. 查询所有有生效合同的供应商（按 partyBId 分组）
        List<BizPurchaseContract> allContracts = purchaseContractMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseContract>()
                        .eq(BizPurchaseContract::getStatus, "EFFECTIVE"));

        Map<Long, List<BizPurchaseContract>> contractsBySupplier = allContracts.stream()
                .filter(c -> c.getPartyBId() != null)
                .collect(Collectors.groupingBy(BizPurchaseContract::getPartyBId));

        int scoredCount = 0;
        for (Map.Entry<Long, List<BizPurchaseContract>> entry : contractsBySupplier.entrySet()) {
            try {
                Long supplierId = entry.getKey();
                List<BizPurchaseContract> supplierContracts = entry.getValue();
                scoreSupplier(supplierId, supplierContracts, lastMonthStart, lastMonthEnd);
                scoredCount++;
            } catch (Exception e) {
                log.error("供应商自动评分异常, supplierId={}", entry.getKey(), e);
            }
        }

        log.info("供应商自动评分完成, 评分供应商数: {}", scoredCount);
    }

    /**
     * 对单个供应商评分
     */
    @Transactional(rollbackFor = Exception.class)
    public void scoreSupplier(Long supplierId, List<BizPurchaseContract> contracts,
                              LocalDate periodStart, LocalDate periodEnd) {
        // 获取该供应商所有合同ID
        List<Long> contractIds = contracts.stream()
                .map(BizPurchaseContract::getId)
                .collect(Collectors.toList());

        String supplierName = contracts.get(0).getPartyBName();

        // --- 维度1: 交付质量（退货率） ---
        BigDecimal totalInboundAmount = getInboundAmount(contractIds);
        BigDecimal totalRefundAmount = getRefundAmount(contractIds);
        int qualityScore = calculateQualityScore(totalInboundAmount, totalRefundAmount);

        // --- 维度2: 交付及时性（入库批次 / 合同数） ---
        long inboundCount = getInboundCount(contractIds);
        int timelinessScore = calculateTimelinessScore(inboundCount, contracts.size());

        // --- 维度3: 价格合理性（结算偏差率） ---
        BigDecimal totalContractAmount = contracts.stream()
                .map(c -> c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSettledAmount = getSettledAmount(contractIds);
        int priceScore = calculatePriceScore(totalContractAmount, totalSettledAmount);

        // --- 维度4: 服务配合度（退货次数） ---
        long refundCount = getRefundCount(contractIds);
        int serviceScore = calculateServiceScore(refundCount);

        // --- 维度5: 合作频率 ---
        int cooperationScore = calculateCooperationScore(contracts.size());

        // 保存自动评价记录
        BizSupplierEvaluation evaluation = new BizSupplierEvaluation();
        evaluation.setSupplierId(supplierId);
        evaluation.setSupplierName(supplierName);
        evaluation.setQualityScore(qualityScore);
        evaluation.setTimelinessScore(timelinessScore);
        evaluation.setPriceScore(priceScore);
        evaluation.setServiceScore(serviceScore);
        evaluation.setCooperationScore(cooperationScore);
        evaluation.setEvaluationDate(LocalDate.now());
        evaluation.setEvaluationType("AUTO");
        evaluation.setRemark("系统自动评分（" + periodStart + "~" + periodEnd + "）");

        // 计算综合评分
        int total = qualityScore + timelinessScore + priceScore + serviceScore + cooperationScore;
        evaluation.setTotalScore(BigDecimal.valueOf(total).divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP));

        evaluationMapper.insert(evaluation);
    }

    // ==================== 数据查询 ====================

    private BigDecimal getInboundAmount(List<Long> contractIds) {
        if (contractIds.isEmpty()) return BigDecimal.ZERO;
        List<BizMaterialInbound> inbounds = inboundMapper.selectList(
                new LambdaQueryWrapper<BizMaterialInbound>()
                        .in(BizMaterialInbound::getContractId, contractIds)
                        .eq(BizMaterialInbound::getStatus, "APPROVED"));
        return inbounds.stream()
                .map(i -> i.getTotalAmount() != null ? i.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private long getInboundCount(List<Long> contractIds) {
        if (contractIds.isEmpty()) return 0;
        return inboundMapper.selectCount(
                new LambdaQueryWrapper<BizMaterialInbound>()
                        .in(BizMaterialInbound::getContractId, contractIds)
                        .eq(BizMaterialInbound::getStatus, "APPROVED"));
    }

    private BigDecimal getRefundAmount(List<Long> contractIds) {
        if (contractIds.isEmpty()) return BigDecimal.ZERO;
        List<BizMaterialRefund> refunds = refundMapper.selectList(
                new LambdaQueryWrapper<BizMaterialRefund>()
                        .in(BizMaterialRefund::getContractId, contractIds)
                        .eq(BizMaterialRefund::getStatus, "APPROVED"));
        return refunds.stream()
                .map(r -> r.getRefundAmount() != null ? r.getRefundAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private long getRefundCount(List<Long> contractIds) {
        if (contractIds.isEmpty()) return 0;
        return refundMapper.selectCount(
                new LambdaQueryWrapper<BizMaterialRefund>()
                        .in(BizMaterialRefund::getContractId, contractIds)
                        .eq(BizMaterialRefund::getStatus, "APPROVED"));
    }

    private BigDecimal getSettledAmount(List<Long> contractIds) {
        if (contractIds.isEmpty()) return BigDecimal.ZERO;
        List<BizPurchaseSettlement> settlements = settlementMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseSettlement>()
                        .in(BizPurchaseSettlement::getContractId, contractIds)
                        .eq(BizPurchaseSettlement::getStatus, "APPROVED"));
        return settlements.stream()
                .map(s -> s.getSettlementAmount() != null ? s.getSettlementAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== 评分算法 ====================

    /** 交付质量：退货率0%=20分，>=20%=0分，线性递减 */
    private int calculateQualityScore(BigDecimal inboundAmount, BigDecimal refundAmount) {
        if (inboundAmount.compareTo(BigDecimal.ZERO) <= 0) return MAX_SCORE;
        BigDecimal refundRate = refundAmount.divide(inboundAmount, 4, RoundingMode.HALF_UP);
        double rate = refundRate.doubleValue();
        if (rate >= 0.2) return 0;
        return (int) Math.round(MAX_SCORE * (1 - rate / 0.2));
    }

    /** 交付及时性：入库批次/合同数 >= 3 满分，0次=0分 */
    private int calculateTimelinessScore(long inboundCount, int contractCount) {
        if (contractCount == 0) return 0;
        double ratio = (double) inboundCount / contractCount;
        if (ratio >= 3) return MAX_SCORE;
        return (int) Math.round(MAX_SCORE * ratio / 3.0);
    }

    /** 价格合理性：结算/合同偏差 0%=20分，>=30%=0分 */
    private int calculatePriceScore(BigDecimal contractAmount, BigDecimal settledAmount) {
        if (contractAmount.compareTo(BigDecimal.ZERO) <= 0) return MAX_SCORE;
        BigDecimal deviation = settledAmount.subtract(contractAmount).abs()
                .divide(contractAmount, 4, RoundingMode.HALF_UP);
        double dev = deviation.doubleValue();
        if (dev >= 0.3) return 0;
        return (int) Math.round(MAX_SCORE * (1 - dev / 0.3));
    }

    /** 服务配合度：0次退货=20分，1次=15分，2次=10分，3次=5分，>=4次=0分 */
    private int calculateServiceScore(long refundCount) {
        if (refundCount == 0) return MAX_SCORE;
        if (refundCount == 1) return 15;
        if (refundCount == 2) return 10;
        if (refundCount == 3) return 5;
        return 0;
    }

    /** 合作频率：>=5份合同=20分，1份=4分，线性递增 */
    private int calculateCooperationScore(int contractCount) {
        if (contractCount >= 5) return MAX_SCORE;
        return (int) Math.round(MAX_SCORE * contractCount / 5.0);
    }
}
