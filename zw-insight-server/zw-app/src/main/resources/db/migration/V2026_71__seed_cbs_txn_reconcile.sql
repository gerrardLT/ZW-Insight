-- ============================================================
-- V2026_71__seed_cbs_txn_reconcile.sql
-- 补齐 CBS 成本账户流水，使「账户余额 = 流水汇总」（修复 audit-data-round7 Section 3.6 的 2 项 FAIL）
--
-- 【问题】2026-09-24 线上数据审计：PASS=65 FAIL=2
--   ❌ CBS actual_amount vs TXN(ACTUAL) 汇总 = 27（期望 0）
--   ❌ CBS commitment_amount vs TXN(COMMITMENT) 汇总 = 23（期望 0）
-- 【根因】V2026_66 演示种子直接写入了 biz_cost_account 的 actual_amount / commitment_amount，
--   但 **未写配套的 biz_cost_account_txn 流水**（该表 0 行）→ 违反 AGENTS.md
--   「累计值必须有单据支撑」原则。此前该检查一直走 SKIP 分支（CBS 账户数=0 时无数据可校），
--   所以补了 30 个账户后勾稽检查首次生效并暴露不一致——**这是审计正常工作，不是误报**。
-- 【为何不改审计脚本】把期望值放宽或加白名单等于掩盖问题；AGENTS.md 已明确记录
--   「把报错当成零违规的静默失败比漏检更危险」（2026-09-18 的 3.2 节事故）。故修数据不修标尺。
--
-- 【修复方式】为每个金额非 0 的账户补一行初始流水（delta_amount = 账户当前值），
--   使 SUM(txn.delta_amount) 与账户余额精确相等。
--
-- 【与 CostRollUpTask 的兼容性（关键，已推演）】
--   归集任务每日 02:30 按单据汇总做「目标绝对值对账」：delta = target − current，
--   并写一条 SRC_ROLLUP 流水 + 更新账户余额。因此本脚本执行后：
--     SUM(txn) = SEED(初始值) + ROLLUP(target − 初始值) = target = account.actual_amount
--   即**归集前后勾稽关系始终成立**，不会因后续对账而再次失衡。
--
-- 【幂等】NOT EXISTS 守卫（同账户同 amount_type 已有任意流水则跳过），可重复执行；
--   若归集任务已先跑过并写了 ROLLUP 流水，本脚本自动跳过该账户（不重复计）。
-- 【ID 段】99701-99749（ACTUAL）/ 99751-99799（COMMITMENT），在演示种子约定的
--   90001-99999 段内且为探针确认的空闲区间；用 ROW_NUMBER() 分配而非 id*10+N
--   （V2026_57 曾因此撑爆 BIGINT，见该脚本注释）。
-- ============================================================

-- 1) ACTUAL 初始流水（仅为 actual_amount 非 0 的账户补；0 值账户 SUM 天然为 0，无需流水）
INSERT INTO biz_cost_account_txn
    (id, project_id, account_id, amount_type, delta_amount, balance_after,
     source_type, source_id, source_number, occurred_at, remark,
     tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99700 + ROW_NUMBER() OVER (ORDER BY a.id),
       a.project_id, a.id, 'ACTUAL', a.actual_amount, a.actual_amount,
       'SEED', CONCAT('V2026_66:', a.id), CONCAT('SEED-ACTUAL-', a.account_code),
       COALESCE(a.created_at, NOW()),
       '演示种子初始实际成本（V2026_71 补齐流水，使账户余额与流水汇总一致；口径=对应支出合同 cumulative_settlement）',
       COALESCE(a.tenant_id, 1), 1, NOW(), NOW(), 0, 0
  FROM biz_cost_account a
 WHERE a.deleted = 0
   AND a.id BETWEEN 99611 AND 99657
   AND COALESCE(a.actual_amount, 0) <> 0
   AND NOT EXISTS (
         SELECT 1 FROM biz_cost_account_txn t
          WHERE t.account_id = a.id AND t.amount_type = 'ACTUAL' AND t.deleted = 0);

-- 2) COMMITMENT 初始流水（同上，仅非 0 账户）
INSERT INTO biz_cost_account_txn
    (id, project_id, account_id, amount_type, delta_amount, balance_after,
     source_type, source_id, source_number, occurred_at, remark,
     tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99750 + ROW_NUMBER() OVER (ORDER BY a.id),
       a.project_id, a.id, 'COMMITMENT', a.commitment_amount, a.commitment_amount,
       'SEED', CONCAT('V2026_66:', a.id), CONCAT('SEED-COMMIT-', a.account_code),
       COALESCE(a.created_at, NOW()),
       '演示种子初始已承诺金额（V2026_71 补齐流水；口径=合同/采购占用额）',
       COALESCE(a.tenant_id, 1), 1, NOW(), NOW(), 0, 0
  FROM biz_cost_account a
 WHERE a.deleted = 0
   AND a.id BETWEEN 99611 AND 99657
   AND COALESCE(a.commitment_amount, 0) <> 0
   AND NOT EXISTS (
         SELECT 1 FROM biz_cost_account_txn t
          WHERE t.account_id = a.id AND t.amount_type = 'COMMITMENT' AND t.deleted = 0);
