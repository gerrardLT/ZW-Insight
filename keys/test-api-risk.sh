#!/usr/bin/env bash
###############################################################################
# test-api-risk.sh — L3 API 接口测试：驾驶舱 + 风险中心（V2026_59）
#
# 覆盖端点：
#   - GET /api/v1/dashboard/cockpit/overview          经营总览 8 卡
#   - GET /api/v1/dashboard/cockpit/profit-trend      利润趋势（快照 + 已实现双序列）
#   - GET /api/v1/dashboard/cockpit/profit-attribution 利润变化归因
#   - GET /api/v1/dashboard/cockpit/project-health    项目健康度
#   - GET /api/v1/dashboard/cockpit/project-forecasts 项目预计利润（口径标记可追溯）
#   - GET /api/v1/dashboard/cockpit/risk/summary      风险分级汇总
#   - GET /api/v1/dashboard/cockpit/risk/page         风险台账分页
#   - GET /api/v1/dashboard/cockpit/risk/{id}         风险详情（六要素）
#   - PUT /api/v1/dashboard/cockpit/risk/scan         手动触发扫描
#   - PUT /api/v1/dashboard/cockpit/risk/{id}/handle  风险处理流转
#
# 关键语义用例（单测层无法覆盖，必须真实接口验证）：
#   IGNORED 人工消音的风险，再次扫描后仍为 IGNORED（不被自动 RESOLVED 覆盖）
#
# SKIP 语义：依赖既有业务数据的用例在无数据时记 SKIP 并单列汇总，
#            绝不伪造成 PASS（对齐"不静默跳过"纪律）。
#
# 运行位置：服务器；依赖 verify-base.sh 登录基座 + jq
###############################################################################
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/verify-base.sh" login 2>/dev/null || true

PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0
TOTAL_COUNT=0

assert_http() {
  local expected_prefix="$1" test_name="$2" actual_code
  actual_code=$(cat /tmp/zwi_last_code 2>/dev/null || echo "000")
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [[ "$actual_code" == ${expected_prefix}* ]]; then
    PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] $test_name (HTTP $actual_code)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] $test_name (HTTP $actual_code, 期望 ${expected_prefix}xx)"
  fi
}

assert_body_code() {
  local expected="$1" test_name="$2" actual
  actual=$(grep -oE '"code"\s*:\s*\"?[0-9]+' /tmp/zwi_body 2>/dev/null | head -1 | grep -oE '[0-9]+$')
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [ "$actual" = "$expected" ]; then
    PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] $test_name (code=$actual)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] $test_name (code=$actual, 期望=$expected)"
  fi
}

assert_body_not_success() {
  local test_name="$1" actual http_code
  actual=$(grep -oE '"code"\s*:\s*\"?[0-9]+' /tmp/zwi_body 2>/dev/null | head -1 | grep -oE '[0-9]+$')
  http_code=$(cat /tmp/zwi_last_code 2>/dev/null || echo "000")
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [ "$actual" != "200" ] || [[ "$http_code" == 4* ]] || [[ "$http_code" == 5* ]]; then
    PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] $test_name (code=$actual, HTTP $http_code)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] $test_name (期望非 200, 实际 code=$actual HTTP $http_code)"
  fi
}

assert_jq() {
  local expr="$1" test_name="$2" result
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if ! command -v jq >/dev/null 2>&1; then
    FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] $test_name (jq 未安装)"; return 1
  fi
  result=$(jq -e "$expr" /tmp/zwi_body 2>/dev/null | head -1)
  if [ "$result" = "true" ]; then
    PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] $test_name"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] $test_name (jq 表达式不满足: $expr)"
  fi
}

# 无数据时记 SKIP（不伪造 PASS）
skip_case() {
  local test_name="$1" reason="$2"
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  SKIP_COUNT=$((SKIP_COUNT + 1))
  log "  SKIP [$TOTAL_COUNT] $test_name（$reason）"
}

PAGE_EXPR='.code==200 and (.data.records|type=="array") and ((.data.total|type=="number") or (.data.total|type=="string"))'

report_summary() {
  echo ""
  echo "═══════════════════════════════════════════════════════════"
  log "驾驶舱 + 风险中心 API 测试汇总"
  echo "═══════════════════════════════════════════════════════════"
  log "  通过: $PASS_COUNT"
  log "  失败: $FAIL_COUNT"
  log "  跳过: $SKIP_COUNT  ← 依赖既有业务数据，未验证到，需人工确认"
  log "  总计: $TOTAL_COUNT"
  echo "═══════════════════════════════════════════════════════════"
  [ "$FAIL_COUNT" -eq 0 ]
}

log "========== L3 驾驶舱 + 风险中心 API 测试开始 =========="

