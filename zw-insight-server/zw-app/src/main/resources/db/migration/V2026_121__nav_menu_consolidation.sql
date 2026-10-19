-- ============================================================
-- V2026_121__nav_menu_consolidation.sql
-- 双轨镜像：内容必须与 deploy/db-init/124_V2026_121__nav_menu_consolidation.sql 一致
-- 背景：继 V2026_120（财务 29→8）之后，按用户指令对「经营总览、项目管理、成本管理」
--       三大域做同模式二级菜单收敛：
--         经营总览 5→3（三驾驶舱合 1，资金/风险中心保留）
--         项目管理 7→3（台账+WBS / 投标 / 总包合同与产值）
--         成本管理 31→5（目标成本 / 劳务班组 / 材料与采购 / 机械租赁 / 专业分包）
-- 机制与 V2026_120 完全一致（2026-10-19 代码与双机实测背书）：
--   ① 权限码 SQL 不检查 hidden，菜单行 hidden=1 后权限码照常下发；
--   ② 侧边栏 authorized 集剔除 hidden 菜单行 + router meta.hidden 双保险；
--   ③ 原路由全部保留 URL 直达（含 ?projectId= 透传），BUTTON 行不动；
--   ④ 顺手清理死链：budget 域 602「预算配置」path=config 前端无路由，一并 hidden。
-- 幂等：UPDATE 同值无害；INSERT IGNORE；绑定复制 NOT EXISTS 守卫。
-- 探针（推送前双机完成）：920201-920220 段两机均 0 行；十域 DIR 子菜单数两机一致
--   （project2/contract3/budget5/purchase3/labor9/material6/machine6/subcontract3/tender2/cockpit5）。
-- 回滚：UPDATE sys_menu SET hidden=0 WHERE menu_type='MENU' AND deleted=0 AND hidden=1
--         AND id NOT BETWEEN 920101 AND 920108;  -- 还原本脚本与 V2026_120 之外的（需按 parent 限定）
--       UPDATE sys_menu SET hidden=1 WHERE id BETWEEN 920201 AND 920209;
--       DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id=m.id
--         WHERE m.id BETWEEN 920201 AND 920209;
-- ============================================================

-- 1) 原二级菜单退出侧边栏（42 行：cockpit3 + project2 + tender2 + contract3 + budget5
--    + labor9 + material6 + purchase3 + machine6 + subcontract3）
UPDATE sys_menu SET hidden = 1 WHERE parent_id = 910070 AND menu_type = 'MENU' AND deleted = 0
  AND path IN ('overview', 'project-operation', 'cost-center');
UPDATE sys_menu SET hidden = 1 WHERE parent_id = 3 AND menu_type = 'MENU' AND deleted = 0
  AND path IN ('list', 'wbs');
UPDATE sys_menu SET hidden = 1 WHERE parent_id = 13 AND menu_type = 'MENU' AND deleted = 0
  AND path IN ('register', 'certificate');
UPDATE sys_menu SET hidden = 1 WHERE parent_id = 4 AND menu_type = 'MENU' AND deleted = 0
  AND path IN ('list', 'output-report', 'change-event');
UPDATE sys_menu SET hidden = 1 WHERE parent_id = 6 AND menu_type = 'MENU' AND deleted = 0
  AND path IN ('list', 'config', 'change', 'control-config', 'cost-account');
UPDATE sys_menu SET hidden = 1 WHERE parent_id = 8 AND menu_type = 'MENU' AND deleted = 0
  AND path IN ('contract', 'team', 'roster', 'work-order', 'payroll', 'salary-stats', 'output', 'settlement', 'reward-punish');
UPDATE sys_menu SET hidden = 1 WHERE parent_id = 9 AND menu_type = 'MENU' AND deleted = 0
  AND path IN ('inbound', 'outbound', 'transfer', 'stock', 'inventory', 'refund');
UPDATE sys_menu SET hidden = 1 WHERE parent_id = 7 AND menu_type = 'MENU' AND deleted = 0
  AND path IN ('contract', 'settlement', 'inquiry');
UPDATE sys_menu SET hidden = 1 WHERE parent_id = 10 AND menu_type = 'MENU' AND deleted = 0
  AND path IN ('contract', 'ledger', 'entry', 'work-log', 'repair', 'settlement');
UPDATE sys_menu SET hidden = 1 WHERE parent_id = 11 AND menu_type = 'MENU' AND deleted = 0
  AND path IN ('contract', 'settlement', 'output');

-- 2) 9 个一等入口菜单（固定 id 920201-920209，双机已探针空闲）
INSERT IGNORE INTO sys_menu
  (id, menu_name, menu_type, parent_id, path, icon, sort_order, permission, status, hidden, weight, tenant_id)
