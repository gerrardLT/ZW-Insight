package com.zwinsight.budget.controller;

import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.dto.BudgetCreateRequest;
import com.zwinsight.budget.service.BudgetService;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 预算管理接口
 */
@RestController
@RequestMapping("/api/v1/budget")
@RequiredArgsConstructor
@RequiresPermission("budget:view")
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping("/page")
    public R<PageResult<BizBudget>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status) {
        return R.ok(budgetService.page(page, size, projectId, status));
    }

    @GetMapping("/{id}")
    public R<BizBudget> getById(@PathVariable Long id) {
        return R.ok(budgetService.getById(id));
    }

    @GetMapping("/{id}/details")
    public R<List<BizBudgetDetail>> getDetails(@PathVariable Long id) {
        return R.ok(budgetService.getDetailsByBudgetId(id));
    }

    @PostMapping
    @RequiresPermission("budget:budget:add")
    public R<Void> save(@Valid @RequestBody BudgetCreateRequest request) {
        budgetService.saveFromRequest(request);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @RequiresPermission("budget:budget:submit")
    public R<Void> submit(@PathVariable Long id) {
        budgetService.submit(id);
        return R.ok();
    }

    @GetMapping("/project/{projectId}")
    public R<BizBudget> getByProject(@PathVariable Long projectId) {
        return R.ok(budgetService.getByProject(projectId));
    }

    @PutMapping("/{id}")
    @RequiresPermission("budget:budget:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody BudgetCreateRequest request) {
        budgetService.updateFromRequest(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("budget:budget:delete")
    public R<Void> delete(@PathVariable Long id) {
        budgetService.delete(id);
        return R.ok();
    }
}
