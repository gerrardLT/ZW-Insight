#!/usr/bin/env bash
###############################################################################
# test-api-cost-control.sh — L3 API 接口测试：成本控制看板模块 (Project Cost 360)
#
# 覆盖端点：
#   - GET /api/v1/dashboard/project/{projectId}/cost-control
#     返回 ProjectCostControlDTO：
#       * projectId, projectName
#       * metrics: BAC, EAC, AC, commitments, variance, costVarianceRate, usageRate, etc.
#       * accounts: 树形或平铺 CBS 账户
#       * categories: 费用类别汇总
#       * trends: 成本趋势数据
#   - 负向用例：项目不存在时返回 404 (checkProjectExists)
#
# 设计要点：
#   - 看板全部只读，无数据写入，零残留
#   - projectId 从真实项目分页首条动态获取（与 test-api-dashboard.sh 一致）
#   - 依赖 verify-base.sh 登录基座；jq 断言规范同阶段四批 1-4
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

report_summary() {
  echo ""
  echo "═══════════════════════════════════════════════════════════"
  log "成本控制看板模块 API 测试汇总"
  echo "═══════════════════════════════════════════════════════════"
  log "  通过: $PASS_COUNT"
  log "  失败: $FAIL_COUNT"
  log "  总计: $TOTAL_COUNT"
  echo "═══════════════════════════════════════════════════════════"
  [ "$FAIL_COUNT" -eq 0 ]
}

log "========== L3 成本控制看板模块 API 测试开始 =========="

# 1. 动态获取真实项目ID
call GET "/api/v1/project/page?page=1&size=1"
PROJECT_ID=$(jq -r '.data.records[0].id // empty' /tmp/zwi_body 2>/dev/null)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$PROJECT_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 取到真实项目ID: $PROJECT_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 无法取到项目ID（种子数据缺失），看板验证终止"
  report_summary
  exit 1
fi

# 2. 查询项目成本控制看板
call GET "/api/v1/dashboard/project/$PROJECT_ID/cost-control"
assert_http 2 "项目成本控制看板 HTTP 2xx"
assert_body_code 200 "项目成本控制看板业务码 200"
assert_jq '.code==200 and (.data|type=="object")' "响应数据为对象"
assert_jq '.data.projectId!=null' "包含 projectId 字段"
assert_jq '.data.metrics!=null and (.data.metrics|type=="object")' "包含 metrics 核心指标对象"
assert_jq '.data.accounts!=null and (.data.accounts|type=="array")' "包含 accounts 账户明细列表"
assert_jq '.data.categories!=null and (.data.categories|type=="array")' "包含 categories 类别汇总列表"

# 3. 负向测试：项目不存在校验 (checkProjectExists 应该返回 404 或非 200 错误)
call GET "/api/v1/dashboard/project/999999999/cost-control"
assert_body_not_success "不存在项目 404 拒绝验证"

report_summary
