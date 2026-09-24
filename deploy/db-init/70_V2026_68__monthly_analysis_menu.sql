-- ============================================================
-- V2026_68__monthly_analysis_menu.sql
-- 月度经营分析表菜单（资金流转 §9 / 驾驶舱 §12「项目管理层最终只看一张表」的前端入口）
--
-- 规约：菜单种子必须同脚本写 sys_role_menu 角色绑定（V2026_54 历史踩坑：
--       漏写绑定会导致 admin 侧边栏看不到菜单，而「菜单管理」直视 sys_menu 能看到）。
-- ID 段：延续资金/驾驶舱菜单段，本次占用 910075（910074 已被招待费分析占用）。
-- 权限码：沿用已登记的 finance:view（47_V2026_45_2__permission_guard_catalog.sql 中
--         sys_menu id=5「财务管理」即用该码），不新造权限码。
-- 排序：sort_order=64，接在「招待费分析」(910074, sort 63) 之后。
-- 幂等：INSERT IGNORE + NOT EXISTS 守卫，可重复执行。
-- ============================================================

-- 1) 菜单（父级 5=财务管理，与招待费分析/应收台账同级）
INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission) VALUES
(910075, '月度经营分析', 'MENU', 5, 'monthly-analysis', 'views/finance/monthly-analysis/index', 'Tickets', 64, 1, 0, 'finance:view');

-- 2) SUPER_ADMIN (role_id=1) 授予
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT r.role_id * 100000000 + m.menu_id, r.role_id, m.menu_id
FROM (SELECT 1 AS role_id) r
CROSS JOIN (SELECT id AS menu_id FROM sys_menu WHERE id = 910075) m
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.role_id AND x.menu_id = m.menu_id);

-- 3) 业务角色同步授予（项目经理 90061 / 财务 90062 / 商务 90064）
--    本表是「项目管理层最终只看的一张表」，项目经理为主要使用者，故必须授予
--    仅当角色存在时写入，跨环境安全——不存在则子查询返回空，不产生悬空绑定
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT r.role_id * 100000000 + m.menu_id, r.role_id, m.menu_id
FROM (SELECT id AS role_id FROM sys_role WHERE id IN (90061, 90062, 90064)) r
CROSS JOIN (SELECT id AS menu_id FROM sys_menu WHERE id = 910075) m
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.role_id AND x.menu_id = m.menu_id);
