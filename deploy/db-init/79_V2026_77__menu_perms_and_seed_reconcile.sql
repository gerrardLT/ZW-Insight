-- ============================================================
-- V2026_77__menu_perms_and_seed_reconcile.sql
-- 菜单模块视图码重放 + 双侧种子缺口对账
--
-- 背景（2026-10-05 双库系统性漂移检查，audit-reports/server2-db-drift-check-2026-10-05.md）：
--   1) 43（徽颍）等全新 initdb 环境按文件名字典序执行 db-init，99_data-menu.sql
--      （菜单种子，此前 INSERT 不带 permission 列）在 47_V2026_45_2（UPDATE 菜单
--      1-19 补 {module}:view 权限码）之后执行——UPDATE 扑空（0 行也报成功），
--      Flyway 历史标记 applied 后永不重放，导致非 SUPER_ADMIN 角色进入 14 个
--      业务模块页面时 API 403（wangting 实例）。43 已于 2026-10-05 人工重放修复；
--      本迁移让所有既有环境启动自愈，并配合 99_data-menu.sql 补列根治未来新环境。
--   2) 129（主环境）反向缺口（演进库无 initdb 基座脚本所致）：
--      a. V2026_08 的系统导入模板种子 2001-2005 从未在 129 落库（历史原因不明），
--         TemplateService 无兜底返回 null，导入模板下载功能缺失；
--      b. 01_p0_core_features.sql 的预算控制全局默认行（BLOCK/80%）只在 initdb
--         基座脚本中，129 一直依赖服务层硬编码兜底。两者一并幂等补种。
--
-- 幂等：UPDATE 同值重放无害；模板用 INSERT IGNORE（主键冲突即跳过）；
--       预算默认行带 is_default 与 id 双守卫，可重复执行。
-- 双轨：deploy/db-init/79_V2026_77__menu_perms_and_seed_reconcile.sql 同内容。
--       新环境若经 initdb 预置 Flyway 标记而跳过本迁移，99_data-menu.sql 已带
--       permission 列，权限码不缺失；模板与预算行由 11_V2026_08 / 01_p0 在
--       initdb 序列内种入。两条路径互为冗余。
-- ============================================================

-- 1) 模块级视图码：重放 V2026_45_2 第 1 节（对齐菜单 1-19）
UPDATE sys_menu SET permission = 'dashboard:view'         WHERE id = 1;
UPDATE sys_menu SET permission = 'system:view'            WHERE id = 2;
UPDATE sys_menu SET permission = 'project:view'           WHERE id = 3;
UPDATE sys_menu SET permission = 'contract:view'          WHERE id = 4;
UPDATE sys_menu SET permission = 'finance:view'           WHERE id = 5;
UPDATE sys_menu SET permission = 'budget:view'            WHERE id = 6;
UPDATE sys_menu SET permission = 'purchase:view'          WHERE id = 7;
UPDATE sys_menu SET permission = 'labor:view'             WHERE id = 8;
UPDATE sys_menu SET permission = 'material:view'          WHERE id = 9;
UPDATE sys_menu SET permission = 'machine:view'           WHERE id = 10;
UPDATE sys_menu SET permission = 'subcontract:view'       WHERE id = 11;
UPDATE sys_menu SET permission = 'site:view'              WHERE id = 12;
UPDATE sys_menu SET permission = 'tender:view'            WHERE id = 13;
UPDATE sys_menu SET permission = 'hr:view'                WHERE id = 14;
UPDATE sys_menu SET permission = 'archive:view'           WHERE id = 15;
UPDATE sys_menu SET permission = 'workflow:view'          WHERE id = 16;
UPDATE sys_menu SET permission = 'message:view'           WHERE id = 17;
UPDATE sys_menu SET permission = 'basedata:view'          WHERE id = 18;
UPDATE sys_menu SET permission = 'project-dashboard:view' WHERE id = 19;

-- 2) 系统导入模板种子对账（源：V2026_08 第 4 节；129 缺失，43 已有则 IGNORE 跳过）
INSERT IGNORE INTO sys_template (id, template_name, template_type, module_code, is_default, created_at, updated_at) VALUES
(2001, '机械台账导入模板', 'IMPORT', 'MACHINE_LEDGER', 1, NOW(), NOW()),
(2002, '劳务花名册导入模板', 'IMPORT', 'LABOR_ROSTER', 1, NOW(), NOW()),
(2003, '人员信息导入模板', 'IMPORT', 'SYS_USER', 1, NOW(), NOW()),
(2004, '供应商导入模板', 'IMPORT', 'SUPPLIER', 1, NOW(), NOW()),
(2005, '材料字典导入模板', 'IMPORT', 'MATERIAL', 1, NOW(), NOW());

-- 3) 预算控制全局默认行对账（源：01_p0_core_features.sql；仅 initdb 环境有种，演进库缺失）
--    双守卫：已有任一启用默认配置、或 id=1 已被占用时均跳过，绝不制造重复默认
INSERT INTO sys_budget_control_config
    (id, tenant_id, project_id, control_mode, warning_threshold, is_default, created_by, created_at, updated_at, deleted, version)
SELECT 1, 0, NULL, 'BLOCK', 80, 1, NULL, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_budget_control_config WHERE is_default = 1 AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_budget_control_config WHERE id = 1);
