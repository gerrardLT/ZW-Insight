#!/usr/bin/env bash
###############################################################################
# test-api-finance2.sh — L3 API 接口测试：财务模块补齐（阶段四批 1）
#
# 覆盖端点（test-api-finance.sh 未覆盖部分）：
#   - /api/v1/finance/invoice-apply      开票申请：分页/详情结构
#   - /api/v1/finance/invoice-received   收票登记：列表
#   - /api/v1/finance/invoice-summary    发票汇总
#   - /api/v1/finance/other-payment      其他费用付款：列表
#   - /api/v1/finance/personal-reimbursement / project-reimbursement 报销：列表
#   - /api/v1/project-settlements        项目结算：列表
#   - /api/v1/finance/reserve-fund       备用金：申请列表
#   - /api/v1/finance/retention          质保金：分页/临期
#   - /api/v1/finance/tax-rate           税率：列表/全量/CRUD 闭环/非法值负向
#   - /api/v1/finance/lock               财务锁：状态/分页
#
# 运行位置：服务器（需要 docker exec zwi-redis）；依赖 verify-base.sh 登录基座
# 设计依据：阶段四批 1（coverage-matrix L3 缺口销项）；jq 断言规范同 2.4 契约强化
###############################################################################
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/verify-base.sh" login 2>/dev/null || true

PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0
CREATED_TAXRATE_ID=""

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

# 分页结构契约：code=200 + records 数组 + total 数字或字符串（雪花 Long→String 序列化）
PAGE_EXPR='.code==200 and (.data.records|type=="array") and ((.data.total|type=="number") or (.data.total|type=="string"))'

report_summary() {
  echo ""
  echo "═══════════════════════════════════════════════════════════"
  log "财务模块补齐 API 测试汇总"
  echo "═══════════════════════════════════════════════════════════"
  log "  通过: $PASS_COUNT"
  log "  失败: $FAIL_COUNT"
  log "  总计: $TOTAL_COUNT"
  echo "═══════════════════════════════════════════════════════════"
  [ "$FAIL_COUNT" -eq 0 ]
}

log "========== L3 财务模块补齐 API 测试开始 =========="

# ---------- 开票申请 ----------
call GET "/api/v1/finance/invoice-apply/page?page=1&size=5"
assert_http 2 "开票申请-分页 HTTP"
assert_jq "$PAGE_EXPR" "开票申请-分页结构"

# ---------- 收票登记 / 汇总 / 其他付款 / 报销 / 结算 ----------
call GET "/api/v1/finance/invoice-received?page=1&size=5"
assert_http 2 "收票登记-列表 HTTP"
assert_body_code 200 "收票登记-业务码"

call GET "/api/v1/finance/invoice-summary"
assert_http 2 "发票汇总 HTTP"
assert_body_code 200 "发票汇总-业务码"

call GET "/api/v1/finance/other-payment?page=1&size=5"
assert_http 2 "其他费用付款-列表 HTTP"
assert_body_code 200 "其他费用付款-业务码"

call GET "/api/v1/finance/personal-reimbursement?page=1&size=5"
assert_http 2 "个人报销-列表 HTTP"
assert_body_code 200 "个人报销-业务码"

call GET "/api/v1/finance/project-reimbursement?page=1&size=5"
assert_http 2 "项目报销-列表 HTTP"
assert_body_code 200 "项目报销-业务码"

call GET "/api/v1/project-settlements?page=1&size=5"
assert_http 2 "项目结算-列表 HTTP"
assert_body_code 200 "项目结算-业务码"

# ---------- 备用金 / 质保金 ----------
call GET "/api/v1/finance/reserve-fund/apply?page=1&size=5"
assert_http 2 "备用金申请-列表 HTTP"
assert_body_code 200 "备用金申请-业务码"

call GET "/api/v1/finance/retention/page?page=1&size=5"
assert_http 2 "质保金-分页 HTTP"
assert_jq "$PAGE_EXPR" "质保金-分页结构"

call GET "/api/v1/finance/retention/expiring"
assert_http 2 "质保金-临期列表 HTTP"
assert_body_code 200 "质保金-临期业务码"

# ---------- 税率字典 CRUD 闭环 ----------
TS_SUFFIX=$(date +%s)
call POST "/api/v1/finance/tax-rate" "{\"name\":\"L3临时税率$TS_SUFFIX\",\"rateValue\":3.5}"
assert_http 2 "税率-创建 HTTP"
assert_body_code 200 "税率-创建业务码"
CREATED_TAXRATE_ID=$(jq -r '.data.id // empty' /tmp/zwi_body 2>/dev/null)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$CREATED_TAXRATE_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 税率-创建返回ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 税率-创建未返回ID"
fi

call GET "/api/v1/finance/tax-rate/list"
assert_http 2 "税率-启用列表 HTTP"
assert_jq '.code==200 and (.data|type=="array")' "税率-启用列表为数组"

call GET "/api/v1/finance/tax-rate/all"
assert_http 2 "税率-全量列表 HTTP"
assert_jq '.code==200 and (.data|type=="array")' "税率-全量列表为数组"

if [ -n "$CREATED_TAXRATE_ID" ]; then
  call PUT "/api/v1/finance/tax-rate/$CREATED_TAXRATE_ID" "{\"name\":\"L3临时税率改$TS_SUFFIX\",\"rateValue\":6}"
  assert_http 2 "税率-更新 HTTP"
  assert_body_code 200 "税率-更新业务码"

  # 负向：非法税率值（超上限）必须拒绝，不得静默成功
  call POST "/api/v1/finance/tax-rate" "{\"name\":\"L3非法税率$TS_SUFFIX\",\"rateValue\":999}"
  assert_body_not_success "税率-非法值被拒绝"

  # 负向：重名必须拒绝
  call POST "/api/v1/finance/tax-rate" "{\"name\":\"L3临时税率改$TS_SUFFIX\",\"rateValue\":9}"
  assert_body_not_success "税率-重名被拒绝"

  call DELETE "/api/v1/finance/tax-rate/$CREATED_TAXRATE_ID"
  assert_http 2 "税率-停用 HTTP"
  assert_body_code 200 "税率-停用业务码"
fi

# ---------- 财务锁 ----------
call GET "/api/v1/finance/lock/status"
assert_http 2 "财务锁-状态 HTTP"
assert_body_code 200 "财务锁-状态业务码"

call GET "/api/v1/finance/lock/page?page=1&size=5"
assert_http 2 "财务锁-分页 HTTP"
assert_jq "$PAGE_EXPR" "财务锁-分页结构"

report_summary
