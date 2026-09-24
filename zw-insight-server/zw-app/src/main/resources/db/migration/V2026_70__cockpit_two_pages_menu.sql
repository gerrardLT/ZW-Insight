-- ============================================================
-- V2026_70__cockpit_two_pages_menu.sql
-- 驾驶舱「项目经营」「成本中心」两页面菜单（UI 原型 §12/§6 与 §7/§8，文档对齐第四期 P2-3）
--
-- 规约：菜单种子必须同脚本写 sys_role_menu 角色绑定（V2026_54 历史踩坑：
--       漏写绑定会导致 admin 侧边栏看不到菜单，而「菜单管理」直视 sys_menu 能看到）。
-- ID 段：延续驾驶舱菜单段 910070-910073，本次占用 910076-910077
--        （910074 招待费分析、910075 月度经营分析已占用）。
-- 父级：910070「经营驾驶舱」目录（V2026_61 建立），与经营总览/资金中心/风险中心同级。
-- 权限码：沿用已登记的 dashboard:view（47_V2026_45_2__permission_guard_catalog.sql），
--         不新造权限码——新造而未登记会造成「无法通过角色配置授予」的伪权限。
-- 排序：项目经营 sort=4、成本中心 sort=5（接在风险中心 sort=3 之后，不改既有排序）。
-- 幂等：INSERT IGNORE + NOT EXISTS 守卫，可重复执行。
-- ============================================================

-- 1) 两个页面菜单
INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission) VALUES
(910076, '项目经营', 'MENU', 910070, 'project-operation', 'views/cockpit/project-operation', 'OfficeBuilding', 4, 1, 0, 'dashboard:view'),
(910077, '成本中心', 'MENU', 910070, 'cost-center',       'views/cockpit/cost-center',       'Coin',         5, 1, 0, 'dashboard:view');

-- 2) SUPER_ADMIN (role_id=1) 授予
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT r.role_id * 100000000 + m.menu_id, r.role_id, m.menu_id
FROM (SELECT 1 AS role_id) r
CROSS JOIN (SELECT id AS menu_id FROM sys_menu WHERE id BETWEEN 910076 AND 910077) m
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.role_id AND x.menu_id = m.menu_id);

-- 3) 业务角色同步授予（项目经理 90061 / 财务 90062 / 商务 90064）
--    与 V2026_61 驾驶舱三页面同一批角色，保持驾驶舱可见范围一致；
--    仅当角色存在时写入，跨环境安全——不存在则子查询返回空，不产生悬空绑定
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT r.role_id * 100000000 + m.menu_id, r.role_id, m.menu_id
FROM (SELECT id AS role_id FROM sys_role WHERE id IN (90061, 90062, 90064)) r
CROSS JOIN (SELECT id AS menu_id FROM sys_menu WHERE id BETWEEN 910076 AND 910077) m
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.role_id AND x.menu_id = m.menu_id);
