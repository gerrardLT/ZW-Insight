package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.system.domain.SysMenu;
import com.zwinsight.system.domain.SysUserRole;
import com.zwinsight.system.mapper.SysMenuMapper;
import com.zwinsight.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
@DisplayName("SysMenuService 菜单管理服务测试")
class SysMenuServiceTest {

    @Mock private SysMenuMapper menuMapper;
    @Mock private SysUserRoleMapper userRoleMapper;

    @InjectMocks
    private SysMenuService menuService;

    // ==================== 菜单树构建测试 ====================

    @Nested
    @DisplayName("菜单树构建")
    class MenuTreeTests {

        @Test
        @DisplayName("查询所有菜单：返回按排序号升序排列的列表")
        void list_returnsOrderedMenus() {
            SysMenu menu1 = buildMenu(1L, "系统管理", "DIR", 0L, 1);
            SysMenu menu2 = buildMenu(2L, "用户管理", "MENU", 1L, 1);
            SysMenu menu3 = buildMenu(3L, "角色管理", "MENU", 1L, 2);
            when(menuMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(menu1, menu2, menu3));

            List<SysMenu> result = menuService.list();

            assertThat(result).hasSize(3);
            assertThat(result).extracting(SysMenu::getMenuName)
                    .containsExactly("系统管理", "用户管理", "角色管理");
        }

