package com.zwinsight.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.datapermission.DataPermissionDataProvider;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.system.domain.SysOrg;
import com.zwinsight.system.domain.SysRole;
import com.zwinsight.system.domain.SysUserRole;
import com.zwinsight.system.mapper.SysOrgMapper;
import com.zwinsight.system.mapper.SysRoleMapper;
import com.zwinsight.system.mapper.DataPermUserProjectMapper;
import com.zwinsight.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据权限数据提供者实现
 * <p>
 * 为 ZwDataPermissionHandler 提供用户数据范围、项目列表、部门层级等信息。
 * 每次调用实时查询数据库，确保配置变更立即生效。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataPermissionDataProviderImpl implements DataPermissionDataProvider {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final SysOrgMapper orgMapper;
    private final DataPermUserProjectMapper userProjectMapper;

    @Override
    public List<String> getUserDataScopes(Long userId) {
        // 1. 查询用户关联的所有角色ID
        LambdaQueryWrapper<SysUserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.eq(SysUserRole::getUserId, userId);
        List<SysUserRole> userRoles = userRoleMapper.selectList(userRoleWrapper);

        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 获取角色ID列表
        List<Long> roleIds = userRoles.stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());

        // 3. 查询这些角色的 dataScope 值（去重）
        LambdaQueryWrapper<SysRole> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.in(SysRole::getId, roleIds)
                .eq(SysRole::getStatus, 1)
                .isNotNull(SysRole::getDataScope);
        List<SysRole> roles = roleMapper.selectList(roleWrapper);

        return roles.stream()
                .map(SysRole::getDataScope)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> getUserProjectIds(Long userId) {
        List<Long> projectIds = userProjectMapper.selectProjectIdsByUserId(userId);
        return projectIds != null ? projectIds : Collections.emptyList();
    }

    @Override
    public Long getUserDeptId(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return user.getOrgId();
    }

    @Override
    public List<Long> getDeptAndChildIds(Long deptId) {
        if (deptId == null) {
            return Collections.emptyList();
        }

        List<Long> result = new ArrayList<>();
        // 包含自身
        result.add(deptId);

        // 查询所有 ancestors 包含当前 deptId 的子部门
        // ancestors 格式为 "0,1,2" — 使用 FIND_IN_SET 或 LIKE 查询
        LambdaQueryWrapper<SysOrg> wrapper = new LambdaQueryWrapper<>();
        wrapper.apply("FIND_IN_SET({0}, ancestors)", deptId);
        List<SysOrg> childOrgs = orgMapper.selectList(wrapper);

        for (SysOrg org : childOrgs) {
            if (!result.contains(org.getId())) {
                result.add(org.getId());
            }
        }

        return result;
    }
}
