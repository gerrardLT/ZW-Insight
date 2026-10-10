-- ============================================================
-- V2026_91__permission_guard_catalog_completion.sql
-- 权限码目录完备性补齐：把后端 @RequiresPermission 要求、但 sys_menu 中
-- 从未登记的 54 个权限码补进目录。
--
-- 背景（2026-10-10 线上权限一致性清查，见 audit-reports/permission-consistency-2026-10-10.md F1）：
--   PermissionInterceptor 的授权集合来自 sys_menu.permission ⋈ sys_role_menu
--   （SysUserMapper.selectPermissionsByUserId），因此**菜单目录里不存在的权限码，
--   任何非 SUPER_ADMIN 角色都无法获得，管理员也无法在「角色管理」里勾选授予**。
--   实测：后端守卫码 132 个，活跃菜单只提供 83 个，差 54 个 →
--   POST /api/v1/contract、POST /api/v1/finance/invoice-apply、
--   GET /api/v1/system/version/current 等接口对业务角色一律 403。
--
-- 本次范围：**只补目录，不改任何角色现有授权**（是否把写权限授予业务角色属产品授权策略，
-- 由管理员在「角色管理」中自行勾选；本次仅把 SUPER_ADMIN 登记为目录完整性，与
-- V2026_45_2 的既有做法一致）。
--
-- 另一处口径修正：版本管理菜单(id=211)登记的是 system:version:list，
--   而 VersionController 类级要求的是 system:version:view，且全库无任何接口要求
--   system:version:list（实测 0 处）→ 该菜单码为死码，页面永远 403。此处对齐为 view。
--
-- ID 段：20241-20293（已探针确认 sys_menu 中 20241-20299 全段空闲；20101-20199 属 V2026_45_2，
--        20201-20238 属后续补录）。sys_role_menu 复用同一段 ID。
-- 幂等：INSERT 带「同 id 或 同 permission 未删除」双守卫；UPDATE 可重复执行。
-- 回滚：
--   DELETE FROM sys_role_menu WHERE menu_id BETWEEN 20241 AND 20293;
--   DELETE FROM sys_menu WHERE id BETWEEN 20241 AND 20293;
--   UPDATE sys_menu SET permission='system:version:list' WHERE id=211;
-- ============================================================

-- 1) 版本管理菜单权限码对齐（死码 system:version:list → 实际守卫 system:version:view）
UPDATE sys_menu SET permission = 'system:version:view', updated_at = NOW()
WHERE id = 211 AND deleted = 0;

