-- ============================================================
-- 56_V2026_54__fund_menu_role_binding.sql
-- 双轨镜像：内容必须与 zw-app/src/main/resources/db/migration/V2026_54__fund_menu_role_binding.sql 一致
--（仅文件名加 56_ 前缀供新库 initdb 串行编排；Flyway 靠 classpath 那份自动应用）。
-- 背景：资金管理 P0/P1/P2 新增菜单（sys_menu 910050-910061）此前只写了 sys_menu，
--       漏写 sys_role_menu 角色绑定 → 侧边栏（sys_menu INNER JOIN sys_role_menu，无超管豁免）不显示。
-- 幂等：id = role_id*100000000 + menu_id（唯一、稳定）；NOT EXISTS 守卫；90062 不存在时不写悬空绑定。
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
