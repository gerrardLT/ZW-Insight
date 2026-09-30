-- ============================================================
-- V2026_76__logical_delete_unique_guard.sql
-- 逻辑删除唯一键修复：活动行 guard=0；删除行 guard=NULL。MySQL UNIQUE 允许多行 NULL，因此可反复删除后重建。
-- ADD COLUMN 与索引变更均由 information_schema + PREPARE 条件执行。
-- 索引按有序列清单比对：目标定义跳过，旧定义以同一 ALTER 原子 DROP + ADD，缺失才 ADD。
-- 存量活动行若已有重复，ADD UNIQUE KEY 将以 ERROR 1062 中止，须先清理数据。
--
-- guard 仅引用 deleted，避免 MySQL generated column 不可引用 AUTO_INCREMENT 主键的限制；
-- deleted 变化时 STORED guard 自动重算，并作为复合 UNIQUE 的末列。
--
-- 特例：biz_cost_account_txn.uk_txn_source 不修改。该索引是幂等账本键，
--       source_type + source_id 必须永久占用，逻辑删除也不得释放。
-- ============================================================

SET @db = DATABASE();

-- sys_user.uk_username
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `sys_user` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user' AND INDEX_NAME='uk_username');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user' AND INDEX_NAME='uk_username');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_username` (`username`, `unique_active_guard`)', IF(@index_columns <> 'username,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `sys_user` DROP INDEX `uk_username`, ADD UNIQUE KEY `uk_username` (`username`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_user.uk_phone
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `sys_user` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user' AND INDEX_NAME='uk_phone');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user' AND INDEX_NAME='uk_phone');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_phone` (`phone`, `unique_active_guard`)', IF(@index_columns <> 'phone,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `sys_user` DROP INDEX `uk_phone`, ADD UNIQUE KEY `uk_phone` (`phone`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_dict.uk_dict_code
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_dict' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `sys_dict` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_dict' AND INDEX_NAME='uk_dict_code');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_dict' AND INDEX_NAME='uk_dict_code');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `sys_dict` ADD UNIQUE KEY `uk_dict_code` (`dict_code`, `unique_active_guard`)', IF(@index_columns <> 'dict_code,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `sys_dict` DROP INDEX `uk_dict_code`, ADD UNIQUE KEY `uk_dict_code` (`dict_code`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- msg_push_config.uk_business_type_tenant
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='msg_push_config' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `msg_push_config` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='msg_push_config' AND INDEX_NAME='uk_business_type_tenant');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='msg_push_config' AND INDEX_NAME='uk_business_type_tenant');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `msg_push_config` ADD UNIQUE KEY `uk_business_type_tenant` (`business_type`, `tenant_id`, `unique_active_guard`)', IF(@index_columns <> 'business_type,tenant_id,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `msg_push_config` DROP INDEX `uk_business_type_tenant`, ADD UNIQUE KEY `uk_business_type_tenant` (`business_type`, `tenant_id`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- serial_number_rule.uk_business_tenant
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='serial_number_rule' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `serial_number_rule` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='serial_number_rule' AND INDEX_NAME='uk_business_tenant');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='serial_number_rule' AND INDEX_NAME='uk_business_tenant');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `serial_number_rule` ADD UNIQUE KEY `uk_business_tenant` (`business_type`, `tenant_id`, `unique_active_guard`)', IF(@index_columns <> 'business_type,tenant_id,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `serial_number_rule` DROP INDEX `uk_business_tenant`, ADD UNIQUE KEY `uk_business_tenant` (`business_type`, `tenant_id`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_project.uk_project_code_tenant
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_project' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_project` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_project' AND INDEX_NAME='uk_project_code_tenant');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_project' AND INDEX_NAME='uk_project_code_tenant');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_project` ADD UNIQUE KEY `uk_project_code_tenant` (`project_code`, `tenant_id`, `unique_active_guard`)', IF(@index_columns <> 'project_code,tenant_id,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_project` DROP INDEX `uk_project_code_tenant`, ADD UNIQUE KEY `uk_project_code_tenant` (`project_code`, `tenant_id`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_construction_contract.uk_contract_code_tenant
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_construction_contract' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_construction_contract` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_construction_contract' AND INDEX_NAME='uk_contract_code_tenant');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_construction_contract' AND INDEX_NAME='uk_contract_code_tenant');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_construction_contract` ADD UNIQUE KEY `uk_contract_code_tenant` (`contract_code`, `tenant_id`, `unique_active_guard`)', IF(@index_columns <> 'contract_code,tenant_id,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_construction_contract` DROP INDEX `uk_contract_code_tenant`, ADD UNIQUE KEY `uk_contract_code_tenant` (`contract_code`, `tenant_id`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_purchase_contract.uk_contract_code_tenant
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_purchase_contract' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_purchase_contract` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_purchase_contract' AND INDEX_NAME='uk_contract_code_tenant');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_purchase_contract' AND INDEX_NAME='uk_contract_code_tenant');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_purchase_contract` ADD UNIQUE KEY `uk_contract_code_tenant` (`contract_code`, `tenant_id`, `unique_active_guard`)', IF(@index_columns <> 'contract_code,tenant_id,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_purchase_contract` DROP INDEX `uk_contract_code_tenant`, ADD UNIQUE KEY `uk_contract_code_tenant` (`contract_code`, `tenant_id`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_project_wbs_node.uk_wbs_code
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_project_wbs_node' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_project_wbs_node` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_project_wbs_node' AND INDEX_NAME='uk_wbs_code');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_project_wbs_node' AND INDEX_NAME='uk_wbs_code');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_project_wbs_node` ADD UNIQUE KEY `uk_wbs_code` (`tenant_id`, `project_id`, `node_code`, `unique_active_guard`)', IF(@index_columns <> 'tenant_id,project_id,node_code,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_project_wbs_node` DROP INDEX `uk_wbs_code`, ADD UNIQUE KEY `uk_wbs_code` (`tenant_id`, `project_id`, `node_code`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_cost_account.uk_cost_account_code
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_cost_account' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_cost_account` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_cost_account' AND INDEX_NAME='uk_cost_account_code');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_cost_account' AND INDEX_NAME='uk_cost_account_code');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_cost_account` ADD UNIQUE KEY `uk_cost_account_code` (`tenant_id`, `project_id`, `account_code`, `unique_active_guard`)', IF(@index_columns <> 'tenant_id,project_id,account_code,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_cost_account` DROP INDEX `uk_cost_account_code`, ADD UNIQUE KEY `uk_cost_account_code` (`tenant_id`, `project_id`, `account_code`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_change_event.uk_change_event_number
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_change_event' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_change_event` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_change_event' AND INDEX_NAME='uk_change_event_number');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_change_event' AND INDEX_NAME='uk_change_event_number');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_change_event` ADD UNIQUE KEY `uk_change_event_number` (`tenant_id`, `event_number`, `unique_active_guard`)', IF(@index_columns <> 'tenant_id,event_number,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_change_event` DROP INDEX `uk_change_event_number`, ADD UNIQUE KEY `uk_change_event_number` (`tenant_id`, `event_number`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_cost_account_link.uk_account_source
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_cost_account_link' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_cost_account_link` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_cost_account_link' AND INDEX_NAME='uk_account_source');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_cost_account_link' AND INDEX_NAME='uk_account_source');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_cost_account_link` ADD UNIQUE KEY `uk_account_source` (`tenant_id`, `account_id`, `source_type`, `source_id`, `unique_active_guard`)', IF(@index_columns <> 'tenant_id,account_id,source_type,source_id,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_cost_account_link` DROP INDEX `uk_account_source`, ADD UNIQUE KEY `uk_account_source` (`tenant_id`, `account_id`, `source_type`, `source_id`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_budget_control_config.uk_tenant_project
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_budget_control_config' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `sys_budget_control_config` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_budget_control_config' AND INDEX_NAME='uk_tenant_project');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_budget_control_config' AND INDEX_NAME='uk_tenant_project');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `sys_budget_control_config` ADD UNIQUE KEY `uk_tenant_project` (`tenant_id`, `project_id`, `unique_active_guard`)', IF(@index_columns <> 'tenant_id,project_id,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `sys_budget_control_config` DROP INDEX `uk_tenant_project`, ADD UNIQUE KEY `uk_tenant_project` (`tenant_id`, `project_id`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_machine_work_settlement.uk_settlement_code_tenant
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_machine_work_settlement' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_machine_work_settlement` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_machine_work_settlement' AND INDEX_NAME='uk_settlement_code_tenant');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_machine_work_settlement' AND INDEX_NAME='uk_settlement_code_tenant');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_machine_work_settlement` ADD UNIQUE KEY `uk_settlement_code_tenant` (`settlement_code`, `tenant_id`, `unique_active_guard`)', IF(@index_columns <> 'settlement_code,tenant_id,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_machine_work_settlement` DROP INDEX `uk_settlement_code_tenant`, ADD UNIQUE KEY `uk_settlement_code_tenant` (`settlement_code`, `tenant_id`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_project_member.uk_project_user
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_project_member' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_project_member` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_project_member' AND INDEX_NAME='uk_project_user');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_project_member' AND INDEX_NAME='uk_project_user');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_project_member` ADD UNIQUE KEY `uk_project_user` (`project_id`, `user_id`, `unique_active_guard`)', IF(@index_columns <> 'project_id,user_id,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_project_member` DROP INDEX `uk_project_user`, ADD UNIQUE KEY `uk_project_user` (`project_id`, `user_id`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_finance_lock.uk_period_project
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_finance_lock' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_finance_lock` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_finance_lock' AND INDEX_NAME='uk_period_project');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_finance_lock' AND INDEX_NAME='uk_period_project');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_finance_lock` ADD UNIQUE KEY `uk_period_project` (`period`, `project_id`, `tenant_code`, `unique_active_guard`)', IF(@index_columns <> 'period,project_id,tenant_code,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_finance_lock` DROP INDEX `uk_period_project`, ADD UNIQUE KEY `uk_period_project` (`period`, `project_id`, `tenant_code`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_tax_rate.uk_name_tenant
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_tax_rate' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_tax_rate` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_tax_rate' AND INDEX_NAME='uk_name_tenant');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_tax_rate' AND INDEX_NAME='uk_name_tenant');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_tax_rate` ADD UNIQUE KEY `uk_name_tenant` (`name`, `tenant_code`, `unique_active_guard`)', IF(@index_columns <> 'name,tenant_code,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_tax_rate` DROP INDEX `uk_name_tenant`, ADD UNIQUE KEY `uk_name_tenant` (`name`, `tenant_code`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_profit_snapshot.uk_month_project
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_profit_snapshot' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_profit_snapshot` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_profit_snapshot' AND INDEX_NAME='uk_month_project');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_profit_snapshot' AND INDEX_NAME='uk_month_project');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_profit_snapshot` ADD UNIQUE KEY `uk_month_project` (`snapshot_month`, `project_id`, `tenant_id`, `unique_active_guard`)', IF(@index_columns <> 'snapshot_month,project_id,tenant_id,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_profit_snapshot` DROP INDEX `uk_month_project`, ADD UNIQUE KEY `uk_month_project` (`snapshot_month`, `project_id`, `tenant_id`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_risk_register.uk_risk_code
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_risk_register' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_risk_register` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_risk_register' AND INDEX_NAME='uk_risk_code');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_risk_register' AND INDEX_NAME='uk_risk_code');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_risk_register` ADD UNIQUE KEY `uk_risk_code` (`risk_code`, `tenant_id`, `unique_active_guard`)', IF(@index_columns <> 'risk_code,tenant_id,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_risk_register` DROP INDEX `uk_risk_code`, ADD UNIQUE KEY `uk_risk_code` (`risk_code`, `tenant_id`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_monthly_operation_analysis.uk_project_month_category
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_monthly_operation_analysis' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_monthly_operation_analysis` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_monthly_operation_analysis' AND INDEX_NAME='uk_project_month_category');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_monthly_operation_analysis' AND INDEX_NAME='uk_project_month_category');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_monthly_operation_analysis` ADD UNIQUE KEY `uk_project_month_category` (`tenant_id`, `project_id`, `analysis_month`, `category_code`, `unique_active_guard`)', IF(@index_columns <> 'tenant_id,project_id,analysis_month,category_code,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_monthly_operation_analysis` DROP INDEX `uk_project_month_category`, ADD UNIQUE KEY `uk_project_month_category` (`tenant_id`, `project_id`, `analysis_month`, `category_code`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_entertainment_detail.uk_reimbursement_detail
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_entertainment_detail' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_entertainment_detail` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_entertainment_detail' AND INDEX_NAME='uk_reimbursement_detail');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_entertainment_detail' AND INDEX_NAME='uk_reimbursement_detail');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_entertainment_detail` ADD UNIQUE KEY `uk_reimbursement_detail` (`reimbursement_detail_id`, `unique_active_guard`)', IF(@index_columns <> 'reimbursement_detail_id,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_entertainment_detail` DROP INDEX `uk_reimbursement_detail`, ADD UNIQUE KEY `uk_reimbursement_detail` (`reimbursement_detail_id`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_reminder_config.uk_tenant
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_reminder_config' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_reminder_config` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_reminder_config' AND INDEX_NAME='uk_tenant');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_reminder_config' AND INDEX_NAME='uk_tenant');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_reminder_config` ADD UNIQUE KEY `uk_tenant` (`tenant_id`, `unique_active_guard`)', IF(@index_columns <> 'tenant_id,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_reminder_config` DROP INDEX `uk_tenant`, ADD UNIQUE KEY `uk_tenant` (`tenant_id`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_bank_balance.uk_account_date
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_bank_balance' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_bank_balance` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_bank_balance' AND INDEX_NAME='uk_account_date');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_bank_balance' AND INDEX_NAME='uk_account_date');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_bank_balance` ADD UNIQUE KEY `uk_account_date` (`account_id`, `snapshot_date`, `unique_active_guard`)', IF(@index_columns <> 'account_id,snapshot_date,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_bank_balance` DROP INDEX `uk_account_date`, ADD UNIQUE KEY `uk_account_date` (`account_id`, `snapshot_date`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_balance_reconciliation.uk_account_date
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_balance_reconciliation' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_balance_reconciliation` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_balance_reconciliation' AND INDEX_NAME='uk_account_date');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_balance_reconciliation' AND INDEX_NAME='uk_account_date');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_balance_reconciliation` ADD UNIQUE KEY `uk_account_date` (`account_id`, `reconciliation_date`, `unique_active_guard`)', IF(@index_columns <> 'account_id,reconciliation_date,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_balance_reconciliation` DROP INDEX `uk_account_date`, ADD UNIQUE KEY `uk_account_date` (`account_id`, `reconciliation_date`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_daily_cash_report.uk_report_date
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_daily_cash_report' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_daily_cash_report` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_daily_cash_report' AND INDEX_NAME='uk_report_date');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_daily_cash_report' AND INDEX_NAME='uk_report_date');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_daily_cash_report` ADD UNIQUE KEY `uk_report_date` (`report_date`, `tenant_id`, `unique_active_guard`)', IF(@index_columns <> 'report_date,tenant_id,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_daily_cash_report` DROP INDEX `uk_report_date`, ADD UNIQUE KEY `uk_report_date` (`report_date`, `tenant_id`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_stock_warning_config.uk_project_material
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_stock_warning_config' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_stock_warning_config` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_stock_warning_config' AND INDEX_NAME='uk_project_material');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_stock_warning_config' AND INDEX_NAME='uk_project_material');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_stock_warning_config` ADD UNIQUE KEY `uk_project_material` (`project_id`, `material_id`, `unique_active_guard`)', IF(@index_columns <> 'project_id,material_id,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_stock_warning_config` DROP INDEX `uk_project_material`, ADD UNIQUE KEY `uk_project_material` (`project_id`, `material_id`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- biz_contract_template.uk_template_code
SET @sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_contract_template' AND COLUMN_NAME='unique_active_guard'), 'SELECT 1', 'ALTER TABLE `biz_contract_template` ADD COLUMN `unique_active_guard` BIGINT GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_columns = (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_contract_template' AND INDEX_NAME='uk_template_code');
SET @index_non_unique = (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_contract_template' AND INDEX_NAME='uk_template_code');
SET @sql = IF(@index_columns IS NULL, 'ALTER TABLE `biz_contract_template` ADD UNIQUE KEY `uk_template_code` (`tenant_id`, `template_code`, `unique_active_guard`)', IF(@index_columns <> 'tenant_id,template_code,unique_active_guard' OR @index_non_unique <> 0, 'ALTER TABLE `biz_contract_template` DROP INDEX `uk_template_code`, ADD UNIQUE KEY `uk_template_code` (`tenant_id`, `template_code`, `unique_active_guard`)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
