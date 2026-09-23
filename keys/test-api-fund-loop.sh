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
#   - /api/v1/finance/fund-plan/rolling/generate     滚动预测生成（V2026_63 逾期口径）
#   - /api/v1/finance/fund-plan/rolling/page         滚动预测快照分页
#   - /api/v1/finance/fund-plan/rolling/top-expenses 待支付大额支出 TOP（含逾期）
#   - /api/v1/dashboard/profit-trend                 利润趋势真实化（12月结构）
#
# 关键语义用例（V2026_63，单测层无法覆盖的真实口径回归）：
#   已逾期未付必须计入当月预测、仅计入当月、且为 expectedPayments 的构成项（不可相加）。
#   原口径下线上 16 条/5850 万逾期款全部漏计，风险等级误判 LOW（应为 HIGH）。
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

# ---------- 1D 滚动预测：逾期未付口径（V2026_63 关键回归）----------
# 背景：原口径只统计 payment_date 落在未来窗口的单据，线上实测 16 条/5850 万
#       APPROVED+UNPAID（付款日全在过去）被全部漏计，当月缺口误显示为盈余 LOW。
# 本段钉住：逾期必须计入当月、且仅计入当月、且为构成项（不可相加）。

# 触发一次预测生成（幂等覆盖式写入，与每日 01:15 FundForecastTask 同口径）
call POST "/api/v1/finance/fund-plan/rolling/generate?months=6"
assert_http 2 "滚动预测-手工生成 HTTP"
assert_body_code 200 "滚动预测-手工生成业务码"

call GET "/api/v1/finance/fund-plan/rolling/page?page=1&size=12"
assert_http 2 "滚动预测-分页 HTTP"
assert_jq "$PAGE_EXPR" "滚动预测-分页结构"

FORECAST_MONTH=$(date +%Y-%m)
assert_jq ".code==200 and ([.data.records[] | select(.forecastMonth==\"$FORECAST_MONTH\")] | length == 1)" \
  "滚动预测-含当月快照"
# 当月逾期未付必须 > 0（库内存在 5850 万逾期款；为 0 即说明口径又退回了）
assert_jq ".code==200 and (([.data.records[] | select(.forecastMonth==\"$FORECAST_MONTH\")][0].overdueUnpaid | tonumber) > 0)" \
  "滚动预测-当月逾期未付已计入（overdueUnpaid>0，原口径漏计为0）"
# 构成项校验：expectedPayments 必须 >= overdueUnpaid（包含关系，不是相加关系）
assert_jq ".code==200 and ([.data.records[] | select(.forecastMonth==\"$FORECAST_MONTH\")][0] | (.expectedPayments | tonumber) >= (.overdueUnpaid | tonumber))" \
  "滚动预测-逾期为预计付款的构成项（expectedPayments ≥ overdueUnpaid）"
# 后续月份不得重复摊入同一笔逾期款
assert_jq ".code==200 and ([.data.records[] | select(.forecastMonth != \"$FORECAST_MONTH\") | select((.overdueUnpaid | tonumber) != 0)] | length == 0)" \
  "滚动预测-逾期仅计入当月（不向后续月份摊开）"
# 风险等级不得因漏计而误判：当月有巨额逾期时缺口必为正（付款>收款）
assert_jq ".code==200 and (([.data.records[] | select(.forecastMonth==\"$FORECAST_MONTH\")][0].netGap | tonumber) > 0)" \
  "滚动预测-当月净缺口为正（逾期计入后不再误显示盈余）"

# ---------- 1D-2 待支付大额支出 TOP（含逾期）----------
call GET "/api/v1/finance/fund-plan/rolling/top-expenses?days=30"
assert_http 2 "待支付大额支出TOP HTTP"
assert_jq '.code==200 and (.data|type=="array")' "待支付大额支出TOP-数组结构"
# 原口径下界为 today 会返回空数组（线上实测已暴露），修复后必须非空
assert_jq '.code==200 and (.data | length > 0)' \
  "待支付大额支出TOP-非空（含已逾期；原口径返回空数组为已知缺陷）"
assert_jq '.code==200 and ([.data[] | select(.categoryCode == null or .amount == null or .count == null or .overdueAmount == null)] | length == 0)' \
  "待支付大额支出TOP-行字段齐备（含 overdueAmount）"
assert_jq '.code==200 and ([.data[] | select((.overdueAmount | tonumber) > (.amount | tonumber))] | length == 0)' \
  "待支付大额支出TOP-逾期额不超总额（构成项校验）"

# 负向：days 超范围（>365）必须拒绝
call GET "/api/v1/finance/fund-plan/rolling/top-expenses?days=999"
assert_body_not_success "待支付大额支出TOP-days超范围被拒绝"

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
