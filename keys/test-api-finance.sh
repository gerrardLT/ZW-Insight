#!/usr/bin/env bash
###############################################################################
# test-api-finance.sh — L3 API 接口测试：财务模块
#
# 覆盖端点：
#   - /api/v1/finance/payment-apply  — 付款申请 CRUD + 提交审批
#   - /api/v1/finance/payment-received — 收款登记 CRUD
#   - /api/v1/finance/bank-account — 银行账户管理
#   - /api/v1/project-settlements — 项目结算查询
#
# 运行位置：服务器 129.204.3.200（需要 docker exec zwi-redis）
# 依赖：verify-base.sh 提供登录/调用基座
#
# 设计依据：full-layer-test-suite spec
#   - 需求 4.1：模块化 Shell 脚本，source verify-base.sh
#   - 需求 4.2：复用登录、调用、日志基座能力
#   - 需求 4.3：覆盖 CRUD + 审批 + 分页查询
#   - 需求 4.5：测试结束前 DELETE 清理已创建资源
#   - 需求 4.6：输出通过/失败计数，有失败时非零退出码
###############################################################################
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/verify-base.sh" login 2>/dev/null || true

# ===========================================================================
# 测试计数器
# ===========================================================================
PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0
CREATED_PAYMENT_APPLY_ID=""
CREATED_PAYMENT_RECEIVED_ID=""
CREATED_BANK_ACCOUNT_ID=""

# ===========================================================================
# 公共测试函数（复用 test-api-project.sh 模式）
# ===========================================================================

# assert_http <expected_code_prefix> <test_name>
assert_http() {
  local expected_prefix="$1" test_name="$2"
  local actual_code
  actual_code=$(cat /tmp/zwi_last_code 2>/dev/null || echo "000")
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [[ "$actual_code" == ${expected_prefix}* ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] $test_name (HTTP $actual_code)"
    return 0
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] $test_name (HTTP $actual_code, 期望 ${expected_prefix}xx)"
    return 1
  fi
}

# assert_body_code <expected_biz_code> <test_name>
assert_body_code() {
  local expected="$1" test_name="$2"
  local actual
  actual=$(cat /tmp/zwi_body 2>/dev/null | grep -oE '"code"\s*:\s*[0-9]+' | head -1 | grep -oE '[0-9]+$')
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [ "$actual" = "$expected" ]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] $test_name (code=$actual)"
    return 0
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] $test_name (code=$actual, 期望=$expected)"
    return 1
  fi
}

# assert_has_field <field_name> <test_name>
assert_has_field() {
  local field="$1" test_name="$2"
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if grep -q "\"$field\"" /tmp/zwi_body 2>/dev/null; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] $test_name (含字段 $field)"
    return 0
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] $test_name (缺少字段 $field)"
    return 1
  fi
}

# report_summary：输出测试汇总
report_summary() {
  echo ""
  echo "═══════════════════════════════════════════════════════════"
  log "财务模块 API 测试汇总"
  echo "═══════════════════════════════════════════════════════════"
  log "  通过: $PASS_COUNT"
  log "  失败: $FAIL_COUNT"
  log "  总计: $TOTAL_COUNT"
  echo "═══════════════════════════════════════════════════════════"
  if [ "$FAIL_COUNT" -gt 0 ]; then
    log "结果: FAILED"
    return 1
  else
    log "结果: PASSED"
    return 0
  fi
}

# extract_first_record_id：从分页结果的 records 数组中提取第一个 id
extract_first_record_id() {
  cat /tmp/zwi_body 2>/dev/null | grep -oE '"id"\s*:\s*[0-9]+' | head -1 | grep -oE '[0-9]+$'
}

