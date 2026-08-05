-- ============================================================
-- V2026_37: 业务编号唯一键改为「租户内唯一」（修复跨租户编号撞号）
--
-- 背景（真实缺陷）：
--   SerialNumberService 按租户维护 Redis 序列（serial:{tenantId}:{type}:{date}），
--   但生成的编号 = 前缀+日期+序号，若两个租户使用相同前缀（如默认均为 PRJ/HT/CG），
--   同一天创建同类单据会生成完全相同的编号；而以下业务编号列的唯一键为「全局唯一」
--   （不含 tenant_id），导致第二个租户创建时抛
--   Duplicate entry 'PRJ202607300001' for key 'uk_project_code' → 创建 500。
--
-- 正确语义：业务编号是「租户内」唯一标识，不同租户可有相同编号（数据本就租户隔离）。
--   故唯一键应为复合 (业务编号, tenant_id)。
--
-- 影响表（由 information_schema 实测确认为全局唯一且由编号生成器/月度编号产生）：
--   biz_project.project_code            (uk_project_code)
--   biz_construction_contract.contract_code (uk_contract_code)
--   biz_purchase_contract.contract_code     (uk_contract_code)
--   biz_machine_work_settlement.settlement_code (uk_settlement_code)
--
-- 不改：sys_tenant.tenant_code / sys_user.username（平台级，应保持全局唯一）；
--       biz_tax_rate/biz_finance_lock（已含 tenant_code）；sys_dict（字典编码另议）。
--
-- 幂等：仅当「旧唯一索引存在」且「表含 tenant_id 列」时才 DROP+ADD，可重复执行。
-- 安全：原全局唯一 → 租户内更不可能重复，ADD UNIQUE 不会因存量数据失败。
-- ============================================================

-- ---------- biz_project ----------
SET @has_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_project' AND INDEX_NAME = 'uk_project_code');
SET @has_tenant = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_project' AND COLUMN_NAME = 'tenant_id');
SET @sql = IF(@has_idx > 0 AND @has_tenant > 0,
    'ALTER TABLE `biz_project` DROP INDEX `uk_project_code`, ADD UNIQUE KEY `uk_project_code_tenant` (`project_code`, `tenant_id`)',
    'SELECT 1');
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

-- ---------- biz_construction_contract ----------
SET @has_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_construction_contract' AND INDEX_NAME = 'uk_contract_code');
SET @has_tenant = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_construction_contract' AND COLUMN_NAME = 'tenant_id');
SET @sql = IF(@has_idx > 0 AND @has_tenant > 0,
    'ALTER TABLE `biz_construction_contract` DROP INDEX `uk_contract_code`, ADD UNIQUE KEY `uk_contract_code_tenant` (`contract_code`, `tenant_id`)',
    'SELECT 1');
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

-- ---------- biz_purchase_contract ----------
SET @has_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_purchase_contract' AND INDEX_NAME = 'uk_contract_code');
SET @has_tenant = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_purchase_contract' AND COLUMN_NAME = 'tenant_id');
SET @sql = IF(@has_idx > 0 AND @has_tenant > 0,
    'ALTER TABLE `biz_purchase_contract` DROP INDEX `uk_contract_code`, ADD UNIQUE KEY `uk_contract_code_tenant` (`contract_code`, `tenant_id`)',
    'SELECT 1');
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;

-- ---------- biz_machine_work_settlement ----------
SET @has_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_machine_work_settlement' AND INDEX_NAME = 'uk_settlement_code');
SET @has_tenant = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_machine_work_settlement' AND COLUMN_NAME = 'tenant_id');
SET @sql = IF(@has_idx > 0 AND @has_tenant > 0,
    'ALTER TABLE `biz_machine_work_settlement` DROP INDEX `uk_settlement_code`, ADD UNIQUE KEY `uk_settlement_code_tenant` (`settlement_code`, `tenant_id`)',
    'SELECT 1');
PREPARE __stmt FROM @sql; EXECUTE __stmt; DEALLOCATE PREPARE __stmt;
