package com.zwinsight.system.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.system.domain.SysRole;
import com.zwinsight.system.domain.dto.DataScopeUpdateRequest;
import com.zwinsight.system.service.SysRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理接口
 */
@RestController
@RequestMapping("/api/v1/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @GetMapping
    public R<PageResult<SysRole>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) Integer status) {
        return R.ok(roleService.page(page, size, roleName, status));
    }

    @GetMapping("/{id}")
    public R<SysRole> getById(@PathVariable Long id) {
        return R.ok(roleService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody SysRole role) {
        roleService.save(role);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysRole role) {
        role.setId(id);
        roleService.update(role);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}/menus")
    public R<List<Long>> getMenuIds(@PathVariable Long id) {
        return R.ok(roleService.getMenuIds(id));
    }

    @PutMapping("/{id}/menus")
    public R<Void> assignMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        roleService.assignMenus(id, menuIds);
        return R.ok();
    }

    /**
     * 配置角色数据权限范围
     * <p>
     * 仅系统管理员可操作。dataScope 必须为合法的枚举值：
     * ALL / DEPT_AND_CHILDREN / DEPT / PROJECT / SELF
     */
    @PutMapping("/{id}/data-scope")
    public R<Void> updateDataScope(@PathVariable Long id,
                                   @Valid @RequestBody DataScopeUpdateRequest request) {
        roleService.updateDataScope(id, request.getDataScope());
        return R.ok();
    }
}
