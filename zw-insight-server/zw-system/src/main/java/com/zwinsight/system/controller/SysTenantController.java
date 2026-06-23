package com.zwinsight.system.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.security.domain.SysTenant;
import com.zwinsight.system.dto.TenantRenewRequest;
import com.zwinsight.system.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 租户管理接口（平台管理端）
 */
@RestController
@RequestMapping("/api/v1/platform/tenant")
@RequiredArgsConstructor
public class SysTenantController {

    private final SysTenantService tenantService;

    @GetMapping
    public R<PageResult<SysTenant>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String tenantName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate expireStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate expireEnd) {
        return R.ok(tenantService.page(page, size, tenantName, status, expireStart, expireEnd));
    }

    @GetMapping("/{id}")
    public R<SysTenant> getById(@PathVariable Long id) {
        return R.ok(tenantService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody SysTenant tenant) {
        tenantService.save(tenant);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysTenant tenant) {
        tenant.setId(id);
        tenantService.update(tenant);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        tenantService.delete(id);
        return R.ok();
    }

    @PostMapping("/renew")
    public R<Void> renew(@RequestBody TenantRenewRequest request) {
        tenantService.renew(request.getTenantId(), request.getDurationDays());
        return R.ok();
    }

    @PutMapping("/{id}/permissions")
    public R<Void> updatePermissions(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        tenantService.updatePermissions(id, menuIds);
        return R.ok();
    }
}
