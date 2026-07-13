#!/usr/bin/env bash
###############################################################################
# test-api-purchase.sh — L3 API 接口测试：采购模块
#
# 覆盖端点：
#   - /api/v1/purchase/contract — 采购合同 CRUD + 提交审批
#   - /api/v1/purchase/inquiry  — 询价单 CRUD + 发布 + 报价查询
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
CREATED_PURCHASE_CONTRACT_ID=""
CREATED_INQUIRY_ID=""

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
  log "采购模块 API 测试汇总"
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
  # 先删询价单再删采购合同（逻辑上询价可关联合同）
  if [ -n "$CREATED_INQUIRY_ID" ]; then
    call DELETE "/api/v1/purchase/inquiry/$CREATED_INQUIRY_ID" 2>/dev/null
    log "  已清理询价单 ID=$CREATED_INQUIRY_ID"
  fi
  if [ -n "$CREATED_PURCHASE_CONTRACT_ID" ]; then
    call DELETE "/api/v1/purchase/contract/$CREATED_PURCHASE_CONTRACT_ID" 2>/dev/null
    log "  已清理采购合同 ID=$CREATED_PURCHASE_CONTRACT_ID"
  fi
  log "--- 清理完成 ---"
}

# 注册 trap，确保无论脚本如何退出都执行清理
trap cleanup EXIT

# ===========================================================================
# 测试用例 — 采购合同
# ===========================================================================

test_create_purchase_contract() {
  log "▶ 测试：创建采购合同"
  call POST "/api/v1/purchase/contract" '{"projectId":1,"contractName":"L3测试采购合同","partyAName":"测试甲方","partyBName":"测试供应商","supplierName":"测试供应商","signingDate":"2025-03-01","contractAmount":200000.00,"paymentTerms":"月结30天","status":"DRAFT"}'
  assert_http 2 "POST /api/v1/purchase/contract 状态码"
  assert_body_code 200 "POST /api/v1/purchase/contract 业务码"
}

test_page_purchase_contract() {
  log "▶ 测试：分页查询采购合同"
  sleep 1
  call GET "/api/v1/purchase/contract/page?page=1&size=10"
  assert_http 2 "GET /api/v1/purchase/contract/page 状态码"
  assert_body_code 200 "GET /api/v1/purchase/contract/page 业务码"
  assert_has_field "records" "采购合同分页含 records"
  assert_has_field "total" "采购合同分页含 total"

  # 提取 ID 用于后续测试
  CREATED_PURCHASE_CONTRACT_ID=$(extract_first_record_id)
  if [ -z "$CREATED_PURCHASE_CONTRACT_ID" ]; then
    log "  WARN: 未能获取采购合同ID"
  else
    log "  获取采购合同 ID=$CREATED_PURCHASE_CONTRACT_ID"
  fi
}

test_page_purchase_contract_by_project() {
  log "▶ 测试：按项目ID筛选采购合同"
  call GET "/api/v1/purchase/contract/page?page=1&size=10&projectId=1"
  assert_http 2 "GET /api/v1/purchase/contract/page?projectId=1 状态码"
  assert_body_code 200 "GET /api/v1/purchase/contract/page?projectId=1 业务码"
}

test_page_purchase_contract_by_status() {
  log "▶ 测试：按状态筛选采购合同"
  call GET "/api/v1/purchase/contract/page?page=1&size=10&status=DRAFT"
  assert_http 2 "GET /api/v1/purchase/contract/page?status=DRAFT 状态码"
  assert_body_code 200 "GET /api/v1/purchase/contract/page?status=DRAFT 业务码"
}

test_get_purchase_contract_detail() {
  log "▶ 测试：查询采购合同详情"
  if [ -z "$CREATED_PURCHASE_CONTRACT_ID" ]; then
    log "  SKIP: 无采购合同ID"; return 0
  fi
  call GET "/api/v1/purchase/contract/$CREATED_PURCHASE_CONTRACT_ID"
  assert_http 2 "GET /api/v1/purchase/contract/{id} 状态码"
  assert_body_code 200 "GET /api/v1/purchase/contract/{id} 业务码"
  assert_has_field "contractAmount" "详情含 contractAmount"
}

