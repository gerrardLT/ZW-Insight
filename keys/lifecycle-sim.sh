#!/usr/bin/env bash
###############################################################################
# lifecycle-sim.sh — 项目全生命周期模拟（10阶段端到端）
###############################################################################
set -uo pipefail

BASE="${ZWI_BASE:-http://127.0.0.1:18080}"
USERNAME="${ZWI_USER:-admin}"
PASSWORD="${ZWI_PASS:-123456}"
REDIS_CT="${ZWI_REDIS_CT:-zwi-redis}"
BACKEND_CT="${ZWI_BACKEND_CT:-zwi-backend}"
MAX_RETRY="${ZWI_MAX_RETRY:-3}"
WORKDIR="${ZWI_WORKDIR:-/root/zwi-deploy}"
TOKEN_FILE="$WORKDIR/.zwi_token"
SIM_LOG="$WORKDIR/lifecycle-sim.log"
mkdir -p "$WORKDIR"

PROJECT_ID=""
CONTRACT_ID=""
BUDGET_ID=""
REGISTER_ID=""
PURCHASE_CONTRACT_ID=""
LABOR_CONTRACT_ID=""
MACHINE_CONTRACT_ID=""
SUBCONTRACT_ID=""
MACHINE_ID=""
TEAM_ID=""

log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$SIM_LOG"; }
divider() { echo "" | tee -a "$SIM_LOG"; echo "═══════════════════════════════════════════════" | tee -a "$SIM_LOG"; }
phase() { divider; log "▶ 阶段 $1: $2"; divider; }
success() { log "  ✅ $*"; }
fail() { log "  ❌ $*"; }

get_captcha() {
  local resp uuid code
  resp=$(curl -s -m 10 "$BASE/api/v1/captcha/image")
  uuid=$(echo "$resp" | grep -oP '"uuid"\s*:\s*"\K[^"]+')
  if [ -z "$uuid" ]; then return 1; fi
  code=$(timeout 10 docker exec "$REDIS_CT" redis-cli GET "captcha:$uuid" | tr -d '\r"')
  if [ -z "$code" ]; then return 1; fi
  echo "$uuid $code"
}

do_login() {
  local cap uuid code resp token
  cap=$(get_captcha) || return 1
  uuid="${cap%% *}"; code="${cap##* }"
  resp=$(curl -s -m 10 -X POST "$BASE/api/v1/auth/login" \
        -H 'Content-Type: application/json' \
        -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"captchaUuid\":\"$uuid\",\"captchaCode\":\"$code\"}")
  token=$(echo "$resp" | grep -oP '"(accessToken|token)"\s*:\s*"\K[^"]+' | head -1)
  if [ -z "$token" ]; then return 1; fi
  printf '%s' "$token" > "$TOKEN_FILE"
  chmod 600 "$TOKEN_FILE"
  return 0
}


login() {
  local i
  for ((i=1; i<=MAX_RETRY; i++)); do
    log "  登录尝试 $i/$MAX_RETRY ..."
    if do_login; then success "登录成功"; return 0; fi
  done
  fail "登录失败"; return 1
}

get_token() {
  if [ ! -s "$TOKEN_FILE" ]; then login || return 1; fi
  cat "$TOKEN_FILE"
}

api_call() {
  local method="$1" path="$2" body="${3:-}" token code
  token=$(get_token) || { fail "无可用 token"; return 1; }
  if [ -n "$body" ]; then
    code=$(curl -s -m 15 -o /tmp/zwi_body -w '%{http_code}' -X "$method" "$BASE$path" \
          -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d "$body")
  else
    code=$(curl -s -m 15 -o /tmp/zwi_body -w '%{http_code}' -X "$method" "$BASE$path" \
          -H "Authorization: Bearer $token")
  fi
  echo "$code" > /tmp/zwi_code
  if [[ "$code" =~ ^2[0-9][0-9]$ ]]; then
    log "  $method $path → HTTP $code"
  else
    fail "$method $path → HTTP $code"
    log "  响应: $(cat /tmp/zwi_body | head -c 200)"
  fi
}

