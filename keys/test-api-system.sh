#!/usr/bin/env bash
###############################################################################
# test-api-system.sh — L3 API 接口测试：系统管理 + 认证基础（阶段四批 1）
#
# 覆盖端点：
#   - /api/v1/system/user        用户：分页/详情
#   - /api/v1/system/org         机构：树形列表
#   - /api/v1/system/role        角色：分页/菜单权限查询
#   - /api/v1/system/menu        菜单：树形/当前用户菜单
#   - /api/v1/system/post        岗位：分页
#   - /api/v1/system/dict        字典：分页 + CRUD 闭环 + 字典项查询
#   - /api/v1/system/log /audit /monitor /version /config 运维端点
#   - /api/v1/platform/tenant /tenant-type 租户平台
#   - /api/v1/user/devices/list  登录设备列表
#   - /api/v1/captcha/image      图形验证码结构
#
# 运行位置：服务器（需要 docker exec zwi-redis）；依赖 verify-base.sh 登录基座
# 设计依据：阶段四批 1（coverage-matrix L3 缺口销项）；jq 断言规范同 2.4 契约强化
###############################################################################
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/verify-base.sh" login 2>/dev/null || true

PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0
CREATED_DICT_ID=""

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

PAGE_EXPR='.code==200 and (.data.records|type=="array") and ((.data.total|type=="number") or (.data.total|type=="string"))'

report_summary() {
  echo ""
  echo "═══════════════════════════════════════════════════════════"
  log "系统管理模块 API 测试汇总"
  echo "═══════════════════════════════════════════════════════════"
  log "  通过: $PASS_COUNT"
  log "  失败: $FAIL_COUNT"
  log "  总计: $TOTAL_COUNT"
  echo "═══════════════════════════════════════════════════════════"
  [ "$FAIL_COUNT" -eq 0 ]
}

log "========== L3 系统管理模块 API 测试开始 =========="

# ---------- 用户 ----------
call GET "/api/v1/system/user?page=1&size=5"
assert_http 2 "用户-分页 HTTP"
assert_jq "$PAGE_EXPR" "用户-分页结构"

call GET "/api/v1/system/user/1"
assert_http 2 "用户-详情 HTTP"
assert_body_code 200 "用户-详情业务码"

# ---------- 机构 / 岗位 ----------
call GET "/api/v1/system/org"
assert_http 2 "机构-列表 HTTP"
assert_body_code 200 "机构-列表业务码"

call GET "/api/v1/system/post?page=1&size=5"
assert_http 2 "岗位-分页 HTTP"
assert_body_code 200 "岗位-分页业务码"

# ---------- 角色 ----------
call GET "/api/v1/system/role?page=1&size=5"
assert_http 2 "角色-分页 HTTP"
assert_jq "$PAGE_EXPR" "角色-分页结构"
ROLE_ID=$(jq -r '.data.records[0].id // empty' /tmp/zwi_body 2>/dev/null)
if [ -n "$ROLE_ID" ]; then
  call GET "/api/v1/system/role/$ROLE_ID/menus"
  assert_http 2 "角色-菜单权限查询 HTTP"
  assert_body_code 200 "角色-菜单权限业务码"
fi

# ---------- 菜单 ----------
call GET "/api/v1/system/menu"
assert_http 2 "菜单-树形列表 HTTP"
assert_body_code 200 "菜单-树形业务码"

call GET "/api/v1/system/menu/user"
assert_http 2 "菜单-当前用户菜单 HTTP"
assert_body_code 200 "菜单-当前用户业务码"

# ---------- 字典 CRUD 闭环 ----------
TS_SUFFIX=$(date +%s)
call POST "/api/v1/system/dict" "{\"dictName\":\"L3临时字典$TS_SUFFIX\",\"dictCode\":\"l3tmp$TS_SUFFIX\",\"sort\":99}"
assert_http 2 "字典-创建 HTTP"
assert_body_code 200 "字典-创建业务码"

# save 返回无 ID，按名称查回取 ID（真实查询，非伪造）
call GET "/api/v1/system/dict?page=1&size=10&dictName=L3临时字典$TS_SUFFIX"
assert_http 2 "字典-按名查询 HTTP"
CREATED_DICT_ID=$(jq -r '.data.records[0].id // empty' /tmp/zwi_body 2>/dev/null)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$CREATED_DICT_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 字典-创建后按名查回ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 字典-创建后按名未查回ID"
fi

if [ -n "$CREATED_DICT_ID" ]; then
  call PUT "/api/v1/system/dict/$CREATED_DICT_ID" "{\"dictName\":\"L3临时字典改$TS_SUFFIX\",\"dictCode\":\"l3tmp$TS_SUFFIX\",\"sort\":98}"
  assert_http 2 "字典-更新 HTTP"
  assert_body_code 200 "字典-更新业务码"
fi

call GET "/api/v1/system/dict/items/l3tmp$TS_SUFFIX"
assert_http 2 "字典项-按编码查询 HTTP"
assert_body_code 200 "字典项-查询业务码"

if [ -n "$CREATED_DICT_ID" ]; then
  call DELETE "/api/v1/system/dict/$CREATED_DICT_ID"
  assert_http 2 "字典-删除 HTTP"
  assert_body_code 200 "字典-删除业务码"
fi

# ---------- 运维端点 ----------
call GET "/api/v1/system/log?page=1&size=5"
assert_http 2 "日志-分页 HTTP"
assert_body_code 200 "日志-分页业务码"

call GET "/api/v1/system/audit?page=1&size=5"
assert_http 2 "审计日志 HTTP"
assert_body_code 200 "审计日志业务码"

call GET "/api/v1/system/monitor"
assert_http 2 "健康监控 HTTP"
assert_body_code 200 "健康监控业务码"

call GET "/api/v1/system/version"
assert_http 2 "版本信息 HTTP"
assert_body_code 200 "版本信息业务码"

call GET "/api/v1/system/config"
assert_http 2 "系统配置 HTTP"
assert_body_code 200 "系统配置业务码"

# ---------- 租户平台 ----------
call GET "/api/v1/platform/tenant?page=1&size=5"
assert_http 2 "租户-分页 HTTP"
assert_body_code 200 "租户-分页业务码"

call GET "/api/v1/platform/tenant-type?page=1&size=5"
assert_http 2 "租户类型-分页 HTTP"
assert_body_code 200 "租户类型-分页业务码"

# ---------- 认证基础 ----------
call GET "/api/v1/user/devices/list"
assert_http 2 "登录设备-列表 HTTP"
assert_body_code 200 "登录设备-列表业务码"

# 验证码结构：uuid + base64 图片（真实组件契约，k6/登录链路依赖）
resp=$(curl -s -m 10 "$BASE/api/v1/captcha/image")
echo "$resp" > /tmp/zwi_body
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if echo "$resp" | grep -q '"uuid"' && echo "$resp" | grep -q 'data:image'; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 验证码-结构(uuid+base64图片)"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 验证码-结构缺 uuid 或 base64 图片"
fi

report_summary
