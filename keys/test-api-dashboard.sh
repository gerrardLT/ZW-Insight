#!/usr/bin/env bash
###############################################################################
# test-api-dashboard.sh — L3 API 接口测试：数据看板模块（阶段四批 4）
#
# 覆盖端点（依据 zw-dashboard DashboardController + ProjectDashboardController 逐行核对）：
#   - /api/v1/dashboard/*              公司级看板 13 个只读聚合端点
#   - /api/v1/dashboard/project/{id}/* 项目维度看板 5 个端点 + 项目不存在 404 负向
#
# 设计要点：
#   - 看板全部只读，无数据写入，零残留
#   - projectId 从真实项目分页首条动态获取（与批 2/3 脚本一致）
#   - 依赖 verify-base.sh 登录基座；jq 断言规范同阶段四批 1-3
###############################################################################
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/verify-base.sh" login 2>/dev/null || true

PASS_COUNT=0
FAIL_COUNT=0
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

# 负向断言：业务 code 不得为 200（兼容 HTTP 级/业务级两种错误映射方式）
assert_body_not_success() {
  local test_name="$1" actual
  actual=$(grep -oE '"code"\s*:\s*\"?[0-9]+' /tmp/zwi_body 2>/dev/null | head -1 | grep -oE '[0-9]+$')
  local http_code
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

report_summary() {
  echo ""
  echo "═══════════════════════════════════════════════════════════"
  log "数据看板模块 API 测试汇总"
  echo "═══════════════════════════════════════════════════════════"
  log "  通过: $PASS_COUNT"
  log "  失败: $FAIL_COUNT"
  log "  总计: $TOTAL_COUNT"
  echo "═══════════════════════════════════════════════════════════"
  [ "$FAIL_COUNT" -eq 0 ]
}

log "========== L3 数据看板模块 API 测试开始 =========="

# ---------- 公司级看板（全部只读聚合） ----------
call GET "/api/v1/dashboard/company-overview"
assert_http 2 "公司概览 HTTP"
assert_jq '.code==200 and (.data|type=="object")' "公司概览-数据为对象"

call GET "/api/v1/dashboard/receivable-monitor"
assert_http 2 "应收款监控 HTTP"
assert_body_code 200 "应收款监控业务码"

call GET "/api/v1/dashboard/supplier-payable"
assert_http 2 "供应商应付监控 HTTP"
assert_body_code 200 "供应商应付监控业务码"

call GET "/api/v1/dashboard/tender-analysis"
assert_http 2 "投标分析 HTTP"
assert_body_code 200 "投标分析业务码"

call GET "/api/v1/dashboard/inventory-analysis"
assert_http 2 "库存分析 HTTP"
assert_body_code 200 "库存分析业务码"

call GET "/api/v1/dashboard/profit-trend"
assert_http 2 "利润趋势 HTTP"
assert_body_code 200 "利润趋势业务码"

call GET "/api/v1/dashboard/project-ranking?rankBy=output&topN=5"
assert_http 2 "项目排名 HTTP"
assert_body_code 200 "项目排名业务码"

call GET "/api/v1/dashboard/invoice-ledger"
assert_http 2 "发票台账 HTTP"
assert_body_code 200 "发票台账业务码"

call GET "/api/v1/dashboard/hr-statistics"
assert_http 2 "人事统计看板 HTTP"
assert_body_code 200 "人事统计看板业务码"

# ---------- 项目级看板（动态取真实项目ID） ----------
call GET "/api/v1/project/page?page=1&size=1"
PROJECT_ID=$(jq -r '.data.records[0].id // empty' /tmp/zwi_body 2>/dev/null)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$PROJECT_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 取到真实项目ID: $PROJECT_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 无法取到项目ID（种子数据缺失），项目级看板无法验证"
fi

if [ -n "$PROJECT_ID" ]; then
  call GET "/api/v1/dashboard/budget-execution?projectId=$PROJECT_ID"
  assert_http 2 "预算执行 HTTP"
  assert_body_code 200 "预算执行业务码"

  call GET "/api/v1/dashboard/schedule-gantt/$PROJECT_ID"
  assert_http 2 "进度甘特图 HTTP"
  assert_body_code 200 "进度甘特图业务码"

  call GET "/api/v1/dashboard/project/$PROJECT_ID"
  assert_http 2 "项目级看板聚合 HTTP"
  assert_body_code 200 "项目级看板聚合业务码"

  call GET "/api/v1/dashboard/budget-variance?projectId=$PROJECT_ID"
  assert_http 2 "预算偏差分析 HTTP"
  assert_body_code 200 "预算偏差分析业务码"

  # ---------- 项目维度看板（ProjectDashboardController 5 端点） ----------
  call GET "/api/v1/dashboard/project/$PROJECT_ID/budget"
  assert_http 2 "项目看板-预算执行 HTTP"
  assert_body_code 200 "项目看板-预算执行业务码"

  call GET "/api/v1/dashboard/project/$PROJECT_ID/progress"
  assert_http 2 "项目看板-进度完成率 HTTP"
  assert_body_code 200 "项目看板-进度完成率业务码"

  call GET "/api/v1/dashboard/project/$PROJECT_ID/contract"
  assert_http 2 "项目看板-合同回款 HTTP"
  assert_body_code 200 "项目看板-合同回款业务码"

  call GET "/api/v1/dashboard/project/$PROJECT_ID/output"
  assert_http 2 "项目看板-产值趋势 HTTP"
  assert_body_code 200 "项目看板-产值趋势业务码"

  call GET "/api/v1/dashboard/project/$PROJECT_ID/overview"
  assert_http 2 "项目看板-聚合 HTTP"
  assert_jq '.code==200 and (.data|type=="object")' "项目看板-聚合为对象"
fi

# 负向：项目不存在时看板返回 404（Controller checkProjectExists）
call GET "/api/v1/dashboard/project/999999999/budget"
assert_body_not_success "项目看板-不存在项目被拒绝"

report_summary
