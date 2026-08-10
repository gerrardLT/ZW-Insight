#!/usr/bin/env bash
###############################################################################
# test-api-hr.sh — L3 API 接口测试：人事模块（阶段四批 3）
#
# 覆盖端点（依据 zw-hr 8 个 Controller 逐行核对）：
#   - /api/v1/hr/regular-apply   转正申请：分页（根路径）+ submit 不存在负向
#   - /api/v1/hr/resign-apply    离职申请：分页（根路径）+ submit 不存在负向
#   - /api/v1/hr/transfer-apply  调动申请：分页（根路径）+ submit 不存在负向
#   - /api/v1/hr/seal-apply      用印申请：分页（根路径）+ submit 不存在负向
#   - /api/v1/hr/entry-apply     入职申请：CRUD 闭环（按 realName 查回）+ submit 不存在负向
#   - /api/v1/hr/vehicle         车辆：CRUD 闭环（按 plateNumber 查回）；申请/维保分页 + submit 不存在负向
#   - /api/v1/hr/office-supply   办公用品：CRUD 闭环（按 supplyName 查回）；出入库分页 + 不存在用品负向
#   - /api/v1/hr/statistics      人事统计：overview 结构断言
#
# 设计要点：
#   - 申请类分页无筛选参数，无法查回，仅做分页契约 + submit 负向，不创建数据（零残留）
#   - 可筛选资源（entry-apply/vehicle/office-supply）走完整 CRUD 闭环，删除后编辑负向
#   - 标识字段全部 ASCII（realName/plateNumber/supplyName 带时间戳后缀）
#   - 依赖 verify-base.sh 登录基座；jq 断言规范同阶段四批 1/批 2
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
  log "人事模块 API 测试汇总"
  echo "═══════════════════════════════════════════════════════════"
  log "  通过: $PASS_COUNT"
  log "  失败: $FAIL_COUNT"
  log "  总计: $TOTAL_COUNT"
  echo "═══════════════════════════════════════════════════════════"
  [ "$FAIL_COUNT" -eq 0 ]
}

log "========== L3 人事模块 API 测试开始 =========="

# ---------- 只读分页契约（注意四类申请分页为 GET 根路径，无 /page 后缀） ----------
call GET "/api/v1/hr/regular-apply?page=1&size=5"
assert_http 2 "转正申请-分页 HTTP"
assert_jq "$PAGE_EXPR" "转正申请-分页结构"

call GET "/api/v1/hr/resign-apply?page=1&size=5"
assert_http 2 "离职申请-分页 HTTP"
assert_jq "$PAGE_EXPR" "离职申请-分页结构"

call GET "/api/v1/hr/transfer-apply?page=1&size=5"
assert_http 2 "调动申请-分页 HTTP"
assert_jq "$PAGE_EXPR" "调动申请-分页结构"

call GET "/api/v1/hr/seal-apply?page=1&size=5"
assert_http 2 "用印申请-分页 HTTP"
assert_jq "$PAGE_EXPR" "用印申请-分页结构"

call GET "/api/v1/hr/entry-apply/page?page=1&size=5"
assert_http 2 "入职申请-分页 HTTP"
assert_jq "$PAGE_EXPR" "入职申请-分页结构"

call GET "/api/v1/hr/vehicle/page?page=1&size=5"
assert_http 2 "车辆-分页 HTTP"
assert_jq "$PAGE_EXPR" "车辆-分页结构"

call GET "/api/v1/hr/vehicle/apply?page=1&size=5"
assert_http 2 "车辆申请-分页 HTTP"
assert_jq "$PAGE_EXPR" "车辆申请-分页结构"

call GET "/api/v1/hr/vehicle/maintenance?page=1&size=5"
assert_http 2 "车辆维保-分页 HTTP"
assert_jq "$PAGE_EXPR" "车辆维保-分页结构"

call GET "/api/v1/hr/office-supply/page?page=1&size=5"
assert_http 2 "办公用品-分页 HTTP"
assert_jq "$PAGE_EXPR" "办公用品-分页结构"

call GET "/api/v1/hr/office-supply/in-out?page=1&size=5"
assert_http 2 "办公用品出入库-分页 HTTP"
assert_jq "$PAGE_EXPR" "办公用品出入库-分页结构"

# ---------- 人事统计 overview 结构断言 ----------
call GET "/api/v1/hr/statistics/overview"
assert_http 2 "人事统计-overview HTTP"
assert_jq '.code==200 and ((.data.totalActive|type=="number") or (.data.totalActive|type=="string")) and (.data.monthlyTrend|type=="array")' "人事统计-overview 结构（totalActive + monthlyTrend）"

# ---------- 提交类负向：不存在的记录必须拒绝，不得静默成功 ----------
call POST "/api/v1/hr/regular-apply/999999999/submit" ""
assert_body_not_success "转正申请-不存在记录提交被拒绝"

call POST "/api/v1/hr/resign-apply/999999999/submit" ""
assert_body_not_success "离职申请-不存在记录提交被拒绝"

call POST "/api/v1/hr/transfer-apply/999999999/submit" ""
assert_body_not_success "调动申请-不存在记录提交被拒绝"

call POST "/api/v1/hr/seal-apply/999999999/submit" ""
assert_body_not_success "用印申请-不存在记录提交被拒绝"

call POST "/api/v1/hr/entry-apply/999999999/submit" ""
assert_body_not_success "入职申请-不存在记录提交被拒绝"

call POST "/api/v1/hr/vehicle/apply/999999999/submit" ""
assert_body_not_success "车辆申请-不存在记录提交被拒绝"

call POST "/api/v1/hr/office-supply/in-out/999999999/submit" ""
assert_body_not_success "出入库-不存在记录提交被拒绝"

