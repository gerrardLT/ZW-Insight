package com.zwinsight.test.mapper;

import com.zwinsight.system.domain.SysMenu;
import com.zwinsight.system.mapper.SysMenuMapper;
import com.zwinsight.test.BaseH2MapperTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SysMenuMapper XML 查询测试（H2）
 */
class SysMenuMapperTest extends BaseH2MapperTest {

    @Autowired
    private SysMenuMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM sys_role_menu");
        jdbc.execute("DELETE FROM sys_menu");

        // 插入菜单
        jdbc.execute("INSERT INTO sys_menu (id, menu_name, menu_type, sort_order, status, deleted, version) "
                + "VALUES (1, '系统管理', 'DIR', 1, 1, 0, 0)");
        jdbc.execute("INSERT INTO sys_menu (id, menu_name, menu_type, sort_order, status, deleted, version) "
                + "VALUES (2, '用户管理', 'MENU', 2, 1, 0, 0)");
        jdbc.execute("INSERT INTO sys_menu (id, menu_name, menu_type, sort_order, status, deleted, version) "
                + "VALUES (3, '角色管理', 'MENU', 3, 1, 0, 0)");
        jdbc.execute("INSERT INTO sys_menu (id, menu_name, menu_type, sort_order, status, deleted, version) "
                + "VALUES (4, '项目管理', 'DIR', 4, 1, 0, 0)");
        // 停用的菜单
        jdbc.execute("INSERT INTO sys_menu (id, menu_name, menu_type, sort_order, status, deleted, version) "
                + "VALUES (5, '已停用菜单', 'MENU', 5, 0, 0, 0)");
        // 已删除的菜单
        jdbc.execute("INSERT INTO sys_menu (id, menu_name, menu_type, sort_order, status, deleted, version) "
                + "VALUES (6, '已删除菜单', 'MENU', 6, 1, 1, 0)");

        // 角色1 拥有菜单 1, 2, 3
        jdbc.execute("INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES (1, 100, 1)");
        jdbc.execute("INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES (2, 100, 2)");
        jdbc.execute("INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES (3, 100, 3)");

        // 角色2 拥有菜单 1, 4
        jdbc.execute("INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES (4, 200, 1)");
        jdbc.execute("INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES (5, 200, 4)");

        // 角色3 拥有已停用和已删除的菜单
        jdbc.execute("INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES (6, 300, 5)");
        jdbc.execute("INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES (7, 300, 6)");
    }

    @Test
    @DisplayName("selectMenusByRoleIds — 单角色查询菜单")
    void should_select_menus_by_single_role() {
        List<SysMenu> menus = mapper.selectMenusByRoleIds(List.of(100L));

        assertThat(menus).hasSize(3);
        assertThat(menus).extracting(SysMenu::getMenuName)
                .containsExactly("系统管理", "用户管理", "角色管理");
    }

    @Test
    @DisplayName("selectMenusByRoleIds — 多角色查询菜单（去重 + 按 sort_order 排序）")
    void should_select_menus_by_multiple_roles_deduplicated() {
        List<SysMenu> menus = mapper.selectMenusByRoleIds(List.of(100L, 200L));

        // 角色1: 1,2,3 + 角色2: 1,4 → 去重后 1,2,3,4
        assertThat(menus).hasSize(4);
        assertThat(menus).extracting(SysMenu::getId)
                .containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("selectMenusByRoleIds — 停用和已删除菜单不返回")
    void should_exclude_disabled_and_deleted_menus() {
        List<SysMenu> menus = mapper.selectMenusByRoleIds(List.of(300L));

        // 菜单5 status=0, 菜单6 deleted=1 → 都不应返回
        assertThat(menus).isEmpty();
    }

    @Test
    @DisplayName("selectMenusByRoleIds — 无角色匹配返回空")
    void should_return_empty_for_unknown_role() {
        List<SysMenu> menus = mapper.selectMenusByRoleIds(List.of(999L));

        assertThat(menus).isEmpty();
    }

    @Test
    @DisplayName("selectMenusByRoleIds — 结果按 sort_order 升序")
    void should_order_by_sort_order() {
        List<SysMenu> menus = mapper.selectMenusByRoleIds(List.of(100L, 200L));

        assertThat(menus).extracting(SysMenu::getSortOrder)
                .isSorted();
    }
}
