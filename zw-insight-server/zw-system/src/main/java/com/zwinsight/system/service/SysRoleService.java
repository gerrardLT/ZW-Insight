package com.zwinsight.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.system.domain.SysRole;
import com.zwinsight.system.domain.SysRoleMenu;
import com.zwinsight.system.mapper.SysRoleMapper;
import com.zwinsight.system.mapper.SysRoleMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色管理服务
 */
@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    /**
     * 分页查询
     */
    public PageResult<SysRole> page(int page, int size, String roleName, Integer status) {
        Page<SysRole> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(roleName), SysRole::getRoleName, roleName)
                .eq(status != null, SysRole::getStatus, status)
                .orderByDesc(SysRole::getCreatedAt);
        Page<SysRole> result = roleMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public SysRole getById(Long id) {
        return roleMapper.selectById(id);
    }

    /**
     * 新增
     */
    public void save(SysRole role) {
        roleMapper.insert(role);
    }

    /**
     * 更新
     */
    public void update(SysRole role) {
        SysRole existing = roleMapper.selectById(role.getId());
        if (existing == null) {
            throw new BusinessException("角色不存在");
        }
        roleMapper.updateById(role);
    }

    /**
     * 删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        roleMapper.deleteById(id);
        // 同时删除角色菜单关联
        roleMenuMapper.delete(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
    }

    /**
     * 分配菜单权限
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        // 先删除原有关联
        roleMenuMapper.delete(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        // 再批量插入新关联
        if (menuIds != null && !menuIds.isEmpty()) {
            for (Long menuId : menuIds) {
                SysRoleMenu roleMenu = new SysRoleMenu();
                roleMenu.setRoleId(roleId);
                roleMenu.setMenuId(menuId);
                roleMenuMapper.insert(roleMenu);
            }
        }
    }
}
