#!/usr/bin/env bash
###############################################################################
# test-api-material.sh — L3 API 接口测试：材料模块
#
# 覆盖端点：
#   - /api/v1/material/inbound   入库单 CRUD + 提交审批
#   - /api/v1/material/outbound  出库单 CRUD + 提交审批
#   - /api/v1/material/stock     库存分页查询
#   - /api/v1/material/inventory 盘点 CRUD
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
CREATED_INBOUND_ID=""
CREATED_OUTBOUND_ID=""
CREATED_INVENTORY_ID=""

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

report_summary() {
  echo ""
  echo "═══════════════════════════════════════════════════════════"
  log "材料模块 API 测试汇总"
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
  val=$(cat /tmp/zwi_body 2>/dev/null | grep -oE '"data"\s*:\s*[0-9]+' | head -1 | grep -oE '[0-9]+$')
  if [ -n "$val" ]; then echo "$val"; return; fi
  val=$(cat /tmp/zwi_body 2>/dev/null | grep -oE '"id"\s*:\s*[0-9]+' | head -1 | grep -oE '[0-9]+$')
  echo "$val"
}

extract_first_record_id() {
  cat /tmp/zwi_body 2>/dev/null | grep -oE '"id"\s*:\s*[0-9]+' | head -1 | grep -oE '[0-9]+$'
}

# ===========================================================================
# 清理逻辑
# ===========================================================================
cleanup() {
  log "--- 清理测试数据 ---"
  if [ -n "$CREATED_INVENTORY_ID" ]; then
    call DELETE "/api/v1/material/inventory/$CREATED_INVENTORY_ID" 2>/dev/null
    log "  已清理盘点记录 ID=$CREATED_INVENTORY_ID"
  fi
  if [ -n "$CREATED_OUTBOUND_ID" ]; then
    call DELETE "/api/v1/material/outbound/$CREATED_OUTBOUND_ID" 2>/dev/null
    log "  已清理出库单 ID=$CREATED_OUTBOUND_ID"
  fi
  if [ -n "$CREATED_INBOUND_ID" ]; then
    call DELETE "/api/v1/material/inbound/$CREATED_INBOUND_ID" 2>/dev/null
    log "  已清理入库单 ID=$CREATED_INBOUND_ID"
  fi
  log "--- 清理完成 ---"
}

trap cleanup EXIT

# ===========================================================================
# 测试用例：入库模块
# ===========================================================================

test_inbound_create() {
  log "▶ 测试：创建入库单"
  call POST "/api/v1/material/inbound" '{"projectId":1,"supplierName":"测试供应商-自动化","inboundDate":"2025-07-01","remark":"L3接口自动化测试入库单"}'
  assert_http 2 "POST /api/v1/material/inbound 状态码"
  assert_body_code 200 "POST /api/v1/material/inbound 业务码"
}

test_inbound_page() {
  log "▶ 测试：分页查询入库单"
  call GET "/api/v1/material/inbound/page?page=1&size=10"
  assert_http 2 "GET /api/v1/material/inbound/page 状态码"
  assert_body_code 200 "GET /api/v1/material/inbound/page 业务码"
  assert_has_field "records" "入库分页含 records 字段"
  assert_has_field "total" "入库分页含 total 字段"

  CREATED_INBOUND_ID=$(extract_first_record_id)
  if [ -n "$CREATED_INBOUND_ID" ]; then
    log "  获取入库单 ID=$CREATED_INBOUND_ID"
  else
    log "  WARN: 未能获取入库单ID，后续测试可能失败"
  fi
}

test_inbound_detail() {
  log "▶ 测试：查询入库单详情"
  if [ -z "$CREATED_INBOUND_ID" ]; then
    log "  SKIP: 无入库单ID"; return 0
  fi
  call GET "/api/v1/material/inbound/$CREATED_INBOUND_ID"
  assert_http 2 "GET /api/v1/material/inbound/{id} 状态码"
  assert_body_code 200 "GET /api/v1/material/inbound/{id} 业务码"
}

test_inbound_update() {
  log "▶ 测试：更新入库单"
  if [ -z "$CREATED_INBOUND_ID" ]; then
    log "  SKIP: 无入库单ID"; return 0
  fi
  call PUT "/api/v1/material/inbound/$CREATED_INBOUND_ID" '{"projectId":1,"supplierName":"测试供应商-已修改","inboundDate":"2025-07-02","remark":"修改后的入库单"}'
  assert_http 2 "PUT /api/v1/material/inbound/{id} 状态码"
  assert_body_code 200 "PUT /api/v1/material/inbound/{id} 业务码"
}

