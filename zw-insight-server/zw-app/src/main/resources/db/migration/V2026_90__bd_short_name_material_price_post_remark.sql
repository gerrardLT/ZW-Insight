-- V2026_90: 补齐三个「前端有录入项、库中无对应列」的字段
--
-- 背景（2026-10-10 全量排查，同 site/schedule.vue 项目字段一类缺陷）：
--   bd_company.short_name        —— 前端「简称」录入项，实体 BdCompany 无该字段 → 填了不落库，列表列恒空
--   bd_material.reference_price  —— 前端「参考单价」录入项，实体 BdMaterial 无该字段 → 填了不落库，列表列恒空
--   sys_post.remark              —— 前端「备注」录入项，实体 SysPost 无该字段 → 填了不落库
--   三列均可空且无默认值，历史行不受影响；实体侧已同步补字段，前端绑定保持原语义。
-- 方案：information_schema 判存在性，不存在才 ADD COLUMN（幂等，可重复执行）。

SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bd_company' AND COLUMN_NAME = 'short_name') > 0, 'SELECT 1', 'ALTER TABLE `bd_company` ADD COLUMN `short_name` VARCHAR(100) DEFAULT NULL COMMENT ''简称'' AFTER `company_name`'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bd_material' AND COLUMN_NAME = 'reference_price') > 0, 'SELECT 1', 'ALTER TABLE `bd_material` ADD COLUMN `reference_price` DECIMAL(18,2) DEFAULT NULL COMMENT ''参考单价'' AFTER `unit`'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_post' AND COLUMN_NAME = 'remark') > 0, 'SELECT 1', 'ALTER TABLE `sys_post` ADD COLUMN `remark` VARCHAR(500) DEFAULT NULL COMMENT ''备注'' AFTER `sort_order`'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
