#!/bin/bash
# 在事务内验证 V2026_66 种子脚本：执行 → 10 项检查 → ROLLBACK（零副作用）
# 目的：部署前确认 SQL 语法正确（Flyway 语法错会导致应用启动失败）且数据自洽
set -uo pipefail
PW="${ZWI_DB_PW:-zwinsight123}"
DB="${ZWI_DB:-zw_insight}"
CT="${ZWI_MYSQL_CT:-zwi-mysql}"
WORK=/root/zwi-deploy

if [ ! -f "$WORK/V2026_66__seed_cbs_entertainment.sql" ]; then
  echo "缺少种子脚本：$WORK/V2026_66__seed_cbs_entertainment.sql" >&2
  exit 2
fi
if [ ! -f "$WORK/verify-seed-cbs-checks.sql" ]; then
  echo "缺少验证脚本：$WORK/verify-seed-cbs-checks.sql" >&2
  exit 2
fi

echo "START TRANSACTION;" > "$WORK/_pre.sql"

cat "$WORK/_pre.sql" "$WORK/V2026_66__seed_cbs_entertainment.sql" "$WORK/verify-seed-cbs-checks.sql" \
  | docker exec -i "$CT" mysql -uroot -p"$PW" --default-character-set=utf8mb4 --table "$DB" 2>&1 \
  | grep -v "Using a password"

echo ""
echo "=== 事务外复核：确认回滚成功（各表应仍为 0 行）==="
docker exec -i "$CT" mysql -uroot -p"$PW" --default-character-set=utf8mb4 -N -B "$DB" -e \
  "SELECT 'cost_account_99611_57', COUNT(*) FROM biz_cost_account WHERE id BETWEEN 99611 AND 99657
   UNION ALL SELECT 'wbs_99601_09', COUNT(*) FROM biz_project_wbs_node WHERE id BETWEEN 99601 AND 99609
   UNION ALL SELECT 'reimb_99671_75', COUNT(*) FROM biz_project_reimbursement WHERE id BETWEEN 99671 AND 99675
   UNION ALL SELECT 'ent_99691_700', COUNT(*) FROM biz_entertainment_detail WHERE id BETWEEN 99691 AND 99700
   UNION ALL SELECT 'plan_99661_62', COUNT(*) FROM biz_fund_monthly_plan WHERE id IN (99661,99662)
   UNION ALL SELECT 'detail_99061_code_fixed', COUNT(*) FROM biz_reimbursement_detail WHERE id=99061 AND category_code IS NOT NULL;" 2>&1 \
  | grep -v "Using a password"
