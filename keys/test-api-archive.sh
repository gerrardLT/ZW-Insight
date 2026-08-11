#!/usr/bin/env bash
###############################################################################
# test-api-archive.sh — L3 API 接口测试：档案模块（阶段四批 4）
#
# 覆盖端点（依据 zw-archive ArchiveController 逐行核对，档案为只读聚合视图）：
#   - /api/v1/archive/project/{projectId}   项目档案（真实项目ID）
#   - /api/v1/archive/budget/{projectId}    预算档案（真实项目ID）
#   - /api/v1/archive/contract/{contractId} 合同档案（真实施工合同ID）
#   - /api/v1/archive/personnel/{userId}    人事档案（当前登录用户）
#   - /api/v1/archive/other-income-contract / other-expense-contract / office-supply
#     三个分页档案列表契约
#
# 设计要点：
#   - 档案全部只读，无数据写入，零残留
#   - projectId/contractId 从真实分页接口动态获取；依赖实体档案（投标/供应商/
#     材料合同/分包/机械/车辆）需跨模块真实 ID，仅断言 HTTP 契约不强制数据存在
#   - 依赖 verify-base.sh 登录基座；jq 断言规范同阶段四批 1-3
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
  log "档案模块 API 测试汇总"
  echo "═══════════════════════════════════════════════════════════"
  log "  通过: $PASS_COUNT"
  log "  失败: $FAIL_COUNT"
  log "  总计: $TOTAL_COUNT"
  echo "═══════════════════════════════════════════════════════════"
  [ "$FAIL_COUNT" -eq 0 ]
}

log "========== L3 档案模块 API 测试开始 =========="

# ---------- 动态获取真实项目/合同 ID ----------
call GET "/api/v1/project/page?page=1&size=1"
PROJECT_ID=$(jq -r '.data.records[0].id // empty' /tmp/zwi_body 2>/dev/null)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$PROJECT_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 取到真实项目ID: $PROJECT_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 无法取到项目ID（种子数据缺失）"
fi

call GET "/api/v1/contract/page?page=1&size=1"
CONTRACT_ID=$(jq -r '.data.records[0].id // empty' /tmp/zwi_body 2>/dev/null)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$CONTRACT_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 取到真实合同ID: $CONTRACT_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 无法取到合同ID（种子数据缺失）"
fi

# ---------- 实体档案（map 聚合视图） ----------
if [ -n "$PROJECT_ID" ]; then
  call GET "/api/v1/archive/project/$PROJECT_ID"
  assert_http 2 "项目档案 HTTP"
  assert_jq '.code==200 and (.data|type=="object")' "项目档案-数据为对象"

  call GET "/api/v1/archive/budget/$PROJECT_ID"
  assert_http 2 "预算档案 HTTP"
  assert_body_code 200 "预算档案业务码"
fi

if [ -n "$CONTRACT_ID" ]; then
  call GET "/api/v1/archive/contract/$CONTRACT_ID"
  assert_http 2 "合同档案 HTTP"
  assert_body_code 200 "合同档案业务码"
fi

# 人事档案：当前登录用户（admin ID=1 由种子保证）
call GET "/api/v1/archive/personnel/1"
assert_http 2 "人事档案 HTTP"
assert_body_code 200 "人事档案业务码"

# ---------- 分页档案列表契约 ----------
call GET "/api/v1/archive/other-income-contract?page=1&size=5"
assert_http 2 "其它收入合同档案-分页 HTTP"
assert_jq "$PAGE_EXPR" "其它收入合同档案-分页结构"

call GET "/api/v1/archive/other-expense-contract?page=1&size=5"
assert_http 2 "其它支出合同档案-分页 HTTP"
assert_jq "$PAGE_EXPR" "其它支出合同档案-分页结构"

call GET "/api/v1/archive/office-supply?page=1&size=5"
assert_http 2 "办公用品档案-分页 HTTP"
assert_jq "$PAGE_EXPR" "办公用品档案-分页结构"

report_summary