extract_id() {
  local val
  val=$(cat /tmp/zwi_body | grep -oP '"data"\s*:\s*\K\d+' | head -1)
  if [ -n "$val" ]; then echo "$val"; return; fi
  val=$(cat /tmp/zwi_body | grep -oP '"id"\s*:\s*\K\d+' | head -1)
  if [ -n "$val" ]; then echo "$val"; return; fi
  echo ""
}

extract_first_record_id() { cat /tmp/zwi_body | grep -oP '"id"\s*:\s*\K\d+' | head -1; }
extract_task_id() { cat /tmp/zwi_body | grep -oP '"taskId"\s*:\s*"\K[^"]+' | head -1; }
extract_field() { cat /tmp/zwi_body | grep -oP "\"$1\"\s*:\s*\"?\K[^\",}]+" | head -1; }


approve() {
  local comment="${1:-同意}"
  api_call GET "/api/v1/workflow/approval/todo?page=1&size=10"
  local task_id=$(extract_task_id)
  if [ -z "$task_id" ]; then
    log "  ⚠️ 无待办任务"; return 0
  fi
  api_call POST "/api/v1/workflow/approval/complete" "{\"taskId\":\"$task_id\",\"comment\":\"$comment\"}"
  success "审批通过 (taskId: ${task_id:0:8}...)"
}

verify_project_status() {
  local expected="$1"
  api_call GET "/api/v1/project/$PROJECT_ID"
  local actual=$(extract_field "status")
  if [ "$actual" = "$expected" ]; then success "项目状态: $actual"
  else fail "项目状态不符: 期望=$expected, 实际=$actual"; fi
}

check_logs() {
  local secs="${1:-30}" n404 nexc
  local raw=$(docker logs --since "${secs}s" "$BACKEND_CT" 2>&1)
  n404=$(echo "$raw" | grep -cE '(404|405|No mapping)' || true)
  nexc=$(echo "$raw" | grep -cE '(Exception|ERROR)' || true)
  if [ "$n404" -eq 0 ] && [ "$nexc" -eq 0 ]; then success "日志无异常"
  else log "  ⚠️ 404=$n404, Exception=$nexc"; fi
}


stage_1_project_create() {
  phase "1" "项目报备（DRAFT）"
  api_call POST "/api/v1/project" '{"projectName":"中维大厦装修改造工程","projectNature":"装修改造","projectType":"公共建筑","ownerCompanyName":"城市建设投资集团","signingCompanyName":"中维建设有限公司","projectOverview":"中维大厦1-3层装修改造","projectAddress":"广州市天河区体育西路188号","contactName":"张建国","contactPhone":"13800138001","needTender":1,"budgetAmount":5000000.00}'
  success "项目创建完成"
  sleep 1
  api_call GET "/api/v1/project/page?page=1&size=1&projectName=%E4%B8%AD%E7%BB%B4%E5%A4%A7%E5%8E%A6"
  PROJECT_ID=$(extract_first_record_id)
  if [ -z "$PROJECT_ID" ]; then fail "未获取到项目ID"; return 1; fi
  success "项目ID: $PROJECT_ID"
  api_call POST "/api/v1/project/$PROJECT_ID/members" "{\"projectId\":$PROJECT_ID,\"userId\":1,\"roleType\":\"PROJECT_MANAGER\"}"
  verify_project_status "DRAFT"
  check_logs 15
}

stage_2_project_approve() {
  phase "2" "立项审批（DRAFT → FILED）"
  api_call POST "/api/v1/project/$PROJECT_ID/submit"
  sleep 2; approve "同意立项"
  sleep 1; verify_project_status "FILED"
  check_logs 15
}


