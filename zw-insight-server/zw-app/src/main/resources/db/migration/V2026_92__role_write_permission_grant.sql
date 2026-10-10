-- ============================================================
-- V2026_92__role_write_permission_grant.sql
-- 按「角色负责的模块」授予写权限码（权限授权矩阵）
--
-- 背景：V2026_91 把 54 个后端守卫码补进了菜单目录（可被授予），但**未授予任何角色**，
-- 于是业务角色调用写接口仍是 403 —— 实测项目经理（PROJECT_MANAGER）无法
-- POST /api/v1/contract 建合同。本脚本落地授权矩阵。
--
-- 授权原则：
--   1. 角色在它**已持有视图权限**的模块内获得写权限（模块所有权 → 读写同域）；
--   2. 财务敏感码（银行账户/税率/财务锁定）**仅 FINANCE_STAFF**；
--   3. 系统管理码（system:*）与预算控制配置/成本科目维护**不授予业务角色**
--      （预算控制配置与成本科目属财务/成本管控配置，仅 FINANCE_STAFF 与 COST_BUDGET_STAFF）；
--   4. 只读角色（AUDITOR/COMPANY_LEADER 等）不授予写权限。
--
-- 矩阵（role_code → 权限码前缀；仅作用于 V2026_91 新增的菜单 id 20241-20293）：
--   PROJECT_MANAGER    合同全部 / 预算单+预算变更 / 开票申请
--   COMMERCE_STAFF     合同全部 / 开票申请
--   FINANCE_STAFF      预算全部（含控制配置、成本科目）/ 开票申请 / 银行账户 / 税率 / 财务锁定
--   COST_BUDGET_STAFF  合同全部 / 预算全部
--
-- 说明：按 role_code 而非 role_id 授权，故主环境（90061-90064）与徽颍
--   （102101054153294475 等）的同名角色同时生效，两侧口径一致。
--   徽颍的 PROJECT_MANAGER / FINANCE_STAFF / COST_BUDGET_STAFF 亦将获得写权限
--   （同一缺陷的同一修复）；如需保留只读，回滚语句见文末。
--
-- ID 段：600001-603053（已探针确认 sys_role_menu 中 600000-604000 全段空闲；
--        20101-20114 属 V2026_45_2，20241-20293 属 V2026_91）。
-- 幂等：INSERT 带 (role_id, menu_id) 未存在守卫；可重复执行。
-- 回滚：DELETE FROM sys_role_menu WHERE id BETWEEN 600001 AND 603999;
-- ============================================================

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT g.base + (m.id - 20240), r.id, m.id
FROM (
    SELECT 'PROJECT_MANAGER'   AS role_code, 'contract:'              AS pfx, 600000 AS base UNION ALL
    SELECT 'PROJECT_MANAGER',                 'budget:budget:',               600000 UNION ALL
    SELECT 'PROJECT_MANAGER',                 'budget:budgetchange:',         600000 UNION ALL
    SELECT 'PROJECT_MANAGER',                 'budget:add',                   600000 UNION ALL
    SELECT 'PROJECT_MANAGER',                 'finance:invoiceapply:',        600000 UNION ALL
    SELECT 'COMMERCE_STAFF',                  'contract:',                    601000 UNION ALL
    SELECT 'COMMERCE_STAFF',                  'finance:invoiceapply:',        601000 UNION ALL
    SELECT 'FINANCE_STAFF',                   'budget:',                      602000 UNION ALL
    SELECT 'FINANCE_STAFF',                   'finance:invoiceapply:',        602000 UNION ALL
    SELECT 'FINANCE_STAFF',                   'finance:bankaccount:',         602000 UNION ALL
    SELECT 'FINANCE_STAFF',                   'finance:taxrate:',             602000 UNION ALL
    SELECT 'FINANCE_STAFF',                   'finance:financelock:',         602000 UNION ALL
    SELECT 'COST_BUDGET_STAFF',               'contract:',                    603000 UNION ALL
    SELECT 'COST_BUDGET_STAFF',               'budget:',                      603000
) g
JOIN sys_role r
  ON r.role_code = g.role_code AND r.deleted = 0 AND r.status = 1
JOIN sys_menu m
  ON m.deleted = 0 AND m.status = 1
 AND m.id BETWEEN 20241 AND 20293
 AND (m.permission = g.pfx OR m.permission LIKE CONCAT(g.pfx, '%'))
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id
);
