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

    @GetMapping("/page")
    public R<PageResult<BizBudget>> pageAlias(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return page(page, size, projectId);
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

    @PutMapping("/{id}/submit")
    public R<Void> submitByPut(@PathVariable Long id) {
        return submit(id);
    }

    @GetMapping("/project/{projectId}")
    public R<BizBudget> getByProject(@PathVariable Long projectId) {
        return R.ok(budgetService.getByProject(projectId));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizBudget budget) {
        budget.setId(id);
        budgetService.update(budget);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        budgetService.delete(id);
        return R.ok();
    }
}