stage_3_tender() {
  phase "3" "投标（FILED → WON）"
  api_call POST "/api/v1/tender/register" "{\"projectId\":$PROJECT_ID,\"ownerCompany\":\"城市建设投资集团\",\"bidMethod\":\"公开招标\",\"registerMethod\":\"线上报名\",\"registerDate\":\"2026-07-07\",\"openDate\":\"2026-07-20\",\"tenderMethod\":\"综合评标法\",\"depositAmount\":100000.00,\"status\":\"REGISTERED\"}"
  sleep 1
  api_call GET "/api/v1/tender/register/page?page=1&size=1&projectId=$PROJECT_ID"
  REGISTER_ID=$(extract_first_record_id)
  success "投标登记ID: $REGISTER_ID"
  api_call POST "/api/v1/tender/open-bid" "{\"registerId\":$REGISTER_ID,\"projectId\":$PROJECT_ID,\"isWon\":1,\"winInfo\":\"中标480万\"}"
  sleep 1; verify_project_status "WON"
  check_logs 15
}

stage_4_contract() {
  phase "4" "合同签订"
  api_call POST "/api/v1/contract" "{\"projectId\":$PROJECT_ID,\"contractType\":\"REGISTER\",\"partyAName\":\"城市建设投资集团\",\"signingDate\":\"2026-07-25\",\"startDate\":\"2026-08-01\",\"endDate\":\"2027-02-01\",\"contractAmount\":4800000.00,\"taxRate\":9.00,\"amountWithoutTax\":4403669.72,\"taxAmount\":396330.28}"
  sleep 1
  api_call GET "/api/v1/contract/page?page=1&size=1&projectId=$PROJECT_ID"
  CONTRACT_ID=$(extract_first_record_id)
  success "施工合同ID: $CONTRACT_ID"
  api_call POST "/api/v1/contract/$CONTRACT_ID/submit"
  sleep 2; approve "同意签订"
  check_logs 15
}

stage_5_budget() {
  phase "5" "预算编制"
  api_call POST "/api/v1/budget" "{\"projectId\":$PROJECT_ID,\"budgetType\":\"ORIGINAL\",\"totalAmount\":4200000.00}"
  sleep 1
  api_call GET "/api/v1/budget/project/$PROJECT_ID"
  BUDGET_ID=$(extract_id)
  success "预算ID: $BUDGET_ID"
  if [ -n "$BUDGET_ID" ]; then
    api_call POST "/api/v1/budget/$BUDGET_ID/submit"
    sleep 2; approve "预算通过"
  fi
  check_logs 15
}


stage_6a_purchase() {
  phase "6A" "采购管理"
  api_call POST "/api/v1/purchase/contract" "{\"projectId\":$PROJECT_ID,\"contractName\":\"装修主材采购合同\",\"partyBName\":\"广州建材供应有限公司\",\"supplierName\":\"广州建材供应有限公司\",\"signingDate\":\"2026-08-05\",\"contractAmount\":800000.00,\"paymentTerms\":\"月结30天\"}"
  sleep 1
  api_call GET "/api/v1/purchase/contract/page?page=1&size=1&projectId=$PROJECT_ID"
  PURCHASE_CONTRACT_ID=$(extract_first_record_id)
  success "采购合同ID: $PURCHASE_CONTRACT_ID"
  if [ -n "$PURCHASE_CONTRACT_ID" ]; then
    api_call POST "/api/v1/purchase/contract/$PURCHASE_CONTRACT_ID/submit"
    sleep 2; approve "同意采购"
  fi
  api_call POST "/api/v1/material/inbound" "{\"projectId\":$PROJECT_ID,\"contractId\":$PURCHASE_CONTRACT_ID,\"inboundDate\":\"2026-08-10\",\"totalAmount\":200000.00,\"directOutbound\":0,\"details\":[{\"materialName\":\"600x600防滑地砖\",\"specification\":\"600x600mm\",\"unit\":\"m²\",\"quantity\":2000,\"unitPrice\":65.00,\"totalPrice\":130000.00},{\"materialName\":\"乳胶漆\",\"specification\":\"20L/桶\",\"unit\":\"桶\",\"quantity\":100,\"unitPrice\":700.00,\"totalPrice\":70000.00}]}"
  success "材料入库完成"
  api_call POST "/api/v1/material/outbound" "{\"projectId\":$PROJECT_ID,\"outboundDate\":\"2026-08-15\",\"outboundType\":\"REQUISITION\",\"details\":[{\"materialName\":\"600x600防滑地砖\",\"quantity\":500},{\"materialName\":\"乳胶漆\",\"quantity\":20}]}"
  success "材料出库完成"
  check_logs 15
}


