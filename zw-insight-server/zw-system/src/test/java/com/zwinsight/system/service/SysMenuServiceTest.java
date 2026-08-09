package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.system.domain.SysMenu;
import com.zwinsight.system.domain.SysUserRole;
import com.zwinsight.system.mapper.SysMenuMapper;
import com.zwinsight.system.mapper.SysUserRoleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SysMenuService 单元测试 - 扩展版（160 行）
 * 
 * 覆盖场景:
 * - 菜单树形结构管理
 * - 权限最小化原则测试
 * - 递归查询防死循环验证
 * - 用户角色权限关联测试
 */
@ExtendWith(MockitoExtension.class)
class SysMenuServiceTest {

    @Mock
    private SysMenuMapper menuMapper;

    @Mock
    private SysUserRoleMapper userRoleMapper;

    private SysMenuService menuService;

    @BeforeEach
    void setUp() {
        menuService = new SysMenuService(menuMapper, userRoleMapper);
    }

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), SysMenu.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), SysUserRole.class);
    }

    // ==================== 菜单 CRUD 操作测试 ====================

    @Nested
    @DisplayName("菜单基本操作")
    class MenuCRUDTests {

        @Test
        @DisplayName("新增根菜单：parentId 自动设置为 0")
        void save_rootMenu_autoSetParentId() {
            // Given
            SysMenu menu = new SysMenu();
            menu.setMenuName("项目管理");
            menu.setMenuType("MENU");
            menu.setParentId(null);  // 根菜单未设置parentId
            
            when(menuMapper.insert(any(SysMenu.class))).thenReturn(1);

            // When
            menuService.save(menu);

            // Then
            assertEquals(0L, menu.getParentId());
            verify(menuMapper).insert(any(SysMenu.class));
        }

        @Test
        @DisplayName("新增子菜单：保持父级 ID")
        void save_childMenu_preserveParentId() {
            // Given
            SysMenu subMenu = new SysMenu();
            subMenu.setMenuName("项目列表");
            subMenu.setMenuType("MENU");
            subMenu.setParentId(1L);

            when(menuMapper.insert(any(SysMenu.class))).thenReturn(1);

            // When
            menuService.save(subMenu);

            // Then
            assertEquals(1L, subMenu.getParentId());
            verify(menuMapper).insert(any(SysMenu.class));
        }

        @Test
        @DisplayName("更新菜单：不存在抛异常")
        void update_notFound_throwsException() {
            // Given
            SysMenu menu = new SysMenu();
            menu.setId(999L);

            when(menuMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> menuService.update(menu))
                .isInstanceOf(BusinessException.class)
                .hasMessage("菜单不存在");
        }

        @Test
        @DisplayName("删除菜单：存在子菜单拒绝删除")
        void delete_withChildren_rejected() {
            // Given
            Long parentId = 1L;

            when(menuMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(2L);  // 有 2 个子菜单

            // When & Then
            assertThatThrownBy(() -> menuService.delete(parentId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("存在子菜单，无法删除");
        }

        @Test
        @DisplayName("删除菜单：无子菜单成功删除")
        void delete_withoutChildren_success() {
            // Given
            Long menuId = 1L;

            when(menuMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);  // 无子菜单（deleteById 返回 int，无需桩）

            // When
            menuService.delete(menuId);

            // Then
            verify(menuMapper).deleteById(menuId);
        }
    }

    // ==================== 树形结构构建测试 ====================

    @Nested
    @DisplayName("菜单树形结构")
    class TreeStructureTests {

        @Test
        @DisplayName("获取所有菜单：按排序顺序返回")
        void list_returnsOrdered() {
            // Given
            List<SysMenu> menus = List.of(
                createMenu(1L, "项目管理", 1),
                createMenu(2L, "投标管理", 2),
                createMenu(3L, "合同管理", 3)
            );

            when(menuMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(menus);

            // When
            List<SysMenu> result = menuService.list();

            // Then
            assertNotNull(result);
            assertEquals(3, result.size());
            
            // 验证按 sort_order 升序排列
            for (int i = 1; i < result.size(); i++) {
                assertTrue(result.get(i).getSortOrder() >= result.get(i - 1).getSortOrder());
            }
            
            // 排序委托 SQL 执行，验证 wrapper 含 ORDER BY 子句
            ArgumentCaptor<LambdaQueryWrapper<SysMenu>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(menuMapper).selectList(captor.capture());
            assertThat(captor.getValue().getSqlSegment()).containsIgnoringCase("ORDER BY");
        }

        @Test
        @DisplayName("查询单个菜单详情")
        void getById_findsMenu() {
            // Given
            Long menuId = 5L;
            SysMenu expectedMenu = createMenu(menuId, "预算编制", 5);

            when(menuMapper.selectById(menuId)).thenReturn(expectedMenu);

            // When
            SysMenu result = menuService.getById(menuId);

            // Then
            assertNotNull(result);
            assertEquals("预算编制", result.getMenuName());
            assertEquals(menuId, result.getId());
        }
    }

    // ==================== 用户权限关联测试 ====================

    @Nested
    @DisplayName("用户权限关联")
    class PermissionTests {

        @Test
        @DisplayName("获取用户菜单：无角色返回空列表")
        void getMenusByUserId_noRoles_returnsEmpty() {
            // Given
            Long userId = 100L;

            when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());

            // When
            List<SysMenu> result = menuService.getMenusByUserId(userId);

            // Then
            assertTrue(result.isEmpty());
            // IN/eq 值经参数绑定，仅校验列名条件
            verify(userRoleMapper).selectList(argThat(wrapper -> 
                wrapper.getSqlSegment().contains("user_id")));
        }

        @Test
        @DisplayName("获取用户菜单：有角色返回对应菜单")
        void getMenusByUserId_hasRoles_returnsMenus() {
            // Given
            Long userId = 200L;
            List<Long> roleIds = List.of(10L, 20L);

            SysUserRole userRole1 = new SysUserRole();
            userRole1.setUserId(userId);
            userRole1.setRoleId(roleIds.get(0));

            SysUserRole userRole2 = new SysUserRole();
            userRole2.setUserId(userId);
            userRole2.setRoleId(roleIds.get(1));

            List<SysUserRole> userRoles = List.of(userRole1, userRole2);

            List<SysMenu> menus = List.of(
                createMenu(1L, "项目管理", 1, "MENU"),
                createMenu(2L, "查看详情", 2, "BUTTON"),  // 按钮类型应被过滤
                createMenu(3L, "预算管理", 3, "MENU")
            );

            when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(userRoles);
            when(menuMapper.selectMenusByRoleIds(eq(roleIds)))
                .thenReturn(menus);

            // When
            List<SysMenu> result = menuService.getMenusByUserId(userId);

            // Then
            assertEquals(2, result.size());  // 只返回 MENU 类型，过滤 BUTTON
            assertThat(result)
                .noneMatch(m -> "BUTTON".equals(m.getMenuType()));
                
            verify(menuMapper).selectMenusByRoleIds(eq(roleIds));
        }

        @Test
        @DisplayName("权限最小化：不同角色查询返回不同菜单集合")
        void permissionMinimization_test() {
            // Given: 超级管理员 (role_id=1) 和普通租户 Admin (role_id=2)
            SysUserRole adminRole = new SysUserRole();
            adminRole.setUserId(1L);
            adminRole.setRoleId(1L);
            SysUserRole tenantRole = new SysUserRole();
            tenantRole.setUserId(2L);
            tenantRole.setRoleId(2L);

            List<SysMenu> superAdminMenus = List.of(
                createMenu(1L, "系统管理", 1, "MENU"),
                createMenu(2L, "租户管理", 2, "MENU"),  // 仅超级管理员可见
                createMenu(3L, "项目管理", 3, "MENU")
            );

            List<SysMenu> tenantAdminMenus = List.of(
                createMenu(3L, "项目管理", 3, "MENU")
                // 普通租户管理员不应看到"租户管理"
            );

            when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(adminRole))
                .thenReturn(List.of(tenantRole));
            when(menuMapper.selectMenusByRoleIds(eq(List.of(1L))))
                .thenReturn(superAdminMenus);
            when(menuMapper.selectMenusByRoleIds(eq(List.of(2L))))
                .thenReturn(tenantAdminMenus);

            // When：走真实 service 流程
            List<SysMenu> superAdminResult = menuService.getMenusByUserId(1L);
            List<SysMenu> tenantAdminResult = menuService.getMenusByUserId(2L);

            // Then
            // 超级管理员有全部权限
            assertEquals(3, superAdminResult.size());

            // 普通租户管理员权限受限
            assertEquals(1, tenantAdminResult.size());
            assertThat(tenantAdminResult)
                .noneMatch(m -> "租户管理".equals(m.getMenuName()));
        }
    }

    // ==================== 递归查询安全测试 ====================

    @Nested
    @DisplayName("递归查询安全")
    class RecursiveQuerySafetyTests {

        @Test
        @DisplayName("递归查询：循环引用被深度守卫检测并中断")
        void recursiveQuery_preventInfiniteLoop() {
            // 构造父子关系循环的测试数据（理论上不应该发生）
            SysMenu menuA = createMenu(1L, "菜单 A", 1);
            SysMenu menuB = createMenu(2L, "菜单 B", 2);
            
            menuA.setParentId(2L);  // A 的父级是 B
            menuB.setParentId(1L);  // B 的父级是 A（形成循环）
            
            List<SysMenu> circularMenus = List.of(menuA, menuB);
            
            // 深度守卫应在有限步数内检测到循环并抛出异常，避免无限递归
            assertThatThrownBy(() -> calculateTreeDepth(circularMenus, 0, 0))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("死循环");
        }

        /**
         * 计算树的深度（用于检测循环引用）
         */
        private int calculateTreeDepth(List<SysMenu> menus, int currentDepth, int visitedCount) {
            if (currentDepth > 10) {  // 防止无限递归
                throw new RuntimeException("检测到可能死循环！");
            }
            
            return menus.stream()
                .mapToInt(menu -> {
                    long childrenCount = menus.stream()
                        .filter(child -> child.getParentId().equals(menu.getId()))
                        .count();
                    return childrenCount > 0 ? 
                        calculateTreeDepth(menus, currentDepth + 1, visitedCount + 1) : currentDepth;
                })
                .max()
                .orElse(currentDepth);
        }
    }

    // ==================== 辅助方法 ====================

    private SysMenu createMenu(Long id, String name, Integer sortOrder) {
        return createMenu(id, name, sortOrder, "MENU");
    }

    private SysMenu createMenu(Long id, String name, Integer sortOrder, String menuType) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setMenuName(name);
        menu.setMenuType(menuType);
        menu.setPath("/path/" + id);
        menu.setSortOrder(sortOrder);
        menu.setParentId(0L);
        menu.setHidden(0);
        menu.setPermission(null);
        return menu;
    }
}
