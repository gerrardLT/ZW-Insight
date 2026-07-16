-- V2026_20: 幂等补齐 schema 与实体不一致导致缺失的列（联调全量测试暴露）
--
-- 背景：schema.sql 采用 CREATE TABLE IF NOT EXISTS，表已存在后新增到 schema.sql / 实体的字段
--       无法应用到已部署库，造成 10 张表共 37 列缺失，MyBatis insert/select 报
--       「Unknown column 'xxx' in 'field list'」，被全局异常兜底为 500。
-- 列类型来源：对应实体类字段的 Java 类型（String→VARCHAR, BigDecimal→DECIMAL(18,2),
--       LocalDate→DATE, LocalDateTime→DATETIME, Long→BIGINT, Integer→INT），
--       biz_sign_record 缺的 created_by/updated_at/deleted/version 来自 BaseEntity。
-- 方案：逐列用 information_schema.COLUMNS 判断存在性，不存在才 ADD COLUMN（幂等，可重复执行）。


-- biz_inquiry
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_inquiry' AND COLUMN_NAME = 'award_method') > 0, 'SELECT 1', 'ALTER TABLE `biz_inquiry` ADD COLUMN `award_method` VARCHAR(50) DEFAULT NULL COMMENT ''定标方式描述'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_inquiry' AND COLUMN_NAME = 'description') > 0, 'SELECT 1', 'ALTER TABLE `biz_inquiry` ADD COLUMN `description` VARCHAR(500) DEFAULT NULL COMMENT ''询价描述/说明'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_inquiry' AND COLUMN_NAME = 'requirements') > 0, 'SELECT 1', 'ALTER TABLE `biz_inquiry` ADD COLUMN `requirements` VARCHAR(500) DEFAULT NULL COMMENT ''技术要求'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_inquiry' AND COLUMN_NAME = 'material_summary') > 0, 'SELECT 1', 'ALTER TABLE `biz_inquiry` ADD COLUMN `material_summary` VARCHAR(500) DEFAULT NULL COMMENT ''材料清单摘要'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_inquiry' AND COLUMN_NAME = 'winner_name') > 0, 'SELECT 1', 'ALTER TABLE `biz_inquiry` ADD COLUMN `winner_name` VARCHAR(200) DEFAULT NULL COMMENT ''中标供应商名称'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_inquiry' AND COLUMN_NAME = 'winner_amount') > 0, 'SELECT 1', 'ALTER TABLE `biz_inquiry` ADD COLUMN `winner_amount` DECIMAL(18,2) DEFAULT NULL COMMENT ''中标金额'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_inquiry' AND COLUMN_NAME = 'award_date') > 0, 'SELECT 1', 'ALTER TABLE `biz_inquiry` ADD COLUMN `award_date` DATE DEFAULT NULL COMMENT ''定标日期'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_inquiry' AND COLUMN_NAME = 'publicize_date') > 0, 'SELECT 1', 'ALTER TABLE `biz_inquiry` ADD COLUMN `publicize_date` DATE DEFAULT NULL COMMENT ''公示日期'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

-- biz_invoice_apply
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_invoice_apply' AND COLUMN_NAME = 'project_name') > 0, 'SELECT 1', 'ALTER TABLE `biz_invoice_apply` ADD COLUMN `project_name` VARCHAR(200) DEFAULT NULL COMMENT ''项目名称(冗余展示字段)'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_invoice_apply' AND COLUMN_NAME = 'apply_date') > 0, 'SELECT 1', 'ALTER TABLE `biz_invoice_apply` ADD COLUMN `apply_date` VARCHAR(20) DEFAULT NULL COMMENT ''申请日期'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

-- biz_labor_contract
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_labor_contract' AND COLUMN_NAME = 'contract_name') > 0, 'SELECT 1', 'ALTER TABLE `biz_labor_contract` ADD COLUMN `contract_name` VARCHAR(200) DEFAULT NULL COMMENT ''合同名称'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_labor_contract' AND COLUMN_NAME = 'team_name') > 0, 'SELECT 1', 'ALTER TABLE `biz_labor_contract` ADD COLUMN `team_name` VARCHAR(200) DEFAULT NULL COMMENT ''施工队伍名称'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_labor_contract' AND COLUMN_NAME = 'start_date') > 0, 'SELECT 1', 'ALTER TABLE `biz_labor_contract` ADD COLUMN `start_date` DATE DEFAULT NULL COMMENT ''开始日期'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_labor_contract' AND COLUMN_NAME = 'end_date') > 0, 'SELECT 1', 'ALTER TABLE `biz_labor_contract` ADD COLUMN `end_date` DATE DEFAULT NULL COMMENT ''结束日期'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

-- biz_machine_contract
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_machine_contract' AND COLUMN_NAME = 'contract_name') > 0, 'SELECT 1', 'ALTER TABLE `biz_machine_contract` ADD COLUMN `contract_name` VARCHAR(200) DEFAULT NULL COMMENT ''合同名称'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_machine_contract' AND COLUMN_NAME = 'machine_name') > 0, 'SELECT 1', 'ALTER TABLE `biz_machine_contract` ADD COLUMN `machine_name` VARCHAR(200) DEFAULT NULL COMMENT ''设备名称'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_machine_contract' AND COLUMN_NAME = 'rental_type') > 0, 'SELECT 1', 'ALTER TABLE `biz_machine_contract` ADD COLUMN `rental_type` VARCHAR(20) DEFAULT NULL COMMENT ''租赁方式'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_machine_contract' AND COLUMN_NAME = 'start_date') > 0, 'SELECT 1', 'ALTER TABLE `biz_machine_contract` ADD COLUMN `start_date` DATE DEFAULT NULL COMMENT ''开始日期'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_machine_contract' AND COLUMN_NAME = 'end_date') > 0, 'SELECT 1', 'ALTER TABLE `biz_machine_contract` ADD COLUMN `end_date` DATE DEFAULT NULL COMMENT ''结束日期'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

