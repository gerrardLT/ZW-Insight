-- V2026_123：支出合同族修订（统一修订表增加合同类别）
-- 背景：biz_contract_revision 原为施工合同专用；支出合同（采购/劳务/机械/分包）
--       修订复用同一张表，必须增加 contract_category 区分主键空间（不同合同表 id 可重复）。
-- 幂等：information_schema 检查 + PREPARE/EXECUTE，可重复执行。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema = DATABASE()
                 AND table_name = 'biz_contract_revision'
                 AND column_name = 'contract_category') = 0,
 'ALTER TABLE biz_contract_revision ADD COLUMN contract_category VARCHAR(20) NOT NULL DEFAULT ''CONSTRUCTION'' AFTER contract_id',
 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE biz_contract_revision
SET contract_category = 'CONSTRUCTION'
WHERE contract_category IS NULL OR contract_category = '';

SET @idx = IF((SELECT COUNT(*) FROM information_schema.statistics
               WHERE table_schema = DATABASE()
                 AND table_name = 'biz_contract_revision'
                 AND index_name = 'idx_revision_category_contract') = 0,
 'CREATE INDEX idx_revision_category_contract ON biz_contract_revision (contract_category, contract_id)',
 'SELECT 1');
PREPARE stmt2 FROM @idx;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
