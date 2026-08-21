package com.zwinsight.budget.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizBudgetConfig;
import com.zwinsight.budget.service.BudgetConfigService;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 预算管控配置接口
 */
@RestController
@RequestMapping("/api/v1/budget/config")
@RequiredArgsConstructor
@RequiresPermission("budget:view")
public class BudgetConfigController {

    private final BudgetConfigService budgetConfigService;

    /**
     * 查询全部预算管控配置列表
     */
    @GetMapping("/list")
    public R<List<BizBudgetConfig>> list() {
        return R.ok(budgetConfigService.listAll());
    }

    @GetMapping("/{projectId}")
    public R<BizBudgetConfig> getConfig(@PathVariable Long projectId) {
        return R.ok(budgetConfigService.getConfig(projectId));
    }

    @PostMapping
    @RequiresPermission("budget:budgetconfig:add")
    public R<Void> save(@RequestBody BizBudgetConfig config) {
        budgetConfigService.save(config);
        return R.ok();
    }

    @PutMapping("/{id}")
    @RequiresPermission("budget:budgetconfig:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody BizBudgetConfig config) {
        config.setId(id);
        budgetConfigService.update(config);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("budget:budgetconfig:delete")
    public R<Void> delete(@PathVariable Long id) {
        budgetConfigService.delete(id);
        return R.ok();
    }
}
