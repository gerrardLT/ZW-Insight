-- ============================================================
-- V2026_116__payable_menu.sql
-- 双轨镜像：内容必须与 deploy/db-init/119_V2026_116__payable_menu.sql 一致
-- 背景：应付账款台账菜单（V2026_115 台账的查询入口）。
-- 教训族：漏写 sys_role_menu 绑定则侧边栏不可见（V2026_54/V2026_112）。
-- 绑定：SUPER_ADMIN / FINANCE_STAFF 按 role_code 查 id（防双机角色 id 分叉），
--       角色不存在时不写悬空绑定；id = role_id*100000000 + menu_id（唯一稳定）。
-- 回滚：DELETE FROM sys_role_menu WHERE menu_id = 910063;
--       DELETE FROM sys_menu WHERE id = 910063;
-- ============================================================

INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910063, '应付账款台账', 'MENU', 5, 'payable', 'views/finance/payable/index', 'Money', 63, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE id = 910063 OR (parent_id = 5 AND path = 'payable' AND deleted = 0));

-- 角色绑定：按 role_code 查 id，双机角色模型分叉安全
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT r.id * 100000000 + 910063, r.id, 910063
FROM sys_role r
WHERE r.role_code IN ('SUPER_ADMIN', 'FINANCE_STAFF')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = 910063);
