-- V2026_30: 班组(biz_team) 补充 work_type(工种) 字段
--
-- 背景：
--   前端 labor/team 列表页与表单存在"工种(workType)"查询/录入项，但后端 biz_team 无对应列，
--   导致工种查询静默失效、工种列永远空白。本次补充真实列。
--   成员数(memberCount)为运行期从花名册(biz_labor_roster)聚合的透传字段，不落库。
-- 方案：用 information_schema 判存在性，不存在才 ADD COLUMN（幂等，可重复执行）。

SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_team' AND COLUMN_NAME = 'work_type') > 0, 'SELECT 1', 'ALTER TABLE `biz_team` ADD COLUMN `work_type` VARCHAR(50) DEFAULT NULL COMMENT ''工种'' AFTER leader_phone'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
