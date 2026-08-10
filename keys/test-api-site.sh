#!/usr/bin/env bash
###############################################################################
# test-api-site.sh — L3 API 接口测试：现场管理模块（阶段四批 3）
#
# 覆盖端点（依据 zw-site 9 个 Controller 逐行核对）：
#   - /api/v1/site/construction-log  施工日志：CRUD 闭环（按 logDate 范围+productionRecord 查回）
#   - /api/v1/site/schedule          进度：计划分页/计划树契约 + 计划 CRUD 闭环（按 taskName 查回）+ 反馈分页
#   - /api/v1/site/inspection        检查：分页契约 + 详情/结果/指派不存在负向
#   - /api/v1/site/completion        竣工验收：分页契约 + 不存在提交负向（无删除端点，不创建数据）
#   - /api/v1/site/sign              签到：记录/统计/配置只读契约（写入类不执行，避免残留）
#   - /api/v1/site/rectification     整改：不存在检查提交/不存在整改审批负向
#   - /api/v1/site/reminder-config   催办配置：GET 契约（PUT 会改真实租户配置，不执行）
#   - /api/v1/site/reminder-logs|stats 催办日志/统计：只读契约
#   - /api/v1/inspection-schemes     检查方案：列表契约 + 不存在方案检查项负向 + 不存在检查关联方案负向
#
# 设计要点：
#   - 无删除端点的资源（completion/feedback/sign/出入库类）只做契约+负向，不创建数据，零残留
#   - 标识字段全部 ASCII（taskName/productionRecord 带时间戳后缀）
#   - 依赖 verify-base.sh 登录基座；jq 断言规范同阶段四批 1/批 2
###############################################################################
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/verify-base.sh" login 2>/dev/null || true

PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0
TS_SUFFIX=$(date +%s)
TODAY=$(date +%F)
THIS_MONTH=$(date +%Y-%m)

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
  log "现场管理模块 API 测试汇总"
  echo "═══════════════════════════════════════════════════════════"
  log "  通过: $PASS_COUNT"
  log "  失败: $FAIL_COUNT"
  log "  总计: $TOTAL_COUNT"
  echo "═══════════════════════════════════════════════════════════"
  [ "$FAIL_COUNT" -eq 0 ]
}

log "========== L3 现场管理模块 API 测试开始 =========="

# ---------- 只读分页契约 ----------
call GET "/api/v1/site/construction-log/page?page=1&size=5"
assert_http 2 "施工日志-分页 HTTP"
assert_jq "$PAGE_EXPR" "施工日志-分页结构"

call GET "/api/v1/site/schedule/page?page=1&size=5"
assert_http 2 "进度计划-分页 HTTP"
assert_jq "$PAGE_EXPR" "进度计划-分页结构"

call GET "/api/v1/site/schedule/feedback/page?page=1&size=5"
assert_http 2 "进度反馈-分页 HTTP"
assert_jq "$PAGE_EXPR" "进度反馈-分页结构"

call GET "/api/v1/site/inspection/page?page=1&size=5"
assert_http 2 "质量检查-分页 HTTP"
assert_jq "$PAGE_EXPR" "质量检查-分页结构"

call GET "/api/v1/site/completion/page?page=1&size=5"
assert_http 2 "竣工验收-分页 HTTP"
assert_jq "$PAGE_EXPR" "竣工验收-分页结构"

call GET "/api/v1/inspection-schemes?inspectionType=QUALITY&page=1&size=5"
assert_http 2 "检查方案-列表 HTTP"
assert_jq "$PAGE_EXPR" "检查方案-列表结构"

# ---------- 取真实项目ID（后续闭环依赖） ----------
call GET "/api/v1/project/page?page=1&size=1"
PROJECT_ID=$(jq -r '.data.records[0].id // empty' /tmp/zwi_body 2>/dev/null)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$PROJECT_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 取到真实项目ID: $PROJECT_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 无法取到项目ID（种子数据缺失），后续闭环无法执行"
fi

