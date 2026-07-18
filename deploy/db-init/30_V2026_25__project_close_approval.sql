-- 30_V2026_25: 项目结项/关闭审批流程 - biz_project 补充 workflow_instance_id 字段
-- （与 zw-app/src/main/resources/db/migration/V2026_25__project_close_approval.sql 内容一致，供全量部署脚本按序执行）
--
-- 背景：
--   项目结项(closeProject)此前直接置 status=CLOSED，未经过审批流程。
--   本次改造引入 project_close_approval 审批流：COMPLETED --发起结项--> CLOSING --通过--> CLOSED / --驳回--> COMPLETED。
--   需要在 biz_project 上记录关联的流程实例ID(workflow_instance_id)。
-- 方案：用 information_schema 判存在性，不存在才 ADD COLUMN（幂等，可重复执行）。

-- 1. 补充 workflow_instance_id 字段（关联 Flowable 流程实例）
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_project' AND COLUMN_NAME = 'workflow_instance_id') > 0, 'SELECT 1', 'ALTER TABLE `biz_project` ADD COLUMN `workflow_instance_id` VARCHAR(64) DEFAULT NULL COMMENT ''结项审批流程实例ID'' AFTER status'));
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

-- 2. 更新 status 列注释，纳入 CLOSING（结项审批中）状态
ALTER TABLE `biz_project` MODIFY COLUMN `status` VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/FILED/TENDERING/WON/CONSTRUCTION/COMPLETED/CLOSING/CLOSED）';
