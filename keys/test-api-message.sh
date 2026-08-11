#!/usr/bin/env bash
###############################################################################
# test-api-message.sh — L3 API 接口测试：消息模块（阶段四批 4）
#
# 覆盖端点（依据 zw-message 6 个 Controller 逐行核对）：
#   - /api/v1/message/announcement  公告：分页契约 + save→publish→revoke→delete 全生命周期闭环
#   - /api/v1/message/notice        通知：分页契约 + 不存在发布负向（无删除端点，不创建数据）
#   - /api/v1/message/template      模板：分页契约 + CRUD 零残留闭环
#   - /api/v1/message/push-config   推送配置：分页契约 + CRUD 零残留闭环 + by-type 查询/负向
#   - /api/v1/message/msg           站内信：未读/全部/未读数只读契约（标记已读会改真实数据状态，不执行）
#   - /api/v1/message/shortcut      快捷入口：配置/可选列表只读契约 + 空批量 @Valid 负向（批量保存写真实用户配置，不执行）
#
# 设计要点：
#   - 分页均为 GET 根路径
#   - notice 无 DELETE 端点、batch 保存会修改当前用户真实配置，均不写入
#   - 标识字段全部 ASCII（title/templateName/businessType 带时间戳后缀）
#   - 依赖 verify-base.sh 登录基座；jq 断言规范同阶段四批 1-3
###############################################################################
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/verify-base.sh" login 2>/dev/null || true

PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0
TS_SUFFIX=$(date +%s)

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
  log "消息模块 API 测试汇总"
  echo "═══════════════════════════════════════════════════════════"
  log "  通过: $PASS_COUNT"
  log "  失败: $FAIL_COUNT"
  log "  总计: $TOTAL_COUNT"
  echo "═══════════════════════════════════════════════════════════"
  [ "$FAIL_COUNT" -eq 0 ]
}

log "========== L3 消息模块 API 测试开始 =========="

# ---------- 分页契约（GET 根路径） ----------
call GET "/api/v1/message/announcement?page=1&size=5"
assert_http 2 "公告-分页 HTTP"
assert_jq "$PAGE_EXPR" "公告-分页结构"

call GET "/api/v1/message/notice?page=1&size=5"
assert_http 2 "通知-分页 HTTP"
assert_jq "$PAGE_EXPR" "通知-分页结构"

call GET "/api/v1/message/template?page=1&size=5"
assert_http 2 "模板-分页 HTTP"
assert_jq "$PAGE_EXPR" "模板-分页结构"

call GET "/api/v1/message/push-config?page=1&size=5"
assert_http 2 "推送配置-分页 HTTP"
assert_jq "$PAGE_EXPR" "推送配置-分页结构"

# ---------- 站内信只读契约（标记已读会改真实数据状态，不执行写入） ----------
call GET "/api/v1/message/msg/unread?page=1&size=5"
assert_http 2 "站内信-未读分页 HTTP"
assert_jq "$PAGE_EXPR" "站内信-未读分页结构"

call GET "/api/v1/message/msg/all?page=1&size=5"
assert_http 2 "站内信-全部分页 HTTP"
assert_jq "$PAGE_EXPR" "站内信-全部分页结构"

call GET "/api/v1/message/msg/unread-count"
assert_http 2 "站内信-未读数 HTTP"
assert_jq '.code==200 and ((.data|type=="number") or (.data|type=="string"))' "站内信-未读数为数值"

# ---------- 快捷入口只读契约（批量保存会修改当前用户真实配置，不执行写入） ----------
call GET "/api/v1/message/shortcut"
assert_http 2 "快捷入口-当前配置 HTTP"
assert_jq '.code==200 and (.data|type=="array")' "快捷入口-配置为数组"

call GET "/api/v1/message/shortcut/available"
assert_http 2 "快捷入口-可选列表 HTTP"
assert_jq '.code==200 and (.data|type=="array")' "快捷入口-可选列表为数组"

# 负向：空批量保存被 @Valid/服务校验拒绝
call POST "/api/v1/message/shortcut/batch" "{\"shortcutIds\":[]}"
assert_body_not_success "快捷入口-空批量保存被拒绝"

# ---------- 通知负向（无删除端点，不创建数据） ----------
call POST "/api/v1/message/notice/999999999/publish" ""
assert_body_not_success "通知-不存在记录发布被拒绝"

# ---------- 公告全生命周期闭环（save→publish→revoke→delete，按 title 查回） ----------
call POST "/api/v1/message/announcement" "{\"title\":\"L3Ann$TS_SUFFIX\",\"content\":\"L3 announcement content\",\"publishScope\":\"ALL\"}"
assert_http 2 "公告-创建 HTTP"
assert_body_code 200 "公告-创建业务码"

call GET "/api/v1/message/announcement?page=1&size=20&title=L3Ann$TS_SUFFIX"
ANN_ID=$(jq -r --arg t "L3Ann$TS_SUFFIX" '.data.records[] | select(.title==$t) | .id' /tmp/zwi_body 2>/dev/null | head -1)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$ANN_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 公告-查回ID: $ANN_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 公告-创建后查回失败"
fi

