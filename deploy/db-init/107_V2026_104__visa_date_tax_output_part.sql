-- Preserve unknown historical business dates and rates as NULL.
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_change_visa' AND column_name='visa_date')=0,'ALTER TABLE biz_change_visa ADD COLUMN visa_date DATE NULL','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_change_visa' AND column_name='tax_rate')=0,'ALTER TABLE biz_change_visa ADD COLUMN tax_rate DECIMAL(5,2) NULL','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_output_report_detail' AND column_name='part')=0,'ALTER TABLE biz_output_report_detail ADD COLUMN part VARCHAR(200) NULL','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
