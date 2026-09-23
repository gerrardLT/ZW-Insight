-- ============================================================
-- V2026_63__forecast_overdue_unpaid.sql
-- 资金滚动预测：新增「逾期未付」构成列
--
-- 背景（2026-09-23 线上实测发现，属重大口径缺陷）：
--   原预测付款侧仅统计 payment_date 落在「未来窗口」内的已审批未付款单，
--   已过计划付款日但仍未支付的单据被完全排除在预测之外。
--   线上实测：16 条 APPROVED + UNPAID 付款申请（合计 58,500,000 元，
--   payment_date 分布 2025-06-20 ~ 2026-06-15）全部落在窗口外，
--   导致 2026-09 预测 net_gap = -2,600,000（显示 LOW 风险），
--   而真实待付缺口应为 58,500,000 - 2,600,000 = 55,900,000（应为 HIGH）。
--
-- 修复口径：
--   逾期未付金额计入「当月」（i=0）的 expected_payments —— 它们随时可能
--   形成现金流出，是最紧迫的资金压力；同时单列本字段记录其构成，
--   供前端区分展示（不隐藏构成，避免口径含糊）。
--   expected_payments = 当月计划内未付 + 逾期未付；overdue_unpaid = 其中的逾期部分。
--
-- 幂等：information_schema 检查 + PREPARE/EXECUTE 条件 ALTER（重复执行安全）。
-- ============================================================

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_fund_rolling_forecast' AND COLUMN_NAME = 'overdue_unpaid') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_fund_rolling_forecast` ADD COLUMN `overdue_unpaid` DECIMAL(18,2) DEFAULT 0.00 COMMENT ''其中：已逾期未付金额（payment_date 早于当月月初且仍未支付，仅当月快照非0）'' AFTER `expected_payments`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 存量快照回填：历史快照无法区分构成，统一置 0（不伪造历史构成数据）；
-- 下一次 FundForecastTask（每日 01:15）或手工触发预测后即为真实值。
UPDATE biz_fund_rolling_forecast SET overdue_unpaid = 0.00 WHERE overdue_unpaid IS NULL;
