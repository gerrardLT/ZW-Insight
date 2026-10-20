-- ============================================================
-- V2026_124__role_menu_gap_and_payment_permission.sql
-- 双轨镜像：内容必须与 deploy/db-init/127_V2026_124__role_menu_gap_and_payment_permission.sql 一致
--
-- 背景（2026-10-20 菜单与一级页面多角色分级评审）：
--   一、V2026_121 Hub 化按「原子菜单绑定并集」复制入口，而业务角色历史上多只授 DIR、
--       未授叶子菜单，导致重构后多个职责入口消失：
--         PROJECT_MANAGER  缺 现场管理 / 专业分包 / 消息中心 / 审批中心
--         COMMERCE_STAFF   缺 投标管理 / 消息中心 / 审批中心
--         FINANCE_STAFF    缺 消息中心 / 审批中心
--         MATERIAL_STAFF   缺 消息中心 / 审批中心
--         STAFF            仅有首页（消息中心不可见）
--   二、项目经理需要发起付款申请，但 PaymentApplyController 类级要求 finance:view，
--       直接授予会把财务域全部只读接口一并放开。故新增细粒度权限码
--       finance:paymentapply:{view,add,edit,delete,submit}，并在 Controller 方法级覆盖。
--   三、审批入口不复用 workflow:view（该码同时放开流程部署/上传等写接口），
--       新增 workflow:approval:view + 顶级菜单 921002（绝对路径，无需授 DIR 16）。
--       注意：ApprovalController 自 2026-08-21 起因跨业务域审批被设为权限豁免，
--       workflow:approval:view 仅控制前端路由/菜单可见性，不构成服务端鉴权；
--       服务端边界仍是 Flowable 任务参与人 + 租户隔离 + 数据权限。
--
-- 执行前探针（必须执行并留痕）：
--   SELECT id, menu_name FROM sys_menu WHERE id BETWEEN 921001 AND 921199;   -- 必须 0 行
--   SELECT id FROM sys_menu WHERE id IN (3,11,12,13,17,1704,920201,920203,920205,920209,910070,910072,910073,20081);
--   -- 第二段应全部命中；缺失说明目标库未应用 V2026_121 或菜单种子不完整，先停止
--   SELECT role_code, data_scope FROM sys_role WHERE role_code = 'PROJECT_MANAGER' AND deleted = 0;
--   -- 期望 PROJECT；若为 SELF，项目经理会因项目不可见而无法发起付款（V2026_93 应已修正）
--
-- 预期可见入口（仓库口径）：
--   PROJECT_MANAGER 10→19 / FINANCE_STAFF 13→15 / COMMERCE_STAFF 6→9
--   MATERIAL_STAFF 2→4 / STAFF 1→3 / COMPANY_LEADER・AUDITOR 0→4
--
-- 幂等：INSERT IGNORE + (role_id, menu_id) NOT EXISTS 双守卫；可重复执行。
-- 双机：本迁移为公共授权变更，不区分主机，无需更新 docs/双机功能与预设数据差异台账.md。
-- ============================================================

-- 1) 新增菜单与权限码目录（id 段 921001-921199；执行前须按上方探针确认空闲）
INSERT IGNORE INTO sys_menu
  (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, permission, status, hidden, weight, tenant_id)
VALUES
  (921001, '项目付款申请', 'MENU', 3, '/project/payment-apply', 'views/finance/payment-apply', 'CreditCard', 2,
   'finance:paymentapply:view', 1, 0, 'NORMAL', NULL),
  -- 不授 SUPER_ADMIN：超管沿用 1604「审批管理」，避免同一 path 在侧边栏重复
  (921002, '审批中心', 'MENU', 0, '/workflow/approval', 'views/workflow/approval/index', 'Checked', 91,
   'workflow:approval:view', 1, 0, 'NORMAL', NULL);

INSERT IGNORE INTO sys_menu
  (id, menu_name, menu_type, parent_id, permission, status, hidden, weight)
VALUES
  (921101, '付款申请查看', 'BUTTON', 503, 'finance:paymentapply:view', 1, 1, 'NORMAL'),
  (921102, '付款申请新增', 'BUTTON', 503, 'finance:paymentapply:add', 1, 1, 'NORMAL'),
  (921103, '付款申请编辑', 'BUTTON', 503, 'finance:paymentapply:edit', 1, 1, 'NORMAL'),
  (921104, '付款申请删除', 'BUTTON', 503, 'finance:paymentapply:delete', 1, 1, 'NORMAL'),
  (921105, '付款申请提交', 'BUTTON', 503, 'finance:paymentapply:submit', 1, 1, 'NORMAL');