test_inbound_submit() {
  log "▶ 测试：提交入库单审批"
  if [ -z "$CREATED_INBOUND_ID" ]; then
    log "  SKIP: 无入库单ID"; return 0
  fi
  call POST "/api/v1/material/inbound/$CREATED_INBOUND_ID/submit"
  assert_http 2 "POST /api/v1/material/inbound/{id}/submit 状态码"
  assert_body_code 200 "POST /api/v1/material/inbound/{id}/submit 业务码"
}

# ===========================================================================
# 测试用例：出库模块
# ===========================================================================

test_outbound_create() {
  log "▶ 测试：创建出库单"
  call POST "/api/v1/material/outbound" '{"projectId":1,"outboundType":"领用","outboundDate":"2025-07-01","remark":"L3接口自动化测试出库单"}'
  assert_http 2 "POST /api/v1/material/outbound 状态码"
  assert_body_code 200 "POST /api/v1/material/outbound 业务码"
}

test_outbound_page() {
  log "▶ 测试：分页查询出库单"
  call GET "/api/v1/material/outbound/page?page=1&size=10"
  assert_http 2 "GET /api/v1/material/outbound/page 状态码"
  assert_body_code 200 "GET /api/v1/material/outbound/page 业务码"
  assert_has_field "records" "出库分页含 records 字段"
  assert_has_field "total" "出库分页含 total 字段"

  CREATED_OUTBOUND_ID=$(extract_first_record_id)
  if [ -n "$CREATED_OUTBOUND_ID" ]; then
    log "  获取出库单 ID=$CREATED_OUTBOUND_ID"
  fi
}

test_outbound_detail() {
  log "▶ 测试：查询出库单详情"
  if [ -z "$CREATED_OUTBOUND_ID" ]; then
    log "  SKIP: 无出库单ID"; return 0
  fi
  call GET "/api/v1/material/outbound/$CREATED_OUTBOUND_ID"
  assert_http 2 "GET /api/v1/material/outbound/{id} 状态码"
  assert_body_code 200 "GET /api/v1/material/outbound/{id} 业务码"
}

test_outbound_update() {
  log "▶ 测试：更新出库单"
  if [ -z "$CREATED_OUTBOUND_ID" ]; then
    log "  SKIP: 无出库单ID"; return 0
  fi
  call PUT "/api/v1/material/outbound/$CREATED_OUTBOUND_ID" '{"projectId":1,"outboundType":"退料","outboundDate":"2025-07-02","remark":"修改后的出库单"}'
  assert_http 2 "PUT /api/v1/material/outbound/{id} 状态码"
  assert_body_code 200 "PUT /api/v1/material/outbound/{id} 业务码"
}

test_outbound_submit() {
  log "▶ 测试：提交出库单审批"
  if [ -z "$CREATED_OUTBOUND_ID" ]; then
    log "  SKIP: 无出库单ID"; return 0
  fi
  call POST "/api/v1/material/outbound/$CREATED_OUTBOUND_ID/submit"
  assert_http 2 "POST /api/v1/material/outbound/{id}/submit 状态码"
  assert_body_code 200 "POST /api/v1/material/outbound/{id}/submit 业务码"
}

# ===========================================================================
# 测试用例：库存查询
# ===========================================================================

test_stock_page() {
  log "▶ 测试：分页查询库存"
  call GET "/api/v1/material/stock/page?page=1&size=10"
  assert_http 2 "GET /api/v1/material/stock/page 状态码"
  assert_body_code 200 "GET /api/v1/material/stock/page 业务码"
  assert_has_field "records" "库存分页含 records 字段"
}

# ===========================================================================
# 测试用例：盘点模块
# ===========================================================================

test_inventory_create() {
  log "▶ 测试：创建盘点记录"
  call POST "/api/v1/material/inventory" '{"projectId":1,"remark":"L3接口自动化测试盘点"}'
  assert_http 2 "POST /api/v1/material/inventory 状态码"
  assert_body_code 200 "POST /api/v1/material/inventory 业务码"
}

