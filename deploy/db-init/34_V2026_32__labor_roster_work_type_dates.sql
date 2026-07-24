-- 34_V2026_32: 劳务花名册(biz_labor_roster) 补充 work_type(工种)/entry_date(进场日期)/exit_date(退场日期) 字段
-- （与 zw-app/src/main/resources/db/migration/V2026_32__labor_roster_work_type_dates.sql 内容一致，供全量部署脚本按序执行）
--
-- 背景：
--   前端 labor/roster 列表页与表单存在"工种(workType)/进场日期(entryDate)/退场日期(exitDate)"列与录入项，
--   但后端 biz_labor_roster 仅有 worker_type(用工类型) 且无进退场日期列，导致这些列永远空白、编辑提交丢失。
--   注意：工种 work_type 与用工类型 worker_type 语义不同，均需保留。
-- 方案：用 information_schema 判存在性，不存在才 ADD COLUMN（幂等，可重复执行）。

SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_labor_roster' AND COLUMN_NAME = 'work_type') > 0, 'SELECT 1', 'ALTER TABLE `biz_labor_roster` ADD COLUMN `work_type` VARCHAR(50) DEFAULT NULL COMMENT ''工种'' AFTER worker_type'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_labor_roster' AND COLUMN_NAME = 'entry_date') > 0, 'SELECT 1', 'ALTER TABLE `biz_labor_roster` ADD COLUMN `entry_date` DATE DEFAULT NULL COMMENT ''进场日期'' AFTER work_type'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_labor_roster' AND COLUMN_NAME = 'exit_date') > 0, 'SELECT 1', 'ALTER TABLE `biz_labor_roster` ADD COLUMN `exit_date` DATE DEFAULT NULL COMMENT ''退场日期'' AFTER entry_date'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
