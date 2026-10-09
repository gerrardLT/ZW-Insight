-- 仅加当前实例绑定列；历史关联须逐条唯一溯源后备份回填，禁止猜测最新流程。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_regular_apply' AND COLUMN_NAME='workflow_instance_id')=0, 'ALTER TABLE biz_regular_apply ADD COLUMN workflow_instance_id VARCHAR(64) NULL COMMENT ''当前审批流程实例''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_resign_apply' AND COLUMN_NAME='workflow_instance_id')=0, 'ALTER TABLE biz_resign_apply ADD COLUMN workflow_instance_id VARCHAR(64) NULL COMMENT ''当前审批流程实例''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_seal_apply' AND COLUMN_NAME='workflow_instance_id')=0, 'ALTER TABLE biz_seal_apply ADD COLUMN workflow_instance_id VARCHAR(64) NULL COMMENT ''当前审批流程实例''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_transfer_apply' AND COLUMN_NAME='workflow_instance_id')=0, 'ALTER TABLE biz_transfer_apply ADD COLUMN workflow_instance_id VARCHAR(64) NULL COMMENT ''当前审批流程实例''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_vehicle_apply' AND COLUMN_NAME='workflow_instance_id')=0, 'ALTER TABLE biz_vehicle_apply ADD COLUMN workflow_instance_id VARCHAR(64) NULL COMMENT ''当前审批流程实例''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
