package com.zwinsight.system.controller;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.system.domain.SysMenu;
import com.zwinsight.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单管理接口
 */
@RestController
@RequestMapping("/api/v1/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    @GetMapping
    @RequiresPermission("system:view")
    public R<List<SysMenu>> list() {
        return R.ok(menuService.list());
    }

    @GetMapping("/{id}")
    @RequiresPermission("system:view")
    public R<SysMenu> getById(@PathVariable Long id) {
        return R.ok(menuService.getById(id));
    }

    @PostMapping
    @RequiresPermission("system:menu:add")
    public R<Void> save(@RequestBody SysMenu menu) {
        menuService.save(menu);
        return R.ok();
    }

    @PutMapping("/{id}")
    @RequiresPermission("system:menu:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody SysMenu menu) {
        menu.setId(id);
        menuService.update(menu);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:menu:delete")
    public R<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return R.ok();
    }

    /**
     * 获取当前用户菜单（用于动态路由）
     * <p>权限豁免说明：菜单收敛基座，全体登录用户（含无业务菜单角色）必须可达，
     * 返回内容本身已按 userId 的角色-菜单绑定过滤，无越权读取面。</p>
     */
    @GetMapping("/user")
    public R<List<SysMenu>> getUserMenus() {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(menuService.getMenusByUserId(userId));
    }
}
