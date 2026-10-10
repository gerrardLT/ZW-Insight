package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.system.domain.SysMenu;
import com.zwinsight.system.domain.SysRole;
import com.zwinsight.system.domain.SysUserRole;
import com.zwinsight.system.mapper.SysMenuMapper;
import com.zwinsight.system.mapper.SysRoleMapper;
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
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;

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
     * <p>
     * 角色口径必须与 {@code SysUserMapper.selectPermissionsByUserId} 一致：只取
     * <b>用户所属租户内、启用（status=1）且未逻辑删除</b>的角色。否则用户被绑定到
     * 停用/已删角色时，该角色仍会下发菜单（页面可见），却不参与权限码计算（接口 403），
     * 形成「看得见、点不动」的口径分叉（2026-10-10 线上权限一致性清查 F4）。
     * </p>
     */
    public List<SysMenu> getMenusByUserId(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getTenantId() == null) {
            return Collections.emptyList();
        }

        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> roleIds = userRoles.stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());

        // 启用且未删除（deleted 由 @TableLogic 自动过滤），且租户匹配（全局共享角色 tenant_id 为 NULL）
        List<Long> activeRoleIds = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .in(SysRole::getId, roleIds)
                        .eq(SysRole::getStatus, 1)
                        .and(w -> w.eq(SysRole::getTenantId, user.getTenantId())
                                .or().isNull(SysRole::getTenantId)))
                .stream()
                .map(SysRole::getId)
                .collect(Collectors.toList());
        if (activeRoleIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<SysMenu> menus = menuMapper.selectMenusByRoleIds(activeRoleIds);
        return menus.stream()
                .filter(m -> !"BUTTON".equals(m.getMenuType()))
                .collect(Collectors.toList());
    }
}
