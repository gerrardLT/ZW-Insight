package com.zwinsight.budget.controller;

import com.zwinsight.budget.domain.BizBudgetChange;
import com.zwinsight.budget.domain.BizBudgetChangeDetail;
import com.zwinsight.budget.dto.BudgetChangeDTO;
import com.zwinsight.budget.service.BudgetChangeService;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 预算变更接口
 */
@RestController
@RequestMapping("/api/v1/budget/change")
@RequiredArgsConstructor
public class BudgetChangeController {

    private final BudgetChangeService budgetChangeService;

    @GetMapping
    public R<PageResult<BizBudgetChange>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(budgetChangeService.page(page, size, projectId));
    }

    @GetMapping("/{id}")
    public R<BizBudgetChange> getById(@PathVariable Long id) {
        return R.ok(budgetChangeService.getById(id));
    }

    @GetMapping("/{id}/details")
    public R<List<BizBudgetChangeDetail>> getDetails(@PathVariable Long id) {
        return R.ok(budgetChangeService.getDetailsByChangeId(id));
    }

    @PostMapping
    public R<Void> save(@Valid @RequestBody BudgetChangeDTO dto) {
        budgetChangeService.save(dto);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody BudgetChangeDTO dto) {
        budgetChangeService.update(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        budgetChangeService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        budgetChangeService.submit(id);
        return R.ok();
    }

    @PostMapping("/{id}/withdraw")
    public R<Void> withdraw(@PathVariable Long id) {
        budgetChangeService.withdraw(id);
        return R.ok();
    }

    /**
     * 变更轨迹查询 — 按项目查询全部变更记录及审批结果
     */
    @GetMapping("/trace")
    public R<List<BizBudgetChange>> getChangeTrace(@RequestParam Long projectId) {
        return R.ok(budgetChangeService.getChangeTraceByProject(projectId));
    }
}
