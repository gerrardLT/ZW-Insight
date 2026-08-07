#!/usr/bin/env bash
###############################################################################
# test-api-labor.sh — L3 API 接口测试：劳务模块
#
# 覆盖端点：
#   - /api/v1/labor/contract     劳务合同 CRUD + 提交审批
#   - /api/v1/labor/team         班组 CRUD + 状态变更
#   - /api/v1/labor/roster       劳务花名册分页查询
#   - /api/v1/labor/work-order   派工单分页查询
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
CREATED_TEAM_ID=""

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
  actual=$(cat /tmp/zwi_body 2>/dev/null | grep -oE '"code"\s*:\s*\"?[0-9]+' | head -1 | grep -oE '[0-9]+$')
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

# assert_jq：jq 字段结构断言（阶段二 2.4 契约强化：从状态码升级为结构校验）
# jq 表达式须求值为 true/false；jq 缺失或表达式不满足一律记 FAIL，不静默降级
assert_jq() {
  local expr="$1" test_name="$2" result
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if ! command -v jq >/dev/null 2>&1; then
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] $test_name (jq 未安装)"
    return 1
  fi
  result=$(jq -e "$expr" /tmp/zwi_body 2>/dev/null | head -1)
  if [ "$result" = "true" ]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] $test_name"
    return 0
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] $test_name (jq 表达式不满足: $expr)"
    return 1
  fi
}

report_summary() {
  echo ""
  echo "═══════════════════════════════════════════════════════════"
  log "劳务模块 API 测试汇总"
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
  val=$(cat /tmp/zwi_body 2>/dev/null | grep -oE '"data"\s*:\s*\"?[0-9]+' | head -1 | grep -oE '[0-9]+$')
  if [ -n "$val" ]; then echo "$val"; return; fi
  val=$(cat /tmp/zwi_body 2>/dev/null | grep -oE '"id"\s*:\s*\"?[0-9]+' | head -1 | grep -oE '[0-9]+$')
  echo "$val"
}

extract_first_record_id() {
  cat /tmp/zwi_body 2>/dev/null | grep -oE '"id"\s*:\s*\"?[0-9]+' | head -1 | grep -oE '[0-9]+$'
}

# ===========================================================================
# 清理逻辑
# ===========================================================================
cleanup() {
  log "--- 清理测试数据 ---"
  if [ -n "$CREATED_TEAM_ID" ]; then
    call DELETE "/api/v1/labor/team/$CREATED_TEAM_ID" 2>/dev/null
    log "  已清理班组 ID=$CREATED_TEAM_ID"
  fi
  if [ -n "$CREATED_CONTRACT_ID" ]; then
    call DELETE "/api/v1/labor/contract/$CREATED_CONTRACT_ID" 2>/dev/null
    log "  已清理劳务合同 ID=$CREATED_CONTRACT_ID"
  fi
  log "--- 清理完成 ---"
}

trap cleanup EXIT

# ===========================================================================
# 测试用例：劳务合同
# ===========================================================================

test_contract_create() {
  log "▶ 测试：创建劳务合同（预算控制拦截验证）"
  # 种子项目 90001 的 LABOR 科目已发生额（合同1000万+已批付款400万）超预算 1200 万，
  # 按真实业务规则 BLOCK 模式必被拦截：此处断言预算控制真实生效（非绕过）
  call POST "/api/v1/labor/contract" '{"projectId":90001,"contractName":"测试劳务合同-自动化","contractNo":"LC-AUTO-001","teamName":"测试班组","contractAmount":80000.00,"startDate":"2025-07-01","endDate":"2025-12-31","remark":"L3接口自动化测试"}'
  assert_http 2 "POST /api/v1/labor/contract 状态码"
  assert_body_code 500 "POST /api/v1/labor/contract 预算超支拦截（业务码500）"
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if grep -q '已超预算' /tmp/zwi_body 2>/dev/null; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] 预算拦截消息正确：已超预算"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] 预算拦截消息缺失（期望含'已超预算'）"
  fi
}

