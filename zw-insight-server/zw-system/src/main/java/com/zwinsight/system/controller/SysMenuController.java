package com.zwinsight.system.controller;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.R;
import com.zwinsight.system.domain.SysMenu;
import com.zwinsight.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单管理接口
 */
@RestController
@RequestMapping("/v1/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    @GetMapping
    public R<List<SysMenu>> list() {
        return R.ok(menuService.list());
    }

    @GetMapping("/{id}")
    public R<SysMenu> getById(@PathVariable Long id) {
        return R.ok(menuService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody SysMenu menu) {
        menuService.save(menu);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysMenu menu) {
        menu.setId(id);
        menuService.update(menu);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return R.ok();
    }

    /**
     * 获取当前用户菜单（用于动态路由）
     */
    @GetMapping("/user")
    public R<List<SysMenu>> getUserMenus() {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(menuService.getMenusByUserId(userId));
    }
}