# ---------- 经营总览 8 卡 ----------
call GET "/api/v1/dashboard/cockpit/overview"
assert_http 2 "经营总览 HTTP"
assert_body_code 200 "经营总览-业务码"
# 8 卡字段齐备（经营结果 4 + 资金状态 4），缺任一即 FAIL
assert_jq '.code==200 and (.data | has("contractIncome") and has("forecastTotalCost") and has("forecastProfit")
  and has("forecastProfitRate") and has("cumulativeReceived") and has("cumulativePaid")
  and has("receivableOutstanding") and has("gap90Days") and has("accountBalance"))' \
  "经营总览-8卡字段齐备"
# 口径分离：预计利润与已实现利润必须是两个独立字段（防混用）
assert_jq '.code==200 and (.data | has("forecastProfit")) and (.data | has("realizedProfit"))' \
  "经营总览-预计利润与已实现利润双口径并存"

# ---------- 利润趋势 ----------
call GET "/api/v1/dashboard/cockpit/profit-trend?months=6"
assert_http 2 "利润趋势 HTTP"
assert_jq '.code==200 and (.data.snapshots|type=="array") and (.data.realized|type=="object") and (.data.realized.months|length==12)' \
  "利润趋势-快照数组 + 已实现12月结构"

# 负向：月数超范围（1-36）必须拒绝
call GET "/api/v1/dashboard/cockpit/profit-trend?months=99"
assert_body_not_success "利润趋势-月数超范围被拒绝"

# ---------- 项目健康度 ----------
call GET "/api/v1/dashboard/cockpit/project-health"
assert_http 2 "项目健康度 HTTP"
assert_jq '.code==200 and (.data|type=="array")' "项目健康度-数组结构"
# health 值域受控（RED/YELLOW/GREEN），且定级由规则产生（有 forecastProfit 字段佐证）
assert_jq '.code==200 and ([.data[] | select(.health != "RED" and .health != "YELLOW" and .health != "GREEN")] | length == 0)' \
  "项目健康度-health 值域合法"
assert_jq '.code==200 and ([.data[] | select(.forecastProfit == null or .costBasis == null)] | length == 0)' \
  "项目健康度-含预计利润与成本口径标记"

# ---------- 项目预计利润（口径可追溯） ----------
call GET "/api/v1/dashboard/cockpit/project-forecasts"
assert_http 2 "项目预计利润 HTTP"
assert_jq '.code==200 and ([.data[] | select(.incomeBasis == null or .costBasis == null)] | length == 0)' \
  "项目预计利润-收入/成本口径标记齐备（不静默混口径）"

# ---------- 利润归因 ----------
CURRENT_MONTH=$(date +%Y-%m)
call GET "/api/v1/dashboard/cockpit/profit-attribution?month=$CURRENT_MONTH"
# 有快照则校验结构；无快照时后端抛业务异常（快照不存在 404），
# 此时仅验证“明确报错而非返回空对象”，结构校验记 SKIP（不伪造 PASS）
if jq -e '.code==200' /tmp/zwi_body >/dev/null 2>&1; then
  assert_body_code 200 "利润归因-业务码"
  assert_jq '.code==200 and (.data | has("hasBaseline")) and (.data | has("items")) and (.data.month != null)' \
    "利润归因-结构（hasBaseline/items/month）"
else
  assert_body_not_success "利润归因-无快照时明确报错（不返回空对象）"
  skip_case "利润归因-结构校验" \
    "当月无预计利润快照；快照由 ProfitSnapshotTask 每日 03:30 生成（须在 02:30 成本归集之后），可先手工触发后重跑本脚本"
fi

# 负向：缺 month 参数
call GET "/api/v1/dashboard/cockpit/profit-attribution"
assert_body_not_success "利润归因-缺 month 参数被拒绝"

# ---------- 风险中心 ----------
call GET "/api/v1/dashboard/cockpit/risk/summary"
assert_http 2 "风险汇总 HTTP"
assert_jq '.code==200 and (.data | has("redCount") and has("yellowCount") and has("infoCount")
  and has("redImpact") and has("yellowImpact") and has("activeTotal"))' \
  "风险汇总-分级计数与影响金额字段齐备"

call GET "/api/v1/dashboard/cockpit/risk/page?page=1&size=20"
assert_http 2 "风险台账分页 HTTP"
assert_jq "$PAGE_EXPR" "风险台账-分页结构"

# 跨页排序正确性（SQL 层 ORDER BY FIELD(severity,...)）：
# 应用层排序只能影响当前页会造成此断言失败（YELLOW 之后不应再出现 RED）
call GET "/api/v1/dashboard/cockpit/risk/page?page=1&size=50"
assert_jq '([.data.records[].severity]) as $s | ($s | index("YELLOW")) as $y | if $y == null then true else ([$s[$y:][] | select(.=="RED")] | length) == 0 end' \
  "风险台账-排序严重级别优先（YELLOW 之后不再出现 RED）"

