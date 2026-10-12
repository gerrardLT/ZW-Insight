-- =============================================================================
-- V2026_98__subcontract_output_menu.sql
-- 分包产值上报页菜单（2026-10-12）
--
-- 背景：补齐「后端有 API、前端无入口」缺口时新增了 views/subcontract/output.vue
--   （分包产值上报，走 SubcontractOutputController）。菜单树由 sys_menu ⋈ sys_role_menu 驱动
--   （GET /system/menu/user），故必须在库里补菜单行，否则线上侧边栏看不到入口。
--   deploy/db-init/99_data-menu.sql 只在全新装库时生效，已上线库靠本迁移补。
--
-- 幂等：INSERT IGNORE（重复执行报 1062 被忽略）；sys_menu 的 tenant_id 为 NULL，
--   与既有菜单行（1101/1102）一致，故不显式写入该列。
-- =============================================================================

INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden)
VALUES (1103, '分包产值', 'MENU', 11, 'output', 'views/subcontract/output', 'TrendCharts', 3, 1, 0);

-- 授权超级管理员（与 99_data-menu.sql 的 role_id=1 约定一致；其它角色按需另行授权）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES (92, 1, 1103);