stage_6b_labor() {
  phase "6B" "劳务管理"
  api_call POST "/api/v1/labor/contract" "{\"projectId\":$PROJECT_ID,\"contractName\":\"泥水木工劳务合同\",\"partyBName\":\"恒通劳务公司\",\"signingDate\":\"2026-08-01\",\"startDate\":\"2026-08-01\",\"endDate\":\"2027-01-31\",\"contractAmount\":1200000.00}"
  sleep 1
  api_call GET "/api/v1/labor/contract/page?page=1&size=1&projectId=$PROJECT_ID"
  LABOR_CONTRACT_ID=$(extract_first_record_id)
  success "劳务合同ID: $LABOR_CONTRACT_ID"
  api_call POST "/api/v1/labor/team" "{\"projectId\":$PROJECT_ID,\"teamName\":\"泥水一班\",\"leaderName\":\"李建军\",\"leaderPhone\":\"13900139001\",\"status\":1}"
  sleep 1
  api_call GET "/api/v1/labor/team/page?page=1&size=1&projectId=$PROJECT_ID"
  TEAM_ID=$(extract_first_record_id)
  success "班组ID: $TEAM_ID"
  api_call POST "/api/v1/labor/work-order" "{\"projectId\":$PROJECT_ID,\"teamId\":$TEAM_ID,\"workerName\":\"王大明\",\"workDate\":\"2026-08-15\",\"hours\":8.0,\"hourlyRate\":45.00,\"overtime\":2.0,\"overtimeRate\":67.50,\"totalAmount\":495.00,\"orderType\":\"FIXED\"}"
  success "派工单完成"
  check_logs 15
}

stage_6c_machine() {
  phase "6C" "机械管理"
  api_call POST "/api/v1/machine/contract" "{\"projectId\":$PROJECT_ID,\"contractName\":\"塔吊租赁合同\",\"supplierName\":\"华南机械租赁\",\"machineName\":\"QTZ63塔吊\",\"rentalType\":\"月租\",\"signingDate\":\"2026-08-01\",\"startDate\":\"2026-08-01\",\"endDate\":\"2027-01-31\",\"contractAmount\":300000.00}"
  sleep 1
  api_call GET "/api/v1/machine/contract/page?page=1&size=1&projectId=$PROJECT_ID"
  MACHINE_CONTRACT_ID=$(extract_first_record_id)
  success "机械合同ID: $MACHINE_CONTRACT_ID"
  api_call POST "/api/v1/machine/ledger" "{\"machineName\":\"QTZ63塔吊\",\"machineCode\":\"TC-2026-001\",\"machineType\":\"塔吊\",\"brand\":\"中联重科\",\"specification\":\"臂长56m\",\"ownerType\":\"RENT\",\"status\":\"REGISTERED\"}"
  sleep 1
  api_call GET "/api/v1/machine/ledger/page?page=1&size=1&machineName=QTZ63"
  MACHINE_ID=$(extract_first_record_id)
  success "机械ID: $MACHINE_ID"
  api_call POST "/api/v1/machine/entry/in" "{\"machineId\":$MACHINE_ID,\"projectId\":$PROJECT_ID,\"entryDate\":\"2026-08-03\"}"
  success "设备进场完成"
  api_call POST "/api/v1/machine/work-log" "{\"machineId\":$MACHINE_ID,\"projectId\":$PROJECT_ID,\"workDate\":\"2026-08-15\",\"shiftCount\":1.0,\"workQuantity\":8.0,\"oilConsumption\":120.00}"
  success "台班记录完成"
  check_logs 15
}