# ---------- 施工日志 CRUD 闭环（按日期范围+productionRecord 查回） ----------
if [ -n "$PROJECT_ID" ]; then
  call POST "/api/v1/site/construction-log" "{\"projectId\":$PROJECT_ID,\"logDate\":\"$TODAY\",\"weather\":\"Sunny\",\"workerCount\":10,\"productionRecord\":\"L3Log$TS_SUFFIX\"}"
  assert_http 2 "施工日志-创建 HTTP"
  assert_body_code 200 "施工日志-创建业务码"

  call GET "/api/v1/site/construction-log/page?page=1&size=50&projectId=$PROJECT_ID&startDate=$TODAY&endDate=$TODAY"
  LOG_ID=$(jq -r --arg pr "L3Log$TS_SUFFIX" '.data.records[] | select(.productionRecord==$pr) | .id' /tmp/zwi_body 2>/dev/null | head -1)
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [ -n "$LOG_ID" ]; then
    PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 施工日志-查回ID: $LOG_ID"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 施工日志-创建后查回失败"
  fi

  if [ -n "$LOG_ID" ]; then
    call PUT "/api/v1/site/construction-log/$LOG_ID" "{\"projectId\":$PROJECT_ID,\"logDate\":\"$TODAY\",\"weather\":\"Cloudy\",\"workerCount\":20,\"productionRecord\":\"L3Log$TS_SUFFIX\"}"
    assert_http 2 "施工日志-更新 HTTP"
    assert_body_code 200 "施工日志-更新业务码"

    call DELETE "/api/v1/site/construction-log/$LOG_ID"
    assert_http 2 "施工日志-删除 HTTP"
    assert_body_code 200 "施工日志-删除业务码"

    # 负向：删除后再编辑必须拒绝
    call PUT "/api/v1/site/construction-log/$LOG_ID" "{\"projectId\":$PROJECT_ID,\"logDate\":\"$TODAY\",\"productionRecord\":\"L3LogX$TS_SUFFIX\"}"
    assert_body_not_success "施工日志-删除后编辑被拒绝"
  fi

  # ---------- 进度计划 CRUD 闭环（按 taskName 查回） ----------
  call POST "/api/v1/site/schedule/plan" "{\"projectId\":$PROJECT_ID,\"taskName\":\"L3Task$TS_SUFFIX\",\"planStartDate\":\"$TODAY\",\"planEndDate\":\"$TODAY\"}"
  assert_http 2 "进度计划-创建 HTTP"
  assert_body_code 200 "进度计划-创建业务码"

  call GET "/api/v1/site/schedule/page?page=1&size=20&taskName=L3Task$TS_SUFFIX"
  PLAN_ID=$(jq -r --arg tn "L3Task$TS_SUFFIX" '.data.records[] | select(.taskName==$tn) | .id' /tmp/zwi_body 2>/dev/null | head -1)
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [ -n "$PLAN_ID" ]; then
    PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 进度计划-查回ID: $PLAN_ID"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 进度计划-创建后查回失败"
  fi

  if [ -n "$PLAN_ID" ]; then
    call PUT "/api/v1/site/schedule/plan/$PLAN_ID" "{\"projectId\":$PROJECT_ID,\"taskName\":\"L3TaskU$TS_SUFFIX\",\"planStartDate\":\"$TODAY\",\"planEndDate\":\"$TODAY\",\"progress\":10}"
    assert_http 2 "进度计划-更新 HTTP"
    assert_body_code 200 "进度计划-更新业务码"

    call DELETE "/api/v1/site/schedule/plan/$PLAN_ID"
    assert_http 2 "进度计划-删除 HTTP"
    assert_body_code 200 "进度计划-删除业务码"

    # 负向：删除后再编辑必须拒绝
    call PUT "/api/v1/site/schedule/plan/$PLAN_ID" "{\"projectId\":$PROJECT_ID,\"taskName\":\"L3TaskX$TS_SUFFIX\"}"
    assert_body_not_success "进度计划-删除后编辑被拒绝"
  fi

  # ---------- 进度计划树：按项目查询（列表契约） ----------
  call GET "/api/v1/site/schedule/plan/$PROJECT_ID"
  assert_http 2 "进度计划树-查询 HTTP"
  assert_jq '.code==200 and (.data|type=="array")' "进度计划树-返回数组"

  # ---------- 签到只读契约（写入类不执行，避免残留签到记录） ----------
  call GET "/api/v1/site/sign/records?projectId=$PROJECT_ID&month=$THIS_MONTH"
  assert_http 2 "签到记录-查询 HTTP"
  assert_jq '.code==200 and (.data|type=="array")' "签到记录-返回数组"

  call GET "/api/v1/site/sign/statistics?projectId=$PROJECT_ID&month=$THIS_MONTH"
  assert_http 2 "签到统计-查询 HTTP"
  assert_body_code 200 "签到统计-查询业务码"

  call GET "/api/v1/site/sign/config?projectId=$PROJECT_ID"
  assert_http 2 "签到配置-查询 HTTP"
  assert_body_code 200 "签到配置-查询业务码"

  # ---------- 催办日志/统计只读契约 ----------
  call GET "/api/v1/site/reminder-logs/999999999"
  assert_http 2 "催办日志-查询 HTTP"
  assert_jq '.code==200 and (.data|type=="array")' "催办日志-返回数组（无记录为空数组）"

  call GET "/api/v1/site/reminder-stats/$PROJECT_ID"
  assert_http 2 "催办统计-查询 HTTP"
  assert_jq '.code==200 and (.data|type=="object")' "催办统计-返回对象"

  # ---------- 催办配置：仅 GET 契约（PUT 会修改真实租户配置，不执行） ----------
  call GET "/api/v1/site/reminder-config"
  assert_http 2 "催办配置-查询 HTTP"
  assert_body_code 200 "催办配置-查询业务码"
fi

# ---------- 检查负向：不存在的检查记录必须拒绝 ----------
call GET "/api/v1/site/inspection/999999999"
assert_body_not_success "检查-不存在详情被拒绝"

call POST "/api/v1/site/inspection/999999999/results" "{}"
assert_body_not_success "检查-不存在记录提交结果被拒绝"

call POST "/api/v1/site/inspection/999999999/assign" "{\"responsiblePersonId\":1,\"rectificationDeadline\":\"$TODAY\"}"
assert_body_not_success "检查-不存在记录指派整改被拒绝"

# ---------- 竣工验收负向（无删除端点，不创建数据） ----------
call POST "/api/v1/site/completion/999999999/submit" ""
assert_body_not_success "竣工验收-不存在记录提交被拒绝"

# ---------- 整改负向：不存在的检查/整改记录必须拒绝 ----------
call POST "/api/v1/site/rectification/999999999/submit" "{}"
assert_body_not_success "整改-不存在检查记录提交被拒绝"

call POST "/api/v1/site/rectification/999999999/approve" ""
assert_body_not_success "整改-不存在整改记录审批被拒绝"

# ---------- 检查方案负向 ----------
call GET "/api/v1/inspection-schemes/999999999/items"
assert_body_not_success "检查方案-不存在方案检查项被拒绝"

call POST "/api/v1/inspections/999999999/apply-scheme" "{\"schemeId\":999999999}"
assert_body_not_success "检查方案-不存在检查关联方案被拒绝"

report_summary
