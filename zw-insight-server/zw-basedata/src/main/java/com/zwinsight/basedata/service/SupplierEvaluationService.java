package com.zwinsight.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.basedata.domain.BizSupplierEvaluation;
import com.zwinsight.basedata.mapper.BizSupplierEvaluationMapper;
import com.zwinsight.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 供应商评价服务
 */
@Service
@RequiredArgsConstructor
public class SupplierEvaluationService {

    private final BizSupplierEvaluationMapper evaluationMapper;

    /**
     * 分页查询
     */
    public PageResult<BizSupplierEvaluation> page(int page, int size, Long supplierId) {
        Page<BizSupplierEvaluation> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizSupplierEvaluation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(supplierId != null, BizSupplierEvaluation::getSupplierId, supplierId)
                .orderByDesc(BizSupplierEvaluation::getEvaluationDate);
        Page<BizSupplierEvaluation> result = evaluationMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增评价（自动计算综合评分 = 各项平均分）
     */
    public void save(BizSupplierEvaluation evaluation) {
        // 计算综合评分
        int total = 0;
        int count = 0;
        if (evaluation.getQualityScore() != null) { total += evaluation.getQualityScore(); count++; }
        if (evaluation.getTimelinessScore() != null) { total += evaluation.getTimelinessScore(); count++; }
        if (evaluation.getPriceScore() != null) { total += evaluation.getPriceScore(); count++; }
        if (evaluation.getServiceScore() != null) { total += evaluation.getServiceScore(); count++; }
        if (evaluation.getCooperationScore() != null) { total += evaluation.getCooperationScore(); count++; }

        if (count > 0) {
            evaluation.setTotalScore(
                    BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
        } else {
            evaluation.setTotalScore(BigDecimal.ZERO);
        }

        evaluationMapper.insert(evaluation);
    }

    /**
     * 获取供应商平均评分
     */
    public BigDecimal getAvgScore(Long supplierId) {
        List<BizSupplierEvaluation> evaluations = evaluationMapper.selectList(
                new LambdaQueryWrapper<BizSupplierEvaluation>()
                        .eq(BizSupplierEvaluation::getSupplierId, supplierId));
        if (evaluations.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = evaluations.stream()
                .map(e -> e.getTotalScore() != null ? e.getTotalScore() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(evaluations.size()), 2, RoundingMode.HALF_UP);
    }
}
