-- ============================================================
-- 128_V2026_125__expense_revision_write_permission.sql
-- 双轨镜像：内容必须与 zw-insight-server/zw-app/src/main/resources/db/migration/V2026_125__expense_revision_write_permission.sql 一致
--
-- 背景（H3 权限治理，2026-10-20 审阅登记项）：
--   V2026_123 的四类支出合同修订入口按「模块 view 码 OR」放行，
--   Service 层虽按 contractCategory 二次校验，但校验的仍是 view 码，
--   导致只读角色（持 purchase:view 等）也能发起/作废/提交修订。
--   本迁移新增四个修订写权限码，仅授予具备合同管理职责的角色：
--     purchase:contract:edit     采购合同修订（商务、材料）
--     labor:contract:edit        劳务合同修订（商务）
--     machine:contract:edit      机械合同修订（商务）
--     subcontract:contract:edit  分包合同修订（商务、超管）
--   施工合同修订沿用既有 contract:contract:edit / contract:contract:submit，不重复授权。
--
-- 执行前探针（必须对双机真库各执行一次并留痕，2026-10-20 已执行：两机均为 0）：
--   SELECT COUNT(*) FROM sys_menu WHERE id BETWEEN 921901 AND 921904;  -- 必须 0
--   SELECT id, permission FROM sys_menu WHERE id IN (7,8,10,11);      -- 应为四类模块 view 目录
--
-- 幂等：菜单与角色绑定均为「不存在才插入」（NOT EXISTS 双守卫），可重复执行；
--       双轨（Flyway + deploy/db-init 手工灌入）并存时不会主键冲突阻断启动。
--       id 段占用风险由上方探针把关（两机已执行均为 0）。
-- 双机：公共授权变更，不区分主机，无需更新双机差异台账。
-- ============================================================

-- 1) 四个按钮级权限码（隐藏菜单，仅用于权限下发）
INSERT INTO sys_menu
  (id, menu_name, menu_type, parent_id, permission, status, hidden, weight)
SELECT * FROM (
  SELECT 921901 AS id, '采购合同修订' AS menu_name, 'BUTTON' AS menu_type, 7 AS parent_id,
         'purchase:contract:edit' AS permission, 1 AS status, 1 AS hidden, 'NORMAL' AS weight
  UNION ALL SELECT 921902, '劳务合同修订', 'BUTTON', 8,  'labor:contract:edit',       1, 1, 'NORMAL'
  UNION ALL SELECT 921903, '机械合同修订', 'BUTTON', 10, 'machine:contract:edit',     1, 1, 'NORMAL'
  UNION ALL SELECT 921904, '分包合同修订', 'BUTTON', 11, 'subcontract:contract:edit', 1, 1, 'NORMAL'
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = t.id);

-- 2) 角色绑定（role_code 定位，避免雪花 role_id 硬编码）
--    SUPER_ADMIN：四类全量（超管亦持有 *:*:*，此处补齐便于权限页可视化）
--    COMMERCE_STAFF：商务统管四类支出合同修订
--    MATERIAL_STAFF：材料仅采购合同修订
--    PROJECT_MANAGER：现场劳务/机械合同修订（成本类采购/分包仍归商务，防越权改价）
--    COST_BUDGET_STAFF（部分环境存在）：采购/分包合同修订（成本口径）
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu)
       + ROW_NUMBER() OVER (ORDER BY t.role_code, t.menu_id), r.id, t.menu_id
FROM (
  SELECT 'SUPER_ADMIN' AS role_code, 921901 AS menu_id UNION ALL
  SELECT 'SUPER_ADMIN', 921902 UNION ALL
  SELECT 'SUPER_ADMIN', 921903 UNION ALL
  SELECT 'SUPER_ADMIN', 921904 UNION ALL
  SELECT 'COMMERCE_STAFF', 921901 UNION ALL
  SELECT 'COMMERCE_STAFF', 921902 UNION ALL
  SELECT 'COMMERCE_STAFF', 921903 UNION ALL
  SELECT 'COMMERCE_STAFF', 921904 UNION ALL
  SELECT 'MATERIAL_STAFF', 921901 UNION ALL
  SELECT 'PROJECT_MANAGER', 921902 UNION ALL
  SELECT 'PROJECT_MANAGER', 921903 UNION ALL
  SELECT 'COST_BUDGET_STAFF', 921901 UNION ALL
  SELECT 'COST_BUDGET_STAFF', 921904
) t
JOIN sys_role r ON r.role_code = t.role_code AND r.deleted = 0 AND r.status = 1
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = t.menu_id
);

-- ============================================================
-- 回滚：
-- DELETE rm FROM sys_role_menu rm JOIN sys_role r ON rm.role_id = r.id
--  WHERE r.role_code IN ('SUPER_ADMIN','COMMERCE_STAFF','MATERIAL_STAFF','PROJECT_MANAGER','COST_BUDGET_STAFF')
--    AND rm.menu_id IN (921901,921902,921903,921904);
-- DELETE FROM sys_menu WHERE id IN (921901,921902,921903,921904);
-- ============================================================
