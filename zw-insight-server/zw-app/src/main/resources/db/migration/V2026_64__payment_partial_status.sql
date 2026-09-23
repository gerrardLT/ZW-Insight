-- ============================================================
-- V2026_64__payment_partial_status.sql
-- 支付执行态增加「部分支付」档（资金流转 §8「07 部分付款」）
--
-- 背景（2026-09-24 intended-vs-implemented 审计）：
--   银行流水自 V2026_56 起支持部分勾稽（match_amount），但 pay_status 只有
--   UNPAID/PAID 两档，无法表达"已付一部分"：100 万申请已实付 60 万时仍显示
--   "未支付"，且滚动预测/待支付 TOP 按全额计入 → 重复夸大 40 万资金压力。
--   文档 §8 明确要求 11 档状态且"不要只有付了/没付"。
--
-- 本脚本：
--   1) pay_status 列注释更新为三档语义（UNPAID/PARTIAL_PAID/PAID）
--   2) 存量修正：已有勾稽但未足额的 APPROVED 单据 UNPAID → PARTIAL_PAID
--      （口径与 PaymentApplyService.refreshPayStatus 一致：
--        勾稽合计 = SUM(COALESCE(match_amount, amount))，仅 reconciled=1 且 matched_type='PAYMENT_APPLY'）
--   3) 补 pay_status 索引（若 V2026_56 已建则跳过）
--
-- 不变量：pay_status 变更**不回写** total_expense / 合同 cumulative_paid
--         （审批口径与现金口径正交，R7 审计基线 PASS=65 依赖此约定）。
-- 幂等：information_schema 条件 ALTER + 带守卫的 UPDATE（重复执行结果一致）。
-- ============================================================

-- 1) 列注释更新为三档（仅改 COMMENT，不改类型/默认值，存量 UNPAID/PAID 数据不受影响）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_payment_apply' AND COLUMN_NAME = 'pay_status') > 0,
    'ALTER TABLE `biz_payment_apply` MODIFY COLUMN `pay_status` VARCHAR(20) NOT NULL DEFAULT ''UNPAID'' COMMENT ''支付状态（UNPAID-未支付/PARTIAL_PAID-部分支付/PAID-已支付；现金口径，与审批状态status正交；V2026_64 增部分支付档）''',
    'SELECT 1'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 2) 存量修正：已有勾稽但未足额 → PARTIAL_PAID
--    守卫条件确保只改"确有部分勾稽"的单据；足额者保持 PAID（由 refreshPayStatus 维护），
--    无任何勾稽者保持 UNPAID。重复执行时命中 0 行（状态已为 PARTIAL_PAID），幂等安全。
UPDATE biz_payment_apply pa
SET pa.pay_status = 'PARTIAL_PAID'
WHERE pa.deleted = 0
  AND pa.status = 'APPROVED'
  AND pa.pay_status = 'UNPAID'
  AND pa.payment_amount IS NOT NULL
  AND pa.payment_amount > 0
  AND EXISTS (
      SELECT 1 FROM biz_bank_flow f
      WHERE f.deleted = 0 AND f.reconciled = 1
        AND f.matched_type = 'PAYMENT_APPLY' AND f.matched_id = pa.id
  )
  AND (
      SELECT IFNULL(SUM(COALESCE(f.match_amount, f.amount)), 0) FROM biz_bank_flow f
      WHERE f.deleted = 0 AND f.reconciled = 1
        AND f.matched_type = 'PAYMENT_APPLY' AND f.matched_id = pa.id
  ) < pa.payment_amount;

-- 3) 反向守卫：被误置为 PARTIAL_PAID 但实际已足额/无勾稽的单据纠正回来（幂等自愈）
UPDATE biz_payment_apply pa
SET pa.pay_status = 'PAID'
WHERE pa.deleted = 0 AND pa.pay_status = 'PARTIAL_PAID'
  AND pa.payment_amount IS NOT NULL AND pa.payment_amount > 0
  AND (
      SELECT IFNULL(SUM(COALESCE(f.match_amount, f.amount)), 0) FROM biz_bank_flow f
      WHERE f.deleted = 0 AND f.reconciled = 1
        AND f.matched_type = 'PAYMENT_APPLY' AND f.matched_id = pa.id
  ) >= pa.payment_amount;

UPDATE biz_payment_apply pa
SET pa.pay_status = 'UNPAID', pa.pay_date = NULL, pa.pay_account_id = NULL
WHERE pa.deleted = 0 AND pa.pay_status = 'PARTIAL_PAID'
  AND NOT EXISTS (
      SELECT 1 FROM biz_bank_flow f
      WHERE f.deleted = 0 AND f.reconciled = 1
        AND f.matched_type = 'PAYMENT_APPLY' AND f.matched_id = pa.id
  );

-- 4) pay_status 索引（V2026_56 已建则跳过）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_payment_apply' AND INDEX_NAME = 'idx_pay_status') = 0,
    'ALTER TABLE `biz_payment_apply` ADD INDEX `idx_pay_status` (`pay_status`)',
    'SELECT 1'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;