-- biz_machine_ledger
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_machine_ledger' AND COLUMN_NAME = 'brand') > 0, 'SELECT 1', 'ALTER TABLE `biz_machine_ledger` ADD COLUMN `brand` VARCHAR(100) DEFAULT NULL COMMENT ''品牌'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_machine_ledger' AND COLUMN_NAME = 'specification') > 0, 'SELECT 1', 'ALTER TABLE `biz_machine_ledger` ADD COLUMN `specification` VARCHAR(100) DEFAULT NULL COMMENT ''规格型号'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_machine_ledger' AND COLUMN_NAME = 'owner_type') > 0, 'SELECT 1', 'ALTER TABLE `biz_machine_ledger` ADD COLUMN `owner_type` VARCHAR(20) DEFAULT NULL COMMENT ''权属(OWN-自有/RENT-租赁)'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_machine_ledger' AND COLUMN_NAME = 'current_project') > 0, 'SELECT 1', 'ALTER TABLE `biz_machine_ledger` ADD COLUMN `current_project` VARCHAR(200) DEFAULT NULL COMMENT ''当前所在项目'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

-- biz_office_supply
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_office_supply' AND COLUMN_NAME = 'current_stock') > 0, 'SELECT 1', 'ALTER TABLE `biz_office_supply` ADD COLUMN `current_stock` DECIMAL(18,2) DEFAULT 0 COMMENT ''当前库存数量'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_office_supply' AND COLUMN_NAME = 'total_inbound') > 0, 'SELECT 1', 'ALTER TABLE `biz_office_supply` ADD COLUMN `total_inbound` DECIMAL(18,2) DEFAULT 0 COMMENT ''累计入库量'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_office_supply' AND COLUMN_NAME = 'total_issued') > 0, 'SELECT 1', 'ALTER TABLE `biz_office_supply` ADD COLUMN `total_issued` DECIMAL(18,2) DEFAULT 0 COMMENT ''累计领用量'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_office_supply' AND COLUMN_NAME = 'last_inbound_date') > 0, 'SELECT 1', 'ALTER TABLE `biz_office_supply` ADD COLUMN `last_inbound_date` DATE DEFAULT NULL COMMENT ''最近入库日期'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

-- biz_project_material_stock
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_project_material_stock' AND COLUMN_NAME = 'material_id') > 0, 'SELECT 1', 'ALTER TABLE `biz_project_material_stock` ADD COLUMN `material_id` BIGINT DEFAULT NULL COMMENT ''材料ID'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

-- biz_purchase_contract
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_purchase_contract' AND COLUMN_NAME = 'contract_name') > 0, 'SELECT 1', 'ALTER TABLE `biz_purchase_contract` ADD COLUMN `contract_name` VARCHAR(200) DEFAULT NULL COMMENT ''合同名称'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_purchase_contract' AND COLUMN_NAME = 'supplier_name') > 0, 'SELECT 1', 'ALTER TABLE `biz_purchase_contract` ADD COLUMN `supplier_name` VARCHAR(200) DEFAULT NULL COMMENT ''供应商名称'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

-- biz_sign_record
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_sign_record' AND COLUMN_NAME = 'created_by') > 0, 'SELECT 1', 'ALTER TABLE `biz_sign_record` ADD COLUMN `created_by` BIGINT DEFAULT NULL COMMENT ''创建人ID'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_sign_record' AND COLUMN_NAME = 'updated_at') > 0, 'SELECT 1', 'ALTER TABLE `biz_sign_record` ADD COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_sign_record' AND COLUMN_NAME = 'deleted') > 0, 'SELECT 1', 'ALTER TABLE `biz_sign_record` ADD COLUMN `deleted` INT DEFAULT 0 COMMENT ''逻辑删除(0-未删除 1-已删除)'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_sign_record' AND COLUMN_NAME = 'version') > 0, 'SELECT 1', 'ALTER TABLE `biz_sign_record` ADD COLUMN `version` INT DEFAULT 0 COMMENT ''乐观锁版本号'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

-- biz_subcontract
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_subcontract' AND COLUMN_NAME = 'contract_name') > 0, 'SELECT 1', 'ALTER TABLE `biz_subcontract` ADD COLUMN `contract_name` VARCHAR(200) DEFAULT NULL COMMENT ''合同名称'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_subcontract' AND COLUMN_NAME = 'subcontractor') > 0, 'SELECT 1', 'ALTER TABLE `biz_subcontract` ADD COLUMN `subcontractor` VARCHAR(200) DEFAULT NULL COMMENT ''分包方名称'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_subcontract' AND COLUMN_NAME = 'content') > 0, 'SELECT 1', 'ALTER TABLE `biz_subcontract` ADD COLUMN `content` VARCHAR(1000) DEFAULT NULL COMMENT ''分包内容'''));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
