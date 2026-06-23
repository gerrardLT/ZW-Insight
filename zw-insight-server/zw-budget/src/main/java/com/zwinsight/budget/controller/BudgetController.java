package com.zwinsight.budget.controller;

import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.service.BudgetService;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 预算管理接口
 */
@RestController
@RequestMapping("/api/v1/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping
    public R<PageResult<BizBudget>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(budgetService.page(page, size, projectId));
    }

    @GetMapping("/{id}")
    public R<BizBudget> getById(@PathVariable Long id) {
        return R.ok(budgetService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizBudget budget) {
        budgetService.save(budget);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        budgetService.submit(id);
        return R.ok();
    }

    @GetMapping("/project/{projectId}")
    public R<BizBudget> getByProject(@PathVariable Long projectId) {
        return R.ok(budgetService.getByProject(projectId));
    }
}
