package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.system.domain.SysMenu;
import com.zwinsight.system.domain.SysUserRole;
import com.zwinsight.system.mapper.SysMenuMapper;
import com.zwinsight.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 菜单管理服务
 */
@Service
@RequiredArgsConstructor
public class SysMenuService {

    private final SysMenuMapper menuMapper;
    private final SysUserRoleMapper userRoleMapper;

    /**
     * 查询所有菜单列表（用于前端构建树）
     */
    public List<SysMenu> list() {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SysMenu::getSortOrder);
        return menuMapper.selectList(wrapper);
    }

    /**
     * 根据ID查询
     */
    public SysMenu getById(Long id) {
        return menuMapper.selectById(id);
    }

    /**
     * 新增菜单
     */
    public void save(SysMenu menu) {
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        menuMapper.insert(menu);
    }

    /**
     * 更新菜单
     */
    public void update(SysMenu menu) {
        SysMenu existing = menuMapper.selectById(menu.getId());
        if (existing == null) {
            throw new BusinessException("菜单不存在");
        }
        menuMapper.updateById(menu);
    }

    /**
     * 删除菜单
     */
    public void delete(Long id) {
        // 检查是否有子菜单
        long childCount = menuMapper.selectCount(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException("存在子菜单，无法删除");
        }
        menuMapper.deleteById(id);
    }

    /**
     * 获取用户菜单（用于动态路由）
     */
    public List<SysMenu> getMenusByUserId(Long userId) {
        // 查询用户角色
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roleIds = userRoles.stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());
        // 查询角色对应的菜单（排除按钮类型）
        List<SysMenu> menus = menuMapper.selectMenusByRoleIds(roleIds);
        return menus.stream()
                .filter(m -> !"BUTTON".equals(m.getMenuType()))
                .collect(Collectors.toList());
    }
}
