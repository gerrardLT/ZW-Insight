package com.zwinsight.system.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.system.domain.SysOrg;
import com.zwinsight.system.service.SysOrgService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 机构管理接口
 */
@RestController
@RequestMapping("/api/v1/system/org")
@RequiredArgsConstructor
public class SysOrgController {

    private final SysOrgService orgService;

    @GetMapping
    public R<List<SysOrg>> list(
            @RequestParam(required = false) String orgName,
            @RequestParam(required = false) Integer status) {
        return R.ok(orgService.list(orgName, status));
    }

    @GetMapping("/{id}")
    public R<SysOrg> getById(@PathVariable Long id) {
        return R.ok(orgService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody SysOrg org) {
        orgService.save(org);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysOrg org) {
        org.setId(id);
        orgService.update(org);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        orgService.delete(id);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        orgService.updateStatus(id, status);
        return R.ok();
    }
}
