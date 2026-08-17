#!/usr/bin/env bash
# 补充归零重建后缺失的劳务演示数据（租户1，真实接口真实流程）：
#   3 个班组（滨江花园归零演示项目）+ 每班组 2 张已审批用工单
# 背景：e2e-real expense-write.spec.ts B-18 工资单用例要求班组下拉有选项
# 登录模式复用 keys/verify-base.sh（Redis 读真实验证码，不绕过不 mock）
set -uo pipefail

BASE="${ZWI_BASE:-http://127.0.0.1:18080}"
USERNAME="${ZWI_USER:-admin}"
PASSWORD="${ZWI_PASS:-123456}"
PROJECT_ID="${ZWI_PROJECT_ID:-2089276036854378498}"
REDIS_CT="${ZWI_REDIS_CT:-zwi-redis}"
TOKEN_FILE="/root/zwi-deploy/.zwi_token"

log() { echo "[prep-labor-demo] $*"; }
fail() { log "FAIL: $*"; exit 1; }

clear_login_locks() {
  docker exec "$REDIS_CT" redis-cli DEL \
    "login:ip:fail:127.0.0.1" "login:ip:lock:127.0.0.1" >/dev/null 2>&1 || true
  docker exec "$REDIS_CT" redis-cli DEL "login_fail:$USERNAME" >/dev/null 2>&1 || true
}

get_captcha() {
  local resp uuid code
  resp=$(curl -s -m 10 "$BASE/api/v1/captcha/image")
  uuid=$(echo "$resp" | grep -oE '"uuid"[[:space:]]*:[[:space:]]*"[^"]+"' | head -1 | sed -E 's/.*"uuid"[[:space:]]*:[[:space:]]*"//;s/"$//')
  [ -z "$uuid" ] && return 1
  code=$(timeout 10 docker exec "$REDIS_CT" redis-cli GET "captcha:$uuid" | tr -d '\r"')
  [ -z "$code" ] && return 1
  echo "$uuid $code"
}

do_login() {
  local cap uuid code resp token
  clear_login_locks
  cap=$(get_captcha) || return 1
  uuid="${cap%% *}"; code="${cap##* }"
  resp=$(curl -s -m 10 -X POST "$BASE/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"captchaUuid\":\"$uuid\",\"captchaCode\":\"$code\"}")
  token=$(echo "$resp" | grep -oE '"(accessToken|token)"[[:space:]]*:[[:space:]]*"[^"]+"' | head -1 | sed -E 's/.*:[[:space:]]*"//;s/"$//')
  [ -z "$token" ] && return 1
  umask 077
  printf '%s' "$token" > "$TOKEN_FILE"
  chmod 600 "$TOKEN_FILE"
  return 0
}

TOKEN=""
login() {
  local i
  for i in 1 2 3; do
    if do_login; then TOKEN=$(cat "$TOKEN_FILE"); return 0; fi
  done
  return 1
}

api() { # api <METHOD> <PATH> [JSON]
  local method="$1" path="$2" body="${3:-}"
  if [ -n "$body" ]; then
    curl -s -m 15 -X "$method" "$BASE$path" -H "Authorization: Bearer $TOKEN" \
      -H 'Content-Type: application/json' -d "$body"
  else
    curl -s -m 15 -X "$method" "$BASE$path" -H "Authorization: Bearer $TOKEN"
  fi
}

# GET + URL 编码参数（中文 teamName 不编码会查询落空，实证）
apig() { # apig <PATH> <KEY=VALUE>...
  local path="$1"; shift
  local args=()
  local kv
  for kv in "$@"; do args+=(--data-urlencode "$kv"); done
  curl -s -m 15 -G "$BASE$path" -H "Authorization: Bearer $TOKEN" "${args[@]}"
}

jget() { # jget <json> <field>
  echo "$1" | sed -n 's/.*"'"$2"'"[[:space:]]*:[[:space:]]*\("[^"]*"\|[0-9.]*\).*/\1/p' | head -1 | tr -d '"'
}

# Long 型 ID 序列化为带引号字符串（雪花 ID 防 JS 精度丢失），提取时需含引号形态
first_id() {
  echo "$1" | grep -oE '"id"[[:space:]]*:[[:space:]]*"?[0-9]+"?' | head -1 | grep -oE '[0-9]+'
}

login || fail "登录失败（3 次重试）"
log "登录成功"

