-- ============================================================
-- V2026_73__system_config_menu.sql
-- 补齐「系统设置」菜单入口（含品牌设置 Tab）
--
-- 背景：前端路由 (router/index.ts path='config' -> views/system/config/index.vue)
-- 与后端接口 (SystemConfigController /api/v1/system/config/**) 均已上线，
-- 但初始种子数据 (99_data-menu.sql) 从未在 sys_menu 里注册这条菜单，
-- 导致侧边栏「系统管理」下永远看不到「系统设置」入口——两台服务器
-- （129.204.3.200 / 43.142.44.145）均缺失，属遗留缺口，非本次改造引入。
--
-- 排序：sort_order=13，接在「系统监控」(212, sort 12) 之后，不改动既有排序。
-- 权限：沿用 sys_menu id=2「系统管理」已登记的 system:view 权限码，
--       不新造权限码（新造未登记的权限码只有 SUPER_ADMIN 靠豁免能访问）。
-- 幂等：INSERT IGNORE + NOT EXISTS 守卫，可重复执行；仅绑定 SUPER_ADMIN，
--       与「数据备份/版本管理/系统监控」等管理类菜单的可见范围一致。
-- ============================================================

INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission) VALUES
(213, '系统设置', 'MENU', 2, 'config', 'views/system/config/index', 'Tools', 13, 1, 0, 'system:view');

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT r.role_id * 100000000 + m.menu_id, r.role_id, m.menu_id
FROM (SELECT 1 AS role_id) r
CROSS JOIN (SELECT id AS menu_id FROM sys_menu WHERE id = 213) m
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.role_id AND x.menu_id = m.menu_id);