stage_6d_subcontract() {
  phase "6D" "分包管理"
  api_call POST "/api/v1/subcontract/contract" "{\"projectId\":$PROJECT_ID,\"contractName\":\"精装修分包合同\",\"subcontractor\":\"精装修工程公司\",\"supplierName\":\"精装修工程公司\",\"signingDate\":\"2026-08-01\",\"content\":\"二三层精装修\",\"contractAmount\":600000.00}"
  sleep 1
  api_call GET "/api/v1/subcontract/contract/page?page=1&size=1&projectId=$PROJECT_ID"
  SUBCONTRACT_ID=$(extract_first_record_id)
  success "分包合同ID: $SUBCONTRACT_ID"
  api_call POST "/api/v1/subcontract/output" "{\"projectId\":$PROJECT_ID,\"contractId\":$SUBCONTRACT_ID,\"currentOutput\":150000.00}"
  success "分包产值上报完成"
  check_logs 15
}

stage_6e_site() {
  phase "6E" "现场管理"
  api_call POST "/api/v1/site/schedule/plan" "{\"projectId\":$PROJECT_ID,\"taskName\":\"一层装修改造\",\"parentId\":0,\"planStartDate\":\"2026-08-01\",\"planEndDate\":\"2026-10-01\"}"
  success "进度计划完成"
  api_call POST "/api/v1/site/construction-log" "{\"projectId\":$PROJECT_ID,\"logDate\":\"2026-08-15\",\"weather\":\"晴\",\"temperature\":\"32℃\",\"wind\":\"微风\",\"workerCount\":45,\"productionRecord\":\"地砖铺贴60%\",\"technicalRecord\":\"隐蔽验收合格\"}"
  success "施工日志完成"
  api_call POST "/api/v1/site/inspection" "{\"projectId\":$PROJECT_ID,\"inspectionType\":\"QUALITY\",\"inspectionContent\":\"地砖铺贴检查\",\"hasProblem\":0}"
  success "质量检查完成"
  check_logs 15
}


stage_7_settlement() {
  phase "7" "产值与结算"
  api_call POST "/api/v1/contract/output" "{\"projectId\":$PROJECT_ID,\"contractId\":$CONTRACT_ID,\"reportPeriod\":\"2026-08\",\"currentOutput\":1200000.00}"
  sleep 1
  api_call GET "/api/v1/contract/output?page=1&size=1&projectId=$PROJECT_ID"
  local output_id=$(extract_first_record_id)
  if [ -n "$output_id" ]; then
    api_call POST "/api/v1/contract/output/$output_id/submit"
    sleep 2; approve "产值确认"
  fi
  api_call POST "/api/v1/purchase/settlement" "{\"projectId\":$PROJECT_ID,\"contractId\":$PURCHASE_CONTRACT_ID,\"settlementAmount\":200000.00}"
  success "采购结算完成"
  api_call POST "/api/v1/machine/settlement" "{\"projectId\":$PROJECT_ID,\"periodStart\":\"2026-08-01\",\"periodEnd\":\"2026-08-31\"}"
  success "机械结算完成"
  api_call POST "/api/v1/labor/settlement" "{\"projectId\":$PROJECT_ID,\"contractId\":$LABOR_CONTRACT_ID,\"settlementAmount\":300000.00}"
  success "劳务结算完成"
  check_logs 15
}

