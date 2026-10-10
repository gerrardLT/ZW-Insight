-- ============================================================
-- V2026_93__demo_role_data_scope_and_membership.sql
-- 修正演示角色数据范围（全 SELF → 按角色语义）+ 补齐演示成员的项目归属
--
-- 背景（2026-10-10 线上权限一致性清查 F5，见 audit-reports/permission-consistency-2026-10-10.md §8.3）：
--   `V2026_26__seed_demo_data.sql` 建 5 个演示角色时**未写 data_scope 列**，
--   落到 `V2026_10` 的 `DEFAULT 'SELF'`；而演示业务单据的 created_by 全是 admin(1)，
--   于是 5 个业务角色在**所有** @DataPermission 表上可见行数均为 0 ——
--   他们持有 44/41/13/20/2 个业务菜单，点进去却全是空列表。
--
-- 目标（对齐徽颍同名角色语义 + 「项目为中心」的数据模型）：
--   PROJECT_MANAGER  → PROJECT   看自己参与项目的全部单据
--   COMMERCE_STAFF   → PROJECT   商务跟项目
--   MATERIAL_STAFF   → PROJECT   材料员跟项目
--   FINANCE_STAFF    → ALL       财务需全公司口径
--   STAFF            → SELF      不变
--
-- 守卫：仅当当前值仍为 'SELF'（即种子默认值）时才更新，避免覆盖人工调整。
--   徽颍的同名角色（PROJECT_MANAGER=PROJECT / FINANCE_STAFF=ALL / STAFF=SELF）
--   已等于目标值或不为 SELF，故为 no-op；徽颍无 MATERIAL_STAFF / COMMERCE_STAFF。
--
-- 成员补齐：`PROJECT` 范围按 `sys_user_project` 过滤，而演示用户 zhaolei（商务人员）
--   在种子中**没有任何项目归属**（种子只有 90091-90096 六行），改为 PROJECT 后将看到 0 行。
--   按演示剧本补：90001（滨江花园一期，施工中，含合同/采购）+ 90003（高新区产业园二期，投标，含投标登记）
--   —— 与商务人员「合同 + 投标 + 采购」职能对应。
--
-- ID 段：sys_user_project 主键**非自增**，且线上 id 混用「小号种子段」与「雪花 ID」——
--        V2026_26 已占用 90091-90098，固定 id 会撞号并被 NOT EXISTS 守卫**静默跳过**
--        （初版用 90097 即踩此坑），故改取 MAX(id)+1（每条语句只插 1 行，不会互相冲突）。
-- 幂等：UPDATE 带值守卫；INSERT 带 (user_id, project_id) 守卫。
-- 回滚：
--   UPDATE sys_role SET data_scope='SELF' WHERE role_code IN
--     ('PROJECT_MANAGER','COMMERCE_STAFF','MATERIAL_STAFF','FINANCE_STAFF');
--   DELETE up FROM sys_user_project up
--     JOIN sys_user u ON u.id = up.user_id
--     JOIN biz_project p ON p.id = up.project_id
--    WHERE u.username = 'zhaolei' AND p.project_code IN ('PRJ20260101001', 'PRJ20260301001');
-- ============================================================

-- 1) 演示角色数据范围（仅修正仍为种子默认 SELF 的行）
UPDATE sys_role SET data_scope = 'PROJECT', updated_at = NOW()
 WHERE role_code = 'PROJECT_MANAGER' AND data_scope = 'SELF' AND deleted = 0;

UPDATE sys_role SET data_scope = 'PROJECT', updated_at = NOW()
 WHERE role_code = 'COMMERCE_STAFF' AND data_scope = 'SELF' AND deleted = 0;

UPDATE sys_role SET data_scope = 'PROJECT', updated_at = NOW()
 WHERE role_code = 'MATERIAL_STAFF' AND data_scope = 'SELF' AND deleted = 0;

UPDATE sys_role SET data_scope = 'ALL', updated_at = NOW()
 WHERE role_code = 'FINANCE_STAFF' AND data_scope = 'SELF' AND deleted = 0;

-- 2) 补齐 zhaolei 的项目归属（按 username + project_code 定位，跨环境可用）
--    id 取 MAX(id)+1：sys_user_project 主键非自增，且线上 id 混用「小号种子段」与「雪花 ID」，
--    固定 id 会撞号并被 NOT EXISTS 守卫**静默跳过**（本迁移初版用 90097 即踩此坑，
--    主环境 90097 已被占用）。每条语句只插 1 行，故 MAX+1 不会互相冲突。
INSERT INTO sys_user_project (id, user_id, project_id, tenant_id, created_at)
SELECT (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_user_project), u.id, p.id, u.tenant_id, NOW()
  FROM sys_user u
  JOIN biz_project p ON p.project_code = 'PRJ20260101001' AND p.deleted = 0 AND p.tenant_id = u.tenant_id
 WHERE u.username = 'zhaolei' AND u.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM sys_user_project x WHERE x.user_id = u.id AND x.project_id = p.id);

INSERT INTO sys_user_project (id, user_id, project_id, tenant_id, created_at)
SELECT (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_user_project), u.id, p.id, u.tenant_id, NOW()
  FROM sys_user u
  JOIN biz_project p ON p.project_code = 'PRJ20260301001' AND p.deleted = 0 AND p.tenant_id = u.tenant_id
 WHERE u.username = 'zhaolei' AND u.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM sys_user_project x WHERE x.user_id = u.id AND x.project_id = p.id);
