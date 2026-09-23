-- ============================================================
-- V2026_61__cockpit_menu.sql
-- 经营驾驶舱菜单（V2026_59 后端 + cockpit 前端三页面配套入口）
--
-- 规约：菜单种子必须同脚本写 sys_role_menu 角色绑定（V2026_54 历史踩坑：
--       漏写绑定会导致 admin 侧边栏看不到菜单，而「菜单管理」直读 sys_menu 能看到）。
-- ID 段：910070-910073（延续资金菜单段，910062 已被应收台账占用）。
-- 权限码：沿用已登记的 dashboard:view（47_V2026_45_2__permission_guard_catalog.sql），
--         不新造权限码——新造而未登记会造成"无法通过角色配置授予"的伪权限。
-- 排序：sort_order=2 与「项目管理」并列（不改既有菜单排序以免影响用户习惯）；
--       如需严格前置，应统一调整全部顶级菜单 sort_order，不在本脚本范围内。
-- 幂等：INSERT IGNORE + NOT EXISTS 守卫。
-- ============================================================

-- 1) 目录 + 三个页面菜单
INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission) VALUES
(910070, '经营驾驶舱', 'DIR',  0,      '/cockpit',      NULL,                        'Odometer',  2, 1, 0, 'dashboard:view'),
(910071, '经营总览',   'MENU', 910070, 'overview',      'views/cockpit/index',       'DataBoard', 1, 1, 0, 'dashboard:view'),
(910072, '资金中心',   'MENU', 910070, 'fund-center',   'views/cockpit/fund-center', 'Money',     2, 1, 0, 'dashboard:view'),
(910073, '风险中心',   'MENU', 910070, 'risk-center',   'views/cockpit/risk-center', 'WarningFilled', 3, 1, 0, 'dashboard:view');

-- 2) SUPER_ADMIN (role_id=1) 授予目录与全部子菜单
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT r.role_id * 100000000 + m.menu_id, r.role_id, m.menu_id
FROM (SELECT 1 AS role_id) r
CROSS JOIN (SELECT id AS menu_id FROM sys_menu WHERE id BETWEEN 910070 AND 910073) m
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.role_id AND x.menu_id = m.menu_id);

-- 3) 业务角色同步授予（老板/管理层视角：项目经理 90061、财务 90062、商务 90064；
--    仅当角色存在时写入，跨环境安全——不存在则子查询返回空，不产生悬空绑定）
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT r.role_id * 100000000 + m.menu_id, r.role_id, m.menu_id
FROM (SELECT id AS role_id FROM sys_role WHERE id IN (90061, 90062, 90064)) r
CROSS JOIN (SELECT id AS menu_id FROM sys_menu WHERE id BETWEEN 910070 AND 910073) m
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.role_id AND x.menu_id = m.menu_id);