-- 2) 角色授权（按 role_code，跨角色 id 分叉安全；不存在则不写悬空绑定）
-- id 统一取 MAX(id)+ROW_NUMBER，禁止 role_id*100000000：徽颍角色 id 为雪花值（约 1e17），
-- 乘 1e8 会溢出 BIGINT（V2026_119 的公式在雪花角色环境下不可用）。
-- 2.1 项目经理：补齐现场/分包/消息/审批/项目付款；purchase:view 只读，供付款单合同下拉使用
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu)
       + ROW_NUMBER() OVER (ORDER BY t.role_id, t.menu_id), t.role_id, t.menu_id
FROM (
  SELECT r.id AS role_id, g.menu_id
  FROM (
    SELECT 'PROJECT_MANAGER' AS role_code, 1201 AS menu_id UNION ALL
    SELECT 'PROJECT_MANAGER', 1202 UNION ALL
    SELECT 'PROJECT_MANAGER', 1203 UNION ALL
    SELECT 'PROJECT_MANAGER', 1204 UNION ALL
    SELECT 'PROJECT_MANAGER', 910100 UNION ALL
    SELECT 'PROJECT_MANAGER', 920209 UNION ALL
    SELECT 'PROJECT_MANAGER', 1704    UNION ALL
    SELECT 'PROJECT_MANAGER', 921002  UNION ALL
    SELECT 'PROJECT_MANAGER', 921001  UNION ALL
    SELECT 'PROJECT_MANAGER', 7       UNION ALL
    SELECT 'PROJECT_MANAGER', 921101  UNION ALL
    SELECT 'PROJECT_MANAGER', 921102  UNION ALL
    SELECT 'PROJECT_MANAGER', 921103  UNION ALL
    SELECT 'PROJECT_MANAGER', 921104  UNION ALL
    SELECT 'PROJECT_MANAGER', 921105
  ) g
  JOIN sys_role r ON r.role_code = g.role_code AND r.deleted = 0 AND r.status = 1
  JOIN sys_menu m ON m.id = g.menu_id AND m.deleted = 0
  WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = g.menu_id
  )
    -- 已持有 1604「审批管理」的角色不再授 921002，避免同一 path 在侧边栏重复
    AND NOT (g.menu_id = 921002 AND EXISTS (
      SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = 1604
    ))
) t;

-- 2.2 商务：投标 Hub（原子 1301/1302 已 hidden，必须直接授 Hub 920203）+ 消息 + 审批
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu)
       + ROW_NUMBER() OVER (ORDER BY t.role_id, t.menu_id), t.role_id, t.menu_id
FROM (
  SELECT r.id AS role_id, g.menu_id
  FROM (
    SELECT 'COMMERCE_STAFF' AS role_code, 920203 AS menu_id UNION ALL
    SELECT 'COMMERCE_STAFF', 1704    UNION ALL
    SELECT 'COMMERCE_STAFF', 921002
  ) g
  JOIN sys_role r ON r.role_code = g.role_code AND r.deleted = 0 AND r.status = 1
  JOIN sys_menu m ON m.id = g.menu_id AND m.deleted = 0
  WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = g.menu_id
  )
    AND NOT (g.menu_id = 921002 AND EXISTS (
      SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = 1604
    ))
) t;

-- 2.3 财务：消息 + 审批 + 付款细粒度码
--     查询/新增/编辑/删除保留 finance:view 作 OR 兼容分支；submit 为
--     finance:payment:submit 与 finance:paymentapply:submit 双通道。
--     20081 = finance:payment:submit（标记支付/撤销/批量）；V2026_34 只绑了超管，
--     财务角色此前看得到按钮但点击 403，本迁移补齐为财务职责权限。
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu)
       + ROW_NUMBER() OVER (ORDER BY t.role_id, t.menu_id), t.role_id, t.menu_id
FROM (
  SELECT r.id AS role_id, g.menu_id
  FROM (
    SELECT 'FINANCE_STAFF' AS role_code, 1704 AS menu_id UNION ALL
    SELECT 'FINANCE_STAFF', 921002 UNION ALL
    SELECT 'FINANCE_STAFF', 921101 UNION ALL
    SELECT 'FINANCE_STAFF', 921102 UNION ALL
    SELECT 'FINANCE_STAFF', 921103 UNION ALL
    SELECT 'FINANCE_STAFF', 921104 UNION ALL
    SELECT 'FINANCE_STAFF', 921105 UNION ALL
    SELECT 'FINANCE_STAFF', 20081
  ) g
  JOIN sys_role r ON r.role_code = g.role_code AND r.deleted = 0 AND r.status = 1
  JOIN sys_menu m ON m.id = g.menu_id AND m.deleted = 0
  WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = g.menu_id
  )
    AND NOT (g.menu_id = 921002 AND EXISTS (
      SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = 1604
    ))
) t;