test_update_purchase_contract() {
  log "▶ 测试：更新采购合同"
  if [ -z "$CREATED_PURCHASE_CONTRACT_ID" ]; then
    log "  SKIP: 无采购合同ID"; return 0
  fi
  call PUT "/api/v1/purchase/contract/$CREATED_PURCHASE_CONTRACT_ID" '{"projectId":1,"contractName":"L3测试采购合同-已修改","partyAName":"测试甲方","partyBName":"测试供应商-已修改","supplierName":"测试供应商-已修改","signingDate":"2025-03-05","contractAmount":250000.00,"paymentTerms":"月结60天","status":"DRAFT"}'
  assert_http 2 "PUT /api/v1/purchase/contract/{id} 状态码"
  assert_body_code 200 "PUT /api/v1/purchase/contract/{id} 业务码"

  # 验证修改后的值
  call GET "/api/v1/purchase/contract/$CREATED_PURCHASE_CONTRACT_ID"
  local contract_name
  contract_name=$(cat /tmp/zwi_body 2>/dev/null | grep -oE '"contractName"\s*:\s*"[^"]+"' | head -1 | sed -E 's/.*"contractName"\s*:\s*"//;s/"$//')
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [ "$contract_name" = "L3测试采购合同-已修改" ]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] 更新后合同名称正确"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] 更新后合同名称不符 (实际: $contract_name)"
  fi
}

test_get_purchase_contract_details() {
  log "▶ 测试：查询采购合同明细"
  if [ -z "$CREATED_PURCHASE_CONTRACT_ID" ]; then
    log "  SKIP: 无采购合同ID"; return 0
  fi
  call GET "/api/v1/purchase/contract/$CREATED_PURCHASE_CONTRACT_ID/details"
  assert_http 2 "GET /api/v1/purchase/contract/{id}/details 状态码"
  assert_body_code 200 "GET /api/v1/purchase/contract/{id}/details 业务码"
}

test_submit_purchase_contract() {
  log "▶ 测试：提交采购合同审批"
  if [ -z "$CREATED_PURCHASE_CONTRACT_ID" ]; then
    log "  SKIP: 无采购合同ID"; return 0
  fi
  call POST "/api/v1/purchase/contract/$CREATED_PURCHASE_CONTRACT_ID/submit"
  assert_http 2 "POST /api/v1/purchase/contract/{id}/submit 状态码"
  assert_body_code 200 "POST /api/v1/purchase/contract/{id}/submit 业务码"
}

test_delete_purchase_contract() {
  log "▶ 测试：删除采购合同"
  if [ -z "$CREATED_PURCHASE_CONTRACT_ID" ]; then
    log "  SKIP: 无采购合同ID"; return 0
  fi
  call DELETE "/api/v1/purchase/contract/$CREATED_PURCHASE_CONTRACT_ID"
  assert_http 2 "DELETE /api/v1/purchase/contract/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/purchase/contract/{id} 业务码"
  CREATED_PURCHASE_CONTRACT_ID=""
}

# ===========================================================================
# 测试用例 — 询价单（采购申请流程入口）
# ===========================================================================

test_create_inquiry() {
  log "▶ 测试：创建询价单"
  call POST "/api/v1/purchase/inquiry" '{"title":"L3测试询价-钢筋采购","inviteMode":"PUBLIC","bidMode":"LOWEST","description":"自动化测试询价单","requirements":"Q235B HRB400 钢筋","materialSummary":"钢筋100吨"}'
  assert_http 2 "POST /api/v1/purchase/inquiry 状态码"
  assert_body_code 200 "POST /api/v1/purchase/inquiry 业务码"
}

test_page_inquiry() {
  log "▶ 测试：分页查询询价单"
  sleep 1
  call GET "/api/v1/purchase/inquiry/page?page=1&size=10"
  assert_http 2 "GET /api/v1/purchase/inquiry/page 状态码"
  assert_body_code 200 "GET /api/v1/purchase/inquiry/page 业务码"
  assert_has_field "records" "询价单分页含 records"
  assert_has_field "total" "询价单分页含 total"

  # 提取 ID
  CREATED_INQUIRY_ID=$(extract_first_record_id)
  if [ -z "$CREATED_INQUIRY_ID" ]; then
    log "  WARN: 未能获取询价单ID"
  else
    log "  获取询价单 ID=$CREATED_INQUIRY_ID"
  fi
}

