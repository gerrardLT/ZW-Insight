package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.system.domain.SysRole;
import com.zwinsight.system.domain.SysRoleMenu;
import com.zwinsight.system.mapper.SysRoleMapper;
import com.zwinsight.system.mapper.SysRoleMenuMapper;
import com.zwinsight.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysRoleServiceTest {

    @Mock private SysRoleMapper roleMapper;
    @Mock private SysRoleMenuMapper roleMenuMapper;
    @Mock private SysUserRoleMapper userRoleMapper;

    @InjectMocks
    private SysRoleService roleService;

    @Test
    @DisplayName("根据ID查询：返回角色")
    void testGetById() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setRoleName("管理员");
        when(roleMapper.selectById(1L)).thenReturn(role);

        SysRole result = roleService.getById(1L);

        assertThat(result.getRoleName()).isEqualTo("管理员");
    }

    @Test
    @DisplayName("新增角色：正常保存")
    void testSave() {
        SysRole role = new SysRole();
        role.setRoleName("新角色");
        when(roleMapper.insert(any(SysRole.class))).thenReturn(1);

        roleService.save(role);

        verify(roleMapper).insert(role);
    }

    @Test
    @DisplayName("更新角色：不存在抛异常")
    void testUpdate_notFound() {
        when(roleMapper.selectById(999L)).thenReturn(null);

        SysRole update = new SysRole();
        update.setId(999L);

        assertThatThrownBy(() -> roleService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色不存在");
    }

    @Test
    @DisplayName("删除角色：同时删除角色菜单关联")
    void testDelete() {
        roleService.delete(1L);

        verify(roleMapper).deleteById(1L);
        verify(roleMenuMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("分配菜单权限：先删后插")
    void testAssignMenus() {
        roleService.assignMenus(1L, List.of(100L, 200L));

        verify(roleMenuMapper).delete(any(LambdaQueryWrapper.class));
        verify(roleMenuMapper, times(2)).insert(any(SysRoleMenu.class));
    }

    @Test
    @DisplayName("分配菜单权限：空列表仅删除不插入")
    void testAssignMenus_emptyList() {
        roleService.assignMenus(1L, List.of());

        verify(roleMenuMapper).delete(any(LambdaQueryWrapper.class));
        verify(roleMenuMapper, never()).insert(any());
    }

    @Test
    @DisplayName("获取角色菜单ID列表")
    void testGetMenuIds() {
        SysRoleMenu rm1 = new SysRoleMenu();
        rm1.setMenuId(100L);
        SysRoleMenu rm2 = new SysRoleMenu();
        rm2.setMenuId(200L);
        when(roleMenuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rm1, rm2));

        List<Long> menuIds = roleService.getMenuIds(1L);

        assertThat(menuIds).containsExactly(100L, 200L);
    }
}
