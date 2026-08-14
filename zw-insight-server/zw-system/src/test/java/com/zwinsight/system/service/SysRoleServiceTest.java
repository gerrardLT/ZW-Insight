package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.system.domain.SysRole;
import com.zwinsight.system.domain.SysRoleMenu;
import com.zwinsight.system.mapper.SysRoleMapper;
import com.zwinsight.system.mapper.SysRoleMenuMapper;
import com.zwinsight.system.mapper.SysUserRoleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @BeforeAll
    static void initTableInfo() {
        // wrapper.getSqlSegment() 断言需 Lambda 列缓存（跨租户过滤用例）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysRole.class);
    }

    @Test
    @DisplayName("根据ID查询：返回角色（selectOne 含租户条件）")
    void testGetById() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setRoleName("管理员");
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(role);

        SysRole result = roleService.getById(1L);

        assertThat(result.getRoleName()).isEqualTo("管理员");
    }

    @Test
    @DisplayName("分页查询：有租户上下文时 wrapper 含 tenant_id 过滤（跨租户越权修复 2026-08-14）")
    @SuppressWarnings("unchecked")
    void testPage_withTenantContext_filtersByTenant() {
        SecurityContextHolder.setTenantId(1L);
        try {
            Page<SysRole> page = new Page<>(1, 10);
            page.setRecords(List.of());
            when(roleMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

            roleService.page(1, 10, null, null);

            ArgumentCaptor<LambdaQueryWrapper<SysRole>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(roleMapper).selectPage(any(Page.class), captor.capture());
            assertThat(captor.getValue().getSqlSegment()).contains("tenant_id");
        } finally {
            SecurityContextHolder.clear();
        }
    }

    @Test
    @DisplayName("分页查询：无租户上下文时不加租户条件（内部调用零回归）")
    @SuppressWarnings("unchecked")
    void testPage_withoutTenantContext_noTenantFilter() {
        SecurityContextHolder.clear();
        Page<SysRole> page = new Page<>(1, 10);
        page.setRecords(List.of());
        when(roleMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        roleService.page(1, 10, null, null);

        ArgumentCaptor<LambdaQueryWrapper<SysRole>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(roleMapper).selectPage(any(Page.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).doesNotContain("tenant_id");
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
