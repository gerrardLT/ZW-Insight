#!/usr/bin/env bash
###############################################################################
# test-api-subcontract.sh — L3 API 接口测试：分包模块
#
# 覆盖端点：
#   - /api/v1/subcontract/contract     分包合同 CRUD + 提交审批
#   - /api/v1/subcontract/settlement   分包结算 CRUD + 提交审批
#   - /api/v1/subcontract/output       分包产值分页查询
#   - /api/v1/subcontract/reward-punish 奖罚记录分页查询
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
CREATED_SETTLEMENT_ID=""

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
  log "分包模块 API 测试汇总"
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
  if [ -n "$CREATED_SETTLEMENT_ID" ]; then
    call DELETE "/api/v1/subcontract/settlement/$CREATED_SETTLEMENT_ID" 2>/dev/null
    log "  已清理结算单 ID=$CREATED_SETTLEMENT_ID"
  fi
  if [ -n "$CREATED_CONTRACT_ID" ]; then
    call DELETE "/api/v1/subcontract/contract/$CREATED_CONTRACT_ID" 2>/dev/null
    log "  已清理分包合同 ID=$CREATED_CONTRACT_ID"
  fi
  log "--- 清理完成 ---"
}

trap cleanup EXIT

# ===========================================================================
# 测试用例：分包合同
# ===========================================================================

test_contract_create() {
  log "▶ 测试：创建分包合同"
  call POST "/api/v1/subcontract/contract" '{"projectId":1,"contractName":"测试分包合同-自动化","contractNo":"SC-AUTO-001","subcontractorName":"测试分包单位","contractAmount":200000.00,"startDate":"2025-07-01","endDate":"2025-12-31","workScope":"基础施工","remark":"L3接口自动化测试"}'
  assert_http 2 "POST /api/v1/subcontract/contract 状态码"
  assert_body_code 200 "POST /api/v1/subcontract/contract 业务码"
}

test_contract_page() {
  log "▶ 测试：分页查询分包合同"
  call GET "/api/v1/subcontract/contract/page?page=1&size=10"
  assert_http 2 "GET /api/v1/subcontract/contract/page 状态码"
  assert_body_code 200 "GET /api/v1/subcontract/contract/page 业务码"
  assert_has_field "records" "分包合同分页含 records 字段"
  assert_has_field "total" "分包合同分页含 total 字段"

  CREATED_CONTRACT_ID=$(extract_first_record_id)
  if [ -n "$CREATED_CONTRACT_ID" ]; then
    log "  获取分包合同 ID=$CREATED_CONTRACT_ID"
  else
    log "  WARN: 未能获取分包合同ID，后续测试可能失败"
  fi
}

test_contract_detail() {
  log "▶ 测试：查询分包合同详情"
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID"; return 0
  fi
  call GET "/api/v1/subcontract/contract/$CREATED_CONTRACT_ID"
  assert_http 2 "GET /api/v1/subcontract/contract/{id} 状态码"
  assert_body_code 200 "GET /api/v1/subcontract/contract/{id} 业务码"
}

test_contract_update() {
  log "▶ 测试：更新分包合同"
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID"; return 0
  fi
  call PUT "/api/v1/subcontract/contract/$CREATED_CONTRACT_ID" '{"projectId":1,"contractName":"测试分包合同-已修改","contractNo":"SC-AUTO-001","subcontractorName":"测试分包单位-修改","contractAmount":250000.00,"startDate":"2025-07-01","endDate":"2025-12-31","workScope":"基础施工+主体施工","remark":"修改后的分包合同"}'
  assert_http 2 "PUT /api/v1/subcontract/contract/{id} 状态码"
  assert_body_code 200 "PUT /api/v1/subcontract/contract/{id} 业务码"
}

test_contract_submit() {
  log "▶ 测试：提交分包合同审批"
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID"; return 0
  fi
  call POST "/api/v1/subcontract/contract/$CREATED_CONTRACT_ID/submit"
  assert_http 2 "POST /api/v1/subcontract/contract/{id}/submit 状态码"
  assert_body_code 200 "POST /api/v1/subcontract/contract/{id}/submit 业务码"
}

# ===========================================================================
# 测试用例：分包结算
# ===========================================================================

test_settlement_create() {
  log "▶ 测试：创建分包结算单"
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID，无法创建结算单"; return 0
  fi
  call POST "/api/v1/subcontract/settlement" "{\"projectId\":1,\"contractId\":$CREATED_CONTRACT_ID,\"settlementName\":\"第1期结算-自动化\",\"settlementAmount\":50000.00,\"remark\":\"L3接口自动化测试结算\"}"
  assert_http 2 "POST /api/v1/subcontract/settlement 状态码"
  assert_body_code 200 "POST /api/v1/subcontract/settlement 业务码"

  CREATED_SETTLEMENT_ID=$(extract_id)
  if [ -n "$CREATED_SETTLEMENT_ID" ]; then
    log "  获取结算单 ID=$CREATED_SETTLEMENT_ID"
  fi
}

