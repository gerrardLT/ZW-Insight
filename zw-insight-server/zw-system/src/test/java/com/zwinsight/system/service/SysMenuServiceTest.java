package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.system.domain.SysMenu;
import com.zwinsight.system.domain.SysUserRole;
import com.zwinsight.system.mapper.SysMenuMapper;
import com.zwinsight.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysMenuServiceTest {

    @Mock private SysMenuMapper menuMapper;
    @Mock private SysUserRoleMapper userRoleMapper;

    @InjectMocks
    private SysMenuService menuService;

    @Test
    @DisplayName("查询所有菜单：返回列表")
    void testList() {
        SysMenu menu = new SysMenu();
        menu.setId(1L);
        menu.setMenuName("首页");
        when(menuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(menu));

        List<SysMenu> result = menuService.list();

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("新增菜单：parentId为null时设为0")
    void testSave_nullParentId() {
        SysMenu menu = new SysMenu();
        menu.setMenuName("新菜单");
        menu.setParentId(null);

        menuService.save(menu);

        assertThat(menu.getParentId()).isEqualTo(0L);
        verify(menuMapper).insert(menu);
    }

    @Test
    @DisplayName("更新菜单：不存在抛异常")
    void testUpdate_notFound() {
        when(menuMapper.selectById(999L)).thenReturn(null);

        SysMenu update = new SysMenu();
        update.setId(999L);

        assertThatThrownBy(() -> menuService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("菜单不存在");
    }

    @Test
    @DisplayName("删除菜单：存在子菜单抛异常")
    void testDelete_hasChildren() {
        when(menuMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> menuService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在子菜单，无法删除");
    }

    @Test
    @DisplayName("删除菜单：无子菜单正常删除")
    void testDelete_ok() {
        when(menuMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        menuService.delete(1L);

        verify(menuMapper).deleteById(1L);
    }

    @Test
    @DisplayName("获取用户菜单：无角色返回空列表")
    void testGetMenusByUserId_noRoles() {
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<SysMenu> result = menuService.getMenusByUserId(1L);

        assertThat(result).isEmpty();
    }
}
