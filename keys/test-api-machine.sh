#!/usr/bin/env bash
###############################################################################
# test-api-machine.sh — L3 API 接口测试：机械模块
#
# 覆盖端点：
#   - /api/v1/machine/contract   机械合同 CRUD + 提交审批
#   - /api/v1/machine/ledger     机械台账 CRUD
#   - /api/v1/machine/work-log   机械工作日志分页查询
#   - /api/v1/machine/entry      机械进退场分页查询
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
CREATED_LEDGER_ID=""

# ===========================================================================
# 公共测试函数
# ===========================================================================

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

report_summary() {
  echo ""
  echo "═══════════════════════════════════════════════════════════"
  log "机械模块 API 测试汇总"
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

extract_id() {
  local val
  val=$(cat /tmp/zwi_body 2>/dev/null | grep -oE '"data"\s*:\s*[0-9]+' | head -1 | grep -oE '[0-9]+$')
  if [ -n "$val" ]; then echo "$val"; return; fi
  val=$(cat /tmp/zwi_body 2>/dev/null | grep -oE '"id"\s*:\s*[0-9]+' | head -1 | grep -oE '[0-9]+$')
  echo "$val"
}

extract_first_record_id() {
  cat /tmp/zwi_body 2>/dev/null | grep -oE '"id"\s*:\s*[0-9]+' | head -1 | grep -oE '[0-9]+$'
}

# ===========================================================================
# 清理逻辑
# ===========================================================================
cleanup() {
  log "--- 清理测试数据 ---"
  if [ -n "$CREATED_LEDGER_ID" ]; then
    call DELETE "/api/v1/machine/ledger/$CREATED_LEDGER_ID" 2>/dev/null
    log "  已清理台账 ID=$CREATED_LEDGER_ID"
  fi
  if [ -n "$CREATED_CONTRACT_ID" ]; then
    call DELETE "/api/v1/machine/contract/$CREATED_CONTRACT_ID" 2>/dev/null
    log "  已清理合同 ID=$CREATED_CONTRACT_ID"
  fi
  log "--- 清理完成 ---"
}

trap cleanup EXIT

# ===========================================================================
# 测试用例：机械合同
# ===========================================================================

test_contract_create() {
  log "▶ 测试：创建机械合同"
  call POST "/api/v1/machine/contract" '{"projectId":1,"contractName":"测试机械租赁合同-自动化","contractNo":"MC-AUTO-001","supplierName":"测试机械供应商","contractAmount":50000.00,"startDate":"2025-07-01","endDate":"2025-12-31","remark":"L3接口自动化测试"}'
  assert_http 2 "POST /api/v1/machine/contract 状态码"
  assert_body_code 200 "POST /api/v1/machine/contract 业务码"
}

test_contract_page() {
  log "▶ 测试：分页查询机械合同"
  call GET "/api/v1/machine/contract/page?page=1&size=10"
  assert_http 2 "GET /api/v1/machine/contract/page 状态码"
  assert_body_code 200 "GET /api/v1/machine/contract/page 业务码"
  assert_has_field "records" "合同分页含 records 字段"
  assert_has_field "total" "合同分页含 total 字段"

  CREATED_CONTRACT_ID=$(extract_first_record_id)
  if [ -n "$CREATED_CONTRACT_ID" ]; then
    log "  获取合同 ID=$CREATED_CONTRACT_ID"
  else
    log "  WARN: 未能获取合同ID，后续测试可能失败"
  fi
}

test_contract_detail() {
  log "▶ 测试：查询机械合同详情"
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID"; return 0
  fi
  call GET "/api/v1/machine/contract/$CREATED_CONTRACT_ID"
  assert_http 2 "GET /api/v1/machine/contract/{id} 状态码"
  assert_body_code 200 "GET /api/v1/machine/contract/{id} 业务码"
}

test_contract_update() {
  log "▶ 测试：更新机械合同"
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID"; return 0
  fi
  call PUT "/api/v1/machine/contract/$CREATED_CONTRACT_ID" '{"projectId":1,"contractName":"测试机械租赁合同-已修改","contractNo":"MC-AUTO-001","supplierName":"测试机械供应商-修改","contractAmount":60000.00,"startDate":"2025-07-01","endDate":"2025-12-31","remark":"修改后的机械合同"}'
  assert_http 2 "PUT /api/v1/machine/contract/{id} 状态码"
  assert_body_code 200 "PUT /api/v1/machine/contract/{id} 业务码"
}

test_contract_submit() {
  log "▶ 测试：提交机械合同审批"
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID"; return 0
  fi
  call POST "/api/v1/machine/contract/$CREATED_CONTRACT_ID/submit"
  assert_http 2 "POST /api/v1/machine/contract/{id}/submit 状态码"
  assert_body_code 200 "POST /api/v1/machine/contract/{id}/submit 业务码"
}

# ===========================================================================
# 测试用例：机械台账
# ===========================================================================

test_ledger_create() {
  log "▶ 测试：创建机械台账"
  call POST "/api/v1/machine/ledger" '{"machineName":"挖掘机-自动化测试","machineType":"土方机械","machineModel":"CAT320D","plateNumber":"川A-TEST01","ownerType":"自有","status":1}'
  assert_http 2 "POST /api/v1/machine/ledger 状态码"
  assert_body_code 200 "POST /api/v1/machine/ledger 业务码"
}

test_ledger_page() {
  log "▶ 测试：分页查询机械台账"
  call GET "/api/v1/machine/ledger/page?page=1&size=10"
  assert_http 2 "GET /api/v1/machine/ledger/page 状态码"
  assert_body_code 200 "GET /api/v1/machine/ledger/page 业务码"
  assert_has_field "records" "台账分页含 records 字段"

  CREATED_LEDGER_ID=$(extract_first_record_id)
  if [ -n "$CREATED_LEDGER_ID" ]; then
    log "  获取台账 ID=$CREATED_LEDGER_ID"
  fi
}

test_ledger_update() {
  log "▶ 测试：更新机械台账"
  if [ -z "$CREATED_LEDGER_ID" ]; then
    log "  SKIP: 无台账ID"; return 0
  fi
  call PUT "/api/v1/machine/ledger/$CREATED_LEDGER_ID" '{"machineName":"挖掘机-已修改","machineType":"土方机械","machineModel":"CAT320D2","plateNumber":"川A-TEST01","ownerType":"租赁","status":1}'
  assert_http 2 "PUT /api/v1/machine/ledger/{id} 状态码"
  assert_body_code 200 "PUT /api/v1/machine/ledger/{id} 业务码"
}

test_ledger_page_filter() {
  log "▶ 测试：按类型筛选机械台账"
  call GET "/api/v1/machine/ledger/page?page=1&size=10&machineType=%E5%9C%9F%E6%96%B9%E6%9C%BA%E6%A2%B0"
  assert_http 2 "GET /api/v1/machine/ledger/page?machineType 状态码"
  assert_body_code 200 "GET /api/v1/machine/ledger/page?machineType 业务码"
}

test_ledger_delete() {
  log "▶ 测试：删除机械台账"
  if [ -z "$CREATED_LEDGER_ID" ]; then
    log "  SKIP: 无台账ID"; return 0
  fi
  call DELETE "/api/v1/machine/ledger/$CREATED_LEDGER_ID"
  assert_http 2 "DELETE /api/v1/machine/ledger/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/machine/ledger/{id} 业务码"
  CREATED_LEDGER_ID=""
}

# ===========================================================================
# 测试用例：工作日志 & 进退场（只读查询）
# ===========================================================================

test_worklog_page() {
  log "▶ 测试：分页查询机械工作日志"
  call GET "/api/v1/machine/work-log/page?page=1&size=10"
  assert_http 2 "GET /api/v1/machine/work-log/page 状态码"
  assert_body_code 200 "GET /api/v1/machine/work-log/page 业务码"
  assert_has_field "records" "工作日志分页含 records 字段"
}

test_entry_page() {
  log "▶ 测试：分页查询机械进退场"
  call GET "/api/v1/machine/entry/page?page=1&size=10"
  assert_http 2 "GET /api/v1/machine/entry/page 状态码"
  assert_body_code 200 "GET /api/v1/machine/entry/page 业务码"
  assert_has_field "records" "进退场分页含 records 字段"
}

# ===========================================================================
# 测试用例：异常场景
# ===========================================================================

test_contract_nonexistent() {
  log "▶ 测试：查询不存在的机械合同"
  call GET "/api/v1/machine/contract/999999999"
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

test_contract_delete() {
  log "▶ 测试：删除机械合同"
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID"; return 0
  fi
  call DELETE "/api/v1/machine/contract/$CREATED_CONTRACT_ID"
  assert_http 2 "DELETE /api/v1/machine/contract/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/machine/contract/{id} 业务码"
  CREATED_CONTRACT_ID=""
}

# ===========================================================================
# 主流程
# ===========================================================================
main() {
  echo ""
  log "═══ L3 API 接口测试：机械模块 (test-api-machine.sh) ═══"
  log "时间: $(date '+%Y-%m-%d %H:%M:%S') | 服务: $BASE"
  echo ""

  login || { log "登录失败，无法执行测试"; exit 1; }

  # 机械合同
  test_contract_create
  test_contract_page
  test_contract_detail
  test_contract_update
  test_contract_submit

  # 机械台账
  test_ledger_create
  test_ledger_page
  test_ledger_update
  test_ledger_page_filter
  test_ledger_delete

  # 工作日志 & 进退场
  test_worklog_page
  test_entry_page

  # 异常场景
  test_contract_nonexistent

  # 清理合同
  test_contract_delete

  # 日志核对
  echo ""
  log "▶ 后端日志核对"
  check_logs 120

  # 输出汇总
  report_summary
  exit $?
}

main "$@"
