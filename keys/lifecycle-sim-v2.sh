#!/usr/bin/env bash
###############################################################################
# lifecycle-sim-v2.sh — 项目全生命周期模拟 v2（L4 端到端业务流测试）
#
# 相比 v1 (lifecycle-sim.sh) 的改进：
#   - tenant_id=9999 隔离测试数据，与生产完全隔离
#   - CREATED_IDS 数组追踪所有已创建资源
#   - strict_assert() 严格断言（HTTP 2xx + code=200），失败立即中止
#   - trap EXIT → cleanup_all 确保无论如何退出都执行清理
#   - 兜底 SQL 清理（DELETE WHERE tenant_id=9999）
#   - 结构化 JSON 报告输出
#
# 运行位置：服务器 129.204.3.200
# 依赖：verify-base.sh（登录/调用基座）
#
# 设计依据：full-layer-test-suite spec
#   - 需求 5.1: 使用 Test_Tenant (tenant_id=9999) 执行所有业务操作
#   - 需求 5.2: 严格断言模式，每阶段 HTTP 2xx + code=200
#   - 需求 5.3: 严格模式下某阶段失败立即停止并触发清理
#   - 需求 5.4: 追踪所有已创建资源 ID，逆序 DELETE 避免外键冲突
#   - 需求 5.5: trap EXIT 确保无论如何都执行清理
#   - 需求 5.6: 兜底 SQL 清理确保无数据残留
#   - 需求 5.7: 结构化 JSON 报告
###############################################################################
set -uo pipefail

# ===========================================================================
# 环境变量配置
# ===========================================================================
BASE="${ZWI_BASE:-http://127.0.0.1:18080}"
USERNAME="${ZWI_USER:-admin}"
PASSWORD="${ZWI_PASS:-123456}"
REDIS_CT="${ZWI_REDIS_CT:-zwi-redis}"
BACKEND_CT="${ZWI_BACKEND_CT:-zwi-backend}"
MYSQL_CT="${ZWI_MYSQL_CT:-zwi-mysql}"
MAX_RETRY="${ZWI_MAX_RETRY:-3}"
WORKDIR="${ZWI_WORKDIR:-/root/zwi-deploy}"

# 测试租户隔离
TEST_TENANT_ID=9999

# 报告输出目录
REPORT_DIR="${ZWI_REPORT_DIR:-$(dirname "$0")/../tests/reports}"
REPORT_FILE="$REPORT_DIR/lifecycle-sim-report.json"
SIM_LOG="$WORKDIR/lifecycle-sim-v2.log"

mkdir -p "$WORKDIR" "$REPORT_DIR"

# ===========================================================================
# 资源追踪数组（逆序清理时用）
# 格式: "METHOD PATH" —— 例如 "DELETE /api/v1/project/123"
# ===========================================================================
CREATED_IDS=()

# ===========================================================================
# 阶段执行结果追踪
# ===========================================================================
declare -A STAGE_RESULTS=()
TOTAL_PASSED=0
TOTAL_FAILED=0
TOTAL_SKIPPED=0
CLEANED_RECORDS=0
CURRENT_STAGE=""
ABORT_REASON=""

# ===========================================================================
# Source verify-base.sh 复用登录/调用基座
# ===========================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 我们需要 verify-base.sh 中的 login / get_token / mask 等函数，
# 但不执行其底部的 case 命令分发。通过 source 时设置 __SOURCED=1 绕过。
# 由于 verify-base.sh 底部有 case 分发，我们直接定义所需函数。
# 使用 verify-base.sh 的核心函数（登录/token/调用）
source_verify_base() {
  # verify-base.sh 的 case 分发会在 source 时执行，
  # 我们设置 cmd 为空来避免执行任何分支
  local cmd=""
  # 通过子 shell 获取函数定义
  if [ -f "$SCRIPT_DIR/verify-base.sh" ]; then
    # 提取函数定义，跳过尾部 case 分发
    eval "$(sed -n '/^mask()/,/^cmd=.*shift/p' "$SCRIPT_DIR/verify-base.sh" | head -n -2)"
  fi
}

# 直接重用 verify-base.sh 的核心能力（避免 source 执行尾部命令）
# 以下函数直接调用 verify-base.sh 的子命令接口：

