-- 为询价单表新增报价截止时间字段（兼容所有 MySQL 8.0 版本的幂等写法）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_inquiry' AND COLUMN_NAME = 'deadline') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_inquiry` ADD COLUMN `deadline` DATETIME DEFAULT NULL COMMENT ''报价截止时间'' AFTER `publish_time`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;