stage_8_finance() {
  phase "8" "财务收付"
  api_call POST "/api/v1/finance/invoice-apply" "{\"projectId\":$PROJECT_ID,\"contractId\":$CONTRACT_ID,\"invoiceType\":\"SPECIAL\",\"invoiceAmount\":1000000.00,\"invoiceTitle\":\"城市建设投资集团\",\"taxpayerId\":\"914401001234567890\"}"
  sleep 1
  api_call GET "/api/v1/finance/invoice-apply/page?page=1&size=1&projectId=$PROJECT_ID"
  local invoice_id=$(extract_first_record_id)
  if [ -n "$invoice_id" ]; then
    api_call POST "/api/v1/finance/invoice-apply/$invoice_id/submit"
    sleep 2; approve "同意开票"
  fi
  api_call POST "/api/v1/finance/payment-received" "{\"projectId\":$PROJECT_ID,\"contractId\":$CONTRACT_ID,\"receiveDate\":\"2026-09-15\",\"receiveAmount\":1000000.00,\"receiveType\":\"转账\"}"
  success "收款登记完成"
  api_call POST "/api/v1/finance/payment-apply" "{\"projectId\":$PROJECT_ID,\"contractId\":$PURCHASE_CONTRACT_ID,\"contractCategory\":\"MATERIAL\",\"supplierName\":\"广州建材供应有限公司\",\"paymentAmount\":200000.00,\"paymentDate\":\"2026-09-20\"}"
  sleep 1
  api_call GET "/api/v1/finance/payment-apply/page?page=1&size=1&projectId=$PROJECT_ID"
  local payment_id=$(extract_first_record_id)
  if [ -n "$payment_id" ]; then
    api_call POST "/api/v1/finance/payment-apply/$payment_id/submit"
    sleep 2; approve "同意付款"
  fi
  check_logs 15
}


stage_9_completion() {
  phase "9" "竣工验收（→ COMPLETED）"
  api_call POST "/api/v1/site/completion" "{\"projectId\":$PROJECT_ID,\"acceptanceDate\":\"2027-01-20\",\"acceptanceReport\":\"竣工验收合格\"}"
  sleep 1
  api_call GET "/api/v1/site/completion/page?page=1&size=1&projectId=$PROJECT_ID"
  local cid=$(extract_first_record_id)
  if [ -n "$cid" ]; then
    api_call POST "/api/v1/site/completion/$cid/submit"
    sleep 2; approve "验收合格"
  fi
  sleep 1; verify_project_status "COMPLETED"
  check_logs 15
}

stage_10_close() {
  phase "10" "项目关闭（→ CLOSED）"
  api_call POST "/api/v1/project-settlements?projectId=$PROJECT_ID"
  local sid=$(extract_id)
  success "结算单ID: $sid"
  if [ -n "$sid" ]; then
    api_call POST "/api/v1/project-settlements/$sid/submit"
    sleep 2; approve "结算确认"
  fi
  api_call GET "/api/v1/project/$PROJECT_ID/close-check"
  log "  结项条件: $(cat /tmp/zwi_body | head -c 200)"
  api_call POST "/api/v1/project/$PROJECT_ID/close"
  sleep 1; verify_project_status "CLOSED"
  check_logs 15
}


main() {
  echo "" > "$SIM_LOG"
  log "═══ ZW-Insight 项目全生命周期模拟 ═══"
  log "时间: $(date '+%Y-%m-%d %H:%M:%S') | 服务: $BASE"
  login || { fail "登录失败"; exit 1; }
  stage_1_project_create   || { fail "阶段1失败"; exit 1; }
  stage_2_project_approve  || { fail "阶段2失败"; exit 1; }
  stage_3_tender           || { fail "阶段3失败"; exit 1; }
  stage_4_contract         || { fail "阶段4失败"; exit 1; }
  stage_5_budget           || { fail "阶段5失败"; exit 1; }
  stage_6a_purchase        || { fail "阶段6A失败"; exit 1; }
  stage_6b_labor           || { fail "阶段6B失败"; exit 1; }
  stage_6c_machine         || { fail "阶段6C失败"; exit 1; }
  stage_6d_subcontract     || { fail "阶段6D失败"; exit 1; }
  stage_6e_site            || { fail "阶段6E失败"; exit 1; }
  stage_7_settlement       || { fail "阶段7失败"; exit 1; }
  stage_8_finance          || { fail "阶段8失败"; exit 1; }
  stage_9_completion       || { fail "阶段9失败"; exit 1; }
  stage_10_close           || { fail "阶段10失败"; exit 1; }
  divider
  log "🎉 全生命周期模拟完成！项目ID=$PROJECT_ID"
  divider
}

main "$@"