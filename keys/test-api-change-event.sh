#!/usr/bin/env bash
###############################################################################
# test-api-change-event.sh — L3 API 接口测试：变更事件模块
#
# 覆盖端点：/api/v1/contract/change-event
#   - 分页查询：GET /page
#   - 详情查询：GET /{id}
#   - 统计角标：GET /open-count
#   - 成本累计：GET /approved-cost-delta
#   - 状态流转主链路：
#       1. POST / (登记草稿 DRAFT)
#       2. PUT /{id} (更新草稿)
#       3. POST /{id}/start-assessment (转入评估 ASSESSING)
#       4. POST /{id}/assessment (提交评估 PENDING_APPROVAL)
#       5. POST /{id}/approve (批准实施 APPROVED)
#   - 负向用例：
#       * 不存在事件详情查询拦截
#       * 已批准事件禁止修改与删除
#   - 资源清理：
#       * 创建独立测试草稿并执行 DELETE /{id}
#
# 设计依据：
#   - 契约严格对照 ChangeEventController 与 ChangeEventService
#   - 依赖 verify-base.sh 登录基座；jq 断言结构与业务状态
###############################################################################
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/verify-base.sh" login 2>/dev/null || true

PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0
EVENT_ID=""
CLEANUP_EVENT_ID=""

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