test_contract_page() {
  log "▶ 测试：分页查询劳务合同"
  call GET "/api/v1/labor/contract/page?page=1&size=10"
  assert_http 2 "GET /api/v1/labor/contract/page 状态码"
  assert_body_code 200 "GET /api/v1/labor/contract/page 业务码"
  assert_has_field "records" "劳务合同分页含 records 字段"
  assert_has_field "total" "劳务合同分页含 total 字段"
  # 阶段二 2.4 契约强化：jq 字段结构断言
  assert_jq '.code == 200' "分页响应业务码200(jq)"
  assert_jq '(.data.records | type) == "array"' "分页records为数组(jq)"
  # 契约说明：后端 JacksonConfig 全局将 Long/long 序列化为 String（防前端精度丢失），
  # PageResult.total(long) 在 JSON 中恒为字符串，属生产有意设计
  assert_jq '(.data.total | type) == "string" or (.data.total | type) == "number"' "分页total为数字或数字字符串(jq)"

  # 创建测试是预算拦截验证（未实际产生合同），分页首条为种子 EFFECTIVE 合同不可编辑/提交。
  # 故新建一个草稿（不传金额以跳过预算切面）供详情/更新/提交测试使用。
  call POST "/api/v1/labor/contract" '{"projectId":90001,"contractName":"流转测试劳务合同","contractNo":"LC-FLOW-001","teamName":"测试班组","startDate":"2025-07-01","endDate":"2025-12-31","remark":"流转测试"}'
  sleep 1
  call GET "/api/v1/labor/contract/page?page=1&size=1&status=DRAFT"
  CREATED_CONTRACT_ID=$(extract_first_record_id)
  if [ -n "$CREATED_CONTRACT_ID" ]; then
    log "  获取草稿劳务合同 ID=$CREATED_CONTRACT_ID"
  else
    log "  WARN: 未能获取草稿劳务合同ID，后续测试可能失败"
  fi
}

test_contract_detail() {
  log "▶ 测试：查询劳务合同详情"
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID"; return 0
  fi
  call GET "/api/v1/labor/contract/$CREATED_CONTRACT_ID"
  assert_jq '.code == 200 and (.data | has("id"))' "详情响应含id(jq)"
  assert_http 2 "GET /api/v1/labor/contract/{id} 状态码"
  assert_body_code 200 "GET /api/v1/labor/contract/{id} 业务码"
}

test_contract_update() {
  log "▶ 测试：更新劳务合同"
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID"; return 0
  fi
  call PUT "/api/v1/labor/contract/$CREATED_CONTRACT_ID" '{"projectId":1,"contractName":"测试劳务合同-已修改","contractNo":"LC-AUTO-001","teamName":"测试班组-修改","contractAmount":90000.00,"startDate":"2025-07-01","endDate":"2025-12-31","remark":"修改后的劳务合同"}'
  assert_http 2 "PUT /api/v1/labor/contract/{id} 状态码"
  assert_body_code 200 "PUT /api/v1/labor/contract/{id} 业务码"
}

test_contract_submit() {
  log "▶ 测试：提交劳务合同审批"
  if [ -z "$CREATED_CONTRACT_ID" ]; then
    log "  SKIP: 无合同ID"; return 0
  fi
  call POST "/api/v1/labor/contract/$CREATED_CONTRACT_ID/submit"
  assert_http 2 "POST /api/v1/labor/contract/{id}/submit 状态码"
  assert_body_code 200 "POST /api/v1/labor/contract/{id}/submit 业务码"
}

# ===========================================================================
# 测试用例：班组管理
# ===========================================================================

test_team_create() {
  log "▶ 测试：创建班组"
  call POST "/api/v1/labor/team" '{"projectId":1,"teamName":"测试班组-自动化","leaderName":"张三","leaderPhone":"13800000002","teamType":"木工班","remark":"L3接口自动化测试班组"}'
  assert_http 2 "POST /api/v1/labor/team 状态码"
  assert_body_code 200 "POST /api/v1/labor/team 业务码"
}

test_team_page() {
  log "▶ 测试：分页查询班组"
  call GET "/api/v1/labor/team/page?page=1&size=10"
  assert_http 2 "GET /api/v1/labor/team/page 状态码"
  assert_body_code 200 "GET /api/v1/labor/team/page 业务码"
  assert_has_field "records" "班组分页含 records 字段"

  CREATED_TEAM_ID=$(extract_first_record_id)
  if [ -n "$CREATED_TEAM_ID" ]; then
    log "  获取班组 ID=$CREATED_TEAM_ID"
  fi
}