VALUES
  (920201, '经营驾驶舱', 'MENU', 910070, '/cockpit/hub/operation-group', 'DataBoard', 1, NULL, 1, 0, 'NORMAL', NULL),
  (920202, '项目台账与工程分解', 'MENU', 3, '/project/hub/ledger-group', 'Document', 1, NULL, 1, 0, 'NORMAL', NULL),
  (920203, '投标管理', 'MENU', 13, '/tender/hub/tender-group', 'EditPen', 1, NULL, 1, 0, 'NORMAL', NULL),
  (920204, '总包合同与产值', 'MENU', 4, '/contract/hub/contract-group', 'Document', 1, NULL, 1, 0, 'NORMAL', NULL),
  (920205, '目标成本管理', 'MENU', 6, '/budget/hub/cost-group', 'Coin', 1, NULL, 1, 0, 'NORMAL', NULL),
  (920206, '劳务班组管理', 'MENU', 8, '/labor/hub/labor-group', 'Avatar', 1, NULL, 1, 0, 'NORMAL', NULL),
  (920207, '材料与采购', 'MENU', 9, '/material/hub/material-purchase-group', 'Box', 1, NULL, 1, 0, 'NORMAL', NULL),
  (920208, '机械租赁管理', 'MENU', 10, '/machine/hub/machine-group', 'Van', 1, NULL, 1, 0, 'NORMAL', NULL),
  (920209, '专业分包管理', 'MENU', 11, '/subcontract/hub/subcontract-group', 'Connection', 1, NULL, 1, 0, 'NORMAL', NULL);

-- 3) 角色绑定复制（入口可见性=组内任一原菜单绑定角色的并集；MAX(id)+ROW_NUMBER 防雪花溢出）

-- 3.1 经营驾驶舱
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu) + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 920201
FROM (SELECT DISTINCT rm.role_id FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id = m.id
  WHERE m.parent_id = 910070 AND m.menu_type = 'MENU' AND m.deleted = 0
    AND m.path IN ('overview', 'project-operation', 'cost-center')) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = 920201);

-- 3.2 项目台账与工程分解
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu) + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 920202
FROM (SELECT DISTINCT rm.role_id FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id = m.id
  WHERE m.parent_id = 3 AND m.menu_type = 'MENU' AND m.deleted = 0 AND m.path IN ('list', 'wbs')) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = 920202);

-- 3.3 投标管理
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu) + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 920203
FROM (SELECT DISTINCT rm.role_id FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id = m.id
  WHERE m.parent_id = 13 AND m.menu_type = 'MENU' AND m.deleted = 0 AND m.path IN ('register', 'certificate')) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = 920203);

-- 3.4 总包合同与产值
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu) + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 920204
FROM (SELECT DISTINCT rm.role_id FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id = m.id
  WHERE m.parent_id = 4 AND m.menu_type = 'MENU' AND m.deleted = 0
    AND m.path IN ('list', 'output-report', 'change-event')) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = 920204);

-- 3.5 目标成本管理（含 602 预算配置死链行）
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu) + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 920205
FROM (SELECT DISTINCT rm.role_id FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id = m.id
  WHERE m.parent_id = 6 AND m.menu_type = 'MENU' AND m.deleted = 0
    AND m.path IN ('list', 'config', 'change', 'control-config', 'cost-account')) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = 920205);

-- 3.6 劳务班组管理
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu) + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 920206
FROM (SELECT DISTINCT rm.role_id FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id = m.id
  WHERE m.parent_id = 8 AND m.menu_type = 'MENU' AND m.deleted = 0
    AND m.path IN ('contract', 'team', 'roster', 'work-order', 'payroll', 'salary-stats', 'output', 'settlement', 'reward-punish')) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = 920206);

-- 3.7 材料与采购（跨 material/purchase 两目录并集）
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu) + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 920207
FROM (SELECT DISTINCT rm.role_id FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id = m.id
  WHERE m.menu_type = 'MENU' AND m.deleted = 0 AND (
    (m.parent_id = 9 AND m.path IN ('inbound', 'outbound', 'transfer', 'stock', 'inventory', 'refund'))
    OR (m.parent_id = 7 AND m.path IN ('contract', 'settlement', 'inquiry')))) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = 920207);

-- 3.8 机械租赁管理
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu) + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 920208
FROM (SELECT DISTINCT rm.role_id FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id = m.id
  WHERE m.parent_id = 10 AND m.menu_type = 'MENU' AND m.deleted = 0
    AND m.path IN ('contract', 'ledger', 'entry', 'work-log', 'repair', 'settlement')) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = 920208);

-- 3.9 专业分包管理
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu) + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 920209
FROM (SELECT DISTINCT rm.role_id FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id = m.id
  WHERE m.parent_id = 11 AND m.menu_type = 'MENU' AND m.deleted = 0
    AND m.path IN ('contract', 'settlement', 'output')) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = 920209);