# ===========================================================================
# 清理逻辑
# ===========================================================================
cleanup() {
  log "--- 清理测试数据 ---"
  if [ -n "$CREATED_PAYMENT_APPLY_ID" ]; then
    call DELETE "/api/v1/finance/payment-apply/$CREATED_PAYMENT_APPLY_ID" 2>/dev/null
    log "  已清理付款申请 ID=$CREATED_PAYMENT_APPLY_ID"
  fi
  if [ -n "$CREATED_PAYMENT_RECEIVED_ID" ]; then
    call DELETE "/api/v1/finance/payment-received/$CREATED_PAYMENT_RECEIVED_ID" 2>/dev/null
    log "  已清理收款登记 ID=$CREATED_PAYMENT_RECEIVED_ID"
  fi
  if [ -n "$CREATED_BANK_ACCOUNT_ID" ]; then
    call DELETE "/api/v1/finance/bank-account/$CREATED_BANK_ACCOUNT_ID" 2>/dev/null
    log "  已清理银行账户 ID=$CREATED_BANK_ACCOUNT_ID"
  fi
  log "--- 清理完成 ---"
}

# 注册 trap，确保无论脚本如何退出都执行清理
trap cleanup EXIT

# ===========================================================================
# 测试用例 — 付款申请
# ===========================================================================

test_create_payment_apply() {
  log "▶ 测试：创建付款申请"
  call POST "/api/v1/finance/payment-apply" '{"projectId":1,"contractCategory":"CONSTRUCTION","supplierName":"测试供应商","paymentAmount":50000.00,"paymentDate":"2025-03-15"}'
  assert_http 2 "POST /api/v1/finance/payment-apply 状态码"
  assert_body_code 200 "POST /api/v1/finance/payment-apply 业务码"
}

test_page_payment_apply() {
  log "▶ 测试：分页查询付款申请"
  sleep 1
  call GET "/api/v1/finance/payment-apply/page?page=1&size=10"
  assert_http 2 "GET /api/v1/finance/payment-apply/page 状态码"
  assert_body_code 200 "GET /api/v1/finance/payment-apply/page 业务码"
  assert_has_field "records" "付款申请分页含 records"
  assert_has_field "total" "付款申请分页含 total"

  # 提取 ID 用于后续测试
  CREATED_PAYMENT_APPLY_ID=$(extract_first_record_id)
  if [ -z "$CREATED_PAYMENT_APPLY_ID" ]; then
    log "  WARN: 未能获取付款申请ID"
  else
    log "  获取付款申请 ID=$CREATED_PAYMENT_APPLY_ID"
  fi
}

test_page_payment_apply_by_project() {
  log "▶ 测试：按项目ID筛选付款申请"
  call GET "/api/v1/finance/payment-apply/page?page=1&size=10&projectId=1"
  assert_http 2 "GET /api/v1/finance/payment-apply/page?projectId=1 状态码"
  assert_body_code 200 "GET /api/v1/finance/payment-apply/page?projectId=1 业务码"
}

test_get_payment_apply_detail() {
  log "▶ 测试：查询付款申请详情"
  if [ -z "$CREATED_PAYMENT_APPLY_ID" ]; then
    log "  SKIP: 无付款申请ID"; return 0
  fi
  call GET "/api/v1/finance/payment-apply/$CREATED_PAYMENT_APPLY_ID"
  assert_http 2 "GET /api/v1/finance/payment-apply/{id} 状态码"
  assert_body_code 200 "GET /api/v1/finance/payment-apply/{id} 业务码"
  assert_has_field "paymentAmount" "详情含 paymentAmount"
}

test_update_payment_apply() {
  log "▶ 测试：更新付款申请"
  if [ -z "$CREATED_PAYMENT_APPLY_ID" ]; then
    log "  SKIP: 无付款申请ID"; return 0
  fi
  call PUT "/api/v1/finance/payment-apply/$CREATED_PAYMENT_APPLY_ID" '{"projectId":1,"contractCategory":"CONSTRUCTION","supplierName":"测试供应商-已修改","paymentAmount":65000.00,"paymentDate":"2025-03-20"}'
  assert_http 2 "PUT /api/v1/finance/payment-apply/{id} 状态码"
  assert_body_code 200 "PUT /api/v1/finance/payment-apply/{id} 业务码"
}

test_submit_payment_apply() {
  log "▶ 测试：提交付款申请审批"
  if [ -z "$CREATED_PAYMENT_APPLY_ID" ]; then
    log "  SKIP: 无付款申请ID"; return 0
  fi
  call POST "/api/v1/finance/payment-apply/$CREATED_PAYMENT_APPLY_ID/submit"
  assert_http 2 "POST /api/v1/finance/payment-apply/{id}/submit 状态码"
  assert_body_code 200 "POST /api/v1/finance/payment-apply/{id}/submit 业务码"
}

