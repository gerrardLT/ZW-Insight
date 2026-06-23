package com.zwinsight.budget.controller;

import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.service.BudgetChangeService;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 预算变更接口
 */
@RestController
@RequestMapping("/api/v1/budget/change")
@RequiredArgsConstructor
public class BudgetChangeController {

    private final BudgetChangeService budgetChangeService;

    @GetMapping
    public R<PageResult<BizBudget>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(budgetChangeService.page(page, size, projectId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizBudget budget) {
        budgetChangeService.save(budget);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        budgetChangeService.submit(id);
        return R.ok();
    }
}
