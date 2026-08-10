#!/usr/bin/env bash
###############################################################################
# test-api-workflow.sh — L3 API 接口测试：工作流模块（阶段四批 2）
#
# 覆盖端点（依据 zw-workflow 6 个 Controller 逐行核对）：
#   - /api/v1/workflow/approval      待办/已办/我发起（分页契约）+ start/complete 负向 + 催办次数
#   - /api/v1/workflow/process       流程定义列表
#   - /api/v1/workflow/business-type 业务类型树
#   - /api/v1/workflow/urge-config   催办配置查询
#   - /api/v1/workflow/rollback      回滚记录分页
#   - /api/v1/workflow/delegate      我的委托/生效委托/委托给我
#
# 设计要点：
#   - 完整审批流转（start→complete）依赖租户已部署流程定义，属 L4 生命周期范畴，
#     本脚本用负向断言验证非法 start/complete 必须被拒（不得静默 200）
#   - 待办/已办对当前登录用户可能为空，断言只校验分页契约结构
#   - 依赖 verify-base.sh 登录基座；jq 断言规范同阶段四批 1
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

# 分页结构契约：code=200 + records 数组 + total 数字或字符串（雪花 Long→String 序列化）
PAGE_EXPR='.code==200 and (.data.records|type=="array") and ((.data.total|type=="number") or (.data.total|type=="string"))'

report_summary() {
  echo ""
  echo "═══════════════════════════════════════════════════════════"
  log "工作流模块 API 测试汇总"
  echo "═══════════════════════════════════════════════════════════"
  log "  通过: $PASS_COUNT"
  log "  失败: $FAIL_COUNT"
  log "  总计: $TOTAL_COUNT"
  echo "═══════════════════════════════════════════════════════════"
  [ "$FAIL_COUNT" -eq 0 ]
}

log "========== L3 工作流模块 API 测试开始 =========="

# ---------- 待办 / 已办 / 我发起 ----------
call GET "/api/v1/workflow/approval/todo?page=1&size=5"
assert_http 2 "审批待办-分页 HTTP"
assert_jq "$PAGE_EXPR" "审批待办-分页结构"

call GET "/api/v1/workflow/approval/done?page=1&size=5"
assert_http 2 "审批已办-分页 HTTP"
assert_jq "$PAGE_EXPR" "审批已办-分页结构"

call GET "/api/v1/workflow/approval/my-initiated?page=1&size=5"
assert_http 2 "我发起的流程-分页 HTTP"
assert_jq "$PAGE_EXPR" "我发起的流程-分页结构"

# 负向：非法业务类型发起流程必须被拒，不得静默成功
call POST "/api/v1/workflow/approval/start" "{\"businessType\":\"L3_NONEXIST\",\"businessId\":999999,\"processKey\":\"l3_no_such_process\"}"
assert_body_not_success "发起流程-非法业务类型被拒绝"

# 负向：办理不存在的任务必须被拒
call POST "/api/v1/workflow/approval/complete" "{\"taskId\":\"l3_no_such_task_999\",\"comment\":\"L3\"}"
assert_body_not_success "办理-不存在的任务被拒绝"

# 催办次数：不存在的任务应返回 0（数字）
call GET "/api/v1/workflow/approval/urge/count/l3_no_such_task_999"
assert_http 2 "催办次数-查询 HTTP"
assert_jq '.code==200 and (.data|type=="number")' "催办次数-返回数字"

# ---------- 流程定义 ----------
call GET "/api/v1/workflow/process"
assert_http 2 "流程定义-列表 HTTP"
assert_jq '.code==200 and (.data|type=="array")' "流程定义-列表为数组"

# ---------- 业务类型树 ----------
call GET "/api/v1/workflow/business-type/tree"
assert_http 2 "业务类型-树 HTTP"
assert_jq '.code==200 and (.data|type=="array")' "业务类型-树为数组"

# ---------- 催办配置 ----------
call GET "/api/v1/workflow/urge-config"
assert_http 2 "催办配置-查询 HTTP"
assert_body_code 200 "催办配置-查询业务码"

# ---------- 回滚记录 ----------
call GET "/api/v1/workflow/rollback/logs?page=1&size=5"
assert_http 2 "回滚记录-分页 HTTP"
assert_jq "$PAGE_EXPR" "回滚记录-分页结构"

# ---------- 审批委托 ----------
call GET "/api/v1/workflow/delegate/my"
assert_http 2 "委托-我的委托 HTTP"
assert_jq '.code==200 and (.data|type=="array")' "委托-我的委托为数组"

call GET "/api/v1/workflow/delegate/active"
assert_http 2 "委托-生效委托 HTTP"
assert_body_code 200 "委托-生效委托业务码"

call GET "/api/v1/workflow/delegate/to-me"
assert_http 2 "委托-委托给我 HTTP"
assert_jq '.code==200 and (.data|type=="array")' "委托-委托给我为数组"

report_summary
