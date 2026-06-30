package com.zwinsight.budget.controller;

import com.zwinsight.budget.domain.BizBudgetConfig;
import com.zwinsight.budget.service.BudgetConfigService;
import com.zwinsight.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 预算管控配置接口
 */
@RestController
@RequestMapping("/api/v1/budget/config")
@RequiredArgsConstructor
public class BudgetConfigController {

    private final BudgetConfigService budgetConfigService;

    @GetMapping("/{projectId}")
    public R<BizBudgetConfig> getConfig(@PathVariable Long projectId) {
        return R.ok(budgetConfigService.getConfig(projectId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizBudgetConfig config) {
        budgetConfigService.save(config);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizBudgetConfig config) {
        config.setId(id);
        budgetConfigService.update(config);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        budgetConfigService.delete(id);
        return R.ok();
    }
}
