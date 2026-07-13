#!/usr/bin/env bash
###############################################################################
# test-api-project.sh — L3 API 接口测试：项目模块
#
# 覆盖端点：/api/v1/project
#   - CRUD：创建/分页查询/详情/更新/删除
#   - 审批流：提交 submit
#   - 分页查询：按名称/状态筛选
#   - 成员管理：添加/查询/移除
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
CREATED_PROJECT_ID=""
CREATED_MEMBER_ID=""

# ===========================================================================
# 公共测试函数
# ===========================================================================

# assert_http <expected_code_prefix> <test_name>
#   读取 /tmp/zwi_last_code，与期望前缀比较
#   例: assert_http 2 "创建项目" → 期望 2xx
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
#   验证响应体中 "code" 字段值
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
#   验证响应体中包含指定字段
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
  log "项目模块 API 测试汇总"
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

# extract_id：从响应体提取 data 字段中的 ID（创建接口返回）
extract_id() {
  local val
  val=$(cat /tmp/zwi_body 2>/dev/null | grep -oE '"data"\s*:\s*[0-9]+' | head -1 | grep -oE '[0-9]+$')
  if [ -n "$val" ]; then echo "$val"; return; fi
  val=$(cat /tmp/zwi_body 2>/dev/null | grep -oE '"id"\s*:\s*[0-9]+' | head -1 | grep -oE '[0-9]+$')
  echo "$val"
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
  # 先删成员再删项目（外键依赖）
  if [ -n "$CREATED_MEMBER_ID" ]; then
    call DELETE "/api/v1/project/members/$CREATED_MEMBER_ID" 2>/dev/null
    log "  已清理成员 ID=$CREATED_MEMBER_ID"
  fi
  if [ -n "$CREATED_PROJECT_ID" ]; then
    call DELETE "/api/v1/project/$CREATED_PROJECT_ID" 2>/dev/null
    log "  已清理项目 ID=$CREATED_PROJECT_ID"
  fi
  log "--- 清理完成 ---"
}

# 注册 trap，确保无论脚本如何退出都执行清理
trap cleanup EXIT

# ===========================================================================
# 测试用例
# ===========================================================================

test_create_project() {
  log "▶ 测试：创建项目"
  call POST "/api/v1/project" '{"projectName":"API测试项目-自动化","projectNature":"新建","projectType":"公共建筑","ownerCompanyName":"测试业主公司","signingCompanyName":"测试施工单位","projectOverview":"L3接口自动化测试用项目","projectAddress":"测试地址","contactName":"测试联系人","contactPhone":"13800000001","needTender":0,"budgetAmount":1000000.00}'
  assert_http 2 "POST /api/v1/project 状态码"
  assert_body_code 200 "POST /api/v1/project 业务码"
}

test_page_query() {
  log "▶ 测试：分页查询项目"
  sleep 1
  call GET "/api/v1/project/page?page=1&size=10&projectName=API%E6%B5%8B%E8%AF%95%E9%A1%B9%E7%9B%AE"
  assert_http 2 "GET /api/v1/project/page 状态码"
  assert_body_code 200 "GET /api/v1/project/page 业务码"
  assert_has_field "records" "分页结果含 records 字段"
  assert_has_field "total" "分页结果含 total 字段"

  # 提取项目 ID 用于后续测试
  CREATED_PROJECT_ID=$(extract_first_record_id)
  if [ -z "$CREATED_PROJECT_ID" ]; then
    log "  WARN: 未能获取项目ID，后续测试可能失败"
  else
    log "  获取项目 ID=$CREATED_PROJECT_ID"
  fi
}

test_get_detail() {
  log "▶ 测试：查询项目详情"
  if [ -z "$CREATED_PROJECT_ID" ]; then
    log "  SKIP: 无项目ID"; return 0
  fi
  call GET "/api/v1/project/$CREATED_PROJECT_ID"
  assert_http 2 "GET /api/v1/project/{id} 状态码"
  assert_body_code 200 "GET /api/v1/project/{id} 业务码"
  assert_has_field "projectName" "详情含 projectName"
}

test_update_project() {
  log "▶ 测试：更新项目"
  if [ -z "$CREATED_PROJECT_ID" ]; then
    log "  SKIP: 无项目ID"; return 0
  fi
  call PUT "/api/v1/project/$CREATED_PROJECT_ID" '{"projectName":"API测试项目-已修改","projectNature":"新建","projectType":"公共建筑","ownerCompanyName":"测试业主公司","signingCompanyName":"测试施工单位","projectOverview":"修改后的概述","projectAddress":"测试地址","contactName":"测试联系人","contactPhone":"13800000001","needTender":0,"budgetAmount":1500000.00}'
  assert_http 2 "PUT /api/v1/project/{id} 状态码"
  assert_body_code 200 "PUT /api/v1/project/{id} 业务码"

  # 验证修改后的值
  call GET "/api/v1/project/$CREATED_PROJECT_ID"
  local name
  name=$(cat /tmp/zwi_body 2>/dev/null | grep -oE '"projectName"\s*:\s*"[^"]+"' | head -1 | sed -E 's/.*"projectName"\s*:\s*"//;s/"$//')
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [ "$name" = "API测试项目-已修改" ]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] 更新后名称正确"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] 更新后名称不符 (实际: $name)"
  fi
}