-- 2) 补齐 53 个写操作权限码的 BUTTON 目录（挂对应一级目录；hidden=1 不进侧边栏）
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, permission, status, hidden, weight, created_at, updated_at, deleted, version)
SELECT t.id, t.menu_name, 'BUTTON', t.parent_id, t.permission, 1, 1, 'NORMAL', NOW(), NOW(), 0, 0
FROM (
  -- 预算管理 (parent 6)
  SELECT 20241 AS id, '预算新增'         AS menu_name, 6 AS parent_id, 'budget:add'                      AS permission UNION ALL
  SELECT 20242, '预算单新增',       6, 'budget:budget:add'                UNION ALL
  SELECT 20243, '预算单编辑',       6, 'budget:budget:edit'               UNION ALL
  SELECT 20244, '预算单删除',       6, 'budget:budget:delete'             UNION ALL
  SELECT 20245, '预算单提交',       6, 'budget:budget:submit'             UNION ALL
  SELECT 20246, '预算变更新增',     6, 'budget:budgetchange:add'          UNION ALL
  SELECT 20247, '预算变更编辑',     6, 'budget:budgetchange:edit'         UNION ALL
  SELECT 20248, '预算变更删除',     6, 'budget:budgetchange:delete'       UNION ALL
  SELECT 20249, '预算变更提交',     6, 'budget:budgetchange:submit'       UNION ALL
  SELECT 20250, '预算变更撤回',     6, 'budget:budgetchange:withdraw'     UNION ALL
  SELECT 20251, '预算控制配置新增', 6, 'budget:budgetcontrolconfig:add'   UNION ALL
  SELECT 20252, '预算控制配置编辑', 6, 'budget:budgetcontrolconfig:edit'  UNION ALL
  SELECT 20253, '预算控制配置删除', 6, 'budget:budgetcontrolconfig:delete'UNION ALL
  SELECT 20254, '成本账户更新',     6, 'budget:costaccount:update'        UNION ALL
  SELECT 20255, '成本子科目新增',   6, 'budget:costsubcategory:add'       UNION ALL
  SELECT 20256, '成本子科目编辑',   6, 'budget:costsubcategory:edit'      UNION ALL
  SELECT 20257, '成本子科目删除',   6, 'budget:costsubcategory:delete'    UNION ALL
  -- 合同管理 (parent 4)
  SELECT 20258, 'BOM新增',          4, 'contract:bom:add'                 UNION ALL
  SELECT 20259, 'BOM编辑',          4, 'contract:bom:edit'                UNION ALL
  SELECT 20260, 'BOM删除',          4, 'contract:bom:delete'              UNION ALL
  SELECT 20261, 'BOM导入',          4, 'contract:bom:import'              UNION ALL
  SELECT 20262, '清单上传',         4, 'contract:boq:upload'              UNION ALL
  SELECT 20263, '清单删除',         4, 'contract:boq:delete'              UNION ALL
  SELECT 20264, '变更签证新增',     4, 'contract:changevisa:add'          UNION ALL
  SELECT 20265, '变更签证提交',     4, 'contract:changevisa:submit'       UNION ALL
  SELECT 20266, '施工合同新增',     4, 'contract:contract:add'            UNION ALL
  SELECT 20267, '施工合同编辑',     4, 'contract:contract:edit'           UNION ALL
  SELECT 20268, '施工合同删除',     4, 'contract:contract:delete'         UNION ALL
  SELECT 20269, '施工合同提交',     4, 'contract:contract:submit'         UNION ALL
  SELECT 20270, '其他合同新增',     4, 'contract:othercontract:add'       UNION ALL
  SELECT 20271, '其他合同编辑',     4, 'contract:othercontract:edit'      UNION ALL
  SELECT 20272, '其他合同删除',     4, 'contract:othercontract:delete'    UNION ALL
  SELECT 20273, '产值上报新增',     4, 'contract:outputreport:add'        UNION ALL
  SELECT 20274, '产值上报删除',     4, 'contract:outputreport:delete'     UNION ALL
  SELECT 20275, '产值上报提交',     4, 'contract:outputreport:submit'     UNION ALL
  SELECT 20276, '工程量清单新增',   4, 'contract:quantitylist:add'        UNION ALL
  SELECT 20277, '工程量清单编辑',   4, 'contract:quantitylist:edit'       UNION ALL
  SELECT 20278, '工程量清单删除',   4, 'contract:quantitylist:delete'     UNION ALL
  SELECT 20279, '工程量清单导入',   4, 'contract:quantitylist:import'     UNION ALL
  SELECT 20280, '结算新增',         4, 'contract:settlement:add'          UNION ALL
  SELECT 20281, '结算提交',         4, 'contract:settlement:submit'       UNION ALL
  -- 财务管理 (parent 5)
  SELECT 20282, '银行账户新增',     5, 'finance:bankaccount:add'          UNION ALL
  SELECT 20283, '银行账户编辑',     5, 'finance:bankaccount:edit'         UNION ALL
  SELECT 20284, '银行账户删除',     5, 'finance:bankaccount:delete'       UNION ALL
  SELECT 20285, '财务锁定创建',     5, 'finance:financelock:create'       UNION ALL
  SELECT 20286, '财务锁定解锁',     5, 'finance:financelock:unlock'       UNION ALL
  SELECT 20287, '开票申请新增',     5, 'finance:invoiceapply:add'         UNION ALL
  SELECT 20288, '开票申请编辑',     5, 'finance:invoiceapply:edit'        UNION ALL
  SELECT 20289, '开票申请删除',     5, 'finance:invoiceapply:delete'      UNION ALL
  SELECT 20290, '开票申请提交',     5, 'finance:invoiceapply:submit'      UNION ALL
  SELECT 20291, '税率新增',         5, 'finance:taxrate:add'              UNION ALL
  SELECT 20292, '税率编辑',         5, 'finance:taxrate:edit'             UNION ALL
  SELECT 20293, '税率删除',         5, 'finance:taxrate:delete'
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = t.id OR (m.permission = t.permission AND m.deleted = 0));

-- 3) SUPER_ADMIN 目录登记（拦截器已豁免，仅补目录完整性；与 V2026_45_2 做法一致）
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT t.id, 1, t.id FROM (
  SELECT 20241 AS id UNION ALL SELECT 20242 UNION ALL SELECT 20243 UNION ALL SELECT 20244 UNION ALL
  SELECT 20245 UNION ALL SELECT 20246 UNION ALL SELECT 20247 UNION ALL SELECT 20248 UNION ALL
  SELECT 20249 UNION ALL SELECT 20250 UNION ALL SELECT 20251 UNION ALL SELECT 20252 UNION ALL
  SELECT 20253 UNION ALL SELECT 20254 UNION ALL SELECT 20255 UNION ALL SELECT 20256 UNION ALL
  SELECT 20257 UNION ALL SELECT 20258 UNION ALL SELECT 20259 UNION ALL SELECT 20260 UNION ALL
  SELECT 20261 UNION ALL SELECT 20262 UNION ALL SELECT 20263 UNION ALL SELECT 20264 UNION ALL
  SELECT 20265 UNION ALL SELECT 20266 UNION ALL SELECT 20267 UNION ALL SELECT 20268 UNION ALL
  SELECT 20269 UNION ALL SELECT 20270 UNION ALL SELECT 20271 UNION ALL SELECT 20272 UNION ALL
  SELECT 20273 UNION ALL SELECT 20274 UNION ALL SELECT 20275 UNION ALL SELECT 20276 UNION ALL
  SELECT 20277 UNION ALL SELECT 20278 UNION ALL SELECT 20279 UNION ALL SELECT 20280 UNION ALL
  SELECT 20281 UNION ALL SELECT 20282 UNION ALL SELECT 20283 UNION ALL SELECT 20284 UNION ALL
  SELECT 20285 UNION ALL SELECT 20286 UNION ALL SELECT 20287 UNION ALL SELECT 20288 UNION ALL
  SELECT 20289 UNION ALL SELECT 20290 UNION ALL SELECT 20291 UNION ALL SELECT 20292 UNION ALL
  SELECT 20293
) t
WHERE EXISTS (SELECT 1 FROM sys_role r WHERE r.id = 1)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = t.id);
