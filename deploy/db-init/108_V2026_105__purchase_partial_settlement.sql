-- V2026_105: explicit source linkage; no historical source inference.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_material_outbound_detail' AND column_name='inbound_detail_id')=0,
 'ALTER TABLE biz_material_outbound_detail ADD COLUMN inbound_detail_id BIGINT NULL, ADD INDEX idx_return_source (tenant_id,inbound_detail_id,deleted)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_purchase_settlement' AND column_name='settlement_mode')=0,
 "ALTER TABLE biz_purchase_settlement ADD COLUMN settlement_mode VARCHAR(16) NOT NULL DEFAULT 'LEGACY'", 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
CREATE TABLE IF NOT EXISTS biz_purchase_settlement_detail (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
 settlement_id BIGINT NOT NULL, inbound_detail_id BIGINT NOT NULL,
 quantity DECIMAL(18,4) NOT NULL, unit_price DECIMAL(18,2) NOT NULL, amount DECIMAL(18,2) NOT NULL,
 deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
 created_by BIGINT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 INDEX idx_settlement (tenant_id,settlement_id,deleted),
 INDEX idx_source (tenant_id,inbound_detail_id,deleted), INDEX idx_project (tenant_id,project_id,deleted),
 CHECK (quantity > 0), CHECK (unit_price >= 0), CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Rollback: stop writers, export LINES headers/details and linked returns first.
-- Do not drop linkage or convert LINES into LEGACY while any LINES document survives.
-- Application rollback requires blocking settlement/return writes until compatible code restored.
