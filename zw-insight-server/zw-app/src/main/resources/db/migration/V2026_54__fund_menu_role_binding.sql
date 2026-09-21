-- ============================================================
-- V2026_54__fund_menu_role_binding.sql
-- 背景：资金管理 P0/P1/P2 新增菜单（sys_menu 910050-910061）此前只写了 sys_menu，
--       漏写 sys_role_menu 角色绑定 → 侧边栏（SysMenuService.getMenusByUserId 走
--       sys_menu INNER JOIN sys_role_menu，且无超管豁免）取不到 → admin 看不到新菜单，
--       但「菜单管理」直读 sys_menu 能看到。这是交付链路缺陷，本脚本补齐。
-- 口径：与同级老财务菜单（501-507 绑定 role 1,90062）保持一致：
--       SUPER_ADMIN(role_id=1) 全量授予；FINANCE_STAFF(role_id=90062) 若存在则同步授予。
-- 幂等：id = role_id*100000000 + menu_id（唯一、稳定）；NOT EXISTS 守卫，重复执行不产生脏行。
--       跨环境安全：90062 不存在于该环境时子查询返回空，不写入悬空绑定。
-- ============================================================

-- 1) SUPER_ADMIN (role_id=1) 授予全部 12 个新资金菜单（含 amount-tier 910057）
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT r.role_id * 100000000 + m.menu_id, r.role_id, m.menu_id
FROM (SELECT 1 AS role_id) r
CROSS JOIN (SELECT id AS menu_id FROM sys_menu WHERE id BETWEEN 910050 AND 910061) m
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.role_id AND x.menu_id = m.menu_id);

-- 2) FINANCE_STAFF (role_id=90062，仅当该角色存在) 同步授予，与同级财务菜单口径一致
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT r.role_id * 100000000 + m.menu_id, r.role_id, m.menu_id
FROM (SELECT id AS role_id FROM sys_role WHERE id = 90062 LIMIT 1) r
CROSS JOIN (SELECT id AS menu_id FROM sys_menu WHERE id BETWEEN 910050 AND 910061) m
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.role_id AND x.menu_id = m.menu_id);