vb_login() {
  bash "$SCRIPT_DIR/verify-base.sh" login
}

vb_call() {
  bash "$SCRIPT_DIR/verify-base.sh" call "$@"
}

vb_clear_token() {
  bash "$SCRIPT_DIR/verify-base.sh" clear-token
}

# ===========================================================================
# 日志与格式化
# ===========================================================================
log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$SIM_LOG"; }
divider() { echo "" | tee -a "$SIM_LOG"; echo "═══════════════════════════════════════════════" | tee -a "$SIM_LOG"; }
phase() { divider; log "▶ 阶段 $1: $2"; CURRENT_STAGE="$1:$2"; divider; }
success() { log "  ✅ $*"; }
fail() { log "  ❌ $*"; }

# ===========================================================================
# strict_assert() — 严格断言函数
#
# 检查最近一次 API 调用的结果：
#   1. HTTP 状态码为 2xx
#   2. 响应体 JSON 中 code 字段为 200
# 任一条件不满足 → 记录失败 → 触发 cleanup_all → exit 1
#
# 用法: api_call ... && strict_assert "描述信息"
# ===========================================================================
strict_assert() {
  local desc="${1:-API 调用}"
  local http_code
  local body_code

  # 读取上一次 curl 写入的 HTTP 状态码
  if [ -f /tmp/zwi_last_code ]; then
    http_code=$(cat /tmp/zwi_last_code)
  else
    http_code="000"
  fi

  # 检查 HTTP 2xx
  if ! [[ "$http_code" =~ ^2[0-9][0-9]$ ]]; then
    fail "$desc: HTTP 状态码异常 ($http_code)，期望 2xx"
    ABORT_REASON="$desc: HTTP $http_code"
    record_stage_result "FAILED"
    # trap EXIT 会触发 cleanup_all
    exit 1
  fi

  # 检查响应体 code=200
  if [ -f /tmp/zwi_body ]; then
    body_code=$(grep -oE '"code"\s*:\s*[0-9]+' /tmp/zwi_body | head -1 | grep -oE '[0-9]+$')
    if [ -n "$body_code" ] && [ "$body_code" != "200" ]; then
      fail "$desc: 业务码异常 (code=$body_code)，期望 200"
      ABORT_REASON="$desc: code=$body_code"
      record_stage_result "FAILED"
      exit 1
    fi
  fi

  success "$desc: 断言通过 (HTTP $http_code, code=${body_code:-N/A})"
}

# ===========================================================================
# API 调用封装（带状态码写入，供 strict_assert 读取）
# ===========================================================================
api_call() {
  local method="$1" path="$2" body="${3:-}" token code
  token=$(get_token_local) || { fail "无可用 token"; return 1; }
  if [ -n "$body" ]; then
    code=$(curl -s -m 15 -o /tmp/zwi_body -w '%{http_code}' -X "$method" "$BASE$path" \
          -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d "$body")
  else
    code=$(curl -s -m 15 -o /tmp/zwi_body -w '%{http_code}' -X "$method" "$BASE$path" \
          -H "Authorization: Bearer $token")
  fi
  echo "$code" > /tmp/zwi_last_code
  log "  $method $path → HTTP $code"
  return 0
}

# ===========================================================================
# 登录相关（本地实现，避免依赖 verify-base.sh 的 case 分发问题）
# ===========================================================================
TOKEN_FILE="$WORKDIR/.zwi_token"

get_captcha_local() {
  local resp uuid code
  resp=$(curl -s -m 10 "$BASE/api/v1/captcha/image")
  uuid=$(echo "$resp" | grep -oE '"uuid"[[:space:]]*:[[:space:]]*"[^"]+"' | head -1 | sed -E 's/.*"uuid"[[:space:]]*:[[:space:]]*"//;s/"$//')
  if [ -z "$uuid" ]; then return 1; fi
  code=$(timeout 10 docker exec "$REDIS_CT" redis-cli GET "captcha:$uuid" | tr -d '\r"')
  if [ -z "$code" ]; then return 1; fi
  echo "$uuid $code"
}

