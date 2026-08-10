#!/usr/bin/env bash
###############################################################################
# test-api-budget.sh — L3 API 接口测试：预算模块（阶段四批 2）
#
# 覆盖端点（依据 zw-budget 5 个 Controller 逐行核对）：
#   - /api/v1/budget                 预算编制：分页/详情/明细/按项目查询 + 空体校验负向
#   - /api/v1/budget/change          预算变更：分页/变更轨迹 + 空体校验负向
#   - /api/v1/budget/config          预算管控配置：全量列表/按项目查询
#   - /api/v1/budget/subcategory     费用子类：按大类查询
#   - /api/v1/budget-control-configs 预算控制配置：分页/项目生效配置
#
# 设计要点：
#   - 预算编制/变更涉及工作流审批与金额控制，创建类闭环留给 L2（BLOCK 拦截专项），
#     本脚本聚焦只读契约 + @Valid 参数校验负向断言（空 body 必须被拒，不得静默 200）
#   - 详情/明细依赖真实存在的预算记录，从分页首行动态取 ID，不硬编码
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
  log "预算模块 API 测试汇总"
  echo "═══════════════════════════════════════════════════════════"
  log "  通过: $PASS_COUNT"
  log "  失败: $FAIL_COUNT"
  log "  总计: $TOTAL_COUNT"
  echo "═══════════════════════════════════════════════════════════"
  [ "$FAIL_COUNT" -eq 0 ]
}

log "========== L3 预算模块 API 测试开始 =========="

# ---------- 预算编制 ----------
call GET "/api/v1/budget/page?page=1&size=5"
assert_http 2 "预算编制-分页 HTTP"
assert_jq "$PAGE_EXPR" "预算编制-分页结构"

BUDGET_ID=$(jq -r '.data.records[0].id // empty' /tmp/zwi_body 2>/dev/null)
BUDGET_PROJECT_ID=$(jq -r '.data.records[0].projectId // empty' /tmp/zwi_body 2>/dev/null)

if [ -n "$BUDGET_ID" ]; then
  call GET "/api/v1/budget/$BUDGET_ID"
  assert_http 2 "预算编制-详情 HTTP"
  assert_jq '.code==200 and (.data.id!=null)' "预算编制-详情含ID"

  call GET "/api/v1/budget/$BUDGET_ID/details"
  assert_http 2 "预算编制-明细 HTTP"
  assert_jq '.code==200 and (.data|type=="array")' "预算编制-明细为数组"
  COST_CATEGORY=$(jq -r '.data[0].costCategory // empty' /tmp/zwi_body 2>/dev/null)
else
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 预算编制分页为空，无法验证详情/明细（种子数据缺失）"
fi

if [ -n "$BUDGET_PROJECT_ID" ]; then
  call GET "/api/v1/budget/project/$BUDGET_PROJECT_ID"
  assert_http 2 "预算编制-按项目查询 HTTP"
  assert_jq '.code==200 and (.data.id!=null)' "预算编制-按项目查询含ID"
fi

# 负向：@Valid 校验——空 body 缺 projectId/budgetType 必须被拒，不得静默 200
call POST "/api/v1/budget" "{}"
assert_body_not_success "预算编制-空体创建被校验拒绝"

# ---------- 预算变更 ----------
call GET "/api/v1/budget/change/page?page=1&size=5"
assert_http 2 "预算变更-分页 HTTP"
assert_jq "$PAGE_EXPR" "预算变更-分页结构"

CHANGE_ID=$(jq -r '.data.records[0].id // empty' /tmp/zwi_body 2>/dev/null)
if [ -n "$CHANGE_ID" ]; then
  call GET "/api/v1/budget/change/$CHANGE_ID"
  assert_http 2 "预算变更-详情 HTTP"
  assert_jq '.code==200 and (.data.id!=null)' "预算变更-详情含ID"

  call GET "/api/v1/budget/change/$CHANGE_ID/details"
  assert_http 2 "预算变更-明细 HTTP"
  assert_jq '.code==200 and (.data|type=="array")' "预算变更-明细为数组"
fi

if [ -n "$BUDGET_PROJECT_ID" ]; then
  call GET "/api/v1/budget/change/trace?projectId=$BUDGET_PROJECT_ID"
  assert_http 2 "预算变更-轨迹 HTTP"
  assert_jq '.code==200 and (.data|type=="array")' "预算变更-轨迹为数组"
fi

# 负向：@Valid 校验——空 body 缺必填字段必须被拒
call POST "/api/v1/budget/change" "{}"
assert_body_not_success "预算变更-空体创建被校验拒绝"

# ---------- 预算管控配置 ----------
call GET "/api/v1/budget/config/list"
assert_http 2 "预算管控配置-全量列表 HTTP"
assert_jq '.code==200 and (.data|type=="array")' "预算管控配置-列表为数组"

if [ -n "$BUDGET_PROJECT_ID" ]; then
  call GET "/api/v1/budget/config/$BUDGET_PROJECT_ID"
  assert_http 2 "预算管控配置-按项目查询 HTTP"
  assert_body_code 200 "预算管控配置-按项目业务码"
fi

# ---------- 费用子类（costCategory 取自预算明细首行真实值，不硬编码枚举） ----------
if [ -n "${COST_CATEGORY:-}" ]; then
  call GET "/api/v1/budget/subcategory/$COST_CATEGORY"
  assert_http 2 "费用子类-按大类查询 HTTP"
  assert_jq '.code==200 and (.data|type=="array")' "费用子类-返回为数组"
fi

# ---------- 预算控制配置 ----------
call GET "/api/v1/budget-control-configs?page=1&size=5"
assert_http 2 "预算控制配置-分页 HTTP"
assert_jq "$PAGE_EXPR" "预算控制配置-分页结构"

if [ -n "$BUDGET_PROJECT_ID" ]; then
  call GET "/api/v1/budget-control-configs/project/$BUDGET_PROJECT_ID"
  assert_http 2 "预算控制配置-项目生效配置 HTTP"
  assert_body_code 200 "预算控制配置-项目生效业务码"
fi

report_summary
