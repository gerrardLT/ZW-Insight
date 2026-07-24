package com.zwinsight.basedata.controller;

import com.zwinsight.basedata.domain.BdMaterial;
import com.zwinsight.basedata.domain.BdMaterialCategory;
import com.zwinsight.basedata.service.MaterialCategoryService;
import com.zwinsight.basedata.service.MaterialService;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 材料字典接口
 */
@RestController
@RequestMapping("/api/v1/basedata/material")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;
    private final MaterialCategoryService categoryService;

    @GetMapping
    public R<PageResult<BdMaterial>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String categoryName) {
        return R.ok(materialService.page(page, size, materialName, categoryId, categoryName));
    }

    @GetMapping("/page")
    public R<PageResult<BdMaterial>> pageAlias(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String categoryName) {
        return page(page, size, materialName, categoryId, categoryName);
    }

    @GetMapping("/{id}")
    public R<BdMaterial> getById(@PathVariable Long id) {
        return R.ok(materialService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BdMaterial material) {
        materialService.save(material);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BdMaterial material) {
        material.setId(id);
        materialService.update(material);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return R.ok();
    }

    @DeleteMapping("/batch")
    public R<Void> batchDelete(@RequestBody List<Long> ids) {
        materialService.batchDelete(ids);
        return R.ok();
    }

    @GetMapping("/categories")
    public R<List<BdMaterialCategory>> categories() {
        return R.ok(categoryService.listTree());
    }
}
