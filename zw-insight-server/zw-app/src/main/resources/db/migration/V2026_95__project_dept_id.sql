-- ============================================================
-- V2026_95__project_dept_id.sql
-- 项目「负责部门」列 —— 让数据范围 DEPT / DEPT_AND_CHILDREN 真正可用
--
-- 背景（2026-10-10 线上权限一致性清查 F3，见报告 §8.2 方案 A）：
--   全库**没有任何表有 dept_id 列**，而所有 @DataPermission 都声明
--   `deptColumn = "dept_id"`（@DataColumn 默认值），于是 DEPT / DEPT_AND_CHILDREN
--   范围会生成 `dept_id = ?` → MySQL 1054 → 接口 500。
--   当前未爆发只因相关用户同时持有 ALL 范围角色（多角色取最高优先级）掩盖。
--
-- 设计：本系统以**项目**为中心，业务表都已有 project_id，故不逐表加列，而是
--   给「项目」本身标注负责部门，DEPT 条件改为经项目派生：
--     <projectColumn> IN (SELECT id FROM biz_project
--                          WHERE dept_id IN (<部门及子树>) AND tenant_id = ? AND deleted = 0)
--   —— 「本部门数据」= 本部门负责的项目及其全部单据（含 biz_project 自身，
--      其 projectColumn 为 id）。
--   对应 handler 改动见 ZwDataPermissionHandler#buildDeptCondition /
--   #buildDeptAndChildrenCondition。
--
-- 幂等：加列/加索引均以 information_schema 判定 + PREPARE/EXECUTE 条件执行；
--       回填仅作用于 dept_id IS NULL 的行。
-- 回滚：
--   ALTER TABLE biz_project DROP INDEX idx_biz_project_dept_id;
--   ALTER TABLE biz_project DROP COLUMN dept_id;
-- ============================================================

-- 1) 加列（幂等）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'biz_project' AND column_name = 'dept_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE biz_project ADD COLUMN dept_id BIGINT NULL COMMENT ''负责部门ID（数据范围 DEPT/DEPT_AND_CHILDREN 判定依据）''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 加索引（幂等）
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'biz_project' AND index_name = 'idx_biz_project_dept_id');
SET @ddl2 := IF(@idx_exists = 0,
    'ALTER TABLE biz_project ADD INDEX idx_biz_project_dept_id (dept_id)',
    'SELECT 1');
PREPARE stmt2 FROM @ddl2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- 3) 存量回填（仅 dept_id 为空的行）
--    优先级：项目成员中 project_roles JSON 含 "PROJECT_MANAGER" 的所属部门 → 任一成员的所属部门 → 创建人所属部门
--    注：biz_project_member.role_type 是**废弃列**（V2026_19 起真实角色存于 project_roles JSON），
--        故此处用 JSON_CONTAINS 判定，勿改回 role_type。
UPDATE biz_project p
SET p.dept_id = COALESCE(
    (SELECT u.org_id FROM biz_project_member m
       JOIN sys_user u ON u.id = m.user_id AND u.deleted = 0 AND u.org_id IS NOT NULL
      WHERE m.project_id = p.id AND m.deleted = 0
        AND m.project_roles IS NOT NULL AND JSON_CONTAINS(m.project_roles, '"PROJECT_MANAGER"')
      ORDER BY m.id LIMIT 1),
    (SELECT u.org_id FROM biz_project_member m
       JOIN sys_user u ON u.id = m.user_id AND u.deleted = 0 AND u.org_id IS NOT NULL
      WHERE m.project_id = p.id AND m.deleted = 0
      ORDER BY m.id LIMIT 1),
    (SELECT u.org_id FROM sys_user u
      WHERE u.id = p.created_by AND u.deleted = 0 AND u.org_id IS NOT NULL)
)
WHERE p.dept_id IS NULL AND p.deleted = 0;
