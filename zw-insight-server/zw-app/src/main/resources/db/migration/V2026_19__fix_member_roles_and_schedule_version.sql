-- V2026_19: 修复 schema 与实体不一致导致的 SQLSyntaxErrorException
--
-- 背景（联调测试暴露的真实 schema/实体不一致）：
--   1) biz_project_member 实体字段 projectRoles（List<String>, JacksonTypeHandler → JSON 列 project_roles），
--      但建表脚本仅有废弃列 role_type，缺少 project_roles。
--      → 创建项目时自动添加项目经理 insert biz_project_member 报
--        「Unknown column 'project_roles' in 'field list'」，导致创建项目 500。
--   2) biz_export_schedule 实体继承 BaseEntity（含 version 乐观锁字段），
--      但 V5 建表脚本缺 version 列。
--      → 定时导出扫描任务 select version 报「Unknown column 'version' in 'field list'」。
--
-- 方案：幂等补齐缺失列（information_schema 判断存在性，不重复添加）。

-- 1. biz_project_member 补 project_roles（JSON 数组，存项目角色列表）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_project_member' AND COLUMN_NAME = 'project_roles') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_project_member` ADD COLUMN `project_roles` JSON COMMENT ''项目角色列表(JSON数组)'' AFTER `user_name`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 2. biz_export_schedule 补 version（乐观锁版本号）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_export_schedule' AND COLUMN_NAME = 'version') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_export_schedule` ADD COLUMN `version` INT DEFAULT 0 COMMENT ''乐观锁版本号'''
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;
