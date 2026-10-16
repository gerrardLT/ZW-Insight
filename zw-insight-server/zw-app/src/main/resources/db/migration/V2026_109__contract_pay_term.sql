-- C2 合同收付款条款结构化（2026-10-16）
-- 通用挂接：contract_category(CONSTRUCTION/PURCHASE/LABOR/MACHINE/SUBCONTRACT/OTHER) + contract_id。
-- 质保金自动计提幂等：biz_retention_money 补来源列（可空，存量不受影响）+ 唯一 guard。
CREATE TABLE IF NOT EXISTS biz_contract_pay_term (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
 contract_category VARCHAR(20) NOT NULL, contract_id BIGINT NOT NULL,
 term_type VARCHAR(20) NOT NULL,
 trigger_condition VARCHAR(200) NULL,
 plan_date DATE NULL, ratio DECIMAL(7,4) NULL, amount DECIMAL(18,2) NULL,
 deduct_rule VARCHAR(50) NULL, retention_months INT NULL, grace_days INT NULL,
 status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
 deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
 created_by BIGINT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 INDEX idx_contract (tenant_id, contract_category, contract_id, deleted),
 INDEX idx_project (tenant_id, project_id, deleted),
 CHECK (ratio IS NULL OR ratio >= 0), CHECK (amount IS NULL OR amount >= 0),
 CHECK (term_type IN ('PREPAY', 'PROGRESS', 'FINAL', 'RETENTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_retention_money' AND column_name='source_type')=0,
 'ALTER TABLE biz_retention_money ADD COLUMN source_type VARCHAR(30) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_retention_money' AND column_name='source_id')=0,
 'ALTER TABLE biz_retention_money ADD COLUMN source_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 有效来源唯一：deleted=1 或无来源时生成 NULL，不参与唯一约束（沿用 V2026_103 身份 guard 模式）
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_retention_money' AND column_name='active_retention_source')=0,
 'ALTER TABLE biz_retention_money ADD COLUMN active_retention_source VARCHAR(40) GENERATED ALWAYS AS (CASE WHEN deleted=0 AND source_type IS NOT NULL AND source_id IS NOT NULL THEN CONCAT(source_type, '':'', source_id) ELSE NULL END) STORED', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='biz_retention_money' AND index_name='uk_active_retention_source')=0,
 'ALTER TABLE biz_retention_money ADD UNIQUE INDEX uk_active_retention_source (tenant_id, active_retention_source)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- Rollback: 代码回退保留新表与可空列；唯一索引/生成列移除须备份评估。
