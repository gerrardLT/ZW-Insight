package com.zwinsight.budget.controller;

import com.zwinsight.budget.domain.SysBudgetControlConfig;
import com.zwinsight.budget.dto.BudgetControlConfigDTO;
import com.zwinsight.budget.service.BudgetControlConfigService;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 预算控制配置接口
 * <p>
 * 提供预算控制配置的 CRUD 操作及项目生效配置查询。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/budget-control-configs")
@RequiredArgsConstructor
public class BudgetControlConfigController {

    private final BudgetControlConfigService budgetControlConfigService;

    /**
     * 配置列表（分页，支持 projectName 筛选）
     */
    @GetMapping
    public R<PageResult<SysBudgetControlConfig>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String controlMode) {
        return R.ok(budgetControlConfigService.page(page, size, projectName, controlMode));
    }

    /**
     * 配置详情
     */
    @GetMapping("/{id}")
    public R<SysBudgetControlConfig> getById(@PathVariable Long id) {
        return R.ok(budgetControlConfigService.getById(id));
    }

    /**
     * 创建配置
     */
    @PostMapping
    public R<Void> save(@Valid @RequestBody BudgetControlConfigDTO dto) {
        budgetControlConfigService.save(dto);
        return R.ok();
    }

    /**
     * 编辑配置
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody BudgetControlConfigDTO dto) {
        budgetControlConfigService.update(id, dto);
        return R.ok();
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        budgetControlConfigService.delete(id);
        return R.ok();
    }

    /**
     * 获取项目生效配置
     */
    @GetMapping("/project/{projectId}")
    public R<SysBudgetControlConfig> getEffectiveConfig(@PathVariable Long projectId) {
        return R.ok(budgetControlConfigService.getEffectiveConfig(projectId));
    }
}
