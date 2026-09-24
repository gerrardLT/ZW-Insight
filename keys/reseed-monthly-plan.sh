#!/usr/bin/env bash
###############################################################################
# reseed-monthly-plan.sh — 月度资金计划演示夹具跨月补齐（服务器端执行）
#
# 背景（审计 7.5 第 4 项）：V2026_66 种子的 plan_year/plan_month 取执行当时的
# CURDATE()，而 Flyway 只跑一次 → 跨月后招待费第 8 类「超月度限额」预警因
# 「无当月计划」不判定（如实行为，非缺陷）。生产环境的月度计划由人编制，
# 本脚本仅用于持续演示环境把两条演示计划（项目 90001 / 公司级）拨到当月。
#
# 性质声明：这是演示数据运维动作，不是业务功能——不改任何金额（招待费限额
# 8000 元 vs 当月实际 13100 元的预警对局保持原样），只改所属年月并补行。
# 全程幂等：UPDATE 拨月 + INSERT ... WHERE NOT EXISTS 补漏，可重复执行。
#
# 用法：bash keys/reseed-monthly-plan.sh            # 补齐到当月
#       bash keys/reseed-monthly-plan.sh --show    # 只查看当前计划归属月
###############################################################################
set -uo pipefail
MYSQL_CT="${ZWI_MYSQL_CT:-zwi-mysql}"
DB="${ZWI_DB:-zw_insight}"
Q() { docker exec "$MYSQL_CT" mysql -uroot -pzwinsight123 -D "$DB" -e "$1" 2>/dev/null; }

if [ "${1:-}" = "--show" ]; then
  Q "SELECT id, plan_year, plan_month, project_id, expense_plan, status FROM biz_fund_monthly_plan WHERE id IN (99661,99662);"
  exit 0
fi

echo "[1/3] 把演示计划 99661(项目90001)/99662(公司级) 拨到当前年月..."
Q "UPDATE biz_fund_monthly_plan SET plan_year=YEAR(CURDATE()), plan_month=MONTH(CURDATE()), updated_at=NOW() WHERE id IN (99661,99662) AND deleted=0;"

echo "[2/3] 补漏：若两条主计划不存在（新库/被删）按 V2026_66 原样重建..."
Q "INSERT INTO biz_fund_monthly_plan (id, tenant_id, plan_year, plan_month, project_id, income_plan, expense_plan, actual_income, actual_expense, remark, status, created_by, created_at, updated_at, deleted, version)
SELECT 99661, 1, YEAR(CURDATE()), MONTH(CURDATE()), 90001, 2000000.00, 1328000.00, 0.00, 0.00, '演示：滨江花园一期当月资金计划（招待费限额 8000 元，用于验证超月度限额预警）', 'APPROVED', 1, NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM biz_fund_monthly_plan WHERE id = 99661);"
Q "INSERT INTO biz_fund_monthly_plan (id, tenant_id, plan_year, plan_month, project_id, income_plan, expense_plan, actual_income, actual_expense, remark, status, created_by, created_at, updated_at, deleted, version)
SELECT 99662, 1, YEAR(CURDATE()), MONTH(CURDATE()), NULL, 8000000.00, 5200000.00, 0.00, 0.00, '演示：公司级当月资金计划', 'APPROVED', 1, NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM biz_fund_monthly_plan WHERE id = 99662);"
Q "INSERT IGNORE INTO biz_fund_plan_detail (id, tenant_id, plan_id, direction, category_code, amount, remark, created_by, created_at, updated_at, deleted, version) VALUES
(99663, 1, 99661, 'EXPENSE', 'EXP-INDIRECT-ENTERTAIN',    8000.00, '招待费月度限额（当月实际 13100 元 → 触发超月度限额预警）', 1, NOW(), NOW(), 0, 0),
(99664, 1, 99661, 'EXPENSE', 'EXP-INDIRECT-TRAVEL',      20000.00, '差旅费计划', 1, NOW(), NOW(), 0, 0),
(99665, 1, 99661, 'EXPENSE', 'EXP-DIRECT-MATERIAL',     800000.00, '材料款计划', 1, NOW(), NOW(), 0, 0),
(99666, 1, 99661, 'EXPENSE', 'EXP-DIRECT-LABOR',        500000.00, '劳务款计划', 1, NOW(), NOW(), 0, 0),
(99667, 1, 99662, 'EXPENSE', 'EXP-INDIRECT-ENTERTAIN',   30000.00, '公司级招待费限额', 1, NOW(), NOW(), 0, 0);"

echo "[3/3] 复核（应为当前年月，明细合计=主表 expense_plan）..."
Q "SELECT id, plan_year, plan_month, project_id, expense_plan, status FROM biz_fund_monthly_plan WHERE id IN (99661,99662);"
Q "SELECT p.id, IFNULL(SUM(d.amount),0) detail_sum, p.expense_plan FROM biz_fund_monthly_plan p LEFT JOIN biz_fund_plan_detail d ON d.plan_id = p.id AND d.deleted = 0 WHERE p.id IN (99661,99662) GROUP BY p.id, p.expense_plan;"
echo "完成。跨月后第 8 类「超月度限额」预警恢复判定（当月招待费实际 > 8000 即命中）。"
