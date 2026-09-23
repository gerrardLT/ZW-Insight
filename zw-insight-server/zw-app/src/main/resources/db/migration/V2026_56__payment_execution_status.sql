-- ============================================================================
-- V2026_56__payment_execution_status.sql
-- 资金闭环阶段一 1A：支付执行态（审批通过 ≠ 已付款）
--
-- 背景（docs/资金流转流程.md §8 资金状态设计；audit-reports/cockpit-fund-gap-analysis-2026-09-22.md）：
--   现状 PaymentApplyService.onApproved 审批通过即回写 total_expense（审批口径），
--   系统无"银行实际支付"执行态，银行流水勾稽（biz_bank_flow.matchFlow）不回写单据，
--   导致"已批未付"不可知、滚动预测付款侧语义失真。
--
-- 本脚本：
--   1) biz_payment_apply 增加 pay_status/pay_date/pay_account_id（支付执行态三列）；
--   2) biz_bank_flow 增加 match_amount（支持部分勾稽，NULL=整笔勾稽，兼容存量）；
--   3) 存量初始化：仅对"已勾稽流水金额合计 ≥ 付款金额"的 APPROVED 单据置 PAID
--      （以最早勾稽流水日期为 pay_date）；其余保持 UNPAID，如实反映，不伪造。
--
-- 金额纪律（口径不变量）：
--   total_expense 仍为审批口径（仅由 PaymentApplyService.onApproved / 资金调拨回写），
--   pay_status 为现金口径的增量语义，不回写项目账，不影响 R7 审计基线与 52_V2026_50 勾稽种子。
--
-- 幂等：information_schema 检查 + PREPARE/EXECUTE 条件 ALTER；初始化 UPDATE 带守卫可重复执行。
-- ============================================================================

-- ============ 1. biz_payment_apply 增加支付执行态三列 ============

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_payment_apply' AND COLUMN_NAME = 'pay_status') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_payment_apply` ADD COLUMN `pay_status` VARCHAR(20) NOT NULL DEFAULT ''UNPAID'' COMMENT ''支付状态（UNPAID-未支付/PAID-已支付；现金口径，与审批状态status正交）'' AFTER `status`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_payment_apply' AND COLUMN_NAME = 'pay_date') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_payment_apply` ADD COLUMN `pay_date` DATE NULL COMMENT ''实际支付日期（银行流水勾稽回写）'' AFTER `pay_status`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_payment_apply' AND COLUMN_NAME = 'pay_account_id') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_payment_apply` ADD COLUMN `pay_account_id` BIGINT NULL COMMENT ''支付账户ID（biz_bank_account.id，流水勾稽回写）'' AFTER `pay_date`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_payment_apply' AND COLUMN_NAME = 'pay_status') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_payment_apply' AND INDEX_NAME = 'idx_pay_status') = 0,
    'ALTER TABLE `biz_payment_apply` ADD INDEX `idx_pay_status` (`pay_status`)',
    'SELECT 1'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- ============ 2. biz_bank_flow 增加勾稽金额（部分勾稽支持） ============

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_bank_flow' AND COLUMN_NAME = 'match_amount') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_bank_flow` ADD COLUMN `match_amount` DECIMAL(18,2) NULL COMMENT ''本次勾稽金额（NULL=按流水整笔金额勾稽，兼容存量）'' AFTER `matched_id`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- ============ 3. 存量初始化：已足额勾稽的 APPROVED 付款申请置 PAID ============
-- 守卫：仅当勾稽流水金额合计（match_amount 空则取流水 amount）>= 付款金额才置 PAID；
-- 不足额的保持 UNPAID（部分支付事实由流水体现，不伪造全额支付）。
-- 幂等：条件含 pay_status='UNPAID'，重复执行不产生二次变更。

UPDATE biz_payment_apply pa
JOIN (
    SELECT bf.matched_id,
           MIN(bf.flow_date) AS first_flow_date,
           MIN(bf.account_id) AS first_account_id,
           SUM(IFNULL(bf.match_amount, bf.amount)) AS matched_total
    FROM biz_bank_flow bf
    WHERE bf.reconciled = 1
      AND bf.matched_type = 'PAYMENT_APPLY'
      AND bf.deleted = 0
    GROUP BY bf.matched_id
) f ON f.matched_id = pa.id
SET pa.pay_status = 'PAID',
    pa.pay_date = f.first_flow_date,
    pa.pay_account_id = f.first_account_id
WHERE pa.status = 'APPROVED'
  AND pa.pay_status = 'UNPAID'
  AND pa.deleted = 0
  AND f.matched_total >= IFNULL(pa.payment_amount, 0);