test_delete_payment_apply() {
  log "▶ 测试：删除付款申请"
  if [ -z "$CREATED_PAYMENT_APPLY_ID" ]; then
    log "  SKIP: 无付款申请ID"; return 0
  fi
  call DELETE "/api/v1/finance/payment-apply/$CREATED_PAYMENT_APPLY_ID"
  assert_http 2 "DELETE /api/v1/finance/payment-apply/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/finance/payment-apply/{id} 业务码"
  CREATED_PAYMENT_APPLY_ID=""
}

# ===========================================================================
# 测试用例 — 收款登记
# ===========================================================================

test_create_payment_received() {
  log "▶ 测试：创建收款登记"
  call POST "/api/v1/finance/payment-received" '{"projectId":1,"receiveDate":"2025-04-01","amount":100000.00,"payerName":"测试付款方","remark":"L3接口测试收款"}'
  assert_http 2 "POST /api/v1/finance/payment-received 状态码"
  assert_body_code 200 "POST /api/v1/finance/payment-received 业务码"
}

test_page_payment_received() {
  log "▶ 测试：分页查询收款登记"
  sleep 1
  call GET "/api/v1/finance/payment-received/page?page=1&size=10"
  assert_http 2 "GET /api/v1/finance/payment-received/page 状态码"
  assert_body_code 200 "GET /api/v1/finance/payment-received/page 业务码"
  assert_has_field "records" "收款登记分页含 records"

  CREATED_PAYMENT_RECEIVED_ID=$(extract_first_record_id)
  if [ -z "$CREATED_PAYMENT_RECEIVED_ID" ]; then
    log "  WARN: 未能获取收款登记ID"
  else
    log "  获取收款登记 ID=$CREATED_PAYMENT_RECEIVED_ID"
  fi
}

test_page_payment_received_by_project() {
  log "▶ 测试：按项目ID筛选收款登记"
  call GET "/api/v1/finance/payment-received/page?page=1&size=10&projectId=1"
  assert_http 2 "GET /api/v1/finance/payment-received/page?projectId=1 状态码"
  assert_body_code 200 "GET /api/v1/finance/payment-received/page?projectId=1 业务码"
}

test_get_payment_received_detail() {
  log "▶ 测试：查询收款登记详情"
  if [ -z "$CREATED_PAYMENT_RECEIVED_ID" ]; then
    log "  SKIP: 无收款登记ID"; return 0
  fi
  call GET "/api/v1/finance/payment-received/$CREATED_PAYMENT_RECEIVED_ID"
  assert_http 2 "GET /api/v1/finance/payment-received/{id} 状态码"
  assert_body_code 200 "GET /api/v1/finance/payment-received/{id} 业务码"
}

test_update_payment_received() {
  log "▶ 测试：更新收款登记"
  if [ -z "$CREATED_PAYMENT_RECEIVED_ID" ]; then
    log "  SKIP: 无收款登记ID"; return 0
  fi
  call PUT "/api/v1/finance/payment-received/$CREATED_PAYMENT_RECEIVED_ID" '{"projectId":1,"receiveDate":"2025-04-05","amount":120000.00,"payerName":"测试付款方-已修改","remark":"L3接口测试收款-修改"}'
  assert_http 2 "PUT /api/v1/finance/payment-received/{id} 状态码"
  assert_body_code 200 "PUT /api/v1/finance/payment-received/{id} 业务码"
}

test_delete_payment_received() {
  log "▶ 测试：删除收款登记"
  if [ -z "$CREATED_PAYMENT_RECEIVED_ID" ]; then
    log "  SKIP: 无收款登记ID"; return 0
  fi
  call DELETE "/api/v1/finance/payment-received/$CREATED_PAYMENT_RECEIVED_ID"
  assert_http 2 "DELETE /api/v1/finance/payment-received/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/finance/payment-received/{id} 业务码"
  CREATED_PAYMENT_RECEIVED_ID=""
}

# ===========================================================================
# 测试用例 — 银行账户
# ===========================================================================