# 手动触发扫描：返回统计不静默（failedRules / skippedAutoCloseTypes 必须存在）
call PUT "/api/v1/dashboard/cockpit/risk/scan"
assert_http 2 "手动扫描 HTTP"
assert_jq '.code==200 and (.data | has("scannedRules")) and (.data | has("findings")) and (.data | has("failedRules")) and (.data | has("skippedAutoCloseTypes"))' \
  "手动扫描-返回统计（scannedRules/findings/failedRules/skippedAutoCloseTypes）"
assert_jq '.code==200 and (.data.scannedRules >= 7)' \
  "手动扫描-规则数≥7（利润/预算/资金/应收/质保金/工资专户/招待费）"
assert_jq '.code==200 and (.data.failedRules | length == 0)' "手动扫描-无规则执行失败"
# 规则失败的类型必须排除出自动关闭范围（防“执行失败”被当成“风险消失”批量误关）
assert_jq '.code==200 and (.data.skippedAutoCloseTypes | length) <= (.data.failedRules | length)' \
  "手动扫描-失败类型同步进入跳过自动关闭名单"

# 负向：非法处理动作必须拒绝（不得静默按默认值处理）
call GET "/api/v1/dashboard/cockpit/risk/page?page=1&size=1&handleStatus=OPEN"
FIRST_RISK_ID=$(jq -r '.data.records[0].id // empty' /tmp/zwi_body 2>/dev/null)

# 负向：action 为空必须拒绝（后端 RiskHandleRequest 校验）
call PUT "/api/v1/dashboard/cockpit/risk/999999999999/handle" '{}'
assert_body_not_success "风险处理-缺 action 被拒绝"

if [ -n "$FIRST_RISK_ID" ]; then
  call PUT "/api/v1/dashboard/cockpit/risk/$FIRST_RISK_ID/handle" '{"action":"CLOSED"}'
  assert_body_not_success "风险处理-非法 action 被拒绝"

  # 关键语义：IGNORED 人工消音后再次扫描，仍为 IGNORED（不被自动 RESOLVED 覆盖）
  call PUT "/api/v1/dashboard/cockpit/risk/$FIRST_RISK_ID/handle" '{"action":"IGNORED","handleNote":"L3测试消音"}'
  assert_body_code 200 "风险处理-置 IGNORED 成功"

  call PUT "/api/v1/dashboard/cockpit/risk/scan"
  assert_body_code 200 "风险处理-消音后重新扫描"

  call GET "/api/v1/dashboard/cockpit/risk/page?page=1&size=50&handleStatus=IGNORED"
  assert_jq ".code==200 and ([.data.records[] | select((.id|tostring)==\"$FIRST_RISK_ID\")] | length == 1)" \
    "IGNORED 风险经扫描后仍为 IGNORED（人工消音不被自动关闭）"

  # 状态机守卫：已解决风险仅可重开
  call PUT "/api/v1/dashboard/cockpit/risk/$FIRST_RISK_ID/handle" '{"action":"OPEN","handleNote":"L3恢复现场"}'
  assert_body_code 200 "风险处理-重开成功（恢复现场）"
else
  skip_case "风险处理流转全链路（非法action/IGNORED消音保护/重开）" \
    "当前无 OPEN 风险记录，需先制造风险数据（如超预算/逾期应收）后重跑本脚本"
fi

# 负向：不存在的风险记录处理必须拒绝
call PUT "/api/v1/dashboard/cockpit/risk/999999999999/handle" '{"action":"RESOLVED"}'
assert_body_not_success "风险处理-记录不存在被拒绝"

# ---------- 风险详情（移动端详情页数据源） ----------
call GET "/api/v1/dashboard/cockpit/risk/page?page=1&size=1"
DETAIL_RISK_ID=$(jq -r '.data.records[0].id // empty' /tmp/zwi_body 2>/dev/null)
if [ -n "$DETAIL_RISK_ID" ]; then
  call GET "/api/v1/dashboard/cockpit/risk/$DETAIL_RISK_ID"
  assert_http 2 "风险详情 HTTP"
  # 六要素字段齐备（发生了什么/影响多少/为什么/谁负责/下一步/处理状态）
  assert_jq '.code==200 and (.data | has("title") and has("severity") and has("riskType")
    and has("impactAmount") and has("handleStatus") and has("riskCode"))' \
    "风险详情-六要素字段齐备"
  assert_jq ".code==200 and (.data.id|tostring)==\"$DETAIL_RISK_ID\"" "风险详情-ID 一致"
else
  skip_case "风险详情六要素校验" "无风险记录可取详情（先执行一次扫描或制造风险数据后重跑）"
fi

# 负向：详情不存在必须报错（不返回 null 伪装成功）
call GET "/api/v1/dashboard/cockpit/risk/999999999999"
assert_body_not_success "风险详情-记录不存在被拒绝"

report_summary
