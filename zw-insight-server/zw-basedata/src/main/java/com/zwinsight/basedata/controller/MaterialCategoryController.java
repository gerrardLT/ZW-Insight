package com.zwinsight.basedata.controller;

import com.zwinsight.basedata.domain.BdMaterialCategory;
import com.zwinsight.basedata.service.MaterialCategoryService;
import com.zwinsight.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 材料分类接口
 */
@RestController
@RequestMapping("/api/v1/basedata/material-category")
@RequiredArgsConstructor
public class MaterialCategoryController {

    private final MaterialCategoryService categoryService;

    @GetMapping
    public R<List<BdMaterialCategory>> list() {
        return R.ok(categoryService.listTree());
    }

    @PostMapping
    public R<Void> save(@RequestBody BdMaterialCategory category) {
        categoryService.save(category);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BdMaterialCategory category) {
        category.setId(id);
        categoryService.update(category);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return R.ok();
    }
}
