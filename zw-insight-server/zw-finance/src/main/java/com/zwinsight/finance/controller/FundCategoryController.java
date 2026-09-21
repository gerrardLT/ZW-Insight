package com.zwinsight.finance.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizFundCategory;
import com.zwinsight.finance.service.FundCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 资金收支分类科目接口
 * <p>维度1（资金性质）+ 维度2（业务来源）的维护入口，
 * 供付款申请/回款登记选择分类科目。</p>
 */
@RestController
@RequestMapping("/api/v1/finance/fund-category")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class FundCategoryController {

    private final FundCategoryService fundCategoryService;

    /**
     * 查询科目树（可按方向过滤）
     */
    @GetMapping("/tree")
    public R<List<BizFundCategory>> tree(@RequestParam(required = false) String direction) {
        return R.ok(fundCategoryService.tree(direction));
    }

    /**
     * 查询启用科目列表（单据下拉用）
     */
    @GetMapping("/enabled")
    public R<List<BizFundCategory>> listEnabled(@RequestParam(required = false) String direction) {
        return R.ok(fundCategoryService.listEnabled(direction));
    }

    /**
     * 新增科目
     */
    @PostMapping
    public R<Void> save(@RequestBody BizFundCategory category) {
        fundCategoryService.save(category);
        return R.ok();
    }

    /**
     * 修改科目（编码不可改，系统内置方向不可改）
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizFundCategory category) {
        category.setId(id);
        fundCategoryService.update(category);
        return R.ok();
    }

    /**
     * 停用科目（软停用，历史引用不受影响）
     */
    @PutMapping("/{id}/disable")
    public R<Void> disable(@PathVariable Long id) {
        fundCategoryService.disable(id);
        return R.ok();
    }

    /**
     * 删除科目（仅自定义且无子级且无付款引用时可删）
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        fundCategoryService.delete(id);
        return R.ok();
    }
}