test_team_update() {
  log "▶ 测试：更新班组"
  if [ -z "$CREATED_TEAM_ID" ]; then
    log "  SKIP: 无班组ID"; return 0
  fi
  call PUT "/api/v1/labor/team/$CREATED_TEAM_ID" '{"projectId":1,"teamName":"测试班组-已修改","leaderName":"李四","leaderPhone":"13800000003","teamType":"钢筋班","remark":"修改后的班组"}'
  assert_http 2 "PUT /api/v1/labor/team/{id} 状态码"
  assert_body_code 200 "PUT /api/v1/labor/team/{id} 业务码"
}

test_team_status() {
  log "▶ 测试：变更班组状态"
  if [ -z "$CREATED_TEAM_ID" ]; then
    log "  SKIP: 无班组ID"; return 0
  fi
  call PUT "/api/v1/labor/team/$CREATED_TEAM_ID/status?status=0"
  assert_http 2 "PUT /api/v1/labor/team/{id}/status 状态码"
  assert_body_code 200 "PUT /api/v1/labor/team/{id}/status 业务码"
}

test_team_delete() {
  log "▶ 测试：删除班组"
  if [ -z "$CREATED_TEAM_ID" ]; then
    log "  SKIP: 无班组ID"; return 0
  fi
  call DELETE "/api/v1/labor/team/$CREATED_TEAM_ID"
  assert_http 2 "DELETE /api/v1/labor/team/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/labor/team/{id} 业务码"
  CREATED_TEAM_ID=""
}

# ===========================================================================
# 测试用例：花名册 & 派工单（只读查询）
# ===========================================================================

test_roster_page() {
  log "▶ 测试：分页查询劳务花名册"
  call GET "/api/v1/labor/roster/page?page=1&size=10"
  assert_http 2 "GET /api/v1/labor/roster/page 状态码"
  assert_body_code 200 "GET /api/v1/labor/roster/page 业务码"
  assert_has_field "records" "花名册分页含 records 字段"
}

test_workorder_page() {
  log "▶ 测试：分页查询派工单"
  call GET "/api/v1/labor/work-order/page?page=1&size=10"
  assert_http 2 "GET /api/v1/labor/work-order/page 状态码"
  assert_body_code 200 "GET /api/v1/labor/work-order/page 业务码"
  assert_has_field "records" "派工单分页含 records 字段"
}

# ===========================================================================
# 测试用例：异常场景
# ===========================================================================

test_contract_nonexistent() {
  log "▶ 测试：查询不存在的劳务合同"
  call GET "/api/v1/labor/contract/999999999"
  local code
  code=$(cat /tmp/zwi_last_code 2>/dev/null || echo "000")
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [[ "$code" =~ ^[2-4][0-9][0-9]$ ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] GET 不存在劳务合同无 5xx (HTTP $code)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] GET 不存在劳务合同返回 5xx (HTTP $code)"
  fi
}

test_contract_delete() {
  log "▶ 测试：删除劳务合同（仅草稿可删除）"
  # 方案B：新建草稿再删。故意不传 contractAmount——预算切面在金额为空时直接放行，从而绕开劳务预算额度限制
  call POST "/api/v1/labor/contract" '{"projectId":90001,"contractName":"删除测试劳务合同","contractNo":"LC-DEL-001","teamName":"测试班组","startDate":"2025-07-01","endDate":"2025-12-31","remark":"删除测试"}'
  assert_body_code 200 "POST /api/v1/labor/contract 创建删除用草稿"
  sleep 1
  call GET "/api/v1/labor/contract/page?page=1&size=1&status=DRAFT"
  local DEL_ID=$(extract_first_record_id)
  if [ -z "$DEL_ID" ]; then log "  SKIP: 未取到草稿劳务合同ID"; return 0; fi
  call DELETE "/api/v1/labor/contract/$DEL_ID"
  assert_http 2 "DELETE /api/v1/labor/contract/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/labor/contract/{id} 业务码"
}

# ===========================================================================
# 主流程
# ===========================================================================
main() {
  echo ""
  log "═══ L3 API 接口测试：劳务模块 (test-api-labor.sh) ═══"
  log "时间: $(date '+%Y-%m-%d %H:%M:%S') | 服务: $BASE"
  echo ""

  login || { log "登录失败，无法执行测试"; exit 1; }

  # 劳务合同
  test_contract_create
  test_contract_page
  test_contract_detail
  test_contract_update
  test_contract_submit

  # 班组管理
  test_team_create
  test_team_page
  test_team_update
  test_team_status
  test_team_delete

  # 花名册 & 派工单
  test_roster_page
  test_workorder_page

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