assert_body_not_success() {
  local test_name="$1" actual http_code
  actual=$(grep -oE '"code"\s*:\s*\"?[0-9]+' /tmp/zwi_body 2>/dev/null | head -1 | grep -oE '[0-9]+$')
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

report_summary() {
  echo ""
  echo "═══════════════════════════════════════════════════════════"
  log "变更事件模块 API 测试汇总"
  echo "═══════════════════════════════════════════════════════════"
  log "  通过: $PASS_COUNT"
  log "  失败: $FAIL_COUNT"
  log "  总计: $TOTAL_COUNT"
  echo "═══════════════════════════════════════════════════════════"
  [ "$FAIL_COUNT" -eq 0 ]
}

log "========== L3 变更事件模块 API 测试开始 =========="

# 1. 挑选「有 CBS 成本账户」的真实项目（分页首位可能是无账户的测试残留项目，
#    而变更成本明细校验要求受影响账户真实存在且属于当前项目/租户）
PROJECT_ID=""
ACCOUNT_ID=""
call GET "/api/v1/project/page?page=1&size=5"
# 注意：verify-base 的 call 会覆写 /tmp/zwi_body，分页结果必须先存变量再逐个探测
PAGE_JSON=$(cat /tmp/zwi_body 2>/dev/null)
for _i in 0 1 2 3 4; do
  _pid=$(printf '%s' "$PAGE_JSON" | jq -r ".data.records[$_i].id // empty" 2>/dev/null)
  [ -z "$_pid" ] && break
  call GET "/api/v1/dashboard/project/$_pid/cost-control" >/dev/null 2>&1
  _acct=$(jq -r '.data.accounts[0].accountId // empty' /tmp/zwi_body 2>/dev/null)
  if [ -n "$_acct" ]; then PROJECT_ID=$_pid; ACCOUNT_ID=$_acct; break; fi
done
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$PROJECT_ID" ] && [ -n "$ACCOUNT_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 取到 CBS 项目与成本账户: project=$PROJECT_ID account=$ACCOUNT_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 前 5 个项目均无 CBS 成本账户（种子数据缺失），测试终止"
  report_summary
  exit 1
fi

# 2. 分页查询
call GET "/api/v1/contract/change-event/page?page=1&size=10&projectId=$PROJECT_ID"
assert_http 2 "变更事件分页查询 HTTP 2xx"
assert_body_code 200 "变更事件分页查询业务码 200"
assert_jq '.data.records!=null and (.data.records|type=="array")' "分页返回 records 数组"

# 3. 统计指标查询
call GET "/api/v1/contract/change-event/open-count?projectId=$PROJECT_ID"
assert_http 2 "项目下待处理事件数 HTTP 2xx"
assert_body_code 200 "项目下待处理事件数业务码 200"

call GET "/api/v1/contract/change-event/approved-cost-delta?projectId=$PROJECT_ID"
assert_http 2 "累计已批准成本影响 HTTP 2xx"
assert_body_code 200 "累计已批准成本影响业务码 200"

# 4. 登记变更事件（草稿态 DRAFT）
# affectedAccounts 在创建/更新时挂到事件上（校验：签名合计必须等于评估的 costDelta）
CREATE_BODY=$(cat <<EOF
{
  "projectId": $PROJECT_ID,
  "sourceType": "FIELD_EVENT",
  "title": "测试变更事件-基坑支护局部加固",
  "description": "现场开挖遇溶洞地质异常，需局部增加注浆与预应力锚索",
  "category": "COST_IMPACT",
  "priority": "HIGH",
  "affectedAccounts": [
    {
      "accountId": $ACCOUNT_ID,
      "deltaType": "INCREASE",
      "deltaAmount": 35000.00
    }
  ]
}
EOF
)
call POST "/api/v1/contract/change-event" "$CREATE_BODY"
assert_http 2 "登记变更事件 HTTP 2xx"
assert_body_code 200 "登记变更事件业务码 200"
assert_jq '.data.id!=null and .data.status=="DRAFT"' "创建成功且初始状态为 DRAFT"
EVENT_ID=$(jq -r '.data.id // empty' /tmp/zwi_body 2>/dev/null)

if [ -n "$EVENT_ID" ]; then
  # 5. 详情查询
  call GET "/api/v1/contract/change-event/$EVENT_ID"
  assert_http 2 "变更事件详情 HTTP 2xx"
  assert_body_code 200 "变更事件详情业务码 200"
  # 后端把 Long 序列化为字符串（防 JS 精度丢失），ID 比较必须按字符串
  assert_jq ".data.id==\"$EVENT_ID\" and .data.status==\"DRAFT\"" "详情数据 ID 与状态匹配"

  # 6. 更新草稿信息（保留 affectedAccounts，评估校验要求与 costDelta 签名合计一致）
  UPDATE_BODY=$(cat <<EOF
{
  "projectId": $PROJECT_ID,
  "sourceType": "FIELD_EVENT",
  "title": "测试变更事件-基坑支护局部加固(已核实)",
  "description": "现场开挖遇溶洞地质异常，设计院确认需局部增加注浆与预应力锚索",
  "category": "COST_IMPACT",
  "priority": "HIGH",
  "affectedAccounts": [
    {
      "accountId": $ACCOUNT_ID,
      "deltaType": "INCREASE",
      "deltaAmount": 35000.00
    }
  ]
}
EOF
  )
  call PUT "/api/v1/contract/change-event/$EVENT_ID" "$UPDATE_BODY"
  assert_http 2 "更新变更事件 HTTP 2xx"
  assert_body_code 200 "更新变更事件业务码 200"
  assert_jq '.data.title=="测试变更事件-基坑支护局部加固(已核实)"' "草稿标题更新成功"

  # 7. 转入评估中 (DRAFT -> ASSESSING)
  call POST "/api/v1/contract/change-event/$EVENT_ID/start-assessment"
  assert_http 2 "转入评估 HTTP 2xx"
  assert_body_code 200 "转入评估业务码 200"
  assert_jq '.data.status=="ASSESSING"' "状态流转为 ASSESSING"

  # 8. 提交影响评估 (ASSESSING -> APPROVING)
  ASSESSMENT_BODY=$(cat <<EOF
{
  "costDelta": 35000.00,
  "scheduleDelayDays": 5,
  "rationale": "增加注浆材料与施工机械工时消耗测算"
}
EOF
  )
  call POST "/api/v1/contract/change-event/$EVENT_ID/assessment" "$ASSESSMENT_BODY"
  assert_http 2 "提交评估 HTTP 2xx"
  assert_body_code 200 "提交评估业务码 200"
  assert_jq '.data.status=="APPROVING"' "状态流转为 APPROVING"

  # 9. 批准变更事件 (APPROVING -> APPROVED)
  # query 参数不得带未编码中文（Tomcat RFC7230 会直接 400 HTML），用 ASCII 注释
  call POST "/api/v1/contract/change-event/$EVENT_ID/approve?comment=approved-by-l3-change-event-test"
  assert_http 2 "批准变更事件 HTTP 2xx"
  assert_body_code 200 "批准变更事件业务码 200"
  assert_jq '.data.status=="APPROVED"' "状态流转为 APPROVED"

  # 10. 负向用例：已批准事件不可修改
  call PUT "/api/v1/contract/change-event/$EVENT_ID" "$UPDATE_BODY"
  assert_body_not_success "已批准变更事件拒绝修改"

  # 11. 负向用例：已批准事件不可删除
  call DELETE "/api/v1/contract/change-event/$EVENT_ID"
  assert_body_not_success "已批准变更事件拒绝删除"
fi

# 12. 负向用例：查询不存在的变更事件
call GET "/api/v1/contract/change-event/999999999"
assert_body_not_success "不存在变更事件详情拒绝"

# 13. 删除接口与清理闭环验证：新建临时草稿并删除
CLEANUP_BODY=$(cat <<EOF
{
  "projectId": $PROJECT_ID,
  "sourceType": "OTHER",
  "title": "待清理的临时变更事件",
  "description": "用于验证删除接口的临时草稿",
  "category": "OTHER",
  "priority": "LOW"
}
EOF
)
call POST "/api/v1/contract/change-event" "$CLEANUP_BODY"
CLEANUP_EVENT_ID=$(jq -r '.data.id // empty' /tmp/zwi_body 2>/dev/null)
if [ -n "$CLEANUP_EVENT_ID" ]; then
  call DELETE "/api/v1/contract/change-event/$CLEANUP_EVENT_ID"
  assert_http 2 "删除草稿变更事件 HTTP 2xx"
  assert_body_code 200 "删除草稿变更事件业务码 200"

  # 确认删除后不可再查出
  call GET "/api/v1/contract/change-event/$CLEANUP_EVENT_ID"
  assert_body_not_success "删除后详情不可查"
fi

report_summary
