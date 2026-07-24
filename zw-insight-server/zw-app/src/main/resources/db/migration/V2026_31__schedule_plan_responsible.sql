-- V2026_31: 进度计划(biz_schedule_plan) 补充 responsible(负责人) 字段
--
-- 背景：
--   前端 site/schedule 列表页与表单存在"负责人(responsible)"列/录入项，但后端 biz_schedule_plan 无对应列，
--   导致负责人列永远空白、编辑提交时负责人丢失。本次补充真实列。
-- 方案：用 information_schema 判存在性，不存在才 ADD COLUMN（幂等，可重复执行）。

SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_schedule_plan' AND COLUMN_NAME = 'responsible') > 0, 'SELECT 1', 'ALTER TABLE `biz_schedule_plan` ADD COLUMN `responsible` VARCHAR(50) DEFAULT NULL COMMENT ''负责人'' AFTER task_detail'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
