-- ============================================================
-- V2026_62__receivable_menu.sql
-- 应收台账菜单（资金闭环阶段一 1B 配套前端入口）
-- 规约：菜单种子必须同脚本写 sys_role_menu 角色绑定（V2026_54 历史踩坑）。
-- ID 段：延续资金菜单 910050-910061，本次占用 910062。
-- 幂等：INSERT IGNORE + NOT EXISTS 守卫。
-- ============================================================

-- 1) 菜单（父级 5=财务管理，与银行流水/资金日报同级）
INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(910062, '应收台账', 'MENU', 5, 'receivable', 'views/finance/receivable/index', 'Money', 62, 1, 0);

-- 2) SUPER_ADMIN (role_id=1) 授予
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT r.role_id * 100000000 + m.menu_id, r.role_id, m.menu_id
FROM (SELECT 1 AS role_id) r
CROSS JOIN (SELECT id AS menu_id FROM sys_menu WHERE id = 910062) m
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.role_id AND x.menu_id = m.menu_id);

-- 3) FINANCE_STAFF (role_id=90062，仅当该角色存在) 同步授予
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT r.role_id * 100000000 + m.menu_id, r.role_id, m.menu_id
FROM (SELECT id AS role_id FROM sys_role WHERE id = 90062 LIMIT 1) r
CROSS JOIN (SELECT id AS menu_id FROM sys_menu WHERE id = 910062) m
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.role_id AND x.menu_id = m.menu_id);