test_create_bank_account() {
  log "▶ 测试：创建银行账户"
  call POST "/api/v1/finance/bank-account" '{"accountType":"BASIC","projectId":1,"bankName":"中国建设银行","accountName":"测试施工公司","accountNo":"6217001234567890123","remark":"L3接口测试账户"}'
  assert_http 2 "POST /api/v1/finance/bank-account 状态码"
  assert_body_code 200 "POST /api/v1/finance/bank-account 业务码"
}

test_page_bank_account() {
  log "▶ 测试：分页查询银行账户"
  sleep 1
  call GET "/api/v1/finance/bank-account?page=1&size=10"
  assert_http 2 "GET /api/v1/finance/bank-account 状态码"
  assert_body_code 200 "GET /api/v1/finance/bank-account 业务码"
  assert_has_field "records" "银行账户分页含 records"

  CREATED_BANK_ACCOUNT_ID=$(extract_first_record_id)
  if [ -z "$CREATED_BANK_ACCOUNT_ID" ]; then
    log "  WARN: 未能获取银行账户ID"
  else
    log "  获取银行账户 ID=$CREATED_BANK_ACCOUNT_ID"
  fi
}

test_page_bank_account_by_type() {
  log "▶ 测试：按账户类型筛选银行账户"
  call GET "/api/v1/finance/bank-account?page=1&size=10&accountType=BASIC"
  assert_http 2 "GET /api/v1/finance/bank-account?accountType=BASIC 状态码"
  assert_body_code 200 "GET /api/v1/finance/bank-account?accountType=BASIC 业务码"
}

test_delete_bank_account() {
  log "▶ 测试：删除银行账户"
  if [ -z "$CREATED_BANK_ACCOUNT_ID" ]; then
    log "  SKIP: 无银行账户ID"; return 0
  fi
  call DELETE "/api/v1/finance/bank-account/$CREATED_BANK_ACCOUNT_ID"
  assert_http 2 "DELETE /api/v1/finance/bank-account/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/finance/bank-account/{id} 业务码"
  CREATED_BANK_ACCOUNT_ID=""
}

# ===========================================================================
# 测试用例 — 项目结算（汇总查询）
# ===========================================================================

test_get_settlement_nonexistent() {
  log "▶ 测试：查询不存在的结算单"
  call GET "/api/v1/project-settlements/999999999"
  local code
  code=$(cat /tmp/zwi_last_code 2>/dev/null || echo "000")
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [[ "$code" =~ ^[2-4][0-9][0-9]$ ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] GET 不存在结算单无 5xx (HTTP $code)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] GET 不存在结算单返回 5xx (HTTP $code)"
  fi
}

# ===========================================================================
# 主流程
# ===========================================================================
main() {
  echo ""
  log "═══ L3 API 接口测试：财务模块 (test-api-finance.sh) ═══"
  log "时间: $(date '+%Y-%m-%d %H:%M:%S') | 服务: $BASE"
  echo ""

  # 确保登录
  login || { log "登录失败，无法执行测试"; exit 1; }

  # --- 付款申请 ---
  echo ""
  log "─── 付款申请接口测试 ───"
  test_create_payment_apply
  test_page_payment_apply
  test_page_payment_apply_by_project
  test_get_payment_apply_detail
  test_update_payment_apply
  test_submit_payment_apply
  test_delete_payment_apply

  # --- 收款登记 ---
  echo ""
  log "─── 收款登记接口测试 ───"
  test_create_payment_received
  test_page_payment_received
  test_page_payment_received_by_project
  test_get_payment_received_detail
  test_update_payment_received
  test_delete_payment_received

  # --- 银行账户 ---
  echo ""
  log "─── 银行账户接口测试 ───"
  test_create_bank_account
  test_page_bank_account
  test_page_bank_account_by_type
  test_delete_bank_account

  # --- 汇总查询 ---
  echo ""
  log "─── 项目结算汇总查询测试 ───"
  test_get_settlement_nonexistent

  # 日志核对
  echo ""
  log "▶ 后端日志核对"
  check_logs 120

  # 输出汇总
  report_summary
  exit $?
}

main "$@"
