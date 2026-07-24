package com.zwinsight.basedata.controller;

import com.zwinsight.basedata.domain.BizSupplierEvaluation;
import com.zwinsight.basedata.service.SupplierEvaluationService;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 供应商评价接口
 */
@RestController
@RequestMapping("/api/v1/basedata/supplier-evaluation")
@RequiredArgsConstructor
public class SupplierEvaluationController {

    private final SupplierEvaluationService evaluationService;

    /**
     * 分页查询评价
     */
    @GetMapping
    public R<PageResult<BizSupplierEvaluation>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String supplierName) {
        return R.ok(evaluationService.page(page, size, supplierId, supplierName));
    }

    /**
     * 新增评价
     */
    @PostMapping
    public R<Void> save(@RequestBody BizSupplierEvaluation evaluation) {
        evaluationService.save(evaluation);
        return R.ok();
    }

    /**
     * 获取供应商平均评分
     */
    @GetMapping("/avg-score/{supplierId}")
    public R<BigDecimal> getAvgScore(@PathVariable Long supplierId) {
        return R.ok(evaluationService.getAvgScore(supplierId));
    }

    /**
     * 更新评价
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizSupplierEvaluation evaluation) {
        evaluation.setId(id);
        evaluationService.update(evaluation);
        return R.ok();
    }

    /**
     * 删除评价
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        evaluationService.delete(id);
        return R.ok();
    }
}
