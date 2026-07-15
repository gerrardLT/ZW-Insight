-- ============================================================
-- 数据库修复脚本 - 修复列名不匹配 & 缺失列
-- 版本: V2026_16
-- 修复内容:
--   1. biz_finance_lock: create_time→created_at, update_time→updated_at,
--      添加 tenant_id, created_by, version; deleted TINYINT→INT
--   2. biz_tax_rate: 同上
--   3. biz_approval_snapshot: 添加 deleted 列 (MyBatis-Plus 全局逻辑删除)
--   4. biz_approval_rollback_log: 添加 deleted 列 (同上)
-- ============================================================

-- ============ 1. 修复 biz_finance_lock ============

-- 1a. 重命名 create_time → created_at
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_finance_lock' AND COLUMN_NAME = 'create_time') > 0,
    'ALTER TABLE `biz_finance_lock` CHANGE COLUMN `create_time` `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'SELECT 1'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 1b. 重命名 update_time → updated_at
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_finance_lock' AND COLUMN_NAME = 'update_time') > 0,
    'ALTER TABLE `biz_finance_lock` CHANGE COLUMN `update_time` `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'SELECT 1'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 1c. 添加 tenant_id
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_finance_lock' AND COLUMN_NAME = 'tenant_id') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_finance_lock` ADD COLUMN `tenant_id` BIGINT COMMENT ''租户ID'' AFTER `unlock_time`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 1d. 添加 created_by
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_finance_lock' AND COLUMN_NAME = 'created_by') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_finance_lock` ADD COLUMN `created_by` BIGINT COMMENT ''创建人ID'' AFTER `tenant_code`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 1e. 添加 version
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_finance_lock' AND COLUMN_NAME = 'version') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_finance_lock` ADD COLUMN `version` INT DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 1f. 修改 deleted 类型 TINYINT→INT（兼容 MyBatis-Plus Integer 映射）
SET @sql = (SELECT IF(
    (SELECT DATA_TYPE FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_finance_lock' AND COLUMN_NAME = 'deleted') = 'tinyint',
    'ALTER TABLE `biz_finance_lock` MODIFY COLUMN `deleted` INT DEFAULT 0 COMMENT ''逻辑删除（0-未删除 1-已删除）''',
    'SELECT 1'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- ============ 2. 修复 biz_tax_rate ============

-- 2a. 重命名 create_time → created_at
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_tax_rate' AND COLUMN_NAME = 'create_time') > 0,
    'ALTER TABLE `biz_tax_rate` CHANGE COLUMN `create_time` `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'SELECT 1'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 2b. 重命名 update_time → updated_at
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_tax_rate' AND COLUMN_NAME = 'update_time') > 0,
    'ALTER TABLE `biz_tax_rate` CHANGE COLUMN `update_time` `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'SELECT 1'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 2c. 添加 tenant_id
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_tax_rate' AND COLUMN_NAME = 'tenant_id') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_tax_rate` ADD COLUMN `tenant_id` BIGINT COMMENT ''租户ID'' AFTER `status`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 2d. 添加 created_by
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_tax_rate' AND COLUMN_NAME = 'created_by') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_tax_rate` ADD COLUMN `created_by` BIGINT COMMENT ''创建人ID'' AFTER `tenant_code`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 2e. 添加 version
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_tax_rate' AND COLUMN_NAME = 'version') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_tax_rate` ADD COLUMN `version` INT DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `deleted`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 2f. 修改 deleted 类型 TINYINT→INT
SET @sql = (SELECT IF(
    (SELECT DATA_TYPE FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_tax_rate' AND COLUMN_NAME = 'deleted') = 'tinyint',
    'ALTER TABLE `biz_tax_rate` MODIFY COLUMN `deleted` INT DEFAULT 0 COMMENT ''逻辑删除（0-未删除 1-已删除）''',
    'SELECT 1'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- ============ 3. biz_approval_snapshot 添加 deleted 列 ============

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_approval_snapshot' AND COLUMN_NAME = 'deleted') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_approval_snapshot` ADD COLUMN `deleted` INT DEFAULT 0 COMMENT ''逻辑删除（0-未删除 1-已删除）'' AFTER `created_at`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- ============ 4. biz_approval_rollback_log 添加 deleted 列 ============

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_approval_rollback_log' AND COLUMN_NAME = 'deleted') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_approval_rollback_log` ADD COLUMN `deleted` INT DEFAULT 0 COMMENT ''逻辑删除（0-未删除 1-已删除）'' AFTER `created_at`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;
