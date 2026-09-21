-- ============================================
-- P0 数据止血 - 分包/劳务合同新增"累计产值"字段
-- 版本: V2026_22
-- 背景:
--   分包/劳务产值上报(output-report)提交后需回写合同累计产值，
--   但 biz_subcontract / biz_labor_contract 原本只有 cumulative_settlement(累计结算)
--   与 cumulative_paid(累计付款)，缺少独立的"累计产值"字段。
--   历史实现将产值误加到 cumulative_settlement，与"结算单"回写同一字段造成重复累加，
--   导致累计结算虚高。本次新增 cumulative_output 字段，使"产值"与"结算"彻底分离。
-- 影响:
--   1) biz_subcontract 增加 cumulative_output
--   2) biz_labor_contract 增加 cumulative_output
--   注意: 历史被污染的 cumulative_settlement 数据不在此脚本自动纠正，
--        如需纠正请由业务人员核对后单独处理（涉及资金口径，禁止盲目回算）。
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