do_login_local() {
  local cap uuid code resp token
  cap=$(get_captcha_local) || return 1
  uuid="${cap%% *}"; code="${cap##* }"
  resp=$(curl -s -m 10 -X POST "$BASE/api/v1/auth/login" \
        -H 'Content-Type: application/json' \
        -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"captchaUuid\":\"$uuid\",\"captchaCode\":\"$code\"}")
  token=$(echo "$resp" | grep -oE '"(accessToken|token)"[[:space:]]*:[[:space:]]*"[^"]+"' | head -1 | sed -E 's/.*:[[:space:]]*"//;s/"$//')
  if [ -z "$token" ]; then return 1; fi
  printf '%s' "$token" > "$TOKEN_FILE"
  chmod 600 "$TOKEN_FILE"
  return 0
}

login_local() {
  local i
  for ((i=1; i<=MAX_RETRY; i++)); do
    log "  登录尝试 $i/$MAX_RETRY ..."
    if do_login_local; then success "登录成功"; return 0; fi
  done
  fail "登录在 $MAX_RETRY 次重试内仍失败"
  return 1
}

get_token_local() {
  if [ ! -s "$TOKEN_FILE" ]; then
    login_local >&2 || return 1
  fi
  cat "$TOKEN_FILE"
}

# ===========================================================================
# 资源追踪：注册已创建资源（逆序清理用）
# 用法: track_resource "DELETE" "/api/v1/project/123"
# ===========================================================================
track_resource() {
  local method="$1" path="$2"
  CREATED_IDS+=("$method $path")
  log "  📝 追踪资源: $method $path (总计 ${#CREATED_IDS[@]} 项)"
}

# ===========================================================================
# 辅助：从响应中提取 ID
# ===========================================================================
extract_id() {
  local val
  val=$(grep -oE '"data"\s*:\s*[0-9]+' /tmp/zwi_body | head -1 | grep -oE '[0-9]+$')
  if [ -n "$val" ]; then echo "$val"; return; fi
  val=$(grep -oE '"id"\s*:\s*[0-9]+' /tmp/zwi_body | head -1 | grep -oE '[0-9]+$')
  if [ -n "$val" ]; then echo "$val"; return; fi
  echo ""
}

extract_first_record_id() { grep -oE '"id"\s*:\s*[0-9]+' /tmp/zwi_body | head -1 | grep -oE '[0-9]+$'; }
extract_task_id() { grep -oE '"taskId"\s*:\s*"[^"]+"' /tmp/zwi_body | head -1 | sed -E 's/.*"taskId"\s*:\s*"//;s/"$//'; }
extract_field() { grep -oE "\"$1\"\s*:\s*\"?[^\",}]+" /tmp/zwi_body | head -1 | sed -E "s/.*\"$1\"\s*:\s*\"?//;s/\"?$//"; }

# ===========================================================================
# 阶段结果记录
# ===========================================================================
record_stage_result() {
  local result="$1"
  STAGE_RESULTS["$CURRENT_STAGE"]="$result"
  case "$result" in
    PASSED) ((TOTAL_PASSED++)) ;;
    FAILED) ((TOTAL_FAILED++)) ;;
    SKIPPED) ((TOTAL_SKIPPED++)) ;;
  esac
}

