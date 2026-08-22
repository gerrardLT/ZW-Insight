package com.zwinsight.material.controller;

import com.zwinsight.common.annotation.OperLog;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.material.domain.BizStockWarningConfig;
import com.zwinsight.material.service.StockWarningConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库存安全阈值配置接口（P0 Req4.6：按 projectId+materialId 维护安全库存）
 */
@RestController
@RequestMapping("/api/v1/material/stock-warning-config")
@RequiredArgsConstructor
@RequiresPermission("material:view")
public class StockWarningConfigController {

    private final StockWarningConfigService configService;

    @GetMapping("/page")
    public R<PageResult<BizStockWarningConfig>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String materialName) {
        return R.ok(configService.page(page, size, materialName));
    }

    @GetMapping("/list-enabled")
    public R<List<BizStockWarningConfig>> listEnabled() {
        return R.ok(configService.listEnabled());
    }

    /**
     * 新增或更新配置（projectId+materialId 已存在时更新，projectId 为空=全局默认）
     */
    @PostMapping
    @RequiresPermission("material:stockwarningconfig:save")
    @OperLog(module = "库存预警配置", operType = "INSERT", description = "维护库存安全阈值")
    public R<Void> save(@RequestBody BizStockWarningConfig config) {
        configService.save(config);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("material:stockwarningconfig:delete")
    @OperLog(module = "库存预警配置", operType = "DELETE", description = "删除库存安全阈值配置")
    public R<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return R.ok();
    }
}
