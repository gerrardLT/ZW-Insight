-- ============================================================
-- V2026_86__missing_menus.sql
-- 补齐前端 router 已有、sys_menu 从未登记的 21 个页面菜单 + 「平台管理」目录
--
-- 背景：侧边栏由 getUserMenus（sys_menu ⋈ sys_role_menu）驱动，router 有路由而 sys_menu
--       无记录 → 页面可 URL 直达但左侧不显示。差集由 keys/check-menu-coverage.cjs 产出。
-- ID 段：910078-910099（910070-910077 已占用；910100-910103 属 sys_amount_tier，非本表）。
-- 幂等：每条 INSERT 带「同 id 或 同 parent_id+path 未删除」双守卫（兼容已在菜单管理里手工建过的环境）。
-- 授权：SUPER_ADMIN(1) 全部；FINANCE_STAFF(90062) 财务 6 项；PROJECT_MANAGER(90061) 合同/材料/机械 4 项；
--       其余角色经「角色管理」自行勾选。平台管理仅 SUPER_ADMIN。
-- 回滚：DELETE FROM sys_role_menu WHERE menu_id BETWEEN 910078 AND 910099;
--       DELETE FROM sys_menu WHERE id BETWEEN 910078 AND 910099;
-- ============================================================

-- 1) 目录：平台管理（router /platform 为顶层路由，sys_menu 此前无对应 DIR，子菜单无法解析路径）
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910095, '平台管理', 'DIR', 0, '/platform', NULL, 'Platform', 98, 1, 0, 'system:view', NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910095 OR (parent_id = 0 AND path = '/platform' AND deleted = 0));

-- 2) 系统管理(2) / 合同管理(4)
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910078, '模板管理', 'MENU', 2, 'template', 'views/system/template/index', 'Files', 13, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910078 OR (parent_id = 2 AND path = 'template' AND deleted = 0));
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910079, '产值上报', 'MENU', 4, 'output-report', 'views/contract/output-report', 'TrendCharts', 4, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910079 OR (parent_id = 4 AND path = 'output-report' AND deleted = 0));

-- 3) 财务管理(5)
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910080, '发票汇总', 'MENU', 5, 'invoice-summary', 'views/finance/invoice-summary', 'DataAnalysis', 65, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910080 OR (parent_id = 5 AND path = 'invoice-summary' AND deleted = 0));
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910081, '其他费用付款', 'MENU', 5, 'other-payment', 'views/finance/other-payment', 'Coin', 66, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910081 OR (parent_id = 5 AND path = 'other-payment' AND deleted = 0));
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910082, '项目报销', 'MENU', 5, 'project-reimbursement', 'views/finance/project-reimbursement', 'Document', 67, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910082 OR (parent_id = 5 AND path = 'project-reimbursement' AND deleted = 0));
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910083, '个人报销', 'MENU', 5, 'personal-reimbursement', 'views/finance/personal-reimbursement', 'User', 68, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910083 OR (parent_id = 5 AND path = 'personal-reimbursement' AND deleted = 0));
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910084, '备用金管理', 'MENU', 5, 'reserve-fund', 'views/finance/reserve-fund', 'Wallet', 69, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910084 OR (parent_id = 5 AND path = 'reserve-fund' AND deleted = 0));
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910085, '质保金管理', 'MENU', 5, 'retention', 'views/finance/retention', 'GoldMedal', 70, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910085 OR (parent_id = 5 AND path = 'retention' AND deleted = 0));

-- 4) 劳务(8) / 材料(9) / 机械(10)
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910086, '薪资统计', 'MENU', 8, 'salary-stats', 'views/labor/salary/stats', 'DataAnalysis', 9, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910086 OR (parent_id = 8 AND path = 'salary-stats' AND deleted = 0));
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910087, '材料盘点', 'MENU', 9, 'inventory', 'views/material/inventory', 'DocumentChecked', 5, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910087 OR (parent_id = 9 AND path = 'inventory' AND deleted = 0));
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910088, '退货退款', 'MENU', 9, 'refund', 'views/material/refund', 'RefreshLeft', 6, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910088 OR (parent_id = 9 AND path = 'refund' AND deleted = 0));
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910089, '机械结算', 'MENU', 10, 'settlement', 'views/machine/settlement/index', 'Tickets', 6, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910089 OR (parent_id = 10 AND path = 'settlement' AND deleted = 0));

-- 5) 行政人事(14) / 档案管理(15) / 工作流(16)
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910090, '人事统计', 'MENU', 14, 'statistics', 'views/hr/statistics', 'DataAnalysis', 4, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910090 OR (parent_id = 14 AND path = 'statistics' AND deleted = 0));
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910091, '离职申请', 'MENU', 14, 'resign-apply', 'views/hr/resign-apply', 'RemoveFilled', 5, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910091 OR (parent_id = 14 AND path = 'resign-apply' AND deleted = 0));
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910092, '其它收入合同档案', 'MENU', 15, 'other-income-contract', 'views/archive/other-income-contract', 'Document', 2, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910092 OR (parent_id = 15 AND path = 'other-income-contract' AND deleted = 0));
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910093, '其它支出合同档案', 'MENU', 15, 'other-expense-contract', 'views/archive/other-expense-contract', 'Document', 3, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910093 OR (parent_id = 15 AND path = 'other-expense-contract' AND deleted = 0));
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910094, '办公用品档案', 'MENU', 15, 'office-supply', 'views/archive/office-supply', 'Box', 4, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910094 OR (parent_id = 15 AND path = 'office-supply' AND deleted = 0));
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910099, '审批回滚', 'MENU', 16, 'rollback', 'views/workflow/rollback/index', 'RefreshLeft', 5, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910099 OR (parent_id = 16 AND path = 'rollback' AND deleted = 0));

-- 6) 平台管理(910095) 子菜单
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910096, '租户管理', 'MENU', 910095, 'tenant', 'views/platform/tenant/index', 'OfficeBuilding', 1, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910096 OR (parent_id = 910095 AND path = 'tenant' AND deleted = 0));
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910097, '用户类型', 'MENU', 910095, 'tenant-type', 'views/platform/tenant-type/index', 'Collection', 2, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910097 OR (parent_id = 910095 AND path = 'tenant-type' AND deleted = 0));
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910098, '存储管理', 'MENU', 910095, 'storage', 'views/platform/storage/index', 'FolderOpened', 3, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 910098 OR (parent_id = 910095 AND path = 'storage' AND deleted = 0));

-- 7) 角色绑定（V2026_54 教训：漏绑 sys_role_menu 则侧边栏仍不可见）
--    id = role_id*100000000 + menu_id，唯一稳定；NOT EXISTS 守卫可重复执行
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 100000000 + m.id, 1, m.id FROM sys_menu m
WHERE m.id BETWEEN 910078 AND 910099
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = 1 AND x.menu_id = m.id);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT r.role_id * 100000000 + m.id, r.role_id, m.id
FROM (SELECT id AS role_id FROM sys_role WHERE id = 90062 LIMIT 1) r
CROSS JOIN sys_menu m
WHERE m.id BETWEEN 910080 AND 910085
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.role_id AND x.menu_id = m.id);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT r.role_id * 100000000 + m.id, r.role_id, m.id
FROM (SELECT id AS role_id FROM sys_role WHERE id = 90061 LIMIT 1) r
CROSS JOIN sys_menu m
WHERE m.id IN (910079, 910087, 910088, 910089)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.role_id AND x.menu_id = m.id);