# --- 现状盘点 ---
TEAM_TOTAL=$(jget "$(api GET '/api/v1/labor/team/page?page=1&size=1')" total)
log "现有班组数: ${TEAM_TOTAL:-?}"

# --- 创建 3 个班组（幂等：先按名称查，存在则跳过）---
TEAM_IDS=()
create_team() { # create_team <name> <leader> <phone> <workType>
  local name="$1" leader="$2" phone="$3" wtype="$4" exist total
  exist=$(apig /api/v1/labor/team/page "page=1" "size=1" "teamName=$name")
  total=$(jget "$exist" total)
  if [ "$total" != "0" ] && [ -n "$total" ]; then
    log "班组已存在，跳过: $name"
    TEAM_IDS+=("$(first_id "$exist")")
    return 0
  fi
  local resp code
  resp=$(api POST /api/v1/labor/team "{\"teamName\":\"$name\",\"projectId\":$PROJECT_ID,\"leaderName\":\"$leader\",\"leaderPhone\":\"$phone\",\"workType\":\"$wtype\",\"status\":1}")
  code=$(jget "$resp" code)
  [ "$code" = "200" ] || fail "创建班组失败 $name: $resp"
  local created_json
  created_json=$(apig /api/v1/labor/team/page "page=1" "size=1" "teamName=$name")
  TEAM_IDS+=("$(first_id "$created_json")")
  log "班组已创建: $name (id=${TEAM_IDS[-1]})"
}

create_team "滨江钢筋班组" "张建国" "13800138001" "钢筋工"
create_team "滨江模板班组" "李卫东" "13800138002" "模板工"
create_team "滨江混凝土班组" "王志强" "13800138003" "混凝土工"

# --- 每班组 2 张已审批用工单（幂等：该班组已有 APPROVED 工单则跳过）---
WO_DATES=("2026-08-10" "2026-08-11")
WO_NAMES=("赵铁柱" "钱大力")
for idx in 0 1 2; do
  tid="${TEAM_IDS[$idx]}"
  [ -n "$tid" ] || fail "班组 $idx 无 ID"
  wo_total=$(jget "$(api GET "/api/v1/labor/work-order/page?page=1&size=1&teamId=$tid&status=APPROVED")" total)
  if [ "$wo_total" != "0" ] && [ -n "$wo_total" ]; then
    log "班组 $tid 已有 APPROVED 用工单 $wo_total 张，跳过"
    continue
  fi
  for w in 0 1; do
    resp=$(api POST /api/v1/labor/work-order "{\"projectId\":$PROJECT_ID,\"teamId\":$tid,\"workerName\":\"${WO_NAMES[$w]}\",\"workDate\":\"${WO_DATES[$w]}\",\"hours\":8,\"hourlyRate\":45,\"overtime\":1,\"overtimeRate\":67.5,\"orderType\":\"FIXED\"}")
    code=$(jget "$resp" code)
    [ "$code" = "200" ] || fail "创建用工单失败 team=$tid: $resp"
    # 反查 DRAFT 工单 ID 后提交
    wo_id=$(first_id "$(api GET "/api/v1/labor/work-order/page?page=1&size=1&teamId=$tid&status=DRAFT")")
    [ -n "$wo_id" ] || fail "反查用工单 ID 失败 team=$tid"
    resp=$(api POST "/api/v1/labor/work-order/$wo_id/submit")
    code=$(jget "$resp" code)
    [ "$code" = "200" ] || fail "提交用工单失败 id=$wo_id: $resp"
    log "用工单已审批: team=$tid worker=${WO_NAMES[$w]} date=${WO_DATES[$w]} (id=$wo_id)"
  done
done

# --- 收尾断言 ---
TEAM_TOTAL=$(jget "$(api GET '/api/v1/labor/team/page?page=1&size=1')" total)
APPROVED_WO=$(docker exec zwi-mysql mysql -uroot -pzwinsight123 zw_insight -N -B -e "SELECT COUNT(*) FROM biz_work_order WHERE tenant_id=1 AND deleted=0 AND status='APPROVED';" 2>/dev/null)
log "最终班组数=$TEAM_TOTAL 已审批用工单=$APPROVED_WO"
[ "$TEAM_TOTAL" -ge 3 ] 2>/dev/null || fail "班组数不足 3"
[ "$APPROVED_WO" -ge 6 ] 2>/dev/null || fail "已审批用工单不足 6"
log "PASS 劳务演示数据补齐完成"