test_inventory_page() {
  log "▶ 测试：分页查询盘点记录"
  call GET "/api/v1/material/inventory/page?page=1&size=10"
  assert_http 2 "GET /api/v1/material/inventory/page 状态码"
  assert_body_code 200 "GET /api/v1/material/inventory/page 业务码"
  assert_has_field "records" "盘点分页含 records 字段"

  CREATED_INVENTORY_ID=$(extract_first_record_id)
  if [ -n "$CREATED_INVENTORY_ID" ]; then
    log "  获取盘点记录 ID=$CREATED_INVENTORY_ID"
  fi
}

test_inventory_detail() {
  log "▶ 测试：查询盘点记录详情"
  if [ -z "$CREATED_INVENTORY_ID" ]; then
    log "  SKIP: 无盘点记录ID"; return 0
  fi
  call GET "/api/v1/material/inventory/$CREATED_INVENTORY_ID"
  assert_http 2 "GET /api/v1/material/inventory/{id} 状态码"
  assert_body_code 200 "GET /api/v1/material/inventory/{id} 业务码"
}

test_inventory_update() {
  log "▶ 测试：更新盘点记录"
  if [ -z "$CREATED_INVENTORY_ID" ]; then
    log "  SKIP: 无盘点记录ID"; return 0
  fi
  call PUT "/api/v1/material/inventory/$CREATED_INVENTORY_ID" '{"projectId":1,"remark":"修改后的盘点记录"}'
  assert_http 2 "PUT /api/v1/material/inventory/{id} 状态码"
  assert_body_code 200 "PUT /api/v1/material/inventory/{id} 业务码"
}

test_inventory_delete() {
  log "▶ 测试：删除盘点记录"
  if [ -z "$CREATED_INVENTORY_ID" ]; then
    log "  SKIP: 无盘点记录ID"; return 0
  fi
  call DELETE "/api/v1/material/inventory/$CREATED_INVENTORY_ID"
  assert_http 2 "DELETE /api/v1/material/inventory/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/material/inventory/{id} 业务码"
  CREATED_INVENTORY_ID=""
}

# ===========================================================================
# 测试用例：异常场景
# ===========================================================================

test_inbound_nonexistent() {
  log "▶ 测试：查询不存在的入库单"
  call GET "/api/v1/material/inbound/999999999"
  local code
  code=$(cat /tmp/zwi_last_code 2>/dev/null || echo "000")
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [[ "$code" =~ ^[2-4][0-9][0-9]$ ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] GET 不存在入库单无 5xx (HTTP $code)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] GET 不存在入库单返回 5xx (HTTP $code)"
  fi
}

test_inbound_delete() {
  log "▶ 测试：删除入库单"
  if [ -z "$CREATED_INBOUND_ID" ]; then
    log "  SKIP: 无入库单ID"; return 0
  fi
  call DELETE "/api/v1/material/inbound/$CREATED_INBOUND_ID"
  assert_http 2 "DELETE /api/v1/material/inbound/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/material/inbound/{id} 业务码"
  CREATED_INBOUND_ID=""
}

test_outbound_delete() {
  log "▶ 测试：删除出库单"
  if [ -z "$CREATED_OUTBOUND_ID" ]; then
    log "  SKIP: 无出库单ID"; return 0
  fi
  call DELETE "/api/v1/material/outbound/$CREATED_OUTBOUND_ID"
  assert_http 2 "DELETE /api/v1/material/outbound/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/material/outbound/{id} 业务码"
  CREATED_OUTBOUND_ID=""
}

# ===========================================================================
# 主流程
# ===========================================================================
main() {
  echo ""
  log "═══ L3 API 接口测试：材料模块 (test-api-material.sh) ═══"
  log "时间: $(date '+%Y-%m-%d %H:%M:%S') | 服务: $BASE"
  echo ""

  login || { log "登录失败，无法执行测试"; exit 1; }

  # 入库测试
  test_inbound_create
  test_inbound_page
  test_inbound_detail
  test_inbound_update
  test_inbound_submit

  # 出库测试
  test_outbound_create
  test_outbound_page
  test_outbound_detail
  test_outbound_update
  test_outbound_submit

  # 库存查询
  test_stock_page

  # 盘点测试
  test_inventory_create
  test_inventory_page
  test_inventory_detail
  test_inventory_update
  test_inventory_delete

  # 异常场景
  test_inbound_nonexistent

  # 清理已创建资源（先于 trap，避免重复）
  test_inbound_delete
  test_outbound_delete

  # 日志核对
  echo ""
  log "▶ 后端日志核对"
  check_logs 120

  # 输出汇总
  report_summary
  exit $?
}

main "$@"
