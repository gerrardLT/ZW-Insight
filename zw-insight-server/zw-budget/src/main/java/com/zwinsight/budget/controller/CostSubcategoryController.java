package com.zwinsight.budget.controller;

import com.zwinsight.budget.domain.BizCostSubcategory;
import com.zwinsight.budget.service.CostSubcategoryService;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 费用子类接口
 */
@RestController
@RequestMapping("/api/v1/budget/subcategory")
@RequiredArgsConstructor
@RequiresPermission("budget:view")
public class CostSubcategoryController {

    private final CostSubcategoryService costSubcategoryService;

    @GetMapping("/{costCategory}")
    public R<List<BizCostSubcategory>> list(@PathVariable String costCategory) {
        return R.ok(costSubcategoryService.list(costCategory));
    }

    @PostMapping
    @RequiresPermission("budget:costsubcategory:add")
    public R<Void> save(@RequestBody BizCostSubcategory subcategory) {
        costSubcategoryService.save(subcategory);
        return R.ok();
    }

    @PutMapping("/{id}")
    @RequiresPermission("budget:costsubcategory:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody BizCostSubcategory subcategory) {
        subcategory.setId(id);
        costSubcategoryService.update(subcategory);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("budget:costsubcategory:delete")
    public R<Void> delete(@PathVariable Long id) {
        costSubcategoryService.delete(id);
        return R.ok();
    }
}
