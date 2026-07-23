#!/usr/bin/env bash
###############################################################################
# verify-seed.sh  —  演示种子数据（31_V2026_26__seed_demo_data.sql）验证脚本
#
# 运行位置：服务器（需要 docker exec zwi-mysql 与后端可达）
# 目的：确认种子数据已正确落库且能被真实接口读取。
#   1) DB 层：直连 MySQL 统计固定 ID 段（90001-99999）在各关键表的行数
#   2) API 层：复用 verify-base.sh 真实登录 + 带 token 调用分页接口抽查
#
# 全程真实数据、真实接口，禁止 mock。幂等只读，不写业务数据。
#
# 用法：
#   verify-seed.sh              完整验证（DB 统计 + API 抽查）
#   verify-seed.sh db           仅 DB 行数统计
#   verify-seed.sh api          仅 API 抽查
#   verify-seed.sh import       导入种子 SQL 后再统计（可重复执行，INSERT IGNORE）
###############################################################################
set -uo pipefail

PW="${ZWI_DB_PW:-zwinsight123}"
DB="${ZWI_DB:-zw_insight}"
MYSQL_CT="${ZWI_MYSQL_CT:-zwi-mysql}"
WORKDIR="${ZWI_WORKDIR:-/root/zwi-deploy}"
SEED_SQL="${ZWI_SEED_SQL:-$WORKDIR/db-init/31_V2026_26__seed_demo_data.sql}"
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log() { echo "[$(date +%H:%M:%S)] $*"; }

# 直连 MySQL 执行单条 SQL，-N 去表头
Q() { docker exec -i "$MYSQL_CT" sh -c "mysql -uroot -p$PW -N -e \"$1\"" 2>/dev/null; }

# ---------------------------------------------------------------------------
# import：导入种子 SQL（INSERT IGNORE 幂等，可重复执行）
# ---------------------------------------------------------------------------
import_seed() {
  if [ ! -f "$SEED_SQL" ]; then
    log "种子 SQL 不存在：$SEED_SQL" >&2
    return 1
  fi
  log "导入种子 SQL：$SEED_SQL"
  docker exec -i "$MYSQL_CT" sh -c "mysql -uroot -p$PW $DB" < "$SEED_SQL" 2>&1 \
    | grep -iv "1062" | grep -i error | head -20
  log "导入完成（重复键 1062 已忽略）"
}