if [ -n "$ANN_ID" ]; then
  call GET "/api/v1/message/announcement/$ANN_ID"
  assert_http 2 "公告-详情 HTTP"
  assert_jq '.code==200 and (.data.status=="DRAFT")' "公告-初始状态 DRAFT"

  call POST "/api/v1/message/announcement/$ANN_ID/publish" ""
  assert_http 2 "公告-发布 HTTP"
  assert_body_code 200 "公告-发布业务码"
  call GET "/api/v1/message/announcement/$ANN_ID"
  assert_jq '.code==200 and (.data.status=="PUBLISHED")' "公告-发布后状态 PUBLISHED"

  call POST "/api/v1/message/announcement/$ANN_ID/revoke" ""
  assert_http 2 "公告-撤回 HTTP"
  assert_body_code 200 "公告-撤回业务码"
  call GET "/api/v1/message/announcement/$ANN_ID"
  assert_jq '.code==200 and (.data.status=="REVOKED")' "公告-撤回后状态 REVOKED"

  call DELETE "/api/v1/message/announcement/$ANN_ID"
  assert_http 2 "公告-删除 HTTP"
  assert_body_code 200 "公告-删除业务码"

  # 负向：删除后发布必须拒绝
  call POST "/api/v1/message/announcement/$ANN_ID/publish" ""
  assert_body_not_success "公告-删除后发布被拒绝"
fi

# ---------- 模板 CRUD 闭环（按 templateName 查回） ----------
call POST "/api/v1/message/template" "{\"templateName\":\"L3Tpl$TS_SUFFIX\",\"businessType\":\"L3TEST\",\"content\":\"hello\",\"channelTypes\":\"IN_APP\",\"status\":1}"
assert_http 2 "模板-创建 HTTP"
assert_body_code 200 "模板-创建业务码"

call GET "/api/v1/message/template?page=1&size=20&templateName=L3Tpl$TS_SUFFIX"
TPL_ID=$(jq -r --arg tn "L3Tpl$TS_SUFFIX" '.data.records[] | select(.templateName==$tn) | .id' /tmp/zwi_body 2>/dev/null | head -1)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$TPL_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 模板-查回ID: $TPL_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 模板-创建后查回失败"
fi

if [ -n "$TPL_ID" ]; then
  call PUT "/api/v1/message/template/$TPL_ID" "{\"templateName\":\"L3TplU$TS_SUFFIX\",\"businessType\":\"L3TEST\",\"content\":\"hello updated\",\"channelTypes\":\"IN_APP\",\"status\":1}"
  assert_http 2 "模板-更新 HTTP"
  assert_body_code 200 "模板-更新业务码"

  call DELETE "/api/v1/message/template/$TPL_ID"
  assert_http 2 "模板-删除 HTTP"
  assert_body_code 200 "模板-删除业务码"

  call PUT "/api/v1/message/template/$TPL_ID" "{\"templateName\":\"L3TplX$TS_SUFFIX\"}"
  assert_body_not_success "模板-删除后编辑被拒绝"
fi

# ---------- 推送配置 CRUD 闭环（按 businessType 查回，ASCII 唯一编码） ----------
call POST "/api/v1/message/push-config" "{\"businessType\":\"L3PT$TS_SUFFIX\",\"businessTypeName\":\"L3批4测试类型\",\"enableInApp\":true,\"enableSms\":false,\"enableEmail\":false,\"enableAppPush\":false}"
assert_http 2 "推送配置-创建 HTTP"
assert_body_code 200 "推送配置-创建业务码"

call GET "/api/v1/message/push-config?page=1&size=20&businessType=L3PT$TS_SUFFIX"
PC_ID=$(jq -r --arg bt "L3PT$TS_SUFFIX" '.data.records[] | select(.businessType==$bt) | .id' /tmp/zwi_body 2>/dev/null | head -1)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$PC_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 推送配置-查回ID: $PC_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 推送配置-创建后查回失败"
fi

if [ -n "$PC_ID" ]; then
  call GET "/api/v1/message/push-config/by-type/L3PT$TS_SUFFIX"
  assert_http 2 "推送配置-按类型查询 HTTP"
  assert_jq ".code==200 and (.data.id!=null)" "推送配置-按类型查询含ID"

  call PUT "/api/v1/message/push-config/$PC_ID" "{\"businessType\":\"L3PT$TS_SUFFIX\",\"businessTypeName\":\"L3批4测试类型U\",\"enableInApp\":true,\"enableSms\":true,\"enableEmail\":false,\"enableAppPush\":false}"
  assert_http 2 "推送配置-更新 HTTP"
  assert_body_code 200 "推送配置-更新业务码"

  call DELETE "/api/v1/message/push-config/$PC_ID"
  assert_http 2 "推送配置-删除 HTTP"
  assert_body_code 200 "推送配置-删除业务码"

  # 负向：删除后按类型查询必须拒绝
  call GET "/api/v1/message/push-config/by-type/L3PT$TS_SUFFIX"
  assert_body_not_success "推送配置-删除后按类型查询被拒绝"
fi

# 负向：不存在详情被拒绝
call GET "/api/v1/message/push-config/999999999"
assert_body_not_success "推送配置-不存在详情被拒绝"

report_summary
