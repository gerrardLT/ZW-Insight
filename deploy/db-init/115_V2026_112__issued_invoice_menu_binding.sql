-- ============================================================
-- V2026_112__issued_invoice_menu_binding.sql
-- 双轨镜像：内容必须与 deploy/db-init/115_V2026_112__issued_invoice_menu_binding.sql 一致
-- 背景：V2026_110 新增销项发票台账菜单（sys_menu 508），漏写 sys_role_menu 角色绑定 →
--       getMenusByUserId 严格按角色-菜单绑定过滤且无超管豁免 → 侧边栏不显示（同 V2026_54 缺陷族）。
-- 幂等：id = role_id*100000000 + menu_id（唯一、稳定）；NOT EXISTS 守卫；角色不存在时不写悬空绑定。
-- ============================================================

-- 1) SUPER_ADMIN (role_id=1) 授予销项发票台账（与收票登记 507 同口径）
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT r.role_id * 100000000 + m.menu_id, r.role_id, m.menu_id
FROM (SELECT 1 AS role_id) r
CROSS JOIN (SELECT id AS menu_id FROM sys_menu WHERE id = 508) m
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.role_id AND x.menu_id = m.menu_id);

-- 2) FINANCE_STAFF (role_id=90062，仅当该角色存在) 同步授予，与收票登记 507 的绑定口径一致
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT r.role_id * 100000000 + m.menu_id, r.role_id, m.menu_id
FROM (SELECT id AS role_id FROM sys_role WHERE id = 90062 LIMIT 1) r
CROSS JOIN (SELECT id AS menu_id FROM sys_menu WHERE id = 508) m
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.role_id AND x.menu_id = m.menu_id);