-- 2.4 材料：消息 + 审批
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu)
       + ROW_NUMBER() OVER (ORDER BY t.role_id, t.menu_id), t.role_id, t.menu_id
FROM (
  SELECT r.id AS role_id, g.menu_id
  FROM (
    SELECT 'MATERIAL_STAFF' AS role_code, 1704 AS menu_id UNION ALL
    SELECT 'MATERIAL_STAFF', 921002
  ) g
  JOIN sys_role r ON r.role_code = g.role_code AND r.deleted = 0 AND r.status = 1
  JOIN sys_menu m ON m.id = g.menu_id AND m.deleted = 0
  WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = g.menu_id
  )
    AND NOT (g.menu_id = 921002 AND EXISTS (
      SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = 1604
    ))
) t;

-- 2.5 普通员工：消息 + 审批（首页之外至少可用）
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu)
       + ROW_NUMBER() OVER (ORDER BY t.role_id, t.menu_id), t.role_id, t.menu_id
FROM (
  SELECT r.id AS role_id, g.menu_id
  FROM (
    SELECT 'STAFF' AS role_code, 1704 AS menu_id UNION ALL
    SELECT 'STAFF', 921002
  ) g
  JOIN sys_role r ON r.role_code = g.role_code AND r.deleted = 0 AND r.status = 1
  JOIN sys_menu m ON m.id = g.menu_id AND m.deleted = 0
  WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = g.menu_id
  )
    AND NOT (g.menu_id = 921002 AND EXISTS (
      SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = 1604
    ))
) t;

-- 2.6 只读角色（徽颍存在 COMPANY_LEADER / AUDITOR）：经营驾驶舱只读入口
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu)
       + ROW_NUMBER() OVER (ORDER BY t.role_id, t.menu_id), t.role_id, t.menu_id
FROM (
  SELECT r.id AS role_id, g.menu_id
  FROM (
    SELECT 'COMPANY_LEADER' AS role_code, 910070 AS menu_id UNION ALL
    SELECT 'COMPANY_LEADER', 920201 UNION ALL
    SELECT 'COMPANY_LEADER', 910072 UNION ALL
    SELECT 'COMPANY_LEADER', 910073 UNION ALL
    SELECT 'AUDITOR', 910070 UNION ALL
    SELECT 'AUDITOR', 920201 UNION ALL
    SELECT 'AUDITOR', 910072 UNION ALL
    SELECT 'AUDITOR', 910073
  ) g
  JOIN sys_role r ON r.role_code = g.role_code AND r.deleted = 0 AND r.status = 1
  JOIN sys_menu m ON m.id = g.menu_id AND m.deleted = 0
  WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = g.menu_id
  )
) t;

-- 2.7 成本预算角色（徽颍存在 COST_BUDGET_STAFF）：目标成本 Hub 入口
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu)
       + ROW_NUMBER() OVER (ORDER BY t.role_id, t.menu_id), t.role_id, t.menu_id
FROM (
  SELECT r.id AS role_id, g.menu_id
  FROM (
    SELECT 'COST_BUDGET_STAFF' AS role_code, 6 AS menu_id UNION ALL
    SELECT 'COST_BUDGET_STAFF', 920205
  ) g
  JOIN sys_role r ON r.role_code = g.role_code AND r.deleted = 0 AND r.status = 1
  JOIN sys_menu m ON m.id = g.menu_id AND m.deleted = 0
  WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = g.menu_id
  )
) t;

-- 2.8 超级管理员：仅新增项目付款申请入口（审批中心沿用 1604，避免同 path 重复）
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu)
       + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, t.menu_id
FROM (
  SELECT r.id AS role_id, 921001 AS menu_id
  FROM sys_role r
  WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0 AND r.status = 1
    AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = 921001)
) t;

-- ============================================================
-- 回滚（按 role_code + 本迁移新增 menu_id 精确删除，再删菜单行）：
-- DELETE rm FROM sys_role_menu rm JOIN sys_role r ON rm.role_id = r.id
--  WHERE r.role_code IN ('PROJECT_MANAGER','COMMERCE_STAFF','FINANCE_STAFF','MATERIAL_STAFF','STAFF',
--                        'COMPANY_LEADER','AUDITOR','COST_BUDGET_STAFF')
--    AND rm.menu_id IN (1201,1202,1203,1204,910100,920203,920209,1704,910070,910072,910073,920201,920205,6,7,
--                       20081,921001,921002,921101,921102,921103,921104,921105);
-- DELETE rm FROM sys_role_menu rm JOIN sys_role r ON rm.role_id = r.id
--  WHERE r.role_code = 'SUPER_ADMIN' AND rm.menu_id = 921001;
-- DELETE FROM sys_menu WHERE id IN (921001,921002,921101,921102,921103,921104,921105);
-- ============================================================
