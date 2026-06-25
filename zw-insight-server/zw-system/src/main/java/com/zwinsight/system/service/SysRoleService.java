package com.zwinsight.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.datapermission.DataScopeEnum;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.system.domain.SysRole;
import com.zwinsight.system.domain.SysRoleMenu;
import com.zwinsight.system.domain.SysUserRole;
import com.zwinsight.system.mapper.SysRoleMapper;
import com.zwinsight.system.mapper.SysRoleMenuMapper;
import com.zwinsight.system.mapper.SysUserRoleMapper;
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
    private final SysUserRoleMapper userRoleMapper;

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

    /**
     * 更新角色数据权限范围
     * <p>
     * 仅系统管理员（roleCode=ADMIN）可配置
     *
     * @param roleId    角色ID
     * @param dataScope 数据范围名称（ALL/DEPT_AND_CHILDREN/DEPT/PROJECT/SELF）
     */
    public void updateDataScope(Long roleId, String dataScope) {
        // 权限校验：仅系统管理员可操作
        checkAdminPermission();

        // 校验 dataScope 值合法性
        if (!isValidDataScope(dataScope)) {
            throw new BusinessException(400, "不合法的数据范围值: " + dataScope
                    + "，允许值为: ALL, DEPT_AND_CHILDREN, DEPT, PROJECT, SELF");
        }

        // 校验角色存在
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }

        // 更新数据范围
        role.setDataScope(dataScope.trim().toUpperCase());
        roleMapper.updateById(role);
    }

    /**
     * 校验 dataScope 值是否合法（必须是 DataScopeEnum 中定义的枚举名）
     */
    private boolean isValidDataScope(String dataScope) {
        if (dataScope == null || dataScope.isBlank()) {
            return false;
        }
        String trimmed = dataScope.trim().toUpperCase();
        for (DataScopeEnum scope : DataScopeEnum.values()) {
            if (scope.name().equals(trimmed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验当前操作用户是否为系统管理员（拥有 roleCode = ADMIN 的角色）
     */
    private void checkAdminPermission() {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException(403, "未登录，无法执行此操作");
        }

        // 查询当前用户关联的角色
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));

        if (userRoles.isEmpty()) {
            throw new BusinessException(403, "无权限：仅系统管理员可配置数据权限");
        }

        // 查询这些角色中是否有 ADMIN 角色
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        Long adminCount = roleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>()
                        .in(SysRole::getId, roleIds)
                        .eq(SysRole::getRoleCode, "ADMIN"));

        if (adminCount == 0) {
            throw new BusinessException(403, "无权限：仅系统管理员可配置数据权限");
        }
    }
}
