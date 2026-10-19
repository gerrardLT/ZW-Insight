#!/usr/bin/env bash
# ============================================================
# verify-payable.sh — 应付台账数据勾稽核验（V2026_115 配套）
# 运行位置：目标服务器（依赖 docker exec zwi-mysql）
# 全程只读，不写入任何数据。
#
# 检查项：
#   Section 1: biz_project.payable_amount = biz_payable OPEN 余额合计（项目级双写不变量）
#   Section 2: biz_payable 所有 OPEN 行的 paid_amount ≤ payable_amount（不超核销）
#   Section 3: biz_payable_write_off 明细合计 ≤ 对应 biz_payable.paid_amount
#   Section 4: BACKFILL 行的 OPEN 余额 = 合同表 cumulative_settlement − cumulative_paid（回填口径不变量）
#
# 用法：bash verify-payable.sh
# 退出码：0=全部通过，1=存在 FAIL
# ============================================================
set -euo pipefail

PW="${ZWI_DB_PW:-zwinsight123}"
DB="${ZWI_DB:-zw_insight}"
MYSQL_CT="${ZWI_MYSQL_CT:-zwi-mysql}"

PASS=0; FAIL=0

sql() {
  docker exec -i "$MYSQL_CT" sh -c "mysql -uroot -p$PW -N -B $DB -e \"SET NAMES utf8mb4; $1\"" 2>/dev/null
}

check() {
  local desc="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    echo "  ✅ $desc (=$actual)"
    PASS=$((PASS+1))
  else
    echo "  ❌ $desc: expected=$expected actual=$actual"
    FAIL=$((FAIL+1))
  fi
}

echo "==== 应付台账勾稽核验 ===="

echo "Section 1: 项目单值不变量（payable_amount = OPEN 余额合计）"
MISMATCH=$(sql "
SELECT COUNT(*) FROM (
  SELECT p.id, p.payable_amount,
    IFNULL((SELECT SUM(bp.payable_amount - bp.paid_amount) FROM biz_payable bp
      WHERE bp.project_id = p.id AND bp.status = 'OPEN' AND bp.deleted = 0), 0) AS ledger_open
  FROM biz_project p WHERE p.deleted = 0
) t WHERE t.payable_amount != t.ledger_open")
check "项目 payable_amount 与台账 OPEN 合计不一致行数" "0" "$MISMATCH"

echo "Section 2: OPEN 行不超核销（paid_amount ≤ payable_amount）"
OVER=$(sql "
SELECT COUNT(*) FROM biz_payable
WHERE deleted = 0 AND status = 'OPEN' AND paid_amount > payable_amount")
check "OPEN 行超核销行数" "0" "$OVER"

echo "Section 3: 核销明细合计 ≤ 台账 paid_amount"
OVER2=$(sql "
SELECT COUNT(*) FROM (
  SELECT w.payable_id, SUM(w.amount) AS detail_total, p.paid_amount
  FROM biz_payable_write_off w
  JOIN biz_payable p ON p.id = w.payable_id AND p.deleted = 0
  WHERE w.deleted = 0
  GROUP BY w.payable_id, p.paid_amount
  HAVING detail_total > p.paid_amount
) t")
check "核销明细超 paid_amount 行数" "0" "$OVER2"

echo "Section 4: BACKFILL 行余额 = 合同表（结算−已付）"
BACKFILL_MISMATCH=$(sql "
SELECT COUNT(*) FROM (
  SELECT p.id, p.payable_amount - p.paid_amount AS ledger_bal,
    GREATEST(0, COALESCE(c.cumulative_settlement,0) - COALESCE(c.cumulative_paid,0)) AS contract_bal
  FROM biz_payable p
  LEFT JOIN biz_purchase_contract c ON p.source_type = 'BACKFILL' AND p.contract_category = 'PURCHASE' AND p.source_id = c.id
  WHERE p.source_type = 'BACKFILL' AND p.contract_category = 'PURCHASE' AND p.deleted = 0
  UNION ALL
  SELECT p.id, p.payable_amount - p.paid_amount,
    GREATEST(0, COALESCE(c.cumulative_settlement,0) - COALESCE(c.cumulative_paid,0))
  FROM biz_payable p
  LEFT JOIN biz_labor_contract c ON p.source_type = 'BACKFILL' AND p.contract_category = 'LABOR' AND p.source_id = c.id
  WHERE p.source_type = 'BACKFILL' AND p.contract_category = 'LABOR' AND p.deleted = 0
  UNION ALL
  SELECT p.id, p.payable_amount - p.paid_amount,
    GREATEST(0, COALESCE(c.cumulative_settlement,0) - COALESCE(c.cumulative_paid,0))
  FROM biz_payable p
  LEFT JOIN biz_machine_contract c ON p.source_type = 'BACKFILL' AND p.contract_category = 'MACHINE' AND p.source_id = c.id
  WHERE p.source_type = 'BACKFILL' AND p.contract_category = 'MACHINE' AND p.deleted = 0
  UNION ALL
  SELECT p.id, p.payable_amount - p.paid_amount,
    GREATEST(0, COALESCE(c.cumulative_settlement,0) - COALESCE(c.cumulative_paid,0))
  FROM biz_payable p
  LEFT JOIN biz_subcontract c ON p.source_type = 'BACKFILL' AND p.contract_category = 'SUBCONTRACT' AND p.source_id = c.id
  WHERE p.source_type = 'BACKFILL' AND p.contract_category = 'SUBCONTRACT' AND p.deleted = 0
  UNION ALL
  SELECT p.id, p.payable_amount - p.paid_amount,
    GREATEST(0, COALESCE(c.cumulative_settlement,0) - COALESCE(c.cumulative_paid,0))
  FROM biz_payable p
  LEFT JOIN biz_other_contract c ON p.source_type = 'BACKFILL' AND p.contract_category = 'OTHER' AND p.source_id = c.id
  WHERE p.source_type = 'BACKFILL' AND p.contract_category = 'OTHER' AND p.deleted = 0
) t WHERE t.contract_bal IS NOT NULL AND t.ledger_bal != t.contract_bal")
check "BACKFILL 行余额与合同表不一致行数" "0" "$BACKFILL_MISMATCH"

echo ""
echo "==== 结果：PASS=$PASS FAIL=$FAIL ===="
[ "$FAIL" -eq 0 ] || exit 1
