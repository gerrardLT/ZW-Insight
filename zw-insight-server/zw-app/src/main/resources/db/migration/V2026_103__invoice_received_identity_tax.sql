-- No historical identity is invented. NULL invoice_number preserves legacy rows.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_invoice_received' AND column_name='invoice_code')=0,
 'ALTER TABLE biz_invoice_received ADD COLUMN invoice_code VARCHAR(12) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_invoice_received' AND column_name='invoice_number')=0,
 'ALTER TABLE biz_invoice_received ADD COLUMN invoice_number VARCHAR(20) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_invoice_received' AND column_name='amount_without_tax')=0,
 'ALTER TABLE biz_invoice_received ADD COLUMN amount_without_tax DECIMAL(18,2) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_invoice_received' AND column_name='tax_amount')=0,
 'ALTER TABLE biz_invoice_received ADD COLUMN tax_amount DECIMAL(18,2) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- NULL for deleted rows permits unlimited deletion/recreation history.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_invoice_received' AND column_name='active_invoice_identity')=0,
 'ALTER TABLE biz_invoice_received ADD COLUMN active_invoice_identity VARCHAR(33) GENERATED ALWAYS AS (CASE WHEN deleted=0 AND NULLIF(TRIM(invoice_number),'''') IS NOT NULL THEN CASE WHEN CHAR_LENGTH(TRIM(invoice_number))=20 THEN CONCAT(''D:'',TRIM(invoice_number)) ELSE CONCAT(COALESCE(TRIM(invoice_code),''''),'':'',TRIM(invoice_number)) END ELSE NULL END) STORED', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='biz_invoice_received' AND index_name='uk_received_invoice_identity')=0,
 'ALTER TABLE biz_invoice_received ADD UNIQUE INDEX uk_received_invoice_identity (tenant_id, active_invoice_identity)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
