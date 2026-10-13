-- ============================================================
-- V2026_101__change_event_contract_writeback_flag.sql
-- 变更事件 → 合同累计变更额回写幂等标记（X4 幂等加固，幂等）
--
-- 背景：CHANGE_EVENT_APPROVED 事件由多个 Outbox 处理器扇出消费
--   （zw-budget 的 CBS 处理器 + zw-contract 的合同回写处理器）。
--   OutboxDispatcher 在任一处理器失败时会对「全部处理器」重试，
--   因此每个累加型处理器都必须自带幂等。CBS 处理器以成本流水幂等键
--   去重；本列用于合同回写处理器，保证同一事件只回写一次累计变更额。
-- ============================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'biz_change_event'
                      AND column_name = 'contract_change_written');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE biz_change_event ADD COLUMN contract_change_written TINYINT NOT NULL DEFAULT 0 COMMENT ''合同累计变更额是否已回写（0-否 1-是，幂等标记）''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
