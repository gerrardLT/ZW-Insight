-- 38_V2026_36: Settlement income caliber (M7) + reward/punish net + output report detail (M1) + machine work log index (M3)
-- Corresponds to spec batch: settlement_income_and_transfer
-- Idempotent: guarded by information_schema checks; safe to re-run.

-- ============ 1. biz_project_settlement: final_settlement_amount (M7) ============
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_project_settlement' AND COLUMN_NAME = 'final_settlement_amount') > 0, 'SELECT 1', 'ALTER TABLE `biz_project_settlement` ADD COLUMN `final_settlement_amount` DECIMAL(18,2) DEFAULT NULL COMMENT ''final settlement amount (income source; falls back to cumulative_output when null)'' AFTER other_expense'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

-- ============ 2. biz_project_settlement: reward_punish_net (M4) ============
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_project_settlement' AND COLUMN_NAME = 'reward_punish_net') > 0, 'SELECT 1', 'ALTER TABLE `biz_project_settlement` ADD COLUMN `reward_punish_net` DECIMAL(18,2) DEFAULT 0.00 COMMENT ''net reward/punish (reward positive, punish negative; counted into total expenditure)'' AFTER other_expense'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

-- ============ 3. biz_output_report_detail: new table (M1) ============
CREATE TABLE IF NOT EXISTS biz_output_report_detail (
    id BIGINT NOT NULL COMMENT 'primary key',
    report_id BIGINT NOT NULL COMMENT 'output report id',
    boq_item_id BIGINT COMMENT 'BOQ item id',
    quantity DECIMAL(18,2) COMMENT 'completed quantity this period',
    amount DECIMAL(18,2) COMMENT 'amount = quantity * unit price',
    tenant_id BIGINT COMMENT 'tenant id',
    created_by BIGINT COMMENT 'creator id',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    deleted TINYINT DEFAULT 0 COMMENT 'logical delete flag',
    version INT DEFAULT 0 COMMENT 'optimistic lock version',
    PRIMARY KEY (id),
    KEY idx_report_id (report_id),
    KEY idx_boq_item_id (boq_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='output report detail (per BOQ item, optional)';

-- ============ 4. biz_machine_work_log: index on (machine_id, settlement_status) (M3) ============
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_machine_work_log' AND INDEX_NAME = 'idx_machine_settlement_status') > 0, 'SELECT 1', 'ALTER TABLE `biz_machine_work_log` ADD INDEX `idx_machine_settlement_status` (`machine_id`, `settlement_status`)'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

-- ============ 5. biz_material_transfer: workflow_instance_id (M2) ============
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_material_transfer' AND COLUMN_NAME = 'workflow_instance_id') > 0, 'SELECT 1', 'ALTER TABLE `biz_material_transfer` ADD COLUMN `workflow_instance_id` VARCHAR(64) DEFAULT NULL COMMENT ''flowable process instance id'' AFTER status'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
