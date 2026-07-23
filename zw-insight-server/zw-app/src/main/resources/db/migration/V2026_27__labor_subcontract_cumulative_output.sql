-- ============================================
-- P0 数据止血 - 分包/劳务合同新增"累计产值"字段（Flyway 类路径版）
-- 版本: V2026_27
-- 背景:
--   同名逻辑曾以 deploy/db-init/27_V2026_22 形式存在，但从未加入 Flyway 类路径
--   (classpath:db/migration)，导致线上 Flyway 版本从 2026.21 直接跳到 2026.24，
--   biz_subcontract / biz_labor_contract 始终缺少 cumulative_output 列。
--   表现：labor/contract、subcontract/contract、archive 级联查询报
--        "Unknown column 'cumulative_output' in 'field list'" 返回 500。
--   本迁移以前向版本号补齐该列（out-of-order=false，不可用低版本号回补）。
-- 影响:
--   1) biz_subcontract 增加 cumulative_output（累计产值，与累计结算/累计付款分离）
--   2) biz_labor_contract 增加 cumulative_output
--   幂等：已存在则跳过，历史被污染的 cumulative_settlement 数据不在此自动纠正。
-- ============================================

-- 分包合同：新增累计产值字段（幂等）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_subcontract' AND COLUMN_NAME = 'cumulative_output') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_subcontract` ADD COLUMN `cumulative_output` DECIMAL(18,2) DEFAULT 0 COMMENT ''累计产值'' AFTER `payment_terms`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 劳务合同：新增累计产值字段（幂等）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_labor_contract' AND COLUMN_NAME = 'cumulative_output') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_labor_contract` ADD COLUMN `cumulative_output` DECIMAL(18,2) DEFAULT 0 COMMENT ''累计产值'' AFTER `payment_terms`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;