test_get_inquiry_detail() {
  log "▶ 测试：查询询价单详情"
  if [ -z "$CREATED_INQUIRY_ID" ]; then
    log "  SKIP: 无询价单ID"; return 0
  fi
  call GET "/api/v1/purchase/inquiry/$CREATED_INQUIRY_ID"
  assert_http 2 "GET /api/v1/purchase/inquiry/{id} 状态码"
  assert_body_code 200 "GET /api/v1/purchase/inquiry/{id} 业务码"
  assert_has_field "title" "详情含 title"
}

test_update_inquiry() {
  log "▶ 测试：更新询价单"
  if [ -z "$CREATED_INQUIRY_ID" ]; then
    log "  SKIP: 无询价单ID"; return 0
  fi
  call PUT "/api/v1/purchase/inquiry/$CREATED_INQUIRY_ID" '{"title":"L3测试询价-钢筋采购-已修改","inviteMode":"PUBLIC","bidMode":"COMPREHENSIVE","description":"自动化测试询价单-修改","requirements":"Q235B HRB400E 钢筋","materialSummary":"钢筋120吨"}'
  assert_http 2 "PUT /api/v1/purchase/inquiry/{id} 状态码"
  assert_body_code 200 "PUT /api/v1/purchase/inquiry/{id} 业务码"
}

test_publish_inquiry() {
  log "▶ 测试：发布询价单"
  if [ -z "$CREATED_INQUIRY_ID" ]; then
    log "  SKIP: 无询价单ID"; return 0
  fi
  call POST "/api/v1/purchase/inquiry/$CREATED_INQUIRY_ID/publish"
  assert_http 2 "POST /api/v1/purchase/inquiry/{id}/publish 状态码"
  assert_body_code 200 "POST /api/v1/purchase/inquiry/{id}/publish 业务码"
}

test_get_inquiry_quotations() {
  log "▶ 测试：查询询价单报价列表"
  if [ -z "$CREATED_INQUIRY_ID" ]; then
    log "  SKIP: 无询价单ID"; return 0
  fi
  call GET "/api/v1/purchase/inquiry/$CREATED_INQUIRY_ID/quotations"
  assert_http 2 "GET /api/v1/purchase/inquiry/{id}/quotations 状态码"
  assert_body_code 200 "GET /api/v1/purchase/inquiry/{id}/quotations 业务码"
}

test_delete_inquiry() {
  log "▶ 测试：删除询价单"
  if [ -z "$CREATED_INQUIRY_ID" ]; then
    log "  SKIP: 无询价单ID"; return 0
  fi
  call DELETE "/api/v1/purchase/inquiry/$CREATED_INQUIRY_ID"
  assert_http 2 "DELETE /api/v1/purchase/inquiry/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/purchase/inquiry/{id} 业务码"
  CREATED_INQUIRY_ID=""
}

test_get_purchase_nonexistent() {
  log "▶ 测试：查询不存在的采购合同"
  call GET "/api/v1/purchase/contract/999999999"
  local code
  code=$(cat /tmp/zwi_last_code 2>/dev/null || echo "000")
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [[ "$code" =~ ^[2-4][0-9][0-9]$ ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] GET 不存在采购合同无 5xx (HTTP $code)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] GET 不存在采购合同返回 5xx (HTTP $code)"
  fi
}

# ===========================================================================
# 主流程
# ===========================================================================
main() {
  echo ""
  log "═══ L3 API 接口测试：采购模块 (test-api-purchase.sh) ═══"
  log "时间: $(date '+%Y-%m-%d %H:%M:%S') | 服务: $BASE"
  echo ""

  # 确保登录
  login || { log "登录失败，无法执行测试"; exit 1; }

  # --- 采购合同 ---
  echo ""
  log "─── 采购合同接口测试 ───"
  test_create_purchase_contract
  test_page_purchase_contract
  test_page_purchase_contract_by_project
  test_page_purchase_contract_by_status
  test_get_purchase_contract_detail
  test_update_purchase_contract
  test_get_purchase_contract_details
  test_submit_purchase_contract
  test_delete_purchase_contract

  # --- 询价单 ---
  echo ""
  log "─── 询价单接口测试 ───"
  test_create_inquiry
  test_page_inquiry
  test_get_inquiry_detail
  test_update_inquiry
  test_publish_inquiry
  test_get_inquiry_quotations
  test_delete_inquiry

  # --- 边界测试 ---
  echo ""
  log "─── 边界测试 ───"
  test_get_purchase_nonexistent

  # 日志核对
  echo ""
  log "▶ 后端日志核对"
  check_logs 120

  # 输出汇总
  report_summary
  exit $?
}

main "$@"