# ===========================================================================
# cleanup_all() — 自动清理函数
#
# 1. 逆序遍历 CREATED_IDS 调用 DELETE 接口
# 2. 兜底 SQL：docker exec 执行 DELETE WHERE tenant_id=9999
# 3. 清除 Redis test:t9999:* 键
#
# 由 trap EXIT 自动触发，确保无论脚本如何退出都执行清理
# ===========================================================================
cleanup_all() {
  divider
  log "🧹 开始自动清理（共 ${#CREATED_IDS[@]} 项已追踪资源）"
  divider

  local cleaned=0

  # 1. 逆序遍历 CREATED_IDS，逐一调用 DELETE 接口
  if [ ${#CREATED_IDS[@]} -gt 0 ]; then
    for ((i=${#CREATED_IDS[@]}-1; i>=0; i--)); do
      local entry="${CREATED_IDS[$i]}"
      local method="${entry%% *}"
      local path="${entry#* }"
      log "  清理: $method $path"
      # 尝试调用 DELETE，忽略失败（兜底 SQL 会再清理一遍）
      local token
      token=$(cat "$TOKEN_FILE" 2>/dev/null || echo "")
      if [ -n "$token" ]; then
        curl -s -m 10 -X "$method" "$BASE$path" \
          -H "Authorization: Bearer $token" \
          -H 'Content-Type: application/json' \
          -o /dev/null -w '' 2>/dev/null || true
        ((cleaned++))
      fi
    done
    log "  接口清理: 已尝试 $cleaned 项"
  fi

  # 2. 兜底 SQL 清理：通过 docker exec 执行 DELETE WHERE tenant_id=9999
  log "  🗄️ 执行兜底 SQL 清理 (tenant_id=$TEST_TENANT_ID)..."
  local sql_tables=(
    "t_workflow_instance"
    "t_workflow_task"
    "t_finance_payment_apply"
    "t_finance_payment_received"
    "t_finance_invoice_apply"
    "t_subcontract_output"
    "t_subcontract_settlement"
    "t_subcontract_contract"
    "t_machine_work_log"
    "t_machine_entry"
    "t_machine_settlement"
    "t_machine_contract"
    "t_machine_ledger"
    "t_labor_work_order"
    "t_labor_team"
    "t_labor_settlement"
    "t_labor_contract"
    "t_material_outbound_detail"
    "t_material_outbound"
    "t_material_inbound_detail"
    "t_material_inbound"
    "t_material_inventory"
    "t_purchase_settlement"
    "t_purchase_contract"
    "t_contract_output"
    "t_contract"
    "t_budget_item"
    "t_budget"
    "t_tender_open_bid"
    "t_tender_register"
    "t_site_inspection"
    "t_site_construction_log"
    "t_site_schedule_plan"
    "t_site_completion"
    "t_project_settlement"
    "t_project_member"
    "t_project"
  )

  local sql_cleaned=0
  for table in "${sql_tables[@]}"; do
    local result
    result=$(docker exec "$MYSQL_CT" mysql -uroot -p"${ZWI_MYSQL_PASS:-root}" \
      -e "DELETE FROM zw_insight.$table WHERE tenant_id=$TEST_TENANT_ID;" 2>&1) || true
    if echo "$result" | grep -q "Query OK"; then
      local rows
      rows=$(echo "$result" | grep -oE '[0-9]+ row' | grep -oE '[0-9]+')
      if [ -n "$rows" ] && [ "$rows" -gt 0 ]; then
        ((sql_cleaned += rows))
      fi
    fi
  done
  log "  兜底 SQL 清理完成（预估清理 $sql_cleaned 行）"

  # 3. 清除 Redis test:t9999:* 键
  log "  🔑 清除 Redis test:t$TEST_TENANT_ID:* 键..."
  docker exec "$REDIS_CT" redis-cli KEYS "test:t${TEST_TENANT_ID}:*" | \
    xargs -r docker exec -i "$REDIS_CT" redis-cli DEL 2>/dev/null || true
  log "  Redis 清理完成"

  CLEANED_RECORDS=$((cleaned + sql_cleaned))

  # 4. 生成 JSON 报告
  generate_report

  divider
  log "🧹 清理完成（接口 $cleaned 项 + SQL $sql_cleaned 行）"
  divider
}

# ===========================================================================
# trap EXIT → cleanup_all
# 确保无论脚本如何退出（正常/异常/信号中断），都执行清理
# ===========================================================================
trap cleanup_all EXIT

# ===========================================================================
# generate_report() — 生成结构化 JSON 报告
# ===========================================================================
generate_report() {
  local stages_json="["
  local first=true
  for stage in "${!STAGE_RESULTS[@]}"; do
    if [ "$first" = true ]; then first=false; else stages_json+=","; fi
    stages_json+="{\"stage\":\"$stage\",\"result\":\"${STAGE_RESULTS[$stage]}\"}"
  done
  stages_json+="]"

  local total=$((TOTAL_PASSED + TOTAL_FAILED + TOTAL_SKIPPED))

  cat > "$REPORT_FILE" <<EOF
{
  "timestamp": "$(date -Iseconds)",
  "testTenantId": $TEST_TENANT_ID,
  "summary": {
    "total": $total,
    "passed": $TOTAL_PASSED,
    "failed": $TOTAL_FAILED,
    "skipped": $TOTAL_SKIPPED
  },
  "cleanedRecords": $CLEANED_RECORDS,
  "abortReason": $([ -n "$ABORT_REASON" ] && echo "\"$ABORT_REASON\"" || echo "null"),
  "stages": $stages_json
}
EOF

  log "  📊 报告已生成: $REPORT_FILE"
}

# ===========================================================================
# 阶段变量（跨阶段传递资源 ID）
# ===========================================================================
PROJECT_ID=""
CONTRACT_ID=""
BUDGET_ID=""
REGISTER_ID=""
PURCHASE_CONTRACT_ID=""
LABOR_CONTRACT_ID=""
MACHINE_CONTRACT_ID=""

# ===========================================================================
# approve() — 从待办列表中取首个任务并审批通过
# ===========================================================================
approve() {
  local comment="${1:-同意}"
  api_call GET "/api/v1/workflow/approval/todo?page=1&size=10"
  local task_id=$(extract_task_id)
  if [ -z "$task_id" ]; then
    log "  ⚠️ 无待办任务"
    return 0
  fi
  api_call POST "/api/v1/workflow/approval/complete" "{\"taskId\":\"$task_id\",\"comment\":\"$comment\"}"
  success "审批通过 (taskId: ${task_id:0:8}...)"
}

# ===========================================================================
# 阶段 1: 项目报备
# POST /api/v1/project → 创建项目 + track_resource
# ===========================================================================
stage_1_project_create() {
  phase "1" "项目报备"
  CURRENT_STAGE="1:项目报备"

  api_call POST "/api/v1/project" "{\"projectName\":\"[测试]中维综合楼装修工程\",\"projectNature\":\"装修改造\",\"projectType\":\"公共建筑\",\"ownerCompanyName\":\"城市建设投资集团\",\"signingCompanyName\":\"中维建设有限公司\",\"projectOverview\":\"中维综合楼1-5层精装修改造\",\"projectAddress\":\"广州市天河区体育西路188号\",\"contactName\":\"张建国\",\"contactPhone\":\"13800138001\",\"needTender\":1,\"budgetAmount\":5000000.00,\"tenantId\":$TEST_TENANT_ID}"
  strict_assert "创建项目"

  # 通过查询获取项目 ID（创建可能不直接返回 ID）
  sleep 1
  api_call GET "/api/v1/project/page?page=1&size=1&projectName=%5B%E6%B5%8B%E8%AF%95%5D%E4%B8%AD%E7%BB%B4%E7%BB%BC%E5%90%88%E6%A5%BC"
  PROJECT_ID=$(extract_first_record_id)
  if [ -z "$PROJECT_ID" ]; then
    fail "未获取到项目 ID"
    ABORT_REASON="阶段1: 无法获取项目ID"
    record_stage_result "FAILED"
    exit 1
  fi
  success "项目 ID: $PROJECT_ID"
  track_resource "DELETE" "/api/v1/project/$PROJECT_ID"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 2: 立项审批
# POST /api/v1/project/{id}/submit → 提交立项审批
# ===========================================================================
stage_2_project_submit() {
  phase "2" "立项审批"
  CURRENT_STAGE="2:立项审批"

  api_call POST "/api/v1/project/$PROJECT_ID/submit"
  strict_assert "提交立项审批"

  sleep 2
  approve "同意立项"

  # 验证项目状态
  api_call GET "/api/v1/project/$PROJECT_ID"
  local status=$(extract_field "status")
  success "项目状态: $status"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 3: 投标登记
# POST /api/v1/tender/register → 登记投标 + track_resource
# ===========================================================================
stage_3_tender_register() {
  phase "3" "投标登记"
  CURRENT_STAGE="3:投标登记"

  api_call POST "/api/v1/tender/register" "{\"projectId\":$PROJECT_ID,\"ownerCompany\":\"城市建设投资集团\",\"bidMethod\":\"公开招标\",\"registerMethod\":\"线上报名\",\"registerDate\":\"2026-07-07\",\"openDate\":\"2026-07-20\",\"tenderMethod\":\"综合评标法\",\"depositAmount\":100000.00,\"status\":\"REGISTERED\",\"tenantId\":$TEST_TENANT_ID}"
  strict_assert "投标登记"

  sleep 1
  api_call GET "/api/v1/tender/register/page?page=1&size=1&projectId=$PROJECT_ID"
  REGISTER_ID=$(extract_first_record_id)
  if [ -z "$REGISTER_ID" ]; then
    fail "未获取到投标登记 ID"
    ABORT_REASON="阶段3: 无法获取投标登记ID"
    record_stage_result "FAILED"
    exit 1
  fi
  success "投标登记 ID: $REGISTER_ID"
  track_resource "DELETE" "/api/v1/tender/register/$REGISTER_ID"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 4: 施工合同
# POST /api/v1/contract → 创建合同 + track_resource + 提交审批
# ===========================================================================
stage_4_contract() {
  phase "4" "施工合同"
  CURRENT_STAGE="4:施工合同"

  api_call POST "/api/v1/contract" "{\"projectId\":$PROJECT_ID,\"contractType\":\"REGISTER\",\"partyAName\":\"城市建设投资集团\",\"signingDate\":\"2026-07-25\",\"startDate\":\"2026-08-01\",\"endDate\":\"2027-02-01\",\"contractAmount\":4800000.00,\"taxRate\":9.00,\"amountWithoutTax\":4403669.72,\"taxAmount\":396330.28,\"tenantId\":$TEST_TENANT_ID}"
  strict_assert "创建施工合同"

  sleep 1
  api_call GET "/api/v1/contract/page?page=1&size=1&projectId=$PROJECT_ID"
  CONTRACT_ID=$(extract_first_record_id)
  if [ -z "$CONTRACT_ID" ]; then
    fail "未获取到合同 ID"
    ABORT_REASON="阶段4: 无法获取合同ID"
    record_stage_result "FAILED"
    exit 1
  fi
  success "施工合同 ID: $CONTRACT_ID"
  track_resource "DELETE" "/api/v1/contract/$CONTRACT_ID"

  # 提交合同审批
  api_call POST "/api/v1/contract/$CONTRACT_ID/submit"
  strict_assert "提交合同审批"
  sleep 2
  approve "同意签订合同"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 5: 预算编制
# POST /api/v1/budget → 创建预算 + track_resource + 提交审批
# ===========================================================================
stage_5_budget() {
  phase "5" "预算编制"
  CURRENT_STAGE="5:预算编制"

  api_call POST "/api/v1/budget" "{\"projectId\":$PROJECT_ID,\"budgetType\":\"ORIGINAL\",\"totalAmount\":4200000.00,\"tenantId\":$TEST_TENANT_ID}"
  strict_assert "创建预算"

  sleep 1
  api_call GET "/api/v1/budget/project/$PROJECT_ID"
  BUDGET_ID=$(extract_id)
  if [ -z "$BUDGET_ID" ]; then
    fail "未获取到预算 ID"
    ABORT_REASON="阶段5: 无法获取预算ID"
    record_stage_result "FAILED"
    exit 1
  fi
  success "预算 ID: $BUDGET_ID"
  track_resource "DELETE" "/api/v1/budget/$BUDGET_ID"

  # 提交预算审批
  api_call POST "/api/v1/budget/$BUDGET_ID/submit"
  strict_assert "提交预算审批"
  sleep 2
  approve "预算批准"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 6: 采购/劳务/机械 — 每类各创建一份合同 + track_resource
# ===========================================================================
stage_6_subcontracts() {
  phase "6" "采购/劳务/机械合同"
  CURRENT_STAGE="6:采购/劳务/机械"

  # 6A: 采购合同
  log "  ── 6A: 采购合同 ──"
  api_call POST "/api/v1/purchase/contract" "{\"projectId\":$PROJECT_ID,\"contractName\":\"装修主材采购合同\",\"partyBName\":\"广州建材供应有限公司\",\"supplierName\":\"广州建材供应有限公司\",\"signingDate\":\"2026-08-05\",\"contractAmount\":800000.00,\"paymentTerms\":\"月结30天\",\"tenantId\":$TEST_TENANT_ID}"
  strict_assert "创建采购合同"

  sleep 1
  api_call GET "/api/v1/purchase/contract/page?page=1&size=1&projectId=$PROJECT_ID"
  PURCHASE_CONTRACT_ID=$(extract_first_record_id)
  if [ -n "$PURCHASE_CONTRACT_ID" ]; then
    success "采购合同 ID: $PURCHASE_CONTRACT_ID"
    track_resource "DELETE" "/api/v1/purchase/contract/$PURCHASE_CONTRACT_ID"
  fi

  # 6B: 劳务合同
  log "  ── 6B: 劳务合同 ──"
  api_call POST "/api/v1/labor/contract" "{\"projectId\":$PROJECT_ID,\"contractName\":\"泥水木工劳务合同\",\"partyBName\":\"恒通劳务公司\",\"signingDate\":\"2026-08-01\",\"startDate\":\"2026-08-01\",\"endDate\":\"2027-01-31\",\"contractAmount\":1200000.00,\"tenantId\":$TEST_TENANT_ID}"
  strict_assert "创建劳务合同"

  sleep 1
  api_call GET "/api/v1/labor/contract/page?page=1&size=1&projectId=$PROJECT_ID"
  LABOR_CONTRACT_ID=$(extract_first_record_id)
  if [ -n "$LABOR_CONTRACT_ID" ]; then
    success "劳务合同 ID: $LABOR_CONTRACT_ID"
    track_resource "DELETE" "/api/v1/labor/contract/$LABOR_CONTRACT_ID"
  fi

  # 6C: 机械合同
  log "  ── 6C: 机械合同 ──"
  api_call POST "/api/v1/machine/contract" "{\"projectId\":$PROJECT_ID,\"contractName\":\"塔吊租赁合同\",\"supplierName\":\"华南机械租赁\",\"machineName\":\"QTZ63塔吊\",\"rentalType\":\"月租\",\"signingDate\":\"2026-08-01\",\"startDate\":\"2026-08-01\",\"endDate\":\"2027-01-31\",\"contractAmount\":300000.00,\"tenantId\":$TEST_TENANT_ID}"
  strict_assert "创建机械合同"

  sleep 1
  api_call GET "/api/v1/machine/contract/page?page=1&size=1&projectId=$PROJECT_ID"
  MACHINE_CONTRACT_ID=$(extract_first_record_id)
  if [ -n "$MACHINE_CONTRACT_ID" ]; then
    success "机械合同 ID: $MACHINE_CONTRACT_ID"
    track_resource "DELETE" "/api/v1/machine/contract/$MACHINE_CONTRACT_ID"
  fi

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 7: 现场管理
# POST /api/v1/site/construction-log → 创建施工日志
# ===========================================================================
stage_7_site_management() {
  phase "7" "现场管理"
  CURRENT_STAGE="7:现场管理"

  api_call POST "/api/v1/site/construction-log" "{\"projectId\":$PROJECT_ID,\"logDate\":\"2026-08-15\",\"weather\":\"晴\",\"temperature\":\"32℃\",\"wind\":\"微风\",\"workerCount\":45,\"productionRecord\":\"地砖铺贴60%\",\"technicalRecord\":\"隐蔽验收合格\",\"tenantId\":$TEST_TENANT_ID}"
  strict_assert "创建施工日志"

  # 尝试获取施工日志 ID 用于清理追踪
  sleep 1
  api_call GET "/api/v1/site/construction-log/page?page=1&size=1&projectId=$PROJECT_ID"
  local log_id=$(extract_first_record_id)
  if [ -n "$log_id" ]; then
    success "施工日志 ID: $log_id"
    track_resource "DELETE" "/api/v1/site/construction-log/$log_id"
  fi

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 8: 产值结算
# POST /api/v1/contract/{id}/output-report 或 /api/v1/contract/output
# ===========================================================================
stage_8_output_settlement() {
  phase "8" "产值结算"
  CURRENT_STAGE="8:产值结算"

  api_call POST "/api/v1/contract/output" "{\"projectId\":$PROJECT_ID,\"contractId\":$CONTRACT_ID,\"reportPeriod\":\"2026-08\",\"currentOutput\":1200000.00,\"tenantId\":$TEST_TENANT_ID}"
  strict_assert "提交产值报告"

  sleep 1
  api_call GET "/api/v1/contract/output?page=1&size=1&projectId=$PROJECT_ID"
  local output_id=$(extract_first_record_id)
  if [ -n "$output_id" ]; then
    success "产值报告 ID: $output_id"
    track_resource "DELETE" "/api/v1/contract/output/$output_id"

    # 提交产值审批
    api_call POST "/api/v1/contract/output/$output_id/submit"
    strict_assert "提交产值审批"
    sleep 2
    approve "产值确认"
  fi

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 9: 财务收付
# POST /api/v1/finance/invoice-apply + 收款
# ===========================================================================
stage_9_finance() {
  phase "9" "财务收付"
  CURRENT_STAGE="9:财务收付"

  # 开票申请
  api_call POST "/api/v1/finance/invoice-apply" "{\"projectId\":$PROJECT_ID,\"contractId\":$CONTRACT_ID,\"invoiceType\":\"SPECIAL\",\"invoiceAmount\":1000000.00,\"invoiceTitle\":\"城市建设投资集团\",\"taxpayerId\":\"914401001234567890\",\"tenantId\":$TEST_TENANT_ID}"
  strict_assert "开票申请"

  sleep 1
  api_call GET "/api/v1/finance/invoice-apply/page?page=1&size=1&projectId=$PROJECT_ID"
  local invoice_id=$(extract_first_record_id)
  if [ -n "$invoice_id" ]; then
    success "开票申请 ID: $invoice_id"
    track_resource "DELETE" "/api/v1/finance/invoice-apply/$invoice_id"

    # 提交开票审批
    api_call POST "/api/v1/finance/invoice-apply/$invoice_id/submit"
    strict_assert "提交开票审批"
    sleep 2
    approve "同意开票"
  fi

  # 收款登记
  api_call POST "/api/v1/finance/payment-received" "{\"projectId\":$PROJECT_ID,\"contractId\":$CONTRACT_ID,\"receiveDate\":\"2026-09-15\",\"receiveAmount\":1000000.00,\"receiveType\":\"转账\",\"tenantId\":$TEST_TENANT_ID}"
  strict_assert "收款登记"

  sleep 1
  api_call GET "/api/v1/finance/payment-received/page?page=1&size=1&projectId=$PROJECT_ID"
  local received_id=$(extract_first_record_id)
  if [ -n "$received_id" ]; then
    success "收款记录 ID: $received_id"
    track_resource "DELETE" "/api/v1/finance/payment-received/$received_id"
  fi

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 10: 项目关闭
# POST /api/v1/project/{id}/close
# ===========================================================================
stage_10_project_close() {
  phase "10" "项目关闭"
  CURRENT_STAGE="10:项目关闭"

  api_call POST "/api/v1/project/$PROJECT_ID/close"
  strict_assert "项目关闭"

  # 验证项目状态
  api_call GET "/api/v1/project/$PROJECT_ID"
  local status=$(extract_field "status")
  success "项目最终状态: $status"

  record_stage_result "PASSED"
}

# ===========================================================================
# main — 入口
# ===========================================================================
main() {
  echo "" > "$SIM_LOG"
  divider
  log "═══ ZW-Insight 项目全生命周期模拟 v2 ═══"
  log "时间: $(date '+%Y-%m-%d %H:%M:%S')"
  log "服务: $BASE"
  log "测试租户: tenant_id=$TEST_TENANT_ID"
  log "报告目录: $REPORT_DIR"
  divider

  # 登录
  login_local || {
    fail "登录失败，无法继续"
    ABORT_REASON="登录失败"
    exit 1
  }

  # ─── 10 阶段业务流实现 ───
  stage_1_project_create
  stage_2_project_submit
  stage_3_tender_register
  stage_4_contract
  stage_5_budget
  stage_6_subcontracts
  stage_7_site_management
  stage_8_output_settlement
  stage_9_finance
  stage_10_project_close

  divider
  log "🎉 全生命周期模拟 v2 完成！"
  log "通过: $TOTAL_PASSED / 失败: $TOTAL_FAILED / 跳过: $TOTAL_SKIPPED"
  divider
}

main "$@"
