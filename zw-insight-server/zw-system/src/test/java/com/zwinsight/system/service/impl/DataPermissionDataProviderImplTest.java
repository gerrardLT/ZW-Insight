package com.zwinsight.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.system.domain.SysOrg;
import com.zwinsight.system.domain.SysRole;
import com.zwinsight.system.domain.SysUserRole;
import com.zwinsight.system.mapper.SysOrgMapper;
import com.zwinsight.system.mapper.SysRoleMapper;
import com.zwinsight.system.mapper.DataPermUserProjectMapper;
import com.zwinsight.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Feature: p0-data-permission-overdue, Task 2.6: DataPermissionDataProviderImpl 单元测试
/**
 * DataPermissionDataProviderImpl 单元测试
 * <p>
 * 通过 Mock Mapper 层验证各方法的 SQL 查询逻辑正确性。
 * </p>
 * <p>
 * _Requirements: 2.3, 2.4, 2.5, 2.6_
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataPermissionDataProviderImpl 单元测试")
class DataPermissionDataProviderImplTest {

    @Mock
    private SysUserRoleMapper userRoleMapper;

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysOrgMapper orgMapper;

    @Mock
    private DataPermUserProjectMapper userProjectMapper;

    @InjectMocks
    private DataPermissionDataProviderImpl dataProvider;

    @Nested
    @DisplayName("getUserDataScopes - 获取用户数据范围")
    class GetUserDataScopesTest {

        @Test
        @DisplayName("多角色用户应返回所有角色的 dataScope（去重）")
        void shouldReturnAllScopesForMultiRoleUser() {
            Long userId = 100L;

            // Mock: 用户关联两个角色
            SysUserRole ur1 = new SysUserRole();
            ur1.setUserId(userId);
            ur1.setRoleId(1L);
            SysUserRole ur2 = new SysUserRole();
            ur2.setUserId(userId);
            ur2.setRoleId(2L);
            when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(ur1, ur2));

            // Mock: 角色1是 DEPT，角色2是 ALL
            SysRole role1 = new SysRole();
            role1.setId(1L);
            role1.setDataScope("DEPT");
            role1.setStatus(1);
            SysRole role2 = new SysRole();
            role2.setId(2L);
            role2.setDataScope("ALL");
            role2.setStatus(1);
            when(roleMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(role1, role2));

            List<String> result = dataProvider.getUserDataScopes(userId);

            assertThat(result)
                    .containsExactlyInAnyOrder("DEPT", "ALL")
                    .hasSize(2);
        }

        @Test
        @DisplayName("无角色用户应返回空列表")
        void shouldReturnEmptyListForUserWithNoRoles() {
            Long userId = 200L;

            // Mock: 用户无关联角色
            when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            List<String> result = dataProvider.getUserDataScopes(userId);

            assertThat(result).isEmpty();
            // 验证不应调用 roleMapper（提前返回）
            verify(roleMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("所有角色均为停用状态应返回空列表")
        void shouldReturnEmptyListWhenAllRolesDisabled() {
            Long userId = 250L;

            // Mock: 用户关联一个角色
            SysUserRole ur1 = new SysUserRole();
            ur1.setUserId(userId);
            ur1.setRoleId(5L);
            when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.singletonList(ur1));

            // Mock: 该角色被停用（status=0），roleMapper 查询启用角色返回空
            when(roleMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            List<String> result = dataProvider.getUserDataScopes(userId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("重复 dataScope 应去重返回")
        void shouldDeduplicateSameScopes() {
            Long userId = 300L;

            // Mock: 用户关联两个角色，均为 SELF
            SysUserRole ur1 = new SysUserRole();
            ur1.setUserId(userId);
            ur1.setRoleId(10L);
            SysUserRole ur2 = new SysUserRole();
            ur2.setUserId(userId);
            ur2.setRoleId(11L);
            when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(ur1, ur2));

            // Mock: 两个角色的 dataScope 相同
            SysRole role1 = new SysRole();
            role1.setId(10L);
            role1.setDataScope("SELF");
            role1.setStatus(1);
            SysRole role2 = new SysRole();
            role2.setId(11L);
            role2.setDataScope("SELF");
            role2.setStatus(1);
            when(roleMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(role1, role2));

            List<String> result = dataProvider.getUserDataScopes(userId);

            assertThat(result)
                    .containsExactly("SELF")
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("getUserProjectIds - 获取用户参与的项目ID列表")
    class GetUserProjectIdsTest {

        @Test
        @DisplayName("应通过 DataPermUserProjectMapper 查询用户项目ID列表")
        void shouldReturnProjectIdsFromMapper() {
            Long userId = 100L;
            List<Long> expected = Arrays.asList(1001L, 1002L, 1003L);
            when(userProjectMapper.selectProjectIdsByUserId(userId)).thenReturn(expected);

            List<Long> result = dataProvider.getUserProjectIds(userId);

            assertThat(result).isEqualTo(expected);
            verify(userProjectMapper).selectProjectIdsByUserId(userId);
        }

        @Test
        @DisplayName("Mapper 返回 null 时应返回空列表")
        void shouldReturnEmptyListWhenMapperReturnsNull() {
            Long userId = 200L;
            when(userProjectMapper.selectProjectIdsByUserId(userId)).thenReturn(null);

            List<Long> result = dataProvider.getUserProjectIds(userId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getUserDeptId - 获取用户所属部门ID")
    class GetUserDeptIdTest {

        @Test
        @DisplayName("应返回用户的 orgId 作为部门ID")
        void shouldReturnUserOrgId() {
            Long userId = 100L;
            SysUser user = new SysUser();
            user.setOrgId(50L);
            when(userMapper.selectById(userId)).thenReturn(user);

            Long result = dataProvider.getUserDeptId(userId);

            assertThat(result).isEqualTo(50L);
        }

        @Test
        @DisplayName("用户不存在时应返回 null")
        void shouldReturnNullWhenUserNotFound() {
            Long userId = 999L;
            when(userMapper.selectById(userId)).thenReturn(null);

            Long result = dataProvider.getUserDeptId(userId);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getDeptAndChildIds - 获取部门及子部门ID（FIND_IN_SET）")
    class GetDeptAndChildIdsTest {

        @Test
        @DisplayName("应包含自身并通过 FIND_IN_SET 查询子部门")
        void shouldIncludeSelfAndFindChildrenByAncestors() {
            Long deptId = 10L;

            // Mock: 查询到两个子部门（ancestors 中包含 deptId=10）
            SysOrg child1 = new SysOrg();
            child1.setId(101L);
            child1.setAncestors("0,10");
            SysOrg child2 = new SysOrg();
            child2.setId(102L);
            child2.setAncestors("0,10,101");
            when(orgMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(child1, child2));

            List<Long> result = dataProvider.getDeptAndChildIds(deptId);

            // 应包含自身（10）+ 两个子部门（101, 102）
            assertThat(result).containsExactlyInAnyOrder(10L, 101L, 102L);
        }

        @Test
        @DisplayName("无子部门时应仅返回自身")
        void shouldReturnOnlySelfWhenNoChildren() {
            Long deptId = 50L;

            // Mock: 无匹配子部门
            when(orgMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            List<Long> result = dataProvider.getDeptAndChildIds(deptId);

            assertThat(result).containsExactly(50L);
        }

        @Test
        @DisplayName("deptId 为 null 时应返回空列表")
        void shouldReturnEmptyListWhenDeptIdIsNull() {
            List<Long> result = dataProvider.getDeptAndChildIds(null);

            assertThat(result).isEmpty();
            // 不应查询数据库
            verify(orgMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("子部门ID不应重复")
        void shouldNotDuplicateIds() {
            Long deptId = 10L;

            // Mock: 查询结果包含与自身相同的ID（边界情况）
            SysOrg childSame = new SysOrg();
            childSame.setId(10L); // 与自身相同
            childSame.setAncestors("0");
            SysOrg child = new SysOrg();
            child.setId(20L);
            child.setAncestors("0,10");
            when(orgMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(childSame, child));

            List<Long> result = dataProvider.getDeptAndChildIds(deptId);

            // 10 不应出现两次
            assertThat(result).containsExactlyInAnyOrder(10L, 20L);
        }
    }
}
