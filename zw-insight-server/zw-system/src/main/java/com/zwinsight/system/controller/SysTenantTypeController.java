package com.zwinsight.system.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.system.domain.SysTenantType;
import com.zwinsight.system.service.SysTenantTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 租户类型管理接口
 */
@RestController
@RequestMapping("/api/v1/platform/tenant-type")
@RequiredArgsConstructor
public class SysTenantTypeController {

    private final SysTenantTypeService tenantTypeService;

    @GetMapping
    public R<PageResult<SysTenantType>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String typeName,
            @RequestParam(required = false) Integer status) {
        return R.ok(tenantTypeService.page(page, size, typeName, status));
    }

    @GetMapping("/{id}")
    public R<SysTenantType> getById(@PathVariable Long id) {
        return R.ok(tenantTypeService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody SysTenantType tenantType) {
        tenantTypeService.save(tenantType);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysTenantType tenantType) {
        tenantType.setId(id);
        tenantTypeService.update(tenantType);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        tenantTypeService.delete(id);
        return R.ok();
    }

    @DeleteMapping("/batch")
    public R<Void> batchDelete(@RequestBody List<Long> ids) {
        tenantTypeService.batchDelete(ids);
        return R.ok();
    }
}
