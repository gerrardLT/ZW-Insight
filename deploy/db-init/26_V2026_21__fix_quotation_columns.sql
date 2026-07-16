-- V2026_21: 补齐 biz_quotation 缺失列（联调全量测试暴露）
--
-- 背景：BizQuotation 实体含 supplierPhone / quotationSource 字段，但 biz_quotation 表缺对应列，
--       报价提交 / 门户报价录入 insert 报「Unknown column 'supplier_phone'」，被全局异常兜底为 500。
-- 列类型来源：实体字段 Java 类型（String→VARCHAR）。
-- 方案：逐列用 information_schema.COLUMNS 判断存在性，不存在才 ADD COLUMN（幂等，可重复执行）。

-- biz_quotation
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_quotation' AND COLUMN_NAME = 'supplier_phone') > 0, 'SELECT 1', 'ALTER TABLE `biz_quotation` ADD COLUMN `supplier_phone` VARCHAR(50) DEFAULT NULL COMMENT ''供应商联系电话'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_quotation' AND COLUMN_NAME = 'quotation_source') > 0, 'SELECT 1', 'ALTER TABLE `biz_quotation` ADD COLUMN `quotation_source` VARCHAR(20) DEFAULT NULL COMMENT ''报价来源（PORTAL-门户提交/MANUAL-手动录入）'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
