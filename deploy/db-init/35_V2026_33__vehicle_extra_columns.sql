-- 35_V2026_33: 车辆(biz_vehicle) 补充 brand/driver/department/insurance_expiry/inspection_expiry 字段
-- （与 zw-app/src/main/resources/db/migration/V2026_33__vehicle_extra_columns.sql 内容一致，供全量部署脚本按序执行）
--
-- 背景：
--   前端 hr/vehicle 列表页与表单存在"品牌型号(brand)/驾驶人(driver)/使用部门(department)/
--   保险到期(insuranceExpiry)/年检到期(inspectionExpiry)"列与录入项，但后端 biz_vehicle 无对应列，
--   导致这些列永远空白、编辑提交丢失。本次补充真实列。
-- 方案：用 information_schema 判存在性，不存在才 ADD COLUMN（幂等，可重复执行）。

SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_vehicle' AND COLUMN_NAME = 'brand') > 0, 'SELECT 1', 'ALTER TABLE `biz_vehicle` ADD COLUMN `brand` VARCHAR(100) DEFAULT NULL COMMENT ''品牌型号'' AFTER vehicle_type'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_vehicle' AND COLUMN_NAME = 'driver') > 0, 'SELECT 1', 'ALTER TABLE `biz_vehicle` ADD COLUMN `driver` VARCHAR(50) DEFAULT NULL COMMENT ''驾驶人'' AFTER brand'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_vehicle' AND COLUMN_NAME = 'department') > 0, 'SELECT 1', 'ALTER TABLE `biz_vehicle` ADD COLUMN `department` VARCHAR(100) DEFAULT NULL COMMENT ''使用部门'' AFTER driver'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_vehicle' AND COLUMN_NAME = 'insurance_expiry') > 0, 'SELECT 1', 'ALTER TABLE `biz_vehicle` ADD COLUMN `insurance_expiry` DATE DEFAULT NULL COMMENT ''保险到期日期'' AFTER department'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_vehicle' AND COLUMN_NAME = 'inspection_expiry') > 0, 'SELECT 1', 'ALTER TABLE `biz_vehicle` ADD COLUMN `inspection_expiry` DATE DEFAULT NULL COMMENT ''年检到期日期'' AFTER insurance_expiry'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
