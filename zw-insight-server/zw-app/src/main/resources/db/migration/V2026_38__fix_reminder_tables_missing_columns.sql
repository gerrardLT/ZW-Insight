-- V2026_38: 幂等补齐 biz_reminder_config / biz_reminder_log 缺失列（批3 L3 CI 实跑暴露）
--
-- 背景：V2026_09 建表时未含 BaseEntity 要求的 created_by/deleted/version
--       （biz_reminder_log 另缺 updated_at），实体继承 BaseEntity 且 deleted 为
--       @TableLogic，MyBatis select 报「Unknown column」被全局异常兜底为 HTTP 500：
--       GET /api/v1/site/reminder-logs/{inspectionId}、GET /api/v1/site/reminder-config。
-- 方案：同 V2026_20，逐列用 information_schema.COLUMNS 判断存在性，不存在才 ADD COLUMN。

-- biz_reminder_config
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_reminder_config' AND COLUMN_NAME = 'created_by') > 0, 'SELECT 1', 'ALTER TABLE `biz_reminder_config` ADD COLUMN `created_by` BIGINT DEFAULT NULL COMMENT ''创建人ID'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_reminder_config' AND COLUMN_NAME = 'deleted') > 0, 'SELECT 1', 'ALTER TABLE `biz_reminder_config` ADD COLUMN `deleted` INT DEFAULT 0 COMMENT ''逻辑删除(0-未删除 1-已删除)'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_reminder_config' AND COLUMN_NAME = 'version') > 0, 'SELECT 1', 'ALTER TABLE `biz_reminder_config` ADD COLUMN `version` INT DEFAULT 0 COMMENT ''乐观锁版本号'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

-- biz_reminder_log
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_reminder_log' AND COLUMN_NAME = 'created_by') > 0, 'SELECT 1', 'ALTER TABLE `biz_reminder_log` ADD COLUMN `created_by` BIGINT DEFAULT NULL COMMENT ''创建人ID'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_reminder_log' AND COLUMN_NAME = 'updated_at') > 0, 'SELECT 1', 'ALTER TABLE `biz_reminder_log` ADD COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_reminder_log' AND COLUMN_NAME = 'deleted') > 0, 'SELECT 1', 'ALTER TABLE `biz_reminder_log` ADD COLUMN `deleted` INT DEFAULT 0 COMMENT ''逻辑删除(0-未删除 1-已删除)'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_reminder_log' AND COLUMN_NAME = 'version') > 0, 'SELECT 1', 'ALTER TABLE `biz_reminder_log` ADD COLUMN `version` INT DEFAULT 0 COMMENT ''乐观锁版本号'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
