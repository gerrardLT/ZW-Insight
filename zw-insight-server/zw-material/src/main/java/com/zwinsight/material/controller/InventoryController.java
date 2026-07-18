package com.zwinsight.material.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.material.domain.BizMaterialInventory;
import com.zwinsight.material.service.MaterialInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 材料盘点接口
 */
@RestController
@RequestMapping("/api/v1/material/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final MaterialInventoryService inventoryService;

    @GetMapping("/page")
    public R<PageResult<BizMaterialInventory>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(inventoryService.page(page, size, projectId));
    }

    @GetMapping("/{id}")
    public R<BizMaterialInventory> getById(@PathVariable Long id) {
        return R.ok(inventoryService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizMaterialInventory inventory) {
        inventoryService.save(inventory, inventory.getAdjustments() != null ? inventory.getAdjustments() : Map.of());
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizMaterialInventory inventory) {
        inventory.setId(id);
        inventoryService.update(inventory);
        return R.ok();
    }

    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        inventoryService.submit(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        inventoryService.delete(id);
        return R.ok();
    }
}