# 说明：「关联办公用品不存在」校验位于 submit 而非 save，覆盖该分支需先创建
# 出入库记录，但出入库无 DELETE 端点会造成租户 1 残留，故该分支交由 L1 覆盖

# ---------- 入职申请 CRUD 闭环（按 realName 查回） ----------
call POST "/api/v1/hr/entry-apply" "{\"realName\":\"L3Tmp$TS_SUFFIX\",\"username\":\"l3tmp$TS_SUFFIX\",\"phone\":\"13800000000\"}"
assert_http 2 "入职申请-创建 HTTP"
assert_body_code 200 "入职申请-创建业务码"

call GET "/api/v1/hr/entry-apply/page?page=1&size=20&realName=L3Tmp$TS_SUFFIX"
ENTRY_ID=$(jq -r --arg rn "L3Tmp$TS_SUFFIX" '.data.records[] | select(.realName==$rn) | .id' /tmp/zwi_body 2>/dev/null | head -1)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$ENTRY_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 入职申请-查回ID: $ENTRY_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 入职申请-创建后查回失败"
fi

if [ -n "$ENTRY_ID" ]; then
  call GET "/api/v1/hr/entry-apply/$ENTRY_ID"
  assert_http 2 "入职申请-详情 HTTP"
  assert_jq '.code==200 and (.data.id!=null) and (.data.status=="DRAFT")' "入职申请-详情含ID且为草稿"

  call PUT "/api/v1/hr/entry-apply/$ENTRY_ID" "{\"realName\":\"L3Tmp$TS_SUFFIX\",\"username\":\"l3tmp$TS_SUFFIX\",\"phone\":\"13900000000\"}"
  assert_http 2 "入职申请-更新 HTTP"
  assert_body_code 200 "入职申请-更新业务码"

  call DELETE "/api/v1/hr/entry-apply/$ENTRY_ID"
  assert_http 2 "入职申请-删除 HTTP"
  assert_body_code 200 "入职申请-删除业务码"

  # 负向：删除后再编辑必须拒绝
  call PUT "/api/v1/hr/entry-apply/$ENTRY_ID" "{\"realName\":\"L3TmpX$TS_SUFFIX\"}"
  assert_body_not_success "入职申请-删除后编辑被拒绝"
fi

# ---------- 车辆 CRUD 闭环（按 plateNumber 查回） ----------
call POST "/api/v1/hr/vehicle" "{\"plateNumber\":\"L3P$TS_SUFFIX\",\"vehicleType\":\"L3TEST\"}"
assert_http 2 "车辆-创建 HTTP"
assert_body_code 200 "车辆-创建业务码"

call GET "/api/v1/hr/vehicle/page?page=1&size=20&plateNumber=L3P$TS_SUFFIX"
VEHICLE_ID=$(jq -r --arg pn "L3P$TS_SUFFIX" '.data.records[] | select(.plateNumber==$pn) | .id' /tmp/zwi_body 2>/dev/null | head -1)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$VEHICLE_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 车辆-查回ID: $VEHICLE_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 车辆-创建后查回失败"
fi

if [ -n "$VEHICLE_ID" ]; then
  call PUT "/api/v1/hr/vehicle/$VEHICLE_ID" "{\"plateNumber\":\"L3P$TS_SUFFIX\",\"vehicleType\":\"L3TESTU\",\"vehicleStatus\":\"IDLE\"}"
  assert_http 2 "车辆-更新 HTTP"
  assert_body_code 200 "车辆-更新业务码"

  call DELETE "/api/v1/hr/vehicle/$VEHICLE_ID"
  assert_http 2 "车辆-删除 HTTP"
  assert_body_code 200 "车辆-删除业务码"

  # 负向：删除后再编辑必须拒绝
  call PUT "/api/v1/hr/vehicle/$VEHICLE_ID" "{\"plateNumber\":\"L3P$TS_SUFFIX\"}"
  assert_body_not_success "车辆-删除后编辑被拒绝"
fi

# ---------- 办公用品 CRUD 闭环（按 supplyName 查回） ----------
call POST "/api/v1/hr/office-supply" "{\"supplyName\":\"L3Supply$TS_SUFFIX\",\"unit\":\"box\"}"
assert_http 2 "办公用品-创建 HTTP"
assert_body_code 200 "办公用品-创建业务码"

call GET "/api/v1/hr/office-supply/page?page=1&size=20&supplyName=L3Supply$TS_SUFFIX"
SUPPLY_ID=$(jq -r --arg sn "L3Supply$TS_SUFFIX" '.data.records[] | select(.supplyName==$sn) | .id' /tmp/zwi_body 2>/dev/null | head -1)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$SUPPLY_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 办公用品-查回ID: $SUPPLY_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 办公用品-创建后查回失败"
fi

if [ -n "$SUPPLY_ID" ]; then
  call PUT "/api/v1/hr/office-supply/$SUPPLY_ID" "{\"supplyName\":\"L3Supply$TS_SUFFIX\",\"unit\":\"pcs\"}"
  assert_http 2 "办公用品-更新 HTTP"
  assert_body_code 200 "办公用品-更新业务码"

  call DELETE "/api/v1/hr/office-supply/$SUPPLY_ID"
  assert_http 2 "办公用品-删除 HTTP"
  assert_body_code 200 "办公用品-删除业务码"

  # 负向：删除后再编辑必须拒绝
  call PUT "/api/v1/hr/office-supply/$SUPPLY_ID" "{\"supplyName\":\"L3SupplyX$TS_SUFFIX\"}"
  assert_body_not_success "办公用品-删除后编辑被拒绝"
fi

report_summary
