-- ============================================================
-- V2026_120__finance_menu_consolidation.sql
-- 双轨镜像：内容必须与 deploy/db-init/123_V2026_120__finance_menu_consolidation.sql 一致
-- 背景：财务资金域 29 个二级菜单平铺收敛为 8 个一等入口（Hub 页内 Tab），
--       对应批准方案「7 大业务组 + 财务设置下沉组」。前端入口路由见
--       zw-insight-web/src/router/index.ts 的 /finance/hub/*（FinanceHubPage.vue 壳）。
-- 机制安全性（2026-10-19 代码实证）：
--   ① 权限码 SQL（SysUserMapper.selectPermissionsByUserId）不检查 hidden，
--      菜单行 hidden=1 后权限码照常下发，接口不 403；
--   ② 侧边栏授权集（navigation.ts getAuthorizedLeafPaths）剔除 hidden 菜单行，
--      router 侧 meta.hidden 双保险；原 29 条路由全部保留，URL 直达（含 ?projectId=）不受影响；
--   ③ 被收敛菜单的 BUTTON 行不动（权限码载体不变）。
-- 幂等：UPDATE 同值无害；INSERT IGNORE 撞主键静默跳过；绑定复制带 NOT EXISTS 守卫。
-- 探针（推送前已完成，双机）：920101-920120 段两机均 0 行；财务域菜单 tenant_id 均为 NULL。
-- 回滚：UPDATE sys_menu SET hidden=0 WHERE parent_id=5 AND menu_type='MENU';
--       UPDATE sys_menu SET hidden=1 WHERE id BETWEEN 920101 AND 920108;
--       DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id=m.id
--         WHERE m.id BETWEEN 920101 AND 920108;
-- ============================================================

-- 1) 原 29 个财务二级菜单退出侧边栏（保留行、permission、BUTTON 与角色绑定）
UPDATE sys_menu SET hidden = 1
WHERE parent_id = 5 AND menu_type = 'MENU' AND deleted = 0;

-- 2) 8 个一等入口菜单（固定 id 920101-920108，两机已探针空闲；tenant_id=NULL 全局菜单）
--    元组前五列顺序 (id, menu_name, menu_type, parent_id, path) 与既有种子格式一致
INSERT IGNORE INTO sys_menu
  (id, menu_name, menu_type, parent_id, path, icon, sort_order, permission, status, hidden, weight, tenant_id)
VALUES
  (920101, '工程应收与结算', 'MENU', 5, '/finance/hub/receivable-group', 'Money', 1, NULL, 1, 0, 'NORMAL', NULL),
  (920102, '工程应付与支出', 'MENU', 5, '/finance/hub/payable-group', 'CreditCard', 2, NULL, 1, 0, 'NORMAL', NULL),
  (920103, '日常报销与备用金', 'MENU', 5, '/finance/hub/reimbursement-group', 'WalletFilled', 3, NULL, 1, 0, 'NORMAL', NULL),
  (920104, '发票税务台账', 'MENU', 5, '/finance/hub/invoice-group', 'Tickets', 4, NULL, 1, 0, 'NORMAL', NULL),
  (920105, '银行账户与流水', 'MENU', 5, '/finance/hub/bank-group', 'Coin', 5, NULL, 1, 0, 'NORMAL', NULL),
  (920106, '专项资金与金融', 'MENU', 5, '/finance/hub/special-fund-group', 'GoldMedal', 6, NULL, 1, 0, 'NORMAL', NULL),
  (920107, '资金计划与经营分析', 'MENU', 5, '/finance/hub/plan-group', 'Calendar', 7, NULL, 1, 0, 'NORMAL', NULL),
  (920108, '财务基础设置', 'MENU', 5, '/finance/hub/settings-group', 'Setting', 8, NULL, 1, 0, 'NORMAL', NULL);

-- 3) 角色绑定复制：入口可见性 = 组内任一原菜单有绑定的角色（防「角色只绑部分子菜单」
--    在收敛后整组消失的权限丢失）。id 用 MAX+ROW_NUMBER（禁 role_id 乘法——雪花溢出教训）。
--    两机角色集不同（主 5 角色/徽颍 10 角色），按各机实际绑定行复制，天然正确。

-- 3.1 工程应收与结算 ← settlement/receivable/invoice-apply/payment-received
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu) + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 920101
FROM (
  SELECT DISTINCT rm.role_id FROM sys_role_menu rm
  JOIN sys_menu m ON rm.menu_id = m.id
  WHERE m.parent_id = 5 AND m.menu_type = 'MENU' AND m.deleted = 0
    AND m.path IN ('settlement', 'receivable', 'invoice-apply', 'payment-received')
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = 920101);

-- 3.2 工程应付与支出 ← payment-apply/payable/other-payment
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu) + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 920102
FROM (
  SELECT DISTINCT rm.role_id FROM sys_role_menu rm
  JOIN sys_menu m ON rm.menu_id = m.id
  WHERE m.parent_id = 5 AND m.menu_type = 'MENU' AND m.deleted = 0
    AND m.path IN ('payment-apply', 'payable', 'other-payment')
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = 920102);

-- 3.3 日常报销与备用金 ← project-reimbursement/personal-reimbursement/reserve-fund
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu) + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 920103
FROM (
  SELECT DISTINCT rm.role_id FROM sys_role_menu rm
  JOIN sys_menu m ON rm.menu_id = m.id
  WHERE m.parent_id = 5 AND m.menu_type = 'MENU' AND m.deleted = 0
    AND m.path IN ('project-reimbursement', 'personal-reimbursement', 'reserve-fund')
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = 920103);

-- 3.4 发票税务台账 ← invoice-received/invoice-issued/invoice-summary
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu) + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 920104
FROM (
  SELECT DISTINCT rm.role_id FROM sys_role_menu rm
  JOIN sys_menu m ON rm.menu_id = m.id
  WHERE m.parent_id = 5 AND m.menu_type = 'MENU' AND m.deleted = 0
    AND m.path IN ('invoice-received', 'invoice-issued', 'invoice-summary')
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = 920104);

-- 3.5 银行账户与流水 ← bank-account-group/bank-flow/balance-reconciliation
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu) + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 920105
FROM (
  SELECT DISTINCT rm.role_id FROM sys_role_menu rm
  JOIN sys_menu m ON rm.menu_id = m.id
  WHERE m.parent_id = 5 AND m.menu_type = 'MENU' AND m.deleted = 0
    AND m.path IN ('bank-account-group', 'bank-flow', 'balance-reconciliation')
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = 920105);

-- 3.6 专项资金与金融 ← security-bond/retention/wage-account/bill/financing
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu) + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 920106
FROM (
  SELECT DISTINCT rm.role_id FROM sys_role_menu rm
  JOIN sys_menu m ON rm.menu_id = m.id
  WHERE m.parent_id = 5 AND m.menu_type = 'MENU' AND m.deleted = 0
    AND m.path IN ('security-bond', 'retention', 'wage-account', 'bill', 'financing')
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = 920106);

-- 3.7 资金计划与经营分析 ← fund-plan/daily-cash-report/fund-dashboard/monthly-analysis/entertainment-analysis
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu) + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 920107
FROM (
  SELECT DISTINCT rm.role_id FROM sys_role_menu rm
  JOIN sys_menu m ON rm.menu_id = m.id
  WHERE m.parent_id = 5 AND m.menu_type = 'MENU' AND m.deleted = 0
    AND m.path IN ('fund-plan', 'daily-cash-report', 'fund-dashboard', 'monthly-analysis', 'entertainment-analysis')
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = 920107);

-- 3.8 财务基础设置 ← fund-category/tax-rate/finance-lock
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu) + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 920108
FROM (
  SELECT DISTINCT rm.role_id FROM sys_role_menu rm
  JOIN sys_menu m ON rm.menu_id = m.id
  WHERE m.parent_id = 5 AND m.menu_type = 'MENU' AND m.deleted = 0
    AND m.path IN ('fund-category', 'tax-rate', 'finance-lock')
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = 920108);
