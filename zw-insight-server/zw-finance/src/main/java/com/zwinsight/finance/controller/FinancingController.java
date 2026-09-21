package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizFinancing;
import com.zwinsight.finance.domain.BizFinancingRepayment;
import com.zwinsight.finance.service.FinancingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 融资借贷接口（借款台账 + 还款计划 + 按期核销）
 */
@RestController
@RequestMapping("/api/v1/finance/financing")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class FinancingController {

    private final FinancingService financingService;

    /**
     * 分页查询融资台账
     */
    @GetMapping("/page")
    public R<PageResult<BizFinancing>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String financingType) {
        return R.ok(financingService.page(page, size, status, financingType));
    }

    /**
     * 登记融资（自动生成还款计划，同事务）
     */
    @PostMapping
    public R<Void> register(@RequestBody BizFinancing financing) {
        financingService.register(financing);
        return R.ok();
    }

    /**
     * 查询还款计划（按期数升序）
     */
    @GetMapping("/{id}/repayments")
    public R<List<BizFinancingRepayment>> repayments(@PathVariable Long id) {
        return R.ok(financingService.repayments(id));
    }

    /**
     * 登记还款（按期核销，先息后本）
     */
    @PostMapping("/{id}/repay")
    public R<Void> recordRepayment(
            @PathVariable Long id,
            @RequestParam Integer periodNo,
            @RequestParam(required = false) BigDecimal interestPaid,
            @RequestParam(required = false) BigDecimal principalPaid,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidDate) {
        financingService.recordRepayment(id, periodNo, interestPaid, principalPaid, paidDate);
        return R.ok();
    }

    /**
     * 融资统计（在借余额 / 总利息 / 已还总额）
     */
    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics() {
        return R.ok(financingService.statistics());
    }
}
