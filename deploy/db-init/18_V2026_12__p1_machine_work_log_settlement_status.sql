-- P1: 机械工作量结算 - 添加结算状态字段
-- 用于追踪工作日志是否已被结算单覆盖

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_machine_work_log' AND COLUMN_NAME = 'settlement_status') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_machine_work_log` ADD COLUMN `settlement_status` VARCHAR(20) DEFAULT ''UNSETTLED'' COMMENT ''结算状态（UNSETTLED-未结算/SETTLED-已结算）'''
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 为结算状态添加索引，支持创建结算单时快速排除已结算记录
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_machine_work_log' AND INDEX_NAME = 'idx_machine_work_log_settlement_status') > 0,
    'SELECT 1',
    'CREATE INDEX `idx_machine_work_log_settlement_status` ON `biz_machine_work_log` (`settlement_status`)'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;