# ---------------------------------------------------------------------------
# db_check：统计固定 ID 段在各层关键表的行数，逐项打印
# ---------------------------------------------------------------------------
db_check() {
  echo "================ DB 行数统计（ID 段 90001-99999） ================"
  # 表名:期望行数（0 表示仅统计不校验）
  local checks=(
    "sys_org:7"
    "sys_post:7"
    "sys_role:5"
    "sys_user:7"
    "sys_user_role:7"
    "sys_user_project:8"
    "sys_role_menu:63"
    "sys_dict:8"
    "sys_dict_item:30"
    "bd_material_category:5"
    "bd_material:15"
    "bd_supplier:6"
    "bd_owner:3"
    "biz_project:3"
    "biz_project_member:8"
    "biz_tender_register:1"
    "biz_construction_contract:3"
    "biz_boq_item:8"
    "biz_budget:2"
    "biz_budget_detail:10"
    "biz_output_report:3"
    "biz_final_settlement:1"
    "biz_material_inbound:2"
    "biz_machine_ledger:2"
    "biz_team:2"
    "biz_labor_roster:6"
    "biz_subcontract:2"
    "biz_schedule_plan:5"
    "biz_construction_log:3"
    "biz_inspection:2"
    "biz_completion_acceptance:1"
    "biz_invoice_apply:2"
    "biz_payment_received:3"
    "biz_payment_apply:3"
    "biz_bank_account:2"
    "biz_retention_money:1"
    "biz_inquiry:1"
    "biz_quotation:2"
    "biz_bid_result:2"
    "msg_announcement:2"
    "msg_message:3"
    "wf_business_type:5"
    "biz_supplier_evaluation:2"
    "biz_supplier_blacklist:1"
    # —— Layer 15 扩展业务模块（HR/行政/材料机械财务扩展/投标分包/档案门户）——
    "biz_person_certificate:2"
    "biz_company_certificate:3"
    "biz_entry_apply:2"
    "biz_regular_apply:1"
    "biz_transfer_apply:1"
    "biz_resign_apply:1"
    "biz_seal_apply:2"
    "biz_sign_record:3"
    "biz_office_supply:4"
    "biz_office_supply_in_out:3"
    "biz_vehicle:2"
    "biz_vehicle_apply:2"
    "biz_vehicle_maintenance:2"
    "biz_material_refund:1"
    "biz_material_refund_detail:1"
    "biz_material_transfer:1"
    "biz_material_transfer_detail:2"
    "biz_machine_usage_record:2"
    "biz_machine_work_settlement:1"
    "biz_machine_work_settlement_detail:2"
    "biz_purchase_settlement:2"
    "biz_personal_reimbursement:2"
    "biz_expense_contract:2"
    "biz_deposit_return:1"
    "biz_reserve_fund_return:1"
    "biz_retention_return:1"
    "biz_finance_lock:2"
    "biz_open_bid_record:1"
    "biz_subcontract_settlement_detail:2"
    "file_info:3"
    "sys_supplier_account:2"
  )
  local pass=0 fail=0 t exp actual
  for item in "${checks[@]}"; do
    t="${item%%:*}"; exp="${item##*:}"
    actual=$(Q "SELECT COUNT(*) FROM $DB.$t WHERE id BETWEEN 90001 AND 99999")
    actual="${actual:-0}"
    if [ "$exp" = "0" ]; then
      printf "  %-32s %s 条\n" "$t" "$actual"
    elif [ "$actual" -ge "$exp" ]; then
      printf "  ✓ %-30s %s 条 (期望≥%s)\n" "$t" "$actual" "$exp"
      pass=$((pass+1))
    else
      printf "  ✗ %-30s %s 条 (期望≥%s)\n" "$t" "$actual" "$exp"
      fail=$((fail+1))
    fi
  done
  echo "-----------------------------------------------------------------"
  log "DB 统计结果：通过 $pass 项，缺失 $fail 项"
  [ "$fail" -eq 0 ]
}

# ---------------------------------------------------------------------------
# api_check：复用 verify-base.sh 真实登录并抽查分页接口
# ---------------------------------------------------------------------------
api_check() {
  echo "================ API 抽查（真实登录 + 分页接口） ================"
  if [ ! -f "$BASE_DIR/verify-base.sh" ]; then
    log "缺少 verify-base.sh，跳过 API 抽查" >&2
    return 1
  fi
  # shellcheck source=/dev/null
  set --                       # 清空位置参数，避免 verify-base.sh 末尾 case 分发被触发
  source "$BASE_DIR/verify-base.sh"
  login || { log "登录失败，无法执行 API 抽查" >&2; return 1; }
  call GET "/api/v1/project/page?page=1&size=10"
  call GET "/api/v1/contract/page?page=1&size=10&projectId=90001"
  call GET "/api/v1/finance/payment-apply/page?page=1&size=10&projectId=90001"
  call GET "/api/v1/finance/payment-received/page?page=1&size=10&projectId=90001"
  check_logs 60
  echo "-----------------------------------------------------------------"
  log "API 抽查完成（响应已脱敏，请人工核对业务码 200 与数据条数）"
}

usage() {
  cat <<EOF
用法: verify-seed.sh [command]
  (无参)   完整验证：DB 行数统计 + API 抽查
  db       仅 DB 行数统计
  api      仅 API 抽查（真实登录）
  import   导入种子 SQL（幂等）后再做 DB 统计
EOF
}

cmd="${1:-all}"
case "$cmd" in
  db)     db_check ;;
  api)    api_check ;;
  import) import_seed && db_check ;;
  all)    db_check; echo; api_check ;;
  *)      usage ;;
esac
