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
# 默认使用隔离测试租户管理员 t9999admin（tenant_id=9999），
# 由 init-test-tenant.sh 初始化；数据与演示租户 1 完全隔离，兑底 SQL 清理 WHERE tenant_id=9999 真实生效
USERNAME="${ZWI_USER:-t9999admin}"
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
# 分隔线用纯 ASCII，避免多字节字符在 scp/编码转换中破坏函数名解析
divider() { echo "" | tee -a "$SIM_LOG"; echo "===============================================" | tee -a "$SIM_LOG"; }
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
    body_code=$(grep -oE '"code"\s*:\s*\"?[0-9]+' /tmp/zwi_body | head -1 | grep -oE '[0-9]+$')
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
  val=$(grep -oE '"data"\s*:\s*\"?[0-9]+' /tmp/zwi_body | head -1 | grep -oE '[0-9]+$')
  if [ -n "$val" ]; then echo "$val"; return; fi
  val=$(grep -oE '"id"\s*:\s*\"?[0-9]+' /tmp/zwi_body | head -1 | grep -oE '[0-9]+$')
  if [ -n "$val" ]; then echo "$val"; return; fi
  echo ""
}

extract_first_record_id() { grep -oE '"id"\s*:\s*\"?[0-9]+' /tmp/zwi_body | head -1 | grep -oE '[0-9]+$'; }
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

  # 2. 兑底 SQL 清理：动态发现所有含 tenant_id 列的业务表（仅 biz_ 前缀）
  #    关键：必须限定 biz_%，否则会误删 sys_user/serial_number_rule 等含 tenant_id 的系统表
  #    （测试租户自身的账号/编号规则属于基建配置，应跨轮保留）。无真实外键约束，无序删除安全
  log "  执行兑底 SQL 清理 (tenant_id=$TEST_TENANT_ID, 仅 biz_ 表)..."
  local MYSQL_PW="${ZWI_MYSQL_PASS:-zwinsight123}"
  local del_sql
  del_sql=$(docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PW" -N -B \
    -e "SELECT CONCAT('DELETE FROM \`', TABLE_NAME, '\` WHERE tenant_id=$TEST_TENANT_ID;') \
        FROM information_schema.COLUMNS \
        WHERE TABLE_SCHEMA='zw_insight' AND COLUMN_NAME='tenant_id' \
          AND TABLE_NAME LIKE 'biz_%';" 2>/dev/null)
  local sql_cleaned=0
  if [ -n "$del_sql" ]; then
    # SET FOREIGN_KEY_CHECKS=0 防御；批量执行所有 DELETE
    printf 'SET FOREIGN_KEY_CHECKS=0;\n%s\n' "$del_sql" | \
      docker exec -i "$MYSQL_CT" mysql -uroot -p"$MYSQL_PW" zw_insight 2>/dev/null || true
    sql_cleaned=$(echo "$del_sql" | grep -c 'DELETE FROM')
  fi
  log "  兑底 SQL 清理完成（执行 $sql_cleaned 张表的 tenant_id=$TEST_TENANT_ID 删除）"

  # 2b. 清理 Flowable 流程运行时/历史数据（按 TENANT_ID_ 列）
  docker exec "$MYSQL_CT" mysql -uroot -p"$MYSQL_PW" zw_insight 2>/dev/null <<FLOW || true
SET FOREIGN_KEY_CHECKS=0;
DELETE FROM ACT_RU_TASK WHERE TENANT_ID_='$TEST_TENANT_ID';
DELETE FROM ACT_RU_EXECUTION WHERE TENANT_ID_='$TEST_TENANT_ID';
DELETE FROM ACT_HI_TASKINST WHERE TENANT_ID_='$TEST_TENANT_ID';
DELETE FROM ACT_HI_PROCINST WHERE TENANT_ID_='$TEST_TENANT_ID';
SET FOREIGN_KEY_CHECKS=1;
FLOW

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
SUBCONTRACT_CONTRACT_ID=""
MACHINE_LEDGER_ID=""
INBOUND_ID=""
PURCHASE_SETTLEMENT_ID=""
OTHER_CONTRACT_ID=""

# ===========================================================================
# db_next_task — 从 Flowable 运行时表取租户 9999 最早一个待办 taskId
# 原因：getMyTodoTasks 仅按 assignee 查询，而部分 BPMN 第2级为 candidateGroups=FINANCE
#       （如 machine_settlement/project_close）无 assignee，不会出现在待办里。
#       SUPER_ADMIN 在 assertTaskAssignee 中被放行，可直接 complete 任意 taskId，
#       故直接从 ACT_RU_TASK 取待办 ID 驱动，统一处理 assignee 与候选任务。
# ===========================================================================
db_next_task() {
  docker exec "$MYSQL_CT" mysql -uroot -p"${ZWI_MYSQL_PASS:-zwinsight123}" zw_insight -N -B \
    -e "SELECT ID_ FROM ACT_RU_TASK WHERE TENANT_ID_='$TEST_TENANT_ID' ORDER BY CREATE_TIME_ ASC LIMIT 1;" 2>/dev/null | tr -d '\r' | head -1
}

# ===========================================================================
# approve() — 逐个完成当前租户的待办任务（从 ACT_RU_TASK 取 taskId）
# 用法：approve "审批意见" [required]
#   required=1：首轮无待办即判失败（有流程单据用，防流程未启动被静默放过）
# ===========================================================================
approve() {
  local comment="${1:-同意}"
  local required="${2:-0}"
  local handled=0
  local round
  for round in 1 2 3 4 5 6 7 8; do
    local task_id=$(db_next_task)
    if [ -z "$task_id" ]; then
      if [ "$round" -eq 1 ] && [ "$required" = "1" ]; then
        fail "审批失败：期望有待办任务但无待办（流程可能未启动）"
        ABORT_REASON="$CURRENT_STAGE: 审批待办为空"
        record_stage_result "FAILED"
        exit 1
      fi
      break
    fi
    api_call POST "/api/v1/workflow/approval/complete" "{\"taskId\":\"$task_id\",\"comment\":\"$comment\"}"
    strict_assert "审批完成 第${round}级 (taskId: ${task_id:0:8}...)"
    handled=$((handled+1))
    sleep 1
  done
  if [ "$required" = "1" ] && [ "$handled" -eq 0 ]; then
    fail "审批失败：未处理任何待办"
    ABORT_REASON="$CURRENT_STAGE: 未处理待办"
    record_stage_result "FAILED"
    exit 1
  fi
}

# ===========================================================================
# 断言库（需求 5.2：每阶段 HTTP 2xx + 业务状态/金额硬断言）
# 消除“创建返回 200 即算通过”的弱判定，真实回查业务字段
# ===========================================================================
# require_id <value> <desc> — ID 为空立即失败（替代 if [ -n "$id" ] 静默跳过）
require_id() {
  local value="$1" desc="$2"
  if [ -z "$value" ]; then
    fail "$desc: 未获取到 ID（不允许静默跳过）"
    ABORT_REASON="$CURRENT_STAGE: $desc 无ID"
    record_stage_result "FAILED"
    exit 1
  fi
}

# assert_status <GET-path> <field> <expected> <desc> — GET 详情后断言字段值
assert_status() {
  local path="$1" field="$2" expected="$3" desc="$4"
  api_call GET "$path"
  local actual=$(extract_field "$field")
  if [ "$actual" != "$expected" ]; then
    fail "$desc: 期望 $field=$expected，实际=$actual"
    ABORT_REASON="$CURRENT_STAGE: $desc ($field=$actual!=$expected)"
    record_stage_result "FAILED"
    exit 1
  fi
  success "$desc: $field=$actual [OK]"
}

# assert_amount <GET-path> <field> <expected> <desc> — 数值断言（容差 0.01）
assert_amount() {
  local path="$1" field="$2" expected="$3" desc="$4"
  api_call GET "$path"
  local actual=$(grep -oE "\"$field\"[[:space:]]*:[[:space:]]*\"?[0-9.]+" /tmp/zwi_body | head -1 | grep -oE '[0-9.]+$')
  actual="${actual:-0}"
  local ok=$(awk -v a="$actual" -v e="$expected" 'BEGIN{d=a-e; if(d<0)d=-d; print (d<=0.01)?"1":"0"}')
  if [ "$ok" != "1" ]; then
    fail "$desc: 期望 $field~=$expected，实际=$actual"
    ABORT_REASON="$CURRENT_STAGE: $desc ($field=$actual!=$expected)"
    record_stage_result "FAILED"
    exit 1
  fi
  success "$desc: $field=$actual [OK]"
}

# neg_assert <desc> — 负向断言：最近一次调用必须被拒绝（code!=200 或 HTTP 4xx/5xx）
# 若静默成功（code=200 且 HTTP 2xx）即判失败，杜绝"非法操作被放行"的假绿
neg_assert() {
  local desc="$1" http_code body_code
  http_code=$(cat /tmp/zwi_last_code 2>/dev/null || echo "000")
  body_code=$(grep -oE '"code"\s*:\s*\"?[0-9]+' /tmp/zwi_body 2>/dev/null | head -1 | grep -oE '[0-9]+$')
  if [ "$body_code" != "200" ] || [[ "$http_code" =~ ^[45] ]]; then
    success "$desc: 已正确拒绝 (HTTP $http_code, code=${body_code:-N/A})"
  else
    fail "$desc: 期望被拒绝，实际静默成功 (HTTP $http_code, code=$body_code)"
    ABORT_REASON="$CURRENT_STAGE: $desc (负向未拒绝)"
    record_stage_result "FAILED"
    exit 1
  fi
}

# ===========================================================================
# 阶段 1: 项目报备
# POST /api/v1/project → 创建项目 + track_resource
# ===========================================================================
stage_1_project_create() {
  phase "1" "项目报备"
  CURRENT_STAGE="1:项目报备"

  api_call POST "/api/v1/project" "{\"projectName\":\"[测试]中维综合楼装修工程\",\"projectNature\":\"装修改造\",\"projectType\":\"公共建筑\",\"ownerCompanyName\":\"城市建设投资集团\",\"signingCompanyName\":\"中维建设有限公司\",\"projectOverview\":\"中维综合楼1-5层精装修改造\",\"projectAddress\":\"广州市天河区体育西路188号\",\"contactName\":\"张建国\",\"contactPhone\":\"13800138001\",\"needTender\":1,\"budgetAmount\":5000000.00}"
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

  # 创建后断言初始状态为 DRAFT
  assert_status "/api/v1/project/$PROJECT_ID" "status" "DRAFT" "项目初始状态"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 2: 立项（DRAFT → FILED，无审批流程，后端 submit 直接置位）
# ===========================================================================
stage_2_project_submit() {
  phase "2" "立项提交"
  CURRENT_STAGE="2:立项提交"

  api_call POST "/api/v1/project/$PROJECT_ID/submit"
  strict_assert "提交立项"

  sleep 1
  # 立项无审批流程，提交后状态直接为 FILED（ProjectService.submit 硬编码）
  assert_status "/api/v1/project/$PROJECT_ID" "status" "FILED" "立项后项目状态"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 3: 投标登记
# POST /api/v1/tender/register → 登记投标 + track_resource
# ===========================================================================
stage_3_tender_register() {
  phase "3" "投标登记"
  CURRENT_STAGE="3:投标登记"

  api_call POST "/api/v1/tender/register" "{\"projectId\":$PROJECT_ID,\"ownerCompany\":\"城市建设投资集团\",\"bidMethod\":\"公开招标\",\"registerMethod\":\"线上报名\",\"registerDate\":\"2026-07-07\",\"openDate\":\"2026-07-20\",\"tenderMethod\":\"综合评标法\",\"depositAmount\":100000.00,\"status\":\"REGISTERED\"}"
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

  # 投标登记保存时后端自动将项目置 TENDERING（TenderRegisterService）
  assert_status "/api/v1/project/$PROJECT_ID" "status" "TENDERING" "投标登记后项目状态"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 3B: 开标中标
# POST /api/v1/tender/open-bid（isWon=1 → 项目 status=WON、登记 status=WON）
# ===========================================================================
stage_3b_open_bid() {
  phase "3B" "开标中标"
  CURRENT_STAGE="3B:开标中标"

  api_call POST "/api/v1/tender/open-bid" "{\"registerId\":$REGISTER_ID,\"projectId\":$PROJECT_ID,\"openDate\":\"2026-07-20\",\"isWon\":1,\"winAmount\":4800000.00,\"remark\":\"全生命周期模拟-中标\"}"
  strict_assert "开标中标登记"

  sleep 1
  # 中标后项目状态自动置 WON
  assert_status "/api/v1/project/$PROJECT_ID" "status" "WON" "中标后项目状态"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 4: 施工合同
# POST /api/v1/contract → 创建合同 + track_resource + 提交审批
# ===========================================================================
stage_4_contract() {
  phase "4" "施工合同"
  CURRENT_STAGE="4:施工合同"

  api_call POST "/api/v1/contract" "{\"projectId\":$PROJECT_ID,\"contractType\":\"REGISTER\",\"partyAName\":\"城市建设投资集团\",\"signingDate\":\"2026-07-25\",\"startDate\":\"2026-08-01\",\"endDate\":\"2027-02-01\",\"contractAmount\":4800000.00,\"taxRate\":9.00,\"amountWithoutTax\":4403669.72,\"taxAmount\":396330.28}"
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

  # 提交合同审批（construction_contract_approval 两级）
  api_call POST "/api/v1/contract/$CONTRACT_ID/submit"
  strict_assert "提交合同审批"
  sleep 2
  approve "同意签订合同" 1

  # 审批通过后：合同 EFFECTIVE + 项目由 WON → CONSTRUCTION
  assert_status "/api/v1/contract/$CONTRACT_ID" "status" "EFFECTIVE" "合同审批后状态"
  assert_status "/api/v1/project/$PROJECT_ID" "status" "CONSTRUCTION" "合同生效后项目状态"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 5: 预算编制
# POST /api/v1/budget → 创建预算 + track_resource + 提交审批
# ===========================================================================
stage_5_budget() {
  phase "5" "预算编制"
  CURRENT_STAGE="5:预算编制"

  api_call POST "/api/v1/budget" "{\"projectId\":$PROJECT_ID,\"budgetType\":\"ORIGINAL\",\"totalAmount\":4200000.00}"
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

  # 提交预算审批（若无流程则 approve 非 required 会空转）
  api_call POST "/api/v1/budget/$BUDGET_ID/submit"
  strict_assert "提交预算审批"
  sleep 2
  approve "预算批准"

  # ── 预算控制负向用例：先 BLOCK 验证拦截生效，再改 WARN_ONLY 放行 ──
  # 新项目无科目额度，BLOCK 模式下支出合同会被 @BudgetCheck 拦截
  api_call POST "/api/v1/budget-control-configs" "{\"projectId\":$PROJECT_ID,\"controlMode\":\"BLOCK\",\"warningThreshold\":80}"
  strict_assert "配置预算控制 BLOCK"
  sleep 1
  api_call GET "/api/v1/budget-control-configs?page=1&size=1&projectName="
  BUDGET_CTRL_ID=$(extract_first_record_id)
  require_id "$BUDGET_CTRL_ID" "预算控制配置 ID"
  track_resource "DELETE" "/api/v1/budget-control-configs/$BUDGET_CTRL_ID"

  # 负向断言：BLOCK 下创建采购合同应被拦截（HTTP非2xx 或 code!=200 且含“预算”）
  api_call POST "/api/v1/purchase/contract" "{\"projectId\":$PROJECT_ID,\"contractName\":\"[负向]预算拦截探针合同\",\"partyBName\":\"探针供应商\",\"supplierName\":\"探针供应商\",\"signingDate\":\"2026-08-05\",\"contractAmount\":800000.00}"
  local blk_code=$(cat /tmp/zwi_last_code 2>/dev/null || echo 000)
  if [[ "$blk_code" =~ ^2[0-9][0-9]$ ]] && ! grep -q "预算" /tmp/zwi_body; then
    fail "预算 BLOCK 未生效：支出合同本应被拦截（HTTP $blk_code）"
    ABORT_REASON="阶段5: 预算BLOCK未生效"
    record_stage_result "FAILED"
    exit 1
  fi
  success "预算 BLOCK 生效：支出合同创建被拦截 (HTTP $blk_code)"

  # 改为 WARN_ONLY（PUT 更新），后续支出合同可在超预算时仅告警不拦截
  api_call PUT "/api/v1/budget-control-configs/$BUDGET_CTRL_ID" "{\"projectId\":$PROJECT_ID,\"controlMode\":\"WARN_ONLY\",\"warningThreshold\":80}"
  strict_assert "预算控制改为 WARN_ONLY"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 6: 支出合同（采购/劳务/机械/分包）— 创建+提交+断言 EFFECTIVE
# ===========================================================================
stage_6_subcontracts() {
  phase "6" "支出合同（采购/劳务/机械/分包）"
  CURRENT_STAGE="6:支出合同"

  # 6A: 采购合同（purchase_contract_approval 流程）
  log "  -- 6A: 采购合同 --"
  api_call POST "/api/v1/purchase/contract" "{\"projectId\":$PROJECT_ID,\"contractName\":\"装修主材采购合同\",\"partyBName\":\"广州建材供应有限公司\",\"supplierName\":\"广州建材供应有限公司\",\"signingDate\":\"2026-08-05\",\"contractAmount\":800000.00,\"paymentTerms\":\"月结30天\"}"
  strict_assert "创建采购合同"
  sleep 1
  api_call GET "/api/v1/purchase/contract/page?page=1&size=1&projectId=$PROJECT_ID"
  PURCHASE_CONTRACT_ID=$(extract_first_record_id)
  require_id "$PURCHASE_CONTRACT_ID" "采购合同 ID"
  success "采购合同 ID: $PURCHASE_CONTRACT_ID"
  track_resource "DELETE" "/api/v1/purchase/contract/$PURCHASE_CONTRACT_ID"
  api_call POST "/api/v1/purchase/contract/$PURCHASE_CONTRACT_ID/submit"
  strict_assert "提交采购合同审批"
  sleep 2
  approve "同意采购合同"
  assert_status "/api/v1/purchase/contract/$PURCHASE_CONTRACT_ID" "status" "EFFECTIVE" "采购合同状态"

  # 6B: 劳务合同（无工作流，submit 直接 EFFECTIVE）
  log "  -- 6B: 劳务合同 --"
  api_call POST "/api/v1/labor/contract" "{\"projectId\":$PROJECT_ID,\"contractName\":\"泥水木工劳务合同\",\"partyBName\":\"恒通劳务公司\",\"teamName\":\"恒通施工队\",\"signingDate\":\"2026-08-01\",\"startDate\":\"2026-08-01\",\"endDate\":\"2027-01-31\",\"contractAmount\":1200000.00}"
  strict_assert "创建劳务合同"
  sleep 1
  api_call GET "/api/v1/labor/contract/page?page=1&size=1&projectId=$PROJECT_ID"
  LABOR_CONTRACT_ID=$(extract_first_record_id)
  require_id "$LABOR_CONTRACT_ID" "劳务合同 ID"
  success "劳务合同 ID: $LABOR_CONTRACT_ID"
  track_resource "DELETE" "/api/v1/labor/contract/$LABOR_CONTRACT_ID"
  api_call POST "/api/v1/labor/contract/$LABOR_CONTRACT_ID/submit"
  strict_assert "提交劳务合同"
  sleep 1
  assert_status "/api/v1/labor/contract/$LABOR_CONTRACT_ID" "status" "EFFECTIVE" "劳务合同状态"

  # 6C: 机械合同（rentalType=台班，contractAmount 作为台班单价—代码取 contractAmount 为单价）
  log "  -- 6C: 机械合同 --"
  api_call POST "/api/v1/machine/contract" "{\"projectId\":$PROJECT_ID,\"contractName\":\"塔吊租赁合同\",\"supplierName\":\"华南机械租赁\",\"machineName\":\"QTZ63塔吊\",\"rentalType\":\"台班\",\"signingDate\":\"2026-08-01\",\"startDate\":\"2026-08-01\",\"endDate\":\"2027-01-31\",\"contractAmount\":500.00}"
  strict_assert "创建机械合同"
  sleep 1
  api_call GET "/api/v1/machine/contract/page?page=1&size=1&projectId=$PROJECT_ID"
  MACHINE_CONTRACT_ID=$(extract_first_record_id)
  require_id "$MACHINE_CONTRACT_ID" "机械合同 ID"
  success "机械合同 ID: $MACHINE_CONTRACT_ID"
  track_resource "DELETE" "/api/v1/machine/contract/$MACHINE_CONTRACT_ID"
  api_call POST "/api/v1/machine/contract/$MACHINE_CONTRACT_ID/submit"
  strict_assert "提交机械合同"
  sleep 1
  assert_status "/api/v1/machine/contract/$MACHINE_CONTRACT_ID" "status" "EFFECTIVE" "机械合同状态"

  # 6D: 分包合同（无工作流，submit 直接 EFFECTIVE）
  log "  -- 6D: 分包合同 --"
  api_call POST "/api/v1/subcontract/contract" "{\"projectId\":$PROJECT_ID,\"contractName\":\"钢结构分包合同\",\"partyBName\":\"中铁分包公司\",\"supplierName\":\"中铁分包公司\",\"signingDate\":\"2026-08-02\",\"contractAmount\":600000.00}"
  strict_assert "创建分包合同"
  sleep 1
  api_call GET "/api/v1/subcontract/contract/page?page=1&size=1&projectId=$PROJECT_ID"
  SUBCONTRACT_CONTRACT_ID=$(extract_first_record_id)
  require_id "$SUBCONTRACT_CONTRACT_ID" "分包合同 ID"
  success "分包合同 ID: $SUBCONTRACT_CONTRACT_ID"
  track_resource "DELETE" "/api/v1/subcontract/contract/$SUBCONTRACT_CONTRACT_ID"
  api_call POST "/api/v1/subcontract/contract/$SUBCONTRACT_CONTRACT_ID/submit"
  strict_assert "提交分包合同"
  sleep 1
  assert_status "/api/v1/subcontract/contract/$SUBCONTRACT_CONTRACT_ID" "status" "EFFECTIVE" "分包合同状态"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 7: 现场管理
# POST /api/v1/site/construction-log → 创建施工日志
# ===========================================================================
stage_7_site_management() {
  phase "7" "现场管理"
  CURRENT_STAGE="7:现场管理"

  api_call POST "/api/v1/site/construction-log" "{\"projectId\":$PROJECT_ID,\"logDate\":\"2026-08-15\",\"weather\":\"晴\",\"temperature\":\"32℃\",\"wind\":\"微风\",\"workerCount\":45,\"productionRecord\":\"地砖铺贴60%\",\"technicalRecord\":\"隐蔽验收合格\"}"
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

  api_call POST "/api/v1/contract/output" "{\"projectId\":$PROJECT_ID,\"contractId\":$CONTRACT_ID,\"reportPeriod\":\"2026-08\",\"currentOutput\":1200000.00}"
  strict_assert "提交产值报告"

  sleep 1
  api_call GET "/api/v1/contract/output?page=1&size=1&projectId=$PROJECT_ID"
  local output_id=$(extract_first_record_id)
  require_id "$output_id" "产值报告 ID"
  success "产值报告 ID: $output_id"
  track_resource "DELETE" "/api/v1/contract/output/$output_id"

  # 提交产值审批（output_report_approval 两级）
  api_call POST "/api/v1/contract/output/$output_id/submit"
  strict_assert "提交产值审批"
  sleep 2
  approve "产值确认" 1

  # 审批通过后回写施工合同累计产值=120万
  assert_amount "/api/v1/contract/$CONTRACT_ID" "cumulativeOutput" "1200000" "施工合同累计产值"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 9: 财务收付
# POST /api/v1/finance/invoice-apply + 收款
# ===========================================================================
stage_9_finance() {
  phase "9" "财务收付"
  CURRENT_STAGE="9:财务收付"

  # 开票申请（applyDate 为后端 @NotNull 必填；金额对齐累计产值 120 万，为后续收款结清/结项铺路）
  api_call POST "/api/v1/finance/invoice-apply" "{\"projectId\":$PROJECT_ID,\"contractId\":$CONTRACT_ID,\"applyDate\":\"2026-09-10\",\"invoiceType\":\"SPECIAL\",\"invoiceAmount\":1200000.00,\"invoiceTitle\":\"城市建设投资集团\",\"taxpayerId\":\"914401001234567890\"}"
  strict_assert "开票申请"

  sleep 1
  api_call GET "/api/v1/finance/invoice-apply/page?page=1&size=1&projectId=$PROJECT_ID"
  local invoice_id=$(extract_first_record_id)
  require_id "$invoice_id" "开票申请 ID"
  success "开票申请 ID: $invoice_id"
  track_resource "DELETE" "/api/v1/finance/invoice-apply/$invoice_id"
  api_call POST "/api/v1/finance/invoice-apply/$invoice_id/submit"
  strict_assert "提交开票审批"
  sleep 2
  approve "同意开票" 1
  # 开票审批通过后回写累计开票=120万
  assert_amount "/api/v1/contract/$CONTRACT_ID" "cumulativeInvoiceAmount" "1200000" "施工合同累计开票"

  # 收款登记（金额对齐累计产值，满足结项「款项基本结清」条件：产值-已收 ≤ 100）
  api_call POST "/api/v1/finance/payment-received" "{\"projectId\":$PROJECT_ID,\"contractId\":$CONTRACT_ID,\"receiveDate\":\"2026-09-15\",\"receiveAmount\":1200000.00,\"receiveType\":\"转账\"}"
  strict_assert "收款登记"

  sleep 1
  api_call GET "/api/v1/finance/payment-received/page?page=1&size=1&projectId=$PROJECT_ID"
  local received_id=$(extract_first_record_id)
  require_id "$received_id" "收款记录 ID"
  success "收款记录 ID: $received_id"
  track_resource "DELETE" "/api/v1/finance/payment-received/$received_id"

  # 收款登记后回写项目总收入=120万（满足结项“款项基本结清”：产值-已收≤0）
  assert_amount "/api/v1/project/$PROJECT_ID" "totalIncome" "1200000" "项目总收入"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 9B: 竣工验收 + 最终结算（结项前置条件）
# POST /api/v1/site/completion + /api/v1/project-settlements
# ===========================================================================
stage_9b_completion_settlement() {
  phase "9B" "竣工验收与最终结算"
  CURRENT_STAGE="9B:竣工验收与最终结算"

  # 竣工验收：创建 + 提交（提交后项目状态→COMPLETED）
  api_call POST "/api/v1/site/completion" "{\"projectId\":$PROJECT_ID,\"acceptanceDate\":\"2026-10-10\",\"acceptanceResult\":\"合格\",\"remark\":\"全生命周期模拟-竣工验收\"}"
  strict_assert "创建竣工验收"

  sleep 1
  api_call GET "/api/v1/site/completion/page?page=1&size=1&projectId=$PROJECT_ID"
  local acceptance_id=$(extract_first_record_id)
  if [ -z "$acceptance_id" ]; then
    fail "未获取到竣工验收 ID"
    ABORT_REASON="阶段9B: 无法获取竣工验收ID"
    record_stage_result "FAILED"
    exit 1
  fi
  success "竣工验收 ID: $acceptance_id"
  track_resource "DELETE" "/api/v1/site/completion/$acceptance_id"

  api_call POST "/api/v1/site/completion/$acceptance_id/submit"
  strict_assert "提交竣工验收"
  sleep 2
  approve "竣工验收通过" 1

  # 竣工验收单状态断言（提交即置 APPROVED，审批流完成为硬要求）
  assert_status "/api/v1/site/completion/page?page=1&size=1&projectId=$PROJECT_ID" "status" "APPROVED" "竣工验收单状态"

  # 最终结算：创建（RequestParam projectId）+ 提交审批（project_settlement_approval）
  assert_status "/api/v1/project/$PROJECT_ID" "status" "COMPLETED" "completion-project-status"
  api_call POST "/api/v1/project-settlements?projectId=$PROJECT_ID"
  strict_assert "创建最终结算单"
  local settlement_id=$(extract_id)
  if [ -z "$settlement_id" ]; then
    fail "未获取到结算单 ID"
    ABORT_REASON="阶段9B: 无法获取结算单ID"
    record_stage_result "FAILED"
    exit 1
  fi
  success "最终结算单 ID: $settlement_id"

  api_call POST "/api/v1/project-settlements/$settlement_id/submit"
  strict_assert "提交最终结算审批"
  sleep 2
  approve "最终结算通过" 1

  # 结算单审批后状态断言（onApproved 置 APPROVED）+ 施工合同 EFFECTIVE→SETTLED 流转断言
  assert_status "/api/v1/project-settlements/$settlement_id" "status" "APPROVED" "最终结算单状态"
  assert_status "/api/v1/contract/$CONTRACT_ID" "status" "SETTLED" "施工合同结算后状态"

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
  strict_assert "项目关闭（发起结项审批）"
  sleep 2
  approve "同意结项" 1

  # 结项审批通过后项目状态 CLOSED
  assert_status "/api/v1/project/$PROJECT_ID" "status" "CLOSED" "项目最细状态"

  record_stage_result "PASSED"
}

# ===========================================================================
# main — 入口
# ===========================================================================
# ===========================================================================
# 阶段 7B: 材料入库/出库（入库后库存增加、回写采购合同累计入库）
# ===========================================================================
stage_7b_material() {
  phase "7B" "材料入出库"
  CURRENT_STAGE="7B:材料入出库"

  # 入库 10 万（关联采购合同，含明细）
  api_call POST "/api/v1/material/inbound" "{\"projectId\":$PROJECT_ID,\"contractId\":$PURCHASE_CONTRACT_ID,\"inboundDate\":\"2026-08-20\",\"totalAmount\":100000.00,\"directOutbound\":0,\"details\":[{\"materialName\":\"瓷砖\",\"specification\":\"800x800\",\"unit\":\"块\",\"quantity\":2000,\"unitPrice\":50.00,\"totalPrice\":100000.00}]}"
  strict_assert "创建材料入库单"
  sleep 1
  api_call GET "/api/v1/material/inbound/page?page=1&size=1&projectId=$PROJECT_ID"
  INBOUND_ID=$(extract_first_record_id)
  require_id "$INBOUND_ID" "材料入库单 ID"
  success "材料入库单 ID: $INBOUND_ID"
  track_resource "DELETE" "/api/v1/material/inbound/$INBOUND_ID"
  api_call POST "/api/v1/material/inbound/$INBOUND_ID/submit"
  strict_assert "提交材料入库"
  sleep 1
  assert_status "/api/v1/material/inbound/$INBOUND_ID" "status" "APPROVED" "入库单状态"
  # 入库审批后回写采购合同累计入库=100000
  assert_amount "/api/v1/purchase/contract/$PURCHASE_CONTRACT_ID" "cumulativeInbound" "100000" "采购合同累计入库"

  # 出库 6 万
  api_call POST "/api/v1/material/outbound" "{\"projectId\":$PROJECT_ID,\"outboundType\":\"NORMAL\",\"outboundDate\":\"2026-08-25\",\"operatorName\":\"仓管员\",\"details\":[{\"materialName\":\"瓷砖\",\"specification\":\"800x800\",\"unit\":\"块\",\"quantity\":1200,\"unitPrice\":50.00,\"totalPrice\":60000.00}]}"
  strict_assert "创建材料出库单"
  sleep 1
  api_call GET "/api/v1/material/outbound/page?page=1&size=1&projectId=$PROJECT_ID"
  local outbound_id=$(extract_first_record_id)
  require_id "$outbound_id" "材料出库单 ID"
  success "材料出库单 ID: $outbound_id"
  track_resource "DELETE" "/api/v1/material/outbound/$outbound_id"
  api_call POST "/api/v1/material/outbound/$outbound_id/submit"
  strict_assert "提交材料出库"
  sleep 1
  assert_status "/api/v1/material/outbound/$outbound_id" "status" "APPROVED" "出库单状态"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 7C: 机械执行（台账 → 进场 → 台班日志 → 结算）
# 机械结算通过 machineName 匹配 EFFECTIVE 机械合同取单价（=contractAmount=500）
# ===========================================================================
stage_7c_machine_exec() {
  phase "7C" "机械执行与结算"
  CURRENT_STAGE="7C:机械执行"

  # 台账（租户级资产，machineName 与机械合同一致以便结算匹配）
  api_call POST "/api/v1/machine/ledger" "{\"machineName\":\"QTZ63塔吊\",\"machineCode\":\"JX-T9-001\",\"machineType\":\"起重机械\",\"ownerType\":\"租赁\",\"status\":\"REGISTERED\"}"
  strict_assert "创建机械台账"
  sleep 1
  api_call GET "/api/v1/machine/ledger/page?page=1&size=1&machineName=QTZ63"
  MACHINE_LEDGER_ID=$(extract_first_record_id)
  require_id "$MACHINE_LEDGER_ID" "机械台账 ID"
  success "机械台账 ID: $MACHINE_LEDGER_ID"
  track_resource "DELETE" "/api/v1/machine/ledger/$MACHINE_LEDGER_ID"

  # 进场
  api_call POST "/api/v1/machine/entry/in" "{\"machineId\":$MACHINE_LEDGER_ID,\"projectId\":$PROJECT_ID,\"entryDate\":\"2026-08-05\",\"entryType\":\"IN\"}"
  strict_assert "机械进场"

  # 台班日志 shiftCount=10
  api_call POST "/api/v1/machine/work-log" "{\"machineId\":$MACHINE_LEDGER_ID,\"projectId\":$PROJECT_ID,\"workDate\":\"2026-08-15\",\"shiftCount\":10,\"workQuantity\":0}"
  strict_assert "创建台班工作日志"

  # 结算：周期覆盖日志（金额=shiftCount*单价=10*500=5000）
  api_call POST "/api/v1/machine/settlement" "{\"projectId\":$PROJECT_ID,\"periodStart\":\"2026-08-01\",\"periodEnd\":\"2026-08-31\"}"
  strict_assert "创建机械结算单"
  sleep 1
  api_call GET "/api/v1/machine/settlement?page=1&size=1&projectId=$PROJECT_ID"
  local m_settle_id=$(extract_first_record_id)
  require_id "$m_settle_id" "机械结算单 ID"
  success "机械结算单 ID: $m_settle_id"
  track_resource "DELETE" "/api/v1/machine/settlement/$m_settle_id"
  # 结算金额断言=5000
  assert_amount "/api/v1/machine/settlement/$m_settle_id" "totalAmount" "5000" "机械结算金额"
  # 提交审批（machine_settlement 流程）
  api_call POST "/api/v1/machine/settlement/$m_settle_id/submit"
  strict_assert "提交机械结算审批"
  sleep 2
  approve "机械结算确认" 1
  # 审批通过后 status=2（已审批）+ 回写机械合同累计结算=5000
  assert_status "/api/v1/machine/settlement/$m_settle_id" "status" "2" "机械结算状态"
  assert_amount "/api/v1/machine/contract/$MACHINE_CONTRACT_ID" "cumulativeSettlement" "5000" "机械合同累计结算"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 7D: 劳务执行（劳务结算直接给金额，后端 submit 直接 APPROVED）
# ===========================================================================
stage_7d_labor_exec() {
  phase "7D" "劳务执行与结算"
  CURRENT_STAGE="7D:劳务执行"

  # 劳务结算 5 万（LaborSettlement 只要 projectId/contractId/settlementAmount，不依赖工单）
  api_call POST "/api/v1/labor/settlement" "{\"projectId\":$PROJECT_ID,\"contractId\":$LABOR_CONTRACT_ID,\"settlementAmount\":50000.00}"
  strict_assert "创建劳务结算单"
  sleep 1
  api_call GET "/api/v1/labor/settlement/page?page=1&size=1&projectId=$PROJECT_ID"
  local l_settle_id=$(extract_first_record_id)
  require_id "$l_settle_id" "劳务结算单 ID"
  success "劳务结算单 ID: $l_settle_id"
  track_resource "DELETE" "/api/v1/labor/settlement/$l_settle_id"
  api_call POST "/api/v1/labor/settlement/$l_settle_id/submit"
  strict_assert "提交劳务结算"
  sleep 1
  # 无工作流，submit 直接 APPROVED + 回写劳务合同累计结算=50000
  assert_status "/api/v1/labor/settlement/$l_settle_id" "status" "APPROVED" "劳务结算状态"
  assert_amount "/api/v1/labor/contract/$LABOR_CONTRACT_ID" "cumulativeSettlement" "50000" "劳务合同累计结算"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 7E: 分包执行（产值 → 结算，含明细）
# ===========================================================================
stage_7e_subcontract_exec() {
  phase "7E" "分包执行与结算"
  CURRENT_STAGE="7E:分包执行"

  # 分包产值 3 万
  api_call POST "/api/v1/subcontract/output" "{\"projectId\":$PROJECT_ID,\"contractId\":$SUBCONTRACT_CONTRACT_ID,\"currentOutput\":30000.00}"
  strict_assert "创建分包产值"
  sleep 1
  api_call GET "/api/v1/subcontract/output/page?page=1&size=1&projectId=$PROJECT_ID"
  local sub_output_id=$(extract_first_record_id)
  require_id "$sub_output_id" "分包产值 ID"
  track_resource "DELETE" "/api/v1/subcontract/output/$sub_output_id"
  api_call POST "/api/v1/subcontract/output/$sub_output_id/submit"
  strict_assert "提交分包产值"
  sleep 1

  # 分包结算 3 万（明细 quantity*unitPrice=300*100=30000）
  api_call POST "/api/v1/subcontract/settlement" "{\"projectId\":$PROJECT_ID,\"contractId\":$SUBCONTRACT_CONTRACT_ID,\"details\":[{\"itemName\":\"钢结构安装\",\"quantity\":300,\"unitPrice\":100.00}]}"
  strict_assert "创建分包结算单"
  sleep 1
  api_call GET "/api/v1/subcontract/settlement?page=1&size=1&projectId=$PROJECT_ID"
  local sub_settle_id=$(extract_first_record_id)
  require_id "$sub_settle_id" "分包结算单 ID"
  track_resource "DELETE" "/api/v1/subcontract/settlement/$sub_settle_id"
  api_call POST "/api/v1/subcontract/settlement/$sub_settle_id/submit"
  strict_assert "提交分包结算"
  sleep 1
  assert_amount "/api/v1/subcontract/contract/$SUBCONTRACT_CONTRACT_ID" "cumulativeSettlement" "30000" "分包合同累计结算"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 9C: 采购结算（关联已审批入库单，submit 直接 APPROVED）
# ===========================================================================
stage_9c_purchase_settlement() {
  phase "9C" "采购结算"
  CURRENT_STAGE="9C:采购结算"

  # 采购结算 10 万（关联已审批入库单 INBOUND_ID，结算金额≤入库金额）
  api_call POST "/api/v1/purchase/settlement" "{\"projectId\":$PROJECT_ID,\"contractId\":$PURCHASE_CONTRACT_ID,\"inboundId\":$INBOUND_ID,\"settlementAmount\":100000.00}"
  strict_assert "创建采购结算单"
  sleep 1
  api_call GET "/api/v1/purchase/settlement/page?page=1&size=1&projectId=$PROJECT_ID"
  PURCHASE_SETTLEMENT_ID=$(extract_first_record_id)
  require_id "$PURCHASE_SETTLEMENT_ID" "采购结算单 ID"
  success "采购结算单 ID: $PURCHASE_SETTLEMENT_ID"
  track_resource "DELETE" "/api/v1/purchase/settlement/$PURCHASE_SETTLEMENT_ID"
  api_call POST "/api/v1/purchase/settlement/$PURCHASE_SETTLEMENT_ID/submit"
  strict_assert "提交采购结算审批"
  sleep 2
  approve "采购结算确认"
  # submit 后立即回写采购合同累计结算=100000
  assert_amount "/api/v1/purchase/contract/$PURCHASE_CONTRACT_ID" "cumulativeSettlement" "100000" "采购合同累计结算"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 9D: 付款闭环（4 笔付款，均≤各自 cumulativeSettlement，payment_apply_approval）
# 金额：采购8万/劳务5万/机械5000/分包3万 → 项目 totalExpense=165000
# ===========================================================================
pay_one() {
  # 保留占位（已不再使用；付款改为基于 biz_other_contract 的真实流程）
  return 0
}

stage_9d_payment_closure() {
  phase "9D" "付款闭环"
  CURRENT_STAGE="9D:付款闭环"

  # 付款申请支持多合同类型（改造③）：按 contractCategory 路由—OTHER_EXPENSE 走 biz_other_contract，
  # PURCHASE/LABOR/MACHINE/SUBCONTRACT 走各模块合同表。本阶段先验证 OTHER_EXPENSE 路径，
  # 再验证 PURCHASE 跨模块路由。其他合同无独立结算流程，cumulativeSettlement 于创建时直接带入。
  # 创建其他支出合同：累计结算 200000（含 9E 驳回用例余量）
  api_call POST "/api/v1/contract/other" "{\"projectId\":$PROJECT_ID,\"contractName\":\"综合服务其他支出合同\",\"contractCategory\":\"OTHER_EXPENSE\",\"partyBName\":\"综合服务供应商\",\"contractAmount\":200000.00,\"cumulativeSettlement\":200000.00}"
  strict_assert "创建其他支出合同"
  sleep 1
  api_call GET "/api/v1/contract/other?page=1&size=1&projectId=$PROJECT_ID&contractCategory=OTHER_EXPENSE"
  OTHER_CONTRACT_ID=$(extract_first_record_id)
  require_id "$OTHER_CONTRACT_ID" "其他支出合同 ID"
  success "其他支出合同 ID: $OTHER_CONTRACT_ID"
  track_resource "DELETE" "/api/v1/contract/other/$OTHER_CONTRACT_ID"

  # 付款申请 165000（≤累计结算 200000），提交审批（payment_apply_approval）
  api_call POST "/api/v1/finance/payment-apply" "{\"projectId\":$PROJECT_ID,\"contractId\":$OTHER_CONTRACT_ID,\"contractCategory\":\"OTHER_EXPENSE\",\"supplierName\":\"综合服务供应商\",\"paymentAmount\":165000.00,\"paymentDate\":\"2026-09-20\"}"
  strict_assert "创建付款申请"
  sleep 1
  api_call GET "/api/v1/finance/payment-apply/page?page=1&size=1&projectId=$PROJECT_ID&contractId=$OTHER_CONTRACT_ID"
  local pid=$(extract_first_record_id)
  require_id "$pid" "付款申请 ID"
  track_resource "DELETE" "/api/v1/finance/payment-apply/$pid"
  api_call POST "/api/v1/finance/payment-apply/$pid/submit"
  strict_assert "提交付款审批"
  sleep 2
  approve "同意付款" 1
  assert_status "/api/v1/finance/payment-apply/$pid" "status" "APPROVED" "付款状态"
  # 审批通过回写其他合同累计已付=165000（付款口径：仅付款审批通过回写 totalExpense）
  assert_amount "/api/v1/contract/other/$OTHER_CONTRACT_ID" "cumulativePaid" "165000" "其他合同累计已付"

  # ── 跨模块付款路由：对采购合同付款 80000（contractCategory=PURCHASE）──
  # 验证 payment-apply 按 contractCategory 路由到 biz_purchase_contract 并回写其 cumulative_paid
  # 采购合同累计结算=100000（阶段9C），可付 100000 ≥ 80000
  api_call POST "/api/v1/finance/payment-apply" "{\"projectId\":$PROJECT_ID,\"contractId\":$PURCHASE_CONTRACT_ID,\"contractCategory\":\"PURCHASE\",\"supplierName\":\"广州建材供应有限公司\",\"paymentAmount\":80000.00,\"paymentDate\":\"2026-09-20\"}"
  strict_assert "创建付款申请-采购合同"
  sleep 1
  api_call GET "/api/v1/finance/payment-apply/page?page=1&size=1&projectId=$PROJECT_ID&contractId=$PURCHASE_CONTRACT_ID"
  local pid_pur=$(extract_first_record_id)
  require_id "$pid_pur" "采购付款申请 ID"
  track_resource "DELETE" "/api/v1/finance/payment-apply/$pid_pur"
  api_call POST "/api/v1/finance/payment-apply/$pid_pur/submit"
  strict_assert "提交采购付款审批"
  sleep 2
  approve "同意采购付款" 1
  assert_status "/api/v1/finance/payment-apply/$pid_pur" "status" "APPROVED" "采购付款状态"
  # 跨模块路由回写采购合同 cumulative_paid=80000（此前为死字段，改造③后生效）
  assert_amount "/api/v1/purchase/contract/$PURCHASE_CONTRACT_ID" "cumulativePaid" "80000" "采购合同累计已付"

  # 项目总支出=245000（其他合同付款 165000 + 采购合同付款 80000；均付款口径回写）
  assert_amount "/api/v1/project/$PROJECT_ID" "totalExpense" "245000" "项目总支出"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 9E: 驳回分支（两条真实路径，在 totalExpense 断言之后执行）
#   路径A 退回重审：付款 submit → reject-start 退回发起人 → 重新 complete → APPROVED
#   路径B 终止重提：付款 submit → terminate → REJECTED → 重新 submit → APPROVED
# 采购合同已付 80000，cumulativeSettlement=100000，尚可再付 20000
# ===========================================================================
stage_9e_reject_branch() {
  phase "9E" "驳回分支"
  CURRENT_STAGE="9E:驳回分支"

  # 复用 9D 其他支出合同（累计结算200000，已付165000，余量35000）
  # ── 路径A：reject-start 退回发起人 → 重审 complete → APPROVED（再付 10000）──
  log "  -- 9E-A: reject-start 退回重审 --"
  api_call POST "/api/v1/finance/payment-apply" "{\"projectId\":$PROJECT_ID,\"contractId\":$OTHER_CONTRACT_ID,\"contractCategory\":\"OTHER_EXPENSE\",\"supplierName\":\"综合服务供应商\",\"paymentAmount\":10000.00,\"paymentDate\":\"2026-09-21\"}"
  strict_assert "创建付款申请-退回用例"
  sleep 1
  api_call GET "/api/v1/finance/payment-apply/page?page=1&size=1&projectId=$PROJECT_ID&contractId=$OTHER_CONTRACT_ID&status=DRAFT"
  local pidA=$(extract_first_record_id)
  require_id "$pidA" "退回用例付款 ID"
  track_resource "DELETE" "/api/v1/finance/payment-apply/$pidA"
  api_call POST "/api/v1/finance/payment-apply/$pidA/submit"
  strict_assert "提交付款-退回用例"
  sleep 2
  api_call GET "/api/v1/workflow/approval/todo?page=1&size=10"
  local taskA=$(extract_task_id)
  require_id "$taskA" "退回用例待办 taskId"
  api_call POST "/api/v1/workflow/approval/reject-start" "{\"taskId\":\"$taskA\",\"comment\":\"退回发起人重填\"}"
  strict_assert "退回至发起人"
  sleep 2
  approve "重审通过" 1
  assert_status "/api/v1/finance/payment-apply/$pidA" "status" "APPROVED" "退回重审后付款状态"

  # ── 路径B：terminate 终止→REJECTED→重提→APPROVED（再付 5000）──
  log "  -- 9E-B: terminate 终止重提 --"
  api_call POST "/api/v1/finance/payment-apply" "{\"projectId\":$PROJECT_ID,\"contractId\":$OTHER_CONTRACT_ID,\"contractCategory\":\"OTHER_EXPENSE\",\"supplierName\":\"综合服务供应商\",\"paymentAmount\":5000.00,\"paymentDate\":\"2026-09-22\"}"
  strict_assert "创建付款申请-终止用例"
  sleep 1
  api_call GET "/api/v1/finance/payment-apply/page?page=1&size=1&projectId=$PROJECT_ID&contractId=$OTHER_CONTRACT_ID&status=DRAFT"
  local pidB=$(extract_first_record_id)
  require_id "$pidB" "终止用例付款 ID"
  track_resource "DELETE" "/api/v1/finance/payment-apply/$pidB"
  api_call POST "/api/v1/finance/payment-apply/$pidB/submit"
  strict_assert "提交付款-终止用例"
  sleep 2
  api_call GET "/api/v1/workflow/approval/todo?page=1&size=10"
  local taskB=$(extract_task_id)
  require_id "$taskB" "终止用例待办 taskId"
  api_call POST "/api/v1/workflow/approval/terminate" "{\"taskId\":\"$taskB\",\"comment\":\"终止重提\"}"
  strict_assert "终止流程"
  sleep 2
  assert_status "/api/v1/finance/payment-apply/$pidB" "status" "REJECTED" "终止后付款状态"
  api_call POST "/api/v1/finance/payment-apply/$pidB/submit"
  strict_assert "重新提交付款-终止用例"
  sleep 2
  approve "重提通过" 1
  assert_status "/api/v1/finance/payment-apply/$pidB" "status" "APPROVED" "重提后付款状态"

  record_stage_result "PASSED"
}

# ===========================================================================
# db_latest_retention_return — 质保金返还无分页查询 API，直查租户 9999 最新一条
# ===========================================================================
db_latest_retention_return() {
  docker exec "$MYSQL_CT" mysql -uroot -p"${ZWI_MYSQL_PASS:-zwinsight123}" zw_insight -N -B \
    -e "SELECT id FROM biz_retention_return WHERE tenant_id='$TEST_TENANT_ID' AND deleted=0 ORDER BY created_at DESC, id DESC LIMIT 1;" 2>/dev/null | tr -d '\r' | head -1
}

# ===========================================================================
# 阶段 9F: 变更签证（登记 → 提交审批 → 完成待办 → 合同累计变更金额回写）
#   依据功能表 5.2：变更签证审批通过后合同累计变更金额累加
#   依赖 BPMN：change_visa_approval（deploy-bpmn.sh 部署到租户 9999）
# ===========================================================================
stage_9f_change_visa() {
  phase "9F" "变更签证"
  CURRENT_STAGE="9F:变更签证"

  api_call POST "/api/v1/contract/change-visa" "{\"projectId\":$PROJECT_ID,\"contractId\":$CONTRACT_ID,\"changeType\":\"SITE_VISA\",\"changeReason\":\"L4现场签证-工程量变更\",\"changeContent\":\"新增墙面基层处理\",\"changeAmount\":50000.00}"
  strict_assert "创建变更签证"
  sleep 1

  api_call GET "/api/v1/contract/change-visa?page=1&size=1&contractId=$CONTRACT_ID&changeType=SITE_VISA"
  local visaId=$(extract_first_record_id)
  require_id "$visaId" "变更签证 ID"
  assert_status "/api/v1/contract/change-visa?page=1&size=1&contractId=$CONTRACT_ID&changeType=SITE_VISA" "status" "DRAFT" "变更签证初始状态"

  api_call POST "/api/v1/contract/change-visa/$visaId/submit"
  strict_assert "提交变更签证审批"
  sleep 2
  approve "同意变更签证" 1

  # 审批后：签证 APPROVED + 合同累计变更金额回写 50000
  assert_status "/api/v1/contract/change-visa?page=1&size=1&contractId=$CONTRACT_ID&changeType=SITE_VISA" "status" "APPROVED" "签证审批后状态"
  assert_amount "/api/v1/contract/$CONTRACT_ID" "cumulativeChangeAmount" 50000 "合同累计变更金额回写"

  # 负向：非草稿重复提交必须拒绝
  api_call POST "/api/v1/contract/change-visa/$visaId/submit"
  neg_assert "变更签证重复提交被拒绝"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 9G: 质保金（登记 → 到期查询 → 超额返还负向 → 全额返还审批 → RETURNED）
#   依据功能表 5.1：质保金登记/跟踪/返还申请闭环
#   依赖 BPMN：retention_return_approval（deploy-bpmn.sh 部署到租户 9999）
# ===========================================================================
stage_9g_retention() {
  phase "9G" "质保金"
  CURRENT_STAGE="9G:质保金"

  # save 会按 startDate+retentionPeriod 月重算 expireDate：取 retentionPeriod=0
  # 使到期日=今天，稳定落入 expiring?days=30 窗口（避免脚本跨期失效）
  api_call POST "/api/v1/finance/retention" "{\"projectId\":$PROJECT_ID,\"contractId\":$CONTRACT_ID,\"retentionRate\":2.00,\"retentionAmount\":96000.00,\"retentionPeriod\":0,\"startDate\":\"$(date +%F)\"}"
  strict_assert "登记质保金"
  sleep 1

  api_call GET "/api/v1/finance/retention/page?page=1&size=1&projectId=$PROJECT_ID"
  local retentionId=$(extract_first_record_id)
  require_id "$retentionId" "质保金 ID"
  assert_status "/api/v1/finance/retention/page?page=1&size=1&projectId=$PROJECT_ID" "status" "ACTIVE" "质保金初始状态"

  # 到期跟踪：expireDate 在 30 天内，expiring 列表应包含本条
  api_call GET "/api/v1/finance/retention/expiring?days=30"
  strict_assert "查询即将到期质保金"
  if grep -q "$retentionId" /tmp/zwi_body; then
    success "到期列表包含本条质保金: $retentionId"
  else
    fail "到期列表未包含刚登记的质保金 $retentionId"
    ABORT_REASON="$CURRENT_STAGE: expiring 缺本条记录"
    record_stage_result "FAILED"
    exit 1
  fi

  # 负向：返还金额超过可返还余额必须拒绝（先建超额草稿再提交）
  api_call POST "/api/v1/finance/retention/return" "{\"retentionId\":$retentionId,\"returnAmount\":999999.00,\"returnDate\":\"$(date +%F)\"}"
  strict_assert "创建超额返还申请（草稿允许）"
  sleep 1
  local overId=$(db_latest_retention_return)
  require_id "$overId" "超额返还申请 ID"
  api_call POST "/api/v1/finance/retention/return/$overId/submit"
  neg_assert "超额返还提交被拒绝"

  # 主流程：全额返还申请 → 审批 → returnedAmount=96000 且 status=RETURNED
  api_call POST "/api/v1/finance/retention/return" "{\"retentionId\":$retentionId,\"returnAmount\":96000.00,\"returnDate\":\"$(date +%F)\"}"
  strict_assert "创建全额返还申请"
  sleep 1
  local returnId=$(db_latest_retention_return)
  require_id "$returnId" "全额返还申请 ID"

  api_call POST "/api/v1/finance/retention/return/$returnId/submit"
  strict_assert "提交返还申请审批"
  sleep 2
  approve "同意质保金返还" 1

  api_call GET "/api/v1/finance/retention/page?page=1&size=1&projectId=$PROJECT_ID"
  strict_assert "回查质保金"
  assert_amount "/api/v1/finance/retention/page?page=1&size=1&projectId=$PROJECT_ID" "returnedAmount" 96000 "已返还金额回写"
  assert_status "/api/v1/finance/retention/page?page=1&size=1&projectId=$PROJECT_ID" "status" "RETURNED" "全额返还后状态"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 9H: HR 入职审批（创建申请 → 提交 → 完成待办 → APPROVED + 自动建系统账号）
#   依据功能表 2.10：入职审批通过后自动创建系统账号
#   依赖 BPMN：entry_apply_approval（deploy-bpmn.sh 部署到租户 9999）
#   残留说明：biz_entry_apply 由兜底清理；自动创建的 sys_user 账号保留在
#   隔离测试租户 9999（sys_user 为系统表，兜底清理严禁触碰，属预期残留）
# ===========================================================================
stage_9h_entry_approval() {
  phase "9H" "HR入职审批"
  CURRENT_STAGE="9H:HR入职审批"

  local ts=$(date +%s)
  local username="l4entry${ts}"

  api_call POST "/api/v1/hr/entry-apply" "{\"realName\":\"L4Entry$ts\",\"username\":\"$username\",\"phone\":\"139${ts:0:8}\"}"
  strict_assert "创建入职申请"
  sleep 1

  api_call GET "/api/v1/hr/entry-apply/page?page=1&size=1&realName=L4Entry$ts"
  local entryId=$(extract_first_record_id)
  require_id "$entryId" "入职申请 ID"
  assert_status "/api/v1/hr/entry-apply/page?page=1&size=1&realName=L4Entry$ts" "status" "DRAFT" "入职申请初始状态"

  api_call POST "/api/v1/hr/entry-apply/$entryId/submit"
  strict_assert "提交入职审批"
  sleep 2
  approve "同意入职" 1

  # 审批后：申请 APPROVED + 自动创建系统账号（按 username 回查）
  assert_status "/api/v1/hr/entry-apply/page?page=1&size=1&realName=L4Entry$ts" "status" "APPROVED" "入职审批后状态"

  api_call GET "/api/v1/system/user?page=1&size=1&username=$username"
  strict_assert "回查自动创建的系统账号"
  local userCount=$(extract_field "total")
  if [ "${userCount:-0}" != "0" ] && [ -n "$userCount" ]; then
    success "系统账号已自动创建: $username (total=$userCount)"
  else
    fail "系统账号未自动创建: username=$username, total=$userCount"
    ABORT_REASON="$CURRENT_STAGE: 系统账号未创建"
    record_stage_result "FAILED"
    exit 1
  fi

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 9I: 备用金（申请→审批→归还回写 + 超额归还负向）
#   依据功能表 2.9：备用金申请→报销冲抵→归还
#   依赖 BPMN：reserve_fund_apply_approval（deploy-bpmn.sh 部署到租户 9999）
# ===========================================================================
stage_9i_reserve_fund() {
  phase "9I" "备用金"
  CURRENT_STAGE="9I:备用金"

  api_call POST "/api/v1/finance/reserve-fund/apply" "{\"projectId\":$PROJECT_ID,\"applicant\":\"L4申请人\",\"applyDate\":\"$(date +%F)\",\"applyAmount\":2000.00}"
  strict_assert "创建备用金申请"
  sleep 1

  api_call GET "/api/v1/finance/reserve-fund/apply?page=1&size=1&projectId=$PROJECT_ID"
  local reserveId=$(extract_first_record_id)
  require_id "$reserveId" "备用金申请 ID"
  assert_status "/api/v1/finance/reserve-fund/apply?page=1&size=1&projectId=$PROJECT_ID" "status" "DRAFT" "备用金初始状态"

  api_call POST "/api/v1/finance/reserve-fund/apply/$reserveId/submit"
  strict_assert "提交备用金审批"
  sleep 2
  approve "同意备用金申请" 1

  assert_status "/api/v1/finance/reserve-fund/apply?page=1&size=1&projectId=$PROJECT_ID" "status" "APPROVED" "备用金审批后状态"

  # 负向：归还金额超过待归还金额必须拒绝
  api_call POST "/api/v1/finance/reserve-fund/return" "{\"reserveApplyId\":$reserveId,\"returnAmount\":99999.00}"
  local neg_http=$(cat /tmp/zwi_last_code 2>/dev/null)
  local neg_code=$(grep -oE '"code"\s*:\s*\"?[0-9]+' /tmp/zwi_body 2>/dev/null | head -1 | grep -oE '[0-9]+$')
  if [ "$neg_code" != "200" ] || [[ "$neg_http" == 4* ]] || [[ "$neg_http" == 5* ]]; then
    success "超额归还被正确拒绝 (HTTP $neg_http, code=$neg_code)"
  else
    fail "超额归还未被拒绝 (HTTP $neg_http, code=$neg_code)"
    ABORT_REASON="$CURRENT_STAGE: 超额归还未拒绝"
    record_stage_result "FAILED"
    exit 1
  fi

  # 全额归还：returnedAmount 回写 2000
  api_call POST "/api/v1/finance/reserve-fund/return" "{\"reserveApplyId\":$reserveId,\"returnAmount\":2000.00}"
  strict_assert "创建备用金归还"
  assert_amount "/api/v1/finance/reserve-fund/apply?page=1&size=1&projectId=$PROJECT_ID" "returnedAmount" 2000 "备用金已归还金额回写"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 9J: 保证金退还（申请→提交 SUBMITTED→审批通过置 PAID→退还登记）
#   依据功能表 1.4：投标保证金退回登记
#   2026-08-12 批次二修复：提交不再即置 PAID，审批通过由 DepositApplyApprovalListener 置 PAID
#   依赖 BPMN：deposit_apply_approval（deploy-bpmn.sh 部署到租户 9999）
# ===========================================================================
stage_9j_deposit_return() {
  phase "9J" "保证金退还"
  CURRENT_STAGE="9J:保证金退还"

  api_call POST "/api/v1/tender/deposit/apply" "{\"registerId\":$REGISTER_ID,\"projectId\":$PROJECT_ID,\"depositAmount\":5000.00,\"paymentDate\":\"$(date +%F)\"}"
  strict_assert "创建保证金申请"
  sleep 1

  api_call GET "/api/v1/tender/deposit/apply?page=1&size=1&projectId=$PROJECT_ID"
  local depositId=$(extract_first_record_id)
  require_id "$depositId" "保证金申请 ID"
  assert_status "/api/v1/tender/deposit/apply?page=1&size=1&projectId=$PROJECT_ID" "status" "DRAFT" "保证金初始状态"

  api_call POST "/api/v1/tender/deposit/apply/$depositId/submit"
  strict_assert "提交保证金审批"
  sleep 1
  assert_status "/api/v1/tender/deposit/apply?page=1&size=1&projectId=$PROJECT_ID" "status" "SUBMITTED" "保证金提交后中间态（未等审批不得 PAID）"
  sleep 1
  approve "同意保证金缴纳" 1

  assert_status "/api/v1/tender/deposit/apply?page=1&size=1&projectId=$PROJECT_ID" "status" "PAID" "保证金审批后状态"

  # 退还登记（直接记录，无审批流）
  api_call POST "/api/v1/tender/deposit/return" "{\"depositApplyId\":$depositId,\"returnAmount\":5000.00,\"returnDate\":\"$(date +%F)\"}"
  strict_assert "创建保证金退还登记"
  sleep 1

  api_call GET "/api/v1/tender/deposit/return?page=1&size=5&depositApplyId=$depositId"
  strict_assert "回查保证金退还记录"
  assert_amount "/api/v1/tender/deposit/return?page=1&size=5&depositApplyId=$depositId" "returnAmount" 5000 "退还金额一致"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 9K: 材料退货退款（退货出库→自动生成退款申请→审批→退款 APPROVED）
#   依据功能表 2.5：材料退货出库（含退货退款分支）
#   依赖：阶段 7B 入库瓷砖 2000 块、NORMAL 出库 1200 块（库存余 800）
#   依赖 BPMN：material_refund_approval（deploy-bpmn.sh 部署到租户 9999）
# ===========================================================================
stage_9k_material_refund() {
  phase "9K" "材料退货退款"
  CURRENT_STAGE="9K:材料退货退款"

  api_call POST "/api/v1/material/outbound" "{\"projectId\":$PROJECT_ID,\"outboundType\":\"RETURN\",\"returnType\":\"RETURN_REFUND\",\"contractId\":$PURCHASE_CONTRACT_ID,\"outboundDate\":\"$(date +%F)\",\"operatorName\":\"L4仓管\",\"details\":[{\"materialName\":\"瓷砖\",\"specification\":\"800x800\",\"unit\":\"块\",\"quantity\":100,\"unitPrice\":50.00}]}"
  strict_assert "创建退货出库单"
  sleep 1

  api_call GET "/api/v1/material/outbound/page?page=1&size=1&projectId=$PROJECT_ID&outboundType=RETURN"
  local returnOutboundId=$(extract_first_record_id)
  require_id "$returnOutboundId" "退货出库单 ID"

  # 退货出库 save 时已发布事件自动生成退款申请（PENDING，已提交流程）
  sleep 2
  api_call GET "/api/v1/material/refund?page=1&size=5&contractId=$PURCHASE_CONTRACT_ID"
  strict_assert "回查自动生成的退款申请"
  local refundId=$(extract_first_record_id)
  require_id "$refundId" "退款申请 ID（退货事件自动生成）"
  assert_amount "/api/v1/material/refund?page=1&size=5&contractId=$PURCHASE_CONTRACT_ID" "refundAmount" 5000 "退款金额=100块×入库单价50"
  assert_status "/api/v1/material/refund?page=1&size=5&contractId=$PURCHASE_CONTRACT_ID" "status" "PENDING" "退款申请待审批状态"

  # 完成退款审批 → 回调置 APPROVED + 扣减合同累计已付
  approve "同意材料退款" 1
  sleep 2
  assert_status "/api/v1/material/refund?page=1&size=5&contractId=$PURCHASE_CONTRACT_ID" "status" "APPROVED" "退款审批后状态"
  # 硬断言：退款审批通过后采购合同累计已付 80000→75000（钉住 2026-08-13 修复：
  # 扣减原误作 biz_expense_contract 静默无效，现原子扣减 biz_purchase_contract）
  assert_amount "/api/v1/purchase/contract/$PURCHASE_CONTRACT_ID" "cumulativePaid" 75000 "退款审批后采购合同累计已付扣减（80000-5000）"

  record_stage_result "PASSED"
}

# ===========================================================================
# 阶段 9L: 材料调拨审批（审批后生效，双向库存联动）
#   依据功能表 2.5：材料调拨（material_transfer_approval 审批流端到端，销项审批流 partial）
#   依赖：阶段 7B 入库瓷砖 2000 块、NORMAL 出库 1200 块、9K 退货 100 块 → 源库存 700
#   依赖 BPMN：material_transfer_approval（deploy-bpmn.sh 部署到租户 9999）
# ===========================================================================
stage_9l_material_transfer() {
  phase "9L" "材料调拨审批"
  CURRENT_STAGE="9L:材料调拨审批"

  # 调入项目（调拨双向库存断言需两个项目）
  api_call POST "/api/v1/project" "{\"projectName\":\"[测试]材料调拨目标项目\",\"projectNature\":\"新建\",\"projectType\":\"公共建筑\",\"ownerCompanyName\":\"城市建设投资集团\",\"signingCompanyName\":\"中维建设有限公司\",\"projectOverview\":\"L4 材料调拨端到端调入项目\",\"projectAddress\":\"广州市天河区\",\"contactName\":\"李四\",\"contactPhone\":\"13800138002\",\"needTender\":0,\"budgetAmount\":1000000.00}"
  strict_assert "创建调入项目"
  sleep 1
  api_call GET "/api/v1/project/page?page=1&size=1&projectName=%5B%E6%B5%8B%E8%AF%95%5D%E6%9D%90%E6%96%99%E8%B0%83%E6%8B%A8"
  local to_project_id=$(extract_first_record_id)
  require_id "$to_project_id" "调入项目 ID"
  success "调入项目 ID: $to_project_id"
  track_resource "DELETE" "/api/v1/project/$to_project_id"

  # 调拨前源项目库存 = 700（2000 入库 - 1200 出库 - 100 退货）
  assert_amount "/api/v1/material/stock/page?page=1&size=1&projectId=$PROJECT_ID&materialName=%E7%93%B7%E7%A0%96" "stockQuantity" 700 "调拨前源库存"

  # 创建调拨单：源项目 → 调入项目，瓷砖 200 块
  api_call POST "/api/v1/material/transfer" "{\"fromProjectId\":$PROJECT_ID,\"toProjectId\":$to_project_id,\"transferDate\":\"$(date +%F)\",\"details\":[{\"materialName\":\"瓷砖\",\"specification\":\"800x800\",\"unit\":\"块\",\"quantity\":200,\"unitPrice\":50.00}]}"
  strict_assert "创建材料调拨单"
  sleep 1

  api_call GET "/api/v1/material/transfer/page?page=1&size=1&fromProjectId=$PROJECT_ID"
  local transfer_id=$(extract_first_record_id)
  require_id "$transfer_id" "调拨单 ID"
  success "调拨单 ID: $transfer_id"
  track_resource "DELETE" "/api/v1/material/transfer/$transfer_id"
  assert_status "/api/v1/material/transfer/$transfer_id" "status" "DRAFT" "调拨单初始状态"

  api_call POST "/api/v1/material/transfer/$transfer_id/submit"
  strict_assert "提交材料调拨审批"
  sleep 1
  assert_status "/api/v1/material/transfer/$transfer_id" "status" "SUBMITTED" "调拨单提交后状态"

  # 审批后生效：审批前源库存不变（仍 700）
  assert_amount "/api/v1/material/stock/page?page=1&size=1&projectId=$PROJECT_ID&materialName=%E7%93%B7%E7%A0%96" "stockQuantity" 700 "审批前源库存不变"

  approve "同意材料调拨" 1
  sleep 2
  assert_status "/api/v1/material/transfer/$transfer_id" "status" "APPROVED" "调拨单审批后状态"

  # 双向库存断言：源 700-200=500 且累计调出 200；调入方新增 200 且累计调入 200
  assert_amount "/api/v1/material/stock/page?page=1&size=1&projectId=$PROJECT_ID&materialName=%E7%93%B7%E7%A0%96" "stockQuantity" 500 "调拨后源库存"
  assert_amount "/api/v1/material/stock/page?page=1&size=1&projectId=$PROJECT_ID&materialName=%E7%93%B7%E7%A0%96" "totalTransferOut" 200 "源累计调出"
  assert_amount "/api/v1/material/stock/page?page=1&size=1&projectId=$to_project_id&materialName=%E7%93%B7%E7%A0%96" "stockQuantity" 200 "调拨后调入方库存"
  assert_amount "/api/v1/material/stock/page?page=1&size=1&projectId=$to_project_id&materialName=%E7%93%B7%E7%A0%96" "totalTransferIn" 200 "调入方累计调入"

  record_stage_result "PASSED"
}

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
  stage_3b_open_bid
  stage_4_contract
  stage_5_budget
  stage_6_subcontracts
  stage_7_site_management
  stage_7b_material
  stage_7c_machine_exec
  stage_7d_labor_exec
  stage_7e_subcontract_exec
  stage_8_output_settlement
  stage_9_finance
  stage_9c_purchase_settlement
  stage_9d_payment_closure
  stage_9e_reject_branch
  stage_9f_change_visa
  stage_9g_retention
  stage_9h_entry_approval
  stage_9i_reserve_fund
  stage_9j_deposit_return
  stage_9k_material_refund
  stage_9l_material_transfer
  stage_9b_completion_settlement
  stage_10_project_close

  divider
  log "🎉 全生命周期模拟 v2 完成！"
  log "通过: $TOTAL_PASSED / 失败: $TOTAL_FAILED / 跳过: $TOTAL_SKIPPED"
  divider

  # 退出码严格反映断言结果（需求 5.7）
  if [ "$TOTAL_FAILED" -gt 0 ]; then
    exit 1
  fi
  exit 0
}

main "$@"