        @Test
        @DisplayName("菜单树多级嵌套：三级菜单结构正确返回")
        void list_multiLevelNesting() {
            // 模拟三级菜单：系统管理 → 用户管理 → 用户新增(按钮)
            SysMenu level1 = buildMenu(1L, "系统管理", "DIR", 0L, 1);
            SysMenu level2 = buildMenu(2L, "用户管理", "MENU", 1L, 1);
            SysMenu level3 = buildMenu(3L, "用户新增", "BUTTON", 2L, 1);
            when(menuMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(level1, level2, level3));

            List<SysMenu> result = menuService.list();

            assertThat(result).hasSize(3);
            // 验证父子关系
            assertThat(result.get(0).getParentId()).isEqualTo(0L);
            assertThat(result.get(1).getParentId()).isEqualTo(1L);
            assertThat(result.get(2).getParentId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("菜单树空节点：数据库无菜单时返回空列表")
        void list_emptyDatabase_returnsEmptyList() {
            when(menuMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            List<SysMenu> result = menuService.list();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("菜单树包含多个根节点：所有 parentId=0 的菜单都是顶级")
        void list_multipleRootNodes() {
            SysMenu root1 = buildMenu(1L, "系统管理", "DIR", 0L, 1);
            SysMenu root2 = buildMenu(2L, "项目管理", "DIR", 0L, 2);
            SysMenu root3 = buildMenu(3L, "财务管理", "DIR", 0L, 3);
            when(menuMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(root1, root2, root3));

            List<SysMenu> result = menuService.list();

            assertThat(result).hasSize(3);
            assertThat(result).allMatch(m -> m.getParentId().equals(0L));
        }

        @Test
        @DisplayName("根据ID查询菜单：存在时返回菜单对象")
        void getById_existing() {
            SysMenu menu = buildMenu(1L, "首页", "MENU", 0L, 1);
            when(menuMapper.selectById(1L)).thenReturn(menu);

            SysMenu result = menuService.getById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getMenuName()).isEqualTo("首页");
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("根据ID查询菜单：不存在时返回null")
        void getById_notFound_returnsNull() {
            when(menuMapper.selectById(999L)).thenReturn(null);

            SysMenu result = menuService.getById(999L);

            assertThat(result).isNull();
        }
    }

    // ==================== 用户菜单过滤测试 ====================

    @Nested
    @DisplayName("用户菜单过滤（根据角色权限）")
    class UserMenuFilterTests {

        @Test
        @DisplayName("获取用户菜单：无角色返回空列表")
        void getMenusByUserId_noRoles_returnsEmpty() {
            when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            List<SysMenu> result = menuService.getMenusByUserId(1L);

            assertThat(result).isEmpty();
            // 无角色时不应查询菜单
            verify(menuMapper, never()).selectMenusByRoleIds(any());
        }

        @Test
        @DisplayName("获取用户菜单：有角色时按角色过滤并排除BUTTON类型")
        void getMenusByUserId_withRoles_filterButtons() {
            // 模拟用户有两个角色
            SysUserRole role1 = new SysUserRole();
            role1.setUserId(1L);
            role1.setRoleId(10L);
            SysUserRole role2 = new SysUserRole();
            role2.setUserId(1L);
            role2.setRoleId(20L);
            when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(role1, role2));

            // 模拟角色对应的菜单（包含BUTTON类型）
            SysMenu dirMenu = buildMenu(1L, "系统管理", "DIR", 0L, 1);
            SysMenu menuItem = buildMenu(2L, "用户管理", "MENU", 1L, 1);
            SysMenu buttonItem = buildMenu(3L, "用户新增", "BUTTON", 2L, 1);
            when(menuMapper.selectMenusByRoleIds(List.of(10L, 20L)))
                    .thenReturn(List.of(dirMenu, menuItem, buttonItem));

            List<SysMenu> result = menuService.getMenusByUserId(1L);

            // BUTTON类型应被过滤掉
            assertThat(result).hasSize(2);
            assertThat(result).extracting(SysMenu::getMenuType)
                    .containsExactly("DIR", "MENU");
            assertThat(result).extracting(SysMenu::getMenuType)
                    .doesNotContain("BUTTON");
        }

        @Test
        @DisplayName("获取用户菜单：角色关联的菜单全为BUTTON时返回空列表")
        void getMenusByUserId_allButtons_returnsEmpty() {
            SysUserRole role = new SysUserRole();
            role.setUserId(1L);
            role.setRoleId(10L);
            when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(role));

            SysMenu button1 = buildMenu(1L, "新增", "BUTTON", 100L, 1);
            SysMenu button2 = buildMenu(2L, "编辑", "BUTTON", 100L, 2);
            when(menuMapper.selectMenusByRoleIds(List.of(10L)))
                    .thenReturn(List.of(button1, button2));

            List<SysMenu> result = menuService.getMenusByUserId(1L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("获取用户菜单：单角色返回对应菜单")
        void getMenusByUserId_singleRole_returnsMenus() {
            SysUserRole role = new SysUserRole();
            role.setUserId(5L);
            role.setRoleId(100L);
            when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(role));

            SysMenu menu1 = buildMenu(10L, "项目管理", "DIR", 0L, 1);
            SysMenu menu2 = buildMenu(11L, "项目列表", "MENU", 10L, 1);
            when(menuMapper.selectMenusByRoleIds(List.of(100L)))
                    .thenReturn(List.of(menu1, menu2));

            List<SysMenu> result = menuService.getMenusByUserId(5L);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(SysMenu::getMenuName)
                    .containsExactly("项目管理", "项目列表");
        }

        @Test
        @DisplayName("获取用户菜单：多角色角色ID正确传递给Mapper")
        void getMenusByUserId_multipleRoles_passesCorrectRoleIds() {
            SysUserRole r1 = new SysUserRole();
            r1.setUserId(1L);
            r1.setRoleId(10L);
            SysUserRole r2 = new SysUserRole();
            r2.setUserId(1L);
            r2.setRoleId(20L);
            SysUserRole r3 = new SysUserRole();
            r3.setUserId(1L);
            r3.setRoleId(30L);
            when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(r1, r2, r3));
            when(menuMapper.selectMenusByRoleIds(List.of(10L, 20L, 30L)))
                    .thenReturn(Collections.emptyList());

            menuService.getMenusByUserId(1L);

            verify(menuMapper).selectMenusByRoleIds(List.of(10L, 20L, 30L));
        }
    }

    // ==================== 菜单 CRUD 测试 ====================

    @Nested
    @DisplayName("菜单 CRUD 操作")
    class MenuCrudTests {

        @Test
        @DisplayName("新增菜单：parentId 为 null 时自动设为 0（顶级菜单）")
        void save_nullParentId_setsToZero() {
            SysMenu menu = new SysMenu();
            menu.setMenuName("新模块");
            menu.setMenuType("DIR");
            menu.setParentId(null);

            menuService.save(menu);

            assertThat(menu.getParentId()).isEqualTo(0L);
            verify(menuMapper).insert(menu);
        }

        @Test
        @DisplayName("新增菜单：parentId 已设置时保持不变")
        void save_withParentId_keepsOriginal() {
            SysMenu menu = new SysMenu();
            menu.setMenuName("子菜单");
            menu.setMenuType("MENU");
            menu.setParentId(100L);

            menuService.save(menu);

            assertThat(menu.getParentId()).isEqualTo(100L);
            verify(menuMapper).insert(menu);
        }

        @Test
        @DisplayName("更新菜单：存在时正常更新")
        void update_existing_success() {
            SysMenu existing = buildMenu(1L, "旧名称", "MENU", 0L, 1);
            when(menuMapper.selectById(1L)).thenReturn(existing);

            SysMenu update = new SysMenu();
            update.setId(1L);
            update.setMenuName("新名称");

            menuService.update(update);

            verify(menuMapper).updateById(update);
        }

        @Test
        @DisplayName("更新菜单：不存在时抛出业务异常")
        void update_notFound_throwsBusinessException() {
            when(menuMapper.selectById(999L)).thenReturn(null);

            SysMenu update = new SysMenu();
            update.setId(999L);
            update.setMenuName("不存在的菜单");

            assertThatThrownBy(() -> menuService.update(update))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("菜单不存在");
            verify(menuMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("删除菜单：无子菜单时正常删除")
        void delete_noChildren_success() {
            when(menuMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            menuService.delete(1L);

            verify(menuMapper).deleteById(1L);
        }

        @Test
        @DisplayName("删除菜单：存在子菜单时抛出业务异常")
        void delete_hasChildren_throwsBusinessException() {
            when(menuMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

            assertThatThrownBy(() -> menuService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("存在子菜单，无法删除");
            verify(menuMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("删除菜单：检查子菜单数为0的边界条件")
        void delete_zeroChildren_allowsDeletion() {
            when(menuMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            assertThatCode(() -> menuService.delete(50L))
                    .doesNotThrowAnyException();
            verify(menuMapper).deleteById(50L);
        }
    }

    // ==================== 辅助方法 ====================

    private SysMenu buildMenu(Long id, String name, String type, Long parentId, Integer sortOrder) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setMenuName(name);
        menu.setMenuType(type);
        menu.setParentId(parentId);
        menu.setSortOrder(sortOrder);
        return menu;
    }
}
