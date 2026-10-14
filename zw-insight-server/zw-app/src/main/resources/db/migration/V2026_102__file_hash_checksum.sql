-- ====================================================================
-- 迁移脚本：V2026_102__file_hash_checksum.sql
-- 描述：为 file_info 增加 file_hash（SHA-256）列，支撑工程电子档案真实性与防篡改取证
-- 依据：GB/T 50328 / CJJ/T 187 电子档案元数据规范
-- 幂等：通过 information_schema 检测字段存在性后动态执行
-- ====================================================================

SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'file_info'
      AND COLUMN_NAME = 'file_hash'
);

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `file_info` ADD COLUMN `file_hash` VARCHAR(64) NULL COMMENT ''文件 SHA-256 哈希值（电子档案防篡改与完整性校验）'' AFTER `file_type`',
    'SELECT "file_hash already exists, skipping" AS msg'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
