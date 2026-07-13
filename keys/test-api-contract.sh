#!/usr/bin/env bash
###############################################################################
# test-api-contract.sh — L3 API 接口测试：合同模块
#
# 覆盖端点：/api/v1/contract
#   - CRUD：创建/分页查询/详情/更新/删除
#   - 审批流：提交 submit
#   - 合同明细：查询/保存 details
#   - 分页查询：按项目ID/状态筛选
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
CREATED_CONTRACT_ID=""

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
  log "合同模块 API 测试汇总"
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
  if [ -n "$CREATED_CONTRACT_ID" ]; then
    call DELETE "/api/v1/contract/$CREATED_CONTRACT_ID" 2>/dev/null
    log "  已清理合同 ID=$CREATED_CONTRACT_ID"
  fi
  log "--- 清理完成 ---"
}

# 注册 trap，确保无论脚本如何退出都执行清理
trap cleanup EXIT

# ===========================================================================
# 测试用例
# ===========================================================================

test_create_contract() {
  log "▶ 测试：创建合同"
  call POST "/api/v1/contract" '{"projectId":1,"contractType":"REGISTER","partyAName":"测试甲方公司","signingDate":"2025-01-15","startDate":"2025-02-01","endDate":"2025-12-31","contractAmount":500000.00,"taxRate":9.00,"amountWithoutTax":458715.60,"taxAmount":41284.40}'
  assert_http 2 "POST /api/v1/contract 状态码"
  assert_body_code 200 "POST /api/v1/contract 业务码"
}

test_page_query() {
  log "▶ 测试：分页查询合同"
  sleep 1
  call GET "/api/v1/contract/page?page=1&size=10"
  assert_http 2 "GET /api/v1/contract/page 状态码"
  assert_body_code 200 "GET /api/v1/contract/page 业务码"
  assert_has_field "records" "分页结果含 records 字段"
  assert_has_field "total" "分页结果含 total 字段"

  # 提取合同 ID 用于后续测试
  CREATED_CONTRACT_ID=$(extract_first_record_id)
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  WARN: 未能获取合同ID，后续测试可能失败"
  else
    log "  获取合同 ID=$CREATED_CONTRACT_ID"
  fi
}

test_page_query_with_project() {
  log "▶ 测试：按项目ID筛选分页查询"
  call GET "/api/v1/contract/page?page=1&size=10&projectId=1"
  assert_http 2 "GET /api/v1/contract/page?projectId=1 状态码"
  assert_body_code 200 "GET /api/v1/contract/page?projectId=1 业务码"
  assert_has_field "records" "按项目筛选含 records 字段"
}

test_page_query_with_status() {
  log "▶ 测试：按状态筛选分页查询"
  call GET "/api/v1/contract/page?page=1&size=10&status=DRAFT"
  assert_http 2 "GET /api/v1/contract/page?status=DRAFT 状态码"
  assert_body_code 200 "GET /api/v1/contract/page?status=DRAFT 业务码"
}

test_get_detail() {
  log "▶ 测试：查询合同详情"
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID"; return 0
  fi
  call GET "/api/v1/contract/$CREATED_CONTRACT_ID"
  assert_http 2 "GET /api/v1/contract/{id} 状态码"
  assert_body_code 200 "GET /api/v1/contract/{id} 业务码"
  assert_has_field "contractAmount" "详情含 contractAmount"
}

test_update_contract() {
  log "▶ 测试：更新合同"
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID"; return 0
  fi
  call PUT "/api/v1/contract/$CREATED_CONTRACT_ID" '{"projectId":1,"contractType":"REGISTER","partyAName":"测试甲方公司-已修改","signingDate":"2025-01-20","startDate":"2025-02-01","endDate":"2025-12-31","contractAmount":600000.00,"taxRate":9.00,"amountWithoutTax":550458.72,"taxAmount":49541.28}'
  assert_http 2 "PUT /api/v1/contract/{id} 状态码"
  assert_body_code 200 "PUT /api/v1/contract/{id} 业务码"

  # 验证修改后的值
  call GET "/api/v1/contract/$CREATED_CONTRACT_ID"
  local party_a
  party_a=$(cat /tmp/zwi_body 2>/dev/null | grep -oE '"partyAName"\s*:\s*"[^"]+"' | head -1 | sed -E 's/.*"partyAName"\s*:\s*"//;s/"$//')
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [ "$party_a" = "测试甲方公司-已修改" ]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] 更新后甲方名称正确"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] 更新后甲方名称不符 (实际: $party_a)"
  fi
}

test_get_details() {
  log "▶ 测试：查询合同明细"
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID"; return 0
  fi
  call GET "/api/v1/contract/$CREATED_CONTRACT_ID/details"
  assert_http 2 "GET /api/v1/contract/{id}/details 状态码"
  assert_body_code 200 "GET /api/v1/contract/{id}/details 业务码"
}

test_save_details() {
  log "▶ 测试：保存合同明细"
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID"; return 0
  fi
  call POST "/api/v1/contract/$CREATED_CONTRACT_ID/details" '[{"itemName":"测试分项","quantity":100,"unit":"m3","unitPrice":50.00,"amount":5000.00}]'
  assert_http 2 "POST /api/v1/contract/{id}/details 状态码"
  assert_body_code 200 "POST /api/v1/contract/{id}/details 业务码"
}

test_submit_contract() {
  log "▶ 测试：提交合同审批"
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID"; return 0
  fi
  call POST "/api/v1/contract/$CREATED_CONTRACT_ID/submit"
  assert_http 2 "POST /api/v1/contract/{id}/submit 状态码"
  assert_body_code 200 "POST /api/v1/contract/{id}/submit 业务码"
}

test_delete_contract() {
  log "▶ 测试：删除合同"
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID"; return 0
  fi
  call DELETE "/api/v1/contract/$CREATED_CONTRACT_ID"
  assert_http 2 "DELETE /api/v1/contract/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/contract/{id} 业务码"
  # 合同已删除，清理时不重复删
  CREATED_CONTRACT_ID=""
}

test_get_nonexistent() {
  log "▶ 测试：查询不存在的合同"
  call GET "/api/v1/contract/999999999"
  local code
  code=$(cat /tmp/zwi_last_code 2>/dev/null || echo "000")
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [[ "$code" =~ ^[2-4][0-9][0-9]$ ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] GET 不存在合同无 5xx (HTTP $code)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] GET 不存在合同返回 5xx (HTTP $code)"
  fi
}

# ===========================================================================
# 主流程
# ===========================================================================
main() {
  echo ""
  log "═══ L3 API 接口测试：合同模块 (test-api-contract.sh) ═══"
  log "时间: $(date '+%Y-%m-%d %H:%M:%S') | 服务: $BASE"
  echo ""

  # 确保登录
  login || { log "登录失败，无法执行测试"; exit 1; }

  # 按 CRUD + 审批 + 分页 顺序执行
  test_create_contract
  test_page_query
  test_page_query_with_project
  test_page_query_with_status
  test_get_detail
  test_update_contract
  test_get_details
  test_save_details
  test_submit_contract
  test_delete_contract
  test_get_nonexistent

  # 日志核对
  echo ""
  log "▶ 后端日志核对"
  check_logs 120

  # 输出汇总
  report_summary
  exit $?
}

main "$@"
