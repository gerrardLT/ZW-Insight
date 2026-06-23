package com.zwinsight.material.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.material.domain.BizMaterialInventory;
import com.zwinsight.material.service.MaterialInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 材料盘点接口
 */
@RestController
@RequestMapping("/api/v1/material/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final MaterialInventoryService inventoryService;

    @GetMapping
    public R<PageResult<BizMaterialInventory>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(inventoryService.page(page, size, projectId));
    }

    @PostMapping
    public R<Void> save(@RequestBody Map<String, Object> body) {
        BizMaterialInventory inventory = new BizMaterialInventory();
        inventoryService.save(inventory, Map.of());
        return R.ok();
    }
}
