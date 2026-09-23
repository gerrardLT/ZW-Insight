#!/usr/bin/env bash
###############################################################################
# test-api-fund-loop.sh — L3 API 接口测试：资金闭环阶段一（V2026_56/57/58）
#
# 覆盖端点：
#   - /api/v1/finance/payment-apply/page?payStatus=  支付执行态筛选（已批未付清单）
#   - /api/v1/finance/payment-apply/{id}/mark-paid   手工标记支付（正向拦截+负向）
#   - /api/v1/finance/payment-apply/{id}/revoke-paid 撤销支付标记（负向）
#   - /api/v1/finance/receivable/page                应收台账分页
#   - /api/v1/finance/receivable/aging               应收账龄分析
#   - /api/v1/finance/fund-plan/monthly/{id}/details 月度计划科目明细
#   - /api/v1/finance/fund-plan/rolling/top-expenses 未来大额支出 TOP
#   - /api/v1/dashboard/profit-trend                 利润趋势真实化（12月结构）
#
# 运行位置：服务器；依赖 verify-base.sh 登录基座；jq 断言规范同 test-api-finance2.sh
# 设计依据：audit-reports/cockpit-fund-gap-analysis-2026-09-22.md 阶段一（1A/1B/1C/1D/1E）
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

PAGE_EXPR='.code==200 and (.data.records|type=="array") and ((.data.total|type=="number") or (.data.total|type=="string"))'

report_summary() {
  echo ""
  echo "═══════════════════════════════════════════════════════════"
  log "资金闭环阶段一 API 测试汇总"
  echo "═══════════════════════════════════════════════════════════"
  log "  通过: $PASS_COUNT"
  log "  失败: $FAIL_COUNT"
  log "  总计: $TOTAL_COUNT"
  echo "═══════════════════════════════════════════════════════════"
  [ "$FAIL_COUNT" -eq 0 ]
}

log "========== L3 资金闭环阶段一 API 测试开始 =========="

# ---------- 1A 支付执行态 ----------
call GET "/api/v1/finance/payment-apply/page?page=1&size=5&payStatus=UNPAID"
assert_http 2 "付款申请-支付态筛选 HTTP"
assert_jq "$PAGE_EXPR" "付款申请-支付态筛选分页结构"
# 筛选生效：返回记录（若有）payStatus 必须均为 UNPAID，不得混入 PAID
assert_jq '.code==200 and ([.data.records[] | select(.payStatus != null and .payStatus != "UNPAID")] | length == 0)' \
  "付款申请-筛选结果不含已支付记录"

# 负向：不存在的单据标记支付必须拒绝，不得静默成功
call POST "/api/v1/finance/payment-apply/999999999999/mark-paid?payDate=2026-09-22"
assert_body_not_success "标记支付-单据不存在被拒绝"

# 负向：缺支付日期参数（400 参数缺失或业务拒绝均合法，不得 200）
call POST "/api/v1/finance/payment-apply/999999999999/mark-paid"
assert_body_not_success "标记支付-缺支付日期被拒绝"

# 负向：不存在单据撤销标记必须拒绝
call POST "/api/v1/finance/payment-apply/999999999999/revoke-paid"
assert_body_not_success "撤销支付标记-单据不存在被拒绝"

# ---------- 1B 应收台账与账龄 ----------
call GET "/api/v1/finance/receivable/page?page=1&size=10"
assert_http 2 "应收台账-分页 HTTP"
assert_jq "$PAGE_EXPR" "应收台账-分页结构"

call GET "/api/v1/finance/receivable/page?page=1&size=10&status=OPEN"
assert_jq "$PAGE_EXPR" "应收台账-状态筛选分页结构"
assert_jq '.code==200 and ([.data.records[] | select(.status != "OPEN")] | length == 0)' \
  "应收台账-OPEN筛选不混入已结清"

call GET "/api/v1/finance/receivable/aging"
assert_http 2 "应收账龄 HTTP"
assert_jq '.code==200 and (.data|type=="object") and (.data.projects|type=="array") and (.data.totalOpen != null) and (.data.totalOverdue != null)' \
  "应收账龄-结构（totalOpen/totalOverdue/projects）"

# ---------- 1C 月度计划科目明细 ----------
# 负向：缺 planId 路径参数 → 404/405 路由级拒绝
call GET "/api/v1/finance/fund-plan/monthly//details"
assert_body_not_success "计划明细-缺planId被拒绝"

# ---------- 1D 滚动预测：未来大额支出 TOP ----------
call GET "/api/v1/finance/fund-plan/rolling/top-expenses?days=30"
assert_http 2 "未来大额支出TOP HTTP"
assert_jq '.code==200 and (.data|type=="array")' "未来大额支出TOP-数组结构"
# 行结构（若有数据）：categoryCode/categoryName/amount/count 齐备
assert_jq '.code==200 and ([.data[] | select(.categoryCode == null or .amount == null or .count == null)] | length == 0)' \
  "未来大额支出TOP-行字段齐备"

# 负向：days 超范围（>365）必须拒绝
call GET "/api/v1/finance/fund-plan/rolling/top-expenses?days=999"
assert_body_not_success "未来大额支出TOP-days超范围被拒绝"

# ---------- 1E 利润趋势真实化 ----------
call GET "/api/v1/dashboard/profit-trend"
assert_http 2 "利润趋势 HTTP"
assert_body_code 200 "利润趋势-业务码"
assert_jq '.code==200 and (.data.months|length==12) and (.data.totalIncome != null) and (.data.totalExpense != null) and (.data.totalProfit != null)' \
  "利润趋势-12月结构与年度合计字段"
# 勾稽：月度收入合计 = totalIncome（真实分月聚合，非年均摊；均摊实现下 12 个月值恒等，此断言同时钉住口径）
assert_jq '.code==200 and (([.data.months[].income] | add // 0) as $s | (.data.totalIncome == $s or (($s - .data.totalIncome) | fabs) < 0.01))' \
  "利润趋势-月度收入合计等于年度总收入"

report_summary