test_submit_project() {
  log "▶ 测试：提交项目审批"
  if [ -z "$CREATED_PROJECT_ID" ]; then
    log "  SKIP: 无项目ID"; return 0
  fi
  call POST "/api/v1/project/$CREATED_PROJECT_ID/submit"
  assert_http 2 "POST /api/v1/project/{id}/submit 状态码"
  assert_body_code 200 "POST /api/v1/project/{id}/submit 业务码"
}

test_page_query_with_status() {
  log "▶ 测试：按状态筛选分页查询"
  call GET "/api/v1/project/page?page=1&size=10&status=DRAFT"
  assert_http 2 "GET /api/v1/project/page?status=DRAFT 状态码"
  assert_body_code 200 "GET /api/v1/project/page?status=DRAFT 业务码"
  assert_has_field "records" "按状态筛选含 records 字段"
}

test_page_query_with_type() {
  log "▶ 测试：按类型筛选分页查询"
  call GET "/api/v1/project/page?page=1&size=10&projectType=%E5%85%AC%E5%85%B1%E5%BB%BA%E7%AD%91"
  assert_http 2 "GET /api/v1/project/page?projectType 状态码"
  assert_body_code 200 "GET /api/v1/project/page?projectType 业务码"
}

test_add_member() {
  log "▶ 测试：添加项目成员"
  if [ -z "$CREATED_PROJECT_ID" ]; then
    log "  SKIP: 无项目ID"; return 0
  fi
  call POST "/api/v1/project/$CREATED_PROJECT_ID/members" "{\"projectId\":$CREATED_PROJECT_ID,\"userId\":1,\"roleType\":\"PROJECT_MANAGER\"}"
  assert_http 2 "POST /api/v1/project/{id}/members 状态码"
  assert_body_code 200 "POST /api/v1/project/{id}/members 业务码"
}

test_get_members() {
  log "▶ 测试：查询项目成员"
  if [ -z "$CREATED_PROJECT_ID" ]; then
    log "  SKIP: 无项目ID"; return 0
  fi
  call GET "/api/v1/project/$CREATED_PROJECT_ID/members"
  assert_http 2 "GET /api/v1/project/{id}/members 状态码"
  assert_body_code 200 "GET /api/v1/project/{id}/members 业务码"

  # 提取成员 ID 用于清理
  CREATED_MEMBER_ID=$(extract_first_record_id)
  if [ -n "$CREATED_MEMBER_ID" ]; then
    log "  获取成员 ID=$CREATED_MEMBER_ID"
  fi
}

test_remove_member() {
  log "▶ 测试：移除项目成员"
  if [ -z "$CREATED_MEMBER_ID" ]; then
    log "  SKIP: 无成员ID"; return 0
  fi
  call DELETE "/api/v1/project/members/$CREATED_MEMBER_ID"
  assert_http 2 "DELETE /api/v1/project/members/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/project/members/{id} 业务码"
  # 成员已删除，清理时不重复删
  CREATED_MEMBER_ID=""
}

test_delete_project() {
  log "▶ 测试：删除项目"
  if [ -z "$CREATED_PROJECT_ID" ]; then
    log "  SKIP: 无项目ID"; return 0
  fi
  call DELETE "/api/v1/project/$CREATED_PROJECT_ID"
  assert_http 2 "DELETE /api/v1/project/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/project/{id} 业务码"
  # 项目已删除，清理时不重复删
  CREATED_PROJECT_ID=""
}

test_get_nonexistent() {
  log "▶ 测试：查询不存在的项目"
  call GET "/api/v1/project/999999999"
  # 允许 2xx（业务层返回空）或 4xx 均可，关键是不能 5xx
  local code
  code=$(cat /tmp/zwi_last_code 2>/dev/null || echo "000")
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [[ "$code" =~ ^[2-4][0-9][0-9]$ ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] GET 不存在项目无 5xx (HTTP $code)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] GET 不存在项目返回 5xx (HTTP $code)"
  fi
}

# ===========================================================================
# 主流程
# ===========================================================================
main() {
  echo ""
  log "═══ L3 API 接口测试：项目模块 (test-api-project.sh) ═══"
  log "时间: $(date '+%Y-%m-%d %H:%M:%S') | 服务: $BASE"
  echo ""

  # 确保登录
  login || { log "登录失败，无法执行测试"; exit 1; }

  # 按 CRUD + 审批 + 分页 顺序执行
  test_create_project
  test_page_query
  test_get_detail
  test_update_project
  test_submit_project
  test_page_query_with_status
  test_page_query_with_type
  test_add_member
  test_get_members
  test_remove_member
  test_delete_project
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
