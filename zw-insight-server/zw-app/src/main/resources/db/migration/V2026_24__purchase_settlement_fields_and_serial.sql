-- V2026_24: 补全采购结算(biz_purchase_settlement)缺失字段 + 编号规则种子
--
-- 背景：
--   采购结算此前仅有孤儿实体+mapper，无 Service/Controller，前端 6 个接口全部 404。
--   本次补全后端时，实体新增 settlement_no/inbound_amount/settlement_date/remark 四个字段，
--   原表(00_schema.sql)未包含，需幂等补齐；否则 MyBatis insert 报「Unknown column」被兜底为 500。
--   同时 SerialNumberService.generate("PURCHASE_SETTLEMENT") 需要 serial_number_rule 种子，
--   缺失会抛「未配置编号规则: PURCHASE_SETTLEMENT」导致新增结算单 500。
-- 方案：逐列用 information_schema 判存在性，不存在才 ADD COLUMN（幂等，可重复执行）。

-- 1. 补齐 biz_purchase_settlement 缺失字段
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_purchase_settlement' AND COLUMN_NAME = 'settlement_no') > 0, 'SELECT 1', 'ALTER TABLE `biz_purchase_settlement` ADD COLUMN `settlement_no` VARCHAR(50) DEFAULT NULL COMMENT ''结算单号'' AFTER id'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_purchase_settlement' AND COLUMN_NAME = 'inbound_amount') > 0, 'SELECT 1', 'ALTER TABLE `biz_purchase_settlement` ADD COLUMN `inbound_amount` DECIMAL(18,2) DEFAULT NULL COMMENT ''关联入库单金额(结算依据)'' AFTER inbound_id'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_purchase_settlement' AND COLUMN_NAME = 'settlement_date') > 0, 'SELECT 1', 'ALTER TABLE `biz_purchase_settlement` ADD COLUMN `settlement_date` DATE DEFAULT NULL COMMENT ''结算日期'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_purchase_settlement' AND COLUMN_NAME = 'remark') > 0, 'SELECT 1', 'ALTER TABLE `biz_purchase_settlement` ADD COLUMN `remark` VARCHAR(500) DEFAULT NULL COMMENT ''备注'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

-- 2. 补齐默认租户(id=1)的采购结算编号规则（幂等）
INSERT INTO serial_number_rule (id, business_type, rule_prefix, date_format, seq_length, reset_period, description, tenant_id, created_at, updated_at, deleted, version)
SELECT 900005, 'PURCHASE_SETTLEMENT', 'CGJS', 'yyyyMMdd', 4, 'MONTH', '采购结算编号', 1, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM serial_number_rule WHERE business_type = 'PURCHASE_SETTLEMENT' AND tenant_id = 1);
