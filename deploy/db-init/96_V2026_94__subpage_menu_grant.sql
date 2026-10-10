-- ============================================================
-- V2026_94__subpage_menu_grant.sql
-- 子页面 / 按钮菜单下发（20201-20238）
--
-- 背景（2026-10-10 线上权限一致性清查 §8.4）：
--   `V2026_45_2` 建立了细粒度层——4 个子页面菜单 + 17 个按钮（id 20201-20238），
--   但**只 INSERT 给了 SUPER_ADMIN**，业务角色从未绑定。后果：
--   业务角色看不到「WBS结构 / 变更事件 / 成本账户CBS / 成本主线看板」，
--   对应接口一律 403（实测项目经理 `POST /api/v1/contract/change-event` 仍 403）。
--
-- 下发规则（闸门 = **持有该子页面所属模块的一级 DIR 菜单**，比 `{module}:view` 精确：
--   后者被大量授予用于级联下拉，不能代表"该角色负责这个模块"）：
--   DIR 3 项目管理 → 20202 WBS结构 + 20211-20213
--   DIR 4 合同管理 → 20203 变更事件 + 20231-20238
--   DIR 6 预算管理 → 20204 成本账户CBS + 20221-20225
--   20201 成本主线看板（顶层）→ 持有 DIR 4 或 DIR 6 的角色
--
-- 排除项：
--   1. **只读角色** `AUDITOR`（审计查看）、`COMPANY_LEADER`（公司领导）不下发 20201-20238；
--   2. **审批动作码** `20236 contract:changeevent:approve` / `20237 ...:reject` 不下发给业务角色
--      （审批人职责，且与防自审设计相关）——保持 SUPER_ADMIN，后续按需指定审批角色；
--   3. **财务管控码** `20224 budget:costaccount:lock` / `20225 ...:sync` 仅
--      `FINANCE_STAFF` / `COST_BUDGET_STAFF`。
--
-- 幂等：INSERT 带 (role_id, menu_id) 未存在守卫；可重复执行。
-- ID 段：610000-619999（已探针确认空闲；20101-20114 / 20241-20293 / 600001-603999 为既有段）。
-- 回滚：DELETE FROM sys_role_menu WHERE id BETWEEN 610000 AND 619999;
-- ============================================================

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 610000 + ROW_NUMBER() OVER (ORDER BY t.role_id, t.menu_id), t.role_id, t.menu_id
FROM (
    SELECT DISTINCT prm.role_id, g.menu_id
    FROM (
        -- 子页面/按钮 → 闸门 DIR 菜单（20201 双行：DIR 4 或 DIR 6）
        SELECT 20201 AS menu_id, 4 AS dir_id UNION ALL
        SELECT 20201,            6          UNION ALL
        SELECT 20202,            3          UNION ALL
        SELECT 20211,            3          UNION ALL
        SELECT 20212,            3          UNION ALL
        SELECT 20213,            3          UNION ALL
        SELECT 20203,            4          UNION ALL
        SELECT 20231,            4          UNION ALL
        SELECT 20232,            4          UNION ALL
        SELECT 20233,            4          UNION ALL
        SELECT 20234,            4          UNION ALL
        SELECT 20235,            4          UNION ALL
        SELECT 20236,            4          UNION ALL
        SELECT 20237,            4          UNION ALL
        SELECT 20238,            4          UNION ALL
        SELECT 20204,            6          UNION ALL
        SELECT 20221,            6          UNION ALL
        SELECT 20222,            6          UNION ALL
        SELECT 20223,            6          UNION ALL
        SELECT 20224,            6          UNION ALL
        SELECT 20225,            6
    ) g
    JOIN sys_role_menu prm ON prm.menu_id = g.dir_id
    JOIN sys_role r
      ON r.id = prm.role_id AND r.deleted = 0 AND r.status = 1
     AND r.role_code NOT IN ('AUDITOR', 'COMPANY_LEADER')
    WHERE g.menu_id NOT IN (20224, 20225, 20236, 20237)

    UNION ALL

    -- 财务管控码（成本账户锁定/同步）仅财务与成本预算角色
    SELECT r.id, m.id
    FROM sys_role r
    JOIN (SELECT 20224 AS id UNION ALL SELECT 20225) m
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code IN ('FINANCE_STAFF', 'COST_BUDGET_STAFF')
) t
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = t.menu_id
);
