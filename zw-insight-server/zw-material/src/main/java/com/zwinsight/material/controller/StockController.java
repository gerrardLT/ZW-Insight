package com.zwinsight.material.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.service.ProjectMaterialStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目材料库存接口
 */
@RestController
@RequestMapping("/api/v1/material/stock")
@RequiredArgsConstructor
public class StockController {

    private final ProjectMaterialStockService stockService;

    @GetMapping("/page")
    public R<PageResult<BizProjectMaterialStock>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(stockService.page(page, size, projectId));
    }

    @GetMapping("/{projectId}")
    public R<List<BizProjectMaterialStock>> getByProject(@PathVariable Long projectId) {
        return R.ok(stockService.getByProject(projectId));
    }
}