test_settlement_page() {
  log "▶ 测试：分页查询分包结算"
  call GET "/api/v1/subcontract/settlement?page=1&size=10"
  assert_http 2 "GET /api/v1/subcontract/settlement 状态码"
  assert_body_code 200 "GET /api/v1/subcontract/settlement 业务码"
  assert_has_field "records" "分包结算分页含 records 字段"

  # 如果前面创建没拿到 ID，从列表中取
  if [ -z "$CREATED_SETTLEMENT_ID" ]; then
    CREATED_SETTLEMENT_ID=$(extract_first_record_id)
    if [ -n "$CREATED_SETTLEMENT_ID" ]; then
      log "  从列表获取结算单 ID=$CREATED_SETTLEMENT_ID"
    fi
  fi
}

test_settlement_detail() {
  log "▶ 测试：查询分包结算详情"
  if [ -z "$CREATED_SETTLEMENT_ID" ]; then
    log "  SKIP: 无结算单ID"; return 0
  fi
  call GET "/api/v1/subcontract/settlement/$CREATED_SETTLEMENT_ID"
  assert_http 2 "GET /api/v1/subcontract/settlement/{id} 状态码"
  assert_body_code 200 "GET /api/v1/subcontract/settlement/{id} 业务码"
}

test_settlement_update() {
  log "▶ 测试：更新分包结算单"
  if [ -z "$CREATED_SETTLEMENT_ID" ]; then
    log "  SKIP: 无结算单ID"; return 0
  fi
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID"; return 0
  fi
  call PUT "/api/v1/subcontract/settlement/$CREATED_SETTLEMENT_ID" "{\"projectId\":1,\"contractId\":$CREATED_CONTRACT_ID,\"settlementName\":\"第1期结算-已修改\",\"settlementAmount\":55000.00,\"remark\":\"修改后的结算\"}"
  assert_http 2 "PUT /api/v1/subcontract/settlement/{id} 状态码"
  assert_body_code 200 "PUT /api/v1/subcontract/settlement/{id} 业务码"
}

test_settlement_submit() {
  log "▶ 测试：提交分包结算审批"
  if [ -z "$CREATED_SETTLEMENT_ID" ]; then
    log "  SKIP: 无结算单ID"; return 0
  fi
  call POST "/api/v1/subcontract/settlement/$CREATED_SETTLEMENT_ID/submit"
  assert_http 2 "POST /api/v1/subcontract/settlement/{id}/submit 状态码"
  assert_body_code 200 "POST /api/v1/subcontract/settlement/{id}/submit 业务码"
}

test_settlement_delete() {
  log "▶ 测试：删除分包结算单"
  if [ -z "$CREATED_SETTLEMENT_ID" ]; then
    log "  SKIP: 无结算单ID"; return 0
  fi
  call DELETE "/api/v1/subcontract/settlement/$CREATED_SETTLEMENT_ID"
  assert_http 2 "DELETE /api/v1/subcontract/settlement/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/subcontract/settlement/{id} 业务码"
  CREATED_SETTLEMENT_ID=""
}

# ===========================================================================
# 测试用例：产值 & 奖罚（只读查询）
# ===========================================================================

test_output_page() {
  log "▶ 测试：分页查询分包产值"
  call GET "/api/v1/subcontract/output/page?page=1&size=10"
  assert_http 2 "GET /api/v1/subcontract/output/page 状态码"
  assert_body_code 200 "GET /api/v1/subcontract/output/page 业务码"
  assert_has_field "records" "分包产值分页含 records 字段"
}

test_reward_punish_page() {
  log "▶ 测试：分页查询奖罚记录"
  call GET "/api/v1/subcontract/reward-punish/page?page=1&size=10"
  assert_http 2 "GET /api/v1/subcontract/reward-punish/page 状态码"
  assert_body_code 200 "GET /api/v1/subcontract/reward-punish/page 业务码"
  assert_has_field "records" "奖罚记录分页含 records 字段"
}

# ===========================================================================
# 测试用例：异常场景
# ===========================================================================

test_contract_nonexistent() {
  log "▶ 测试：查询不存在的分包合同"
  call GET "/api/v1/subcontract/contract/999999999"
  local code
  code=$(cat /tmp/zwi_last_code 2>/dev/null || echo "000")
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [[ "$code" =~ ^[2-4][0-9][0-9]$ ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] GET 不存在分包合同无 5xx (HTTP $code)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] GET 不存在分包合同返回 5xx (HTTP $code)"
  fi
}

test_contract_delete() {
  log "▶ 测试：删除分包合同"
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID"; return 0
  fi
  call DELETE "/api/v1/subcontract/contract/$CREATED_CONTRACT_ID"
  assert_http 2 "DELETE /api/v1/subcontract/contract/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/subcontract/contract/{id} 业务码"
  CREATED_CONTRACT_ID=""
}

# ===========================================================================
# 主流程
# ===========================================================================
main() {
  echo ""
  log "═══ L3 API 接口测试：分包模块 (test-api-subcontract.sh) ═══"
  log "时间: $(date '+%Y-%m-%d %H:%M:%S') | 服务: $BASE"
  echo ""

  login || { log "登录失败，无法执行测试"; exit 1; }

  # 分包合同
  test_contract_create
  test_contract_page
  test_contract_detail
  test_contract_update
  test_contract_submit

  # 分包结算
  test_settlement_create
  test_settlement_page
  test_settlement_detail
  test_settlement_update
  test_settlement_submit
  test_settlement_delete

  # 产值 & 奖罚
  test_output_page
  test_reward_punish_page

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
