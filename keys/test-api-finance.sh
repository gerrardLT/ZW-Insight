#!/usr/bin/env bash
###############################################################################
# test-api-finance.sh — L3 API 接口测试：财务模块
#
# 覆盖端点：
#   - /api/v1/finance/payment-apply  — 付款申请 CRUD + 提交审批
#   - /api/v1/finance/payment-received — 收款登记 CRUD
#   - /api/v1/finance/bank-account — 银行账户管理
#   - /api/v1/project-settlements — 项目结算查询
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
CREATED_PAYMENT_APPLY_ID=""
CREATED_PAYMENT_RECEIVED_ID=""
CREATED_BANK_ACCOUNT_ID=""

# ===========================================================================
# 公共测试函数（复用 test-api-project.sh 模式）
# ===========================================================================

# assert_http <expected_code_prefix> <test_name>
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
assert_body_code() {
  local expected="$1" test_name="$2"
  local actual
  actual=$(cat /tmp/zwi_body 2>/dev/null | grep -oE '"code"\s*:\s*\"?[0-9]+' | head -1 | grep -oE '[0-9]+$')
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

# assert_jq：jq 字段结构断言（阶段二 2.4 契约强化：从状态码升级为结构校验）
# jq 表达式须求值为 true/false；jq 缺失或表达式不满足一律记 FAIL，不静默降级
assert_jq() {
  local expr="$1" test_name="$2" result
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if ! command -v jq >/dev/null 2>&1; then
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] $test_name (jq 未安装)"
    return 1
  fi
  result=$(jq -e "$expr" /tmp/zwi_body 2>/dev/null | head -1)
  if [ "$result" = "true" ]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] $test_name"
    return 0
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] $test_name (jq 表达式不满足: $expr)"
    return 1
  fi
}

# report_summary：输出测试汇总
report_summary() {
  echo ""
  echo "═══════════════════════════════════════════════════════════"
  log "财务模块 API 测试汇总"
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

# extract_first_record_id：从分页结果的 records 数组中提取第一个 id
extract_first_record_id() {
  cat /tmp/zwi_body 2>/dev/null | grep -oE '"id"\s*:\s*\"?[0-9]+' | head -1 | grep -oE '[0-9]+$'
}

# ===========================================================================
# 清理逻辑
# ===========================================================================
cleanup() {
  log "--- 清理测试数据 ---"
  if [ -n "$CREATED_PAYMENT_APPLY_ID" ]; then
    call DELETE "/api/v1/finance/payment-apply/$CREATED_PAYMENT_APPLY_ID" 2>/dev/null
    log "  已清理付款申请 ID=$CREATED_PAYMENT_APPLY_ID"
  fi
  if [ -n "$CREATED_PAYMENT_RECEIVED_ID" ]; then
    call DELETE "/api/v1/finance/payment-received/$CREATED_PAYMENT_RECEIVED_ID" 2>/dev/null
    log "  已清理收款登记 ID=$CREATED_PAYMENT_RECEIVED_ID"
  fi
  if [ -n "$CREATED_BANK_ACCOUNT_ID" ]; then
    call DELETE "/api/v1/finance/bank-account/$CREATED_BANK_ACCOUNT_ID" 2>/dev/null
    log "  已清理银行账户 ID=$CREATED_BANK_ACCOUNT_ID"
  fi
  log "--- 清理完成 ---"
}

# 注册 trap，确保无论脚本如何退出都执行清理
trap cleanup EXIT

# ===========================================================================
# 测试用例 — 付款申请
# ===========================================================================

test_create_payment_apply() {
  log "▶ 测试：创建付款申请"
  # biz_payment_apply.contract_id NOT NULL：关联种子支出合同 91501（项目 90001 水泥砂石采购合同）
  call POST "/api/v1/finance/payment-apply" '{"projectId":90001,"contractId":91501,"contractCategory":"PURCHASE","supplierName":"测试供应商","paymentAmount":50000.00,"paymentDate":"2025-03-15"}'
  assert_http 2 "POST /api/v1/finance/payment-apply 状态码"
  assert_body_code 200 "POST /api/v1/finance/payment-apply 业务码"
}

test_page_payment_apply() {
  log "▶ 测试：分页查询付款申请"
  sleep 1
  call GET "/api/v1/finance/payment-apply/page?page=1&size=10"
  assert_http 2 "GET /api/v1/finance/payment-apply/page 状态码"
  assert_body_code 200 "GET /api/v1/finance/payment-apply/page 业务码"
  assert_has_field "records" "付款申请分页含 records"
  assert_has_field "total" "付款申请分页含 total"
  # 阶段二 2.4 契约强化：jq 字段结构断言
  assert_jq '.code == 200' "分页响应业务码200(jq)"
  assert_jq '(.data.records | type) == "array"' "分页records为数组(jq)"
  # 契约说明：后端 JacksonConfig 全局将 Long/long 序列化为 String（防前端精度丢失），
  # PageResult.total(long) 在 JSON 中恒为字符串，属生产有意设计
  assert_jq '(.data.total | type) == "string" or (.data.total | type) == "number"' "分页total为数字或数字字符串(jq)"

  # 提取 ID 用于后续测试
  CREATED_PAYMENT_APPLY_ID=$(extract_first_record_id)
  if [ -z "$CREATED_PAYMENT_APPLY_ID" ]; then
    log "  WARN: 未能获取付款申请ID"
  else
    log "  获取付款申请 ID=$CREATED_PAYMENT_APPLY_ID"
  fi
}

test_page_payment_apply_by_project() {
  log "▶ 测试：按项目ID筛选付款申请"
  call GET "/api/v1/finance/payment-apply/page?page=1&size=10&projectId=1"
  assert_http 2 "GET /api/v1/finance/payment-apply/page?projectId=1 状态码"
  assert_body_code 200 "GET /api/v1/finance/payment-apply/page?projectId=1 业务码"
}

test_get_payment_apply_detail() {
  log "▶ 测试：查询付款申请详情"
  if [ -z "$CREATED_PAYMENT_APPLY_ID" ]; then
    log "  SKIP: 无付款申请ID"; return 0
  fi
  call GET "/api/v1/finance/payment-apply/$CREATED_PAYMENT_APPLY_ID"
  assert_jq '.code == 200 and (.data | has("id"))' "详情响应含id(jq)"
  assert_http 2 "GET /api/v1/finance/payment-apply/{id} 状态码"
  assert_body_code 200 "GET /api/v1/finance/payment-apply/{id} 业务码"
  assert_has_field "paymentAmount" "详情含 paymentAmount"
}

test_update_payment_apply() {
  log "▶ 测试：更新付款申请"
  if [ -z "$CREATED_PAYMENT_APPLY_ID" ]; then
    log "  SKIP: 无付款申请ID"; return 0
  fi
  call PUT "/api/v1/finance/payment-apply/$CREATED_PAYMENT_APPLY_ID" '{"projectId":90001,"contractId":91501,"contractCategory":"PURCHASE","supplierName":"测试供应商-已修改","paymentAmount":65000.00,"paymentDate":"2025-03-20"}'
  assert_http 2 "PUT /api/v1/finance/payment-apply/{id} 状态码"
  assert_body_code 200 "PUT /api/v1/finance/payment-apply/{id} 业务码"
}

test_submit_payment_apply() {
  log "▶ 测试：提交付款申请审批"
  if [ -z "$CREATED_PAYMENT_APPLY_ID" ]; then
    log "  SKIP: 无付款申请ID"; return 0
  fi
  call POST "/api/v1/finance/payment-apply/$CREATED_PAYMENT_APPLY_ID/submit"
  assert_http 2 "POST /api/v1/finance/payment-apply/{id}/submit 状态码"
  assert_body_code 200 "POST /api/v1/finance/payment-apply/{id}/submit 业务码"
}

test_delete_payment_apply() {
  log "▶ 测试：删除付款申请（仅草稿可删除）"
  # 方案B：主流程付款申请已提交(非草稿)不可删。新建一个草稿再删(按 status=DRAFT 取最新一条)
  call POST "/api/v1/finance/payment-apply" '{"projectId":90001,"contractId":91501,"contractCategory":"PURCHASE","supplierName":"删除测试供应商","paymentAmount":1000.00,"paymentDate":"2025-03-15"}'
  assert_body_code 200 "POST /api/v1/finance/payment-apply 创建删除用草稿"
  sleep 1
  call GET "/api/v1/finance/payment-apply/page?page=1&size=1&status=DRAFT"
  local DEL_ID=$(extract_first_record_id)
  if [ -z "$DEL_ID" ]; then log "  SKIP: 未取到草稿付款申请ID"; return 0; fi
  call DELETE "/api/v1/finance/payment-apply/$DEL_ID"
  assert_http 2 "DELETE /api/v1/finance/payment-apply/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/finance/payment-apply/{id} 业务码"
}

# ===========================================================================
# 测试用例 — 收款登记
# ===========================================================================

test_create_payment_received() {
  log "▶ 测试：创建收款登记"
  # 字段对齐后端契约：receive_amount NOT NULL，收款人/收款方式为 receiver/receiveType
  # 回款上限=合同已开票未收金额：91001 额度已耗尽（历史测试未回冲），改用 91002（已竣工合同，
  # 已开票 3200 万>已收 3100 万，余额 100 万）；后端 delete 已修复回冲累计收款（与 save 对称），循环净变化为零
  call POST "/api/v1/finance/payment-received" '{"projectId":90002,"contractId":91002,"receiveDate":"2025-04-01","receiveAmount":100000.00,"receiver":"测试收款人","receiveType":"转账"}'
  assert_http 2 "POST /api/v1/finance/payment-received 状态码"
  assert_body_code 200 "POST /api/v1/finance/payment-received 业务码"
}

test_page_payment_received() {
  log "▶ 测试：分页查询收款登记"
  sleep 1
  call GET "/api/v1/finance/payment-received/page?page=1&size=10"
  assert_http 2 "GET /api/v1/finance/payment-received/page 状态码"
  assert_body_code 200 "GET /api/v1/finance/payment-received/page 业务码"
  assert_has_field "records" "收款登记分页含 records"

  CREATED_PAYMENT_RECEIVED_ID=$(extract_first_record_id)
  if [ -z "$CREATED_PAYMENT_RECEIVED_ID" ]; then
    log "  WARN: 未能获取收款登记ID"
  else
    log "  获取收款登记 ID=$CREATED_PAYMENT_RECEIVED_ID"
  fi
}

test_page_payment_received_by_project() {
  log "▶ 测试：按项目ID筛选收款登记"
  call GET "/api/v1/finance/payment-received/page?page=1&size=10&projectId=1"
  assert_http 2 "GET /api/v1/finance/payment-received/page?projectId=1 状态码"
  assert_body_code 200 "GET /api/v1/finance/payment-received/page?projectId=1 业务码"
}

test_get_payment_received_detail() {
  log "▶ 测试：查询收款登记详情"
  if [ -z "$CREATED_PAYMENT_RECEIVED_ID" ]; then
    log "  SKIP: 无收款登记ID"; return 0
  fi
  call GET "/api/v1/finance/payment-received/$CREATED_PAYMENT_RECEIVED_ID"
  assert_http 2 "GET /api/v1/finance/payment-received/{id} 状态码"
  assert_body_code 200 "GET /api/v1/finance/payment-received/{id} 业务码"
}

test_update_payment_received() {
  log "▶ 测试：更新收款登记"
  if [ -z "$CREATED_PAYMENT_RECEIVED_ID" ]; then
    log "  SKIP: 无收款登记ID"; return 0
  fi
  # 字段名对齐实体：receiveAmount（原脚本误写 amount 不绑定，B1 差额回冲校验后暴露）；
  # 100000→120000 差额 +20000 在可回款额度内
  call PUT "/api/v1/finance/payment-received/$CREATED_PAYMENT_RECEIVED_ID" '{"projectId":90002,"contractId":91002,"receiveDate":"2025-04-05","receiveAmount":120000.00,"receiver":"测试收款人-已修改","receiveType":"转账","remark":"L3接口测试收款-修改"}'
  assert_http 2 "PUT /api/v1/finance/payment-received/{id} 状态码"
  assert_body_code 200 "PUT /api/v1/finance/payment-received/{id} 业务码"
}

test_delete_payment_received() {
  log "▶ 测试：删除收款登记"
  if [ -z "$CREATED_PAYMENT_RECEIVED_ID" ]; then
    log "  SKIP: 无收款登记ID"; return 0
  fi
  call DELETE "/api/v1/finance/payment-received/$CREATED_PAYMENT_RECEIVED_ID"
  assert_http 2 "DELETE /api/v1/finance/payment-received/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/finance/payment-received/{id} 业务码"
  CREATED_PAYMENT_RECEIVED_ID=""
}

# ===========================================================================
# 测试用例 — 银行账户
# ===========================================================================

test_create_bank_account() {
  log "▶ 测试：创建银行账户"
  call POST "/api/v1/finance/bank-account" '{"accountType":"BASIC","projectId":1,"bankName":"中国建设银行","accountName":"测试施工公司","accountNo":"6217001234567890123","remark":"L3接口测试账户"}'
  assert_http 2 "POST /api/v1/finance/bank-account 状态码"
  assert_body_code 200 "POST /api/v1/finance/bank-account 业务码"
}

test_page_bank_account() {
  log "▶ 测试：分页查询银行账户"
  sleep 1
  call GET "/api/v1/finance/bank-account?page=1&size=10"
  assert_http 2 "GET /api/v1/finance/bank-account 状态码"
  assert_body_code 200 "GET /api/v1/finance/bank-account 业务码"
  assert_has_field "records" "银行账户分页含 records"

  CREATED_BANK_ACCOUNT_ID=$(extract_first_record_id)
  if [ -z "$CREATED_BANK_ACCOUNT_ID" ]; then
    log "  WARN: 未能获取银行账户ID"
  else
    log "  获取银行账户 ID=$CREATED_BANK_ACCOUNT_ID"
  fi
}

test_page_bank_account_by_type() {
  log "▶ 测试：按账户类型筛选银行账户"
  call GET "/api/v1/finance/bank-account?page=1&size=10&accountType=BASIC"
  assert_http 2 "GET /api/v1/finance/bank-account?accountType=BASIC 状态码"
  assert_body_code 200 "GET /api/v1/finance/bank-account?accountType=BASIC 业务码"
}

test_delete_bank_account() {
  log "▶ 测试：删除银行账户"
  if [ -z "$CREATED_BANK_ACCOUNT_ID" ]; then
    log "  SKIP: 无银行账户ID"; return 0
  fi
  call DELETE "/api/v1/finance/bank-account/$CREATED_BANK_ACCOUNT_ID"
  assert_http 2 "DELETE /api/v1/finance/bank-account/{id} 状态码"
  assert_body_code 200 "DELETE /api/v1/finance/bank-account/{id} 业务码"
  CREATED_BANK_ACCOUNT_ID=""
}

# ===========================================================================
# 测试用例 — 项目结算（汇总查询）
# ===========================================================================

# 非成功断言（与 test-api-risk.sh 同名同语义）：body code != 200 或 HTTP 4xx/5xx 即为通过。
# 本文件原本没有该 helper，而月度分析的三个负向用例需要它（仅靠 assert_body_code 400
# 不够稳：Spring 对 @RequestParam 缺失返回的错误体可能没有 code 字段）。
assert_body_not_success() {
  local test_name="$1" actual http_code
  actual=$(grep -oE '"code"\s*:\s*\"?[0-9]+' /tmp/zwi_body 2>/dev/null | head -1 | grep -oE '[0-9]+$')
  http_code=$(cat /tmp/zwi_last_code 2>/dev/null || echo "000")
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [ "$actual" != "200" ] || [[ "$http_code" == 4* ]] || [[ "$http_code" == 5* ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] $test_name (code=$actual, HTTP $http_code)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] $test_name (期望非 200, 实际 code=$actual HTTP $http_code)"
  fi
}

# 跳过项记录（无数据无法验证时使用）。与 test-api-risk.sh 的同名函数有意不同：
# 本文件不将跳过项计入 TOTAL_COUNT（其 report_summary 无 SKIP 行，计入会造成
# TOTAL ≠ PASS+FAIL 的不一致）；跳过事实仅在本行日志中显式标注，**绝不静默当作通过**。
skip_case() {
  local test_name="$1" reason="$2"
  log "  SKIP [-] $test_name（$reason）——未验证，需人工确认，不计入通过数"
}

# ---------------------------------------------------------------------------
# 月度经营分析表（V2026_67，资金流转 §9：10 类费用 × 6 列）
# 本组会写库（generate），但写入的是基于真实 CBS/合同聚合的业务数据，
# 且同项目同月重跑为覆盖更新（唯一键 upsert），不会累积垃圾行。
# ---------------------------------------------------------------------------
test_monthly_analysis() {
  log "▶ 测试：月度经营分析表（§9 十类×六列）"
  local pid=90001
  local month
  month=$(date +%Y-%m)

  # 1) 类别清单：十类且行序与文档一致（前端表头与导出共用，不写死）
  call GET "/api/v1/finance/monthly-analysis/categories"
  assert_http 2 "月度分析-类别清单 HTTP"
  assert_jq '.code==200 and (.data | length == 10)' "月度分析-类别为 §9 十类"
  assert_jq '.code==200 and ([.data | keys[]] | index("ENTERTAIN") != null) and ([.data | keys[]] | index("TRAVEL_VEHICLE") != null) and ([.data | keys[]] | index("PROFESSIONAL") != null) and ([.data | keys[]] | index("TAX") != null)' \
    "月度分析-含招待/差旅车辆/专业服务/财税四类"

  # 2) 手工生成（首次）
  call POST "/api/v1/finance/monthly-analysis/generate?projectId=${pid}&month=${month}"
  assert_http 2 "月度分析-生成 HTTP"
  assert_jq '.code==200 and (.data.rows >= 10)' "月度分析-生成十类行（实得 $(jq -r '.data.rows // 0' /tmp/zwi_body 2>/dev/null) 行）"
  assert_jq '.code==200 and (.data.month == "'"$month"'")' "月度分析-生成月份回写正确"

  # 3) 幂等：同项目同月重跑为覆盖更新（inserted=0、updated=行数）
  call POST "/api/v1/finance/monthly-analysis/generate?projectId=${pid}&month=${month}"
  assert_jq '.code==200 and (.data.inserted == 0) and (.data.updated == .data.rows)' \
    "月度分析-重跑幂等（不重复插行）"

  # 4) 查询矩阵：行序、列齐备、口径标记
  call GET "/api/v1/finance/monthly-analysis?projectId=${pid}&month=${month}"
  assert_http 2 "月度分析-查询 HTTP"
  assert_jq '.code==200 and (.data.rows | type == "array") and (.data.totals | type == "object") and (.data.notes | type == "array")' \
    "月度分析-矩阵/合计/口径说明三段齐备"
  assert_jq '.code==200 and ([.data.rows[].categoryCode] == ["LABOR","MATERIAL","MACHINE","SUBCONTRACT","MEASURE","ADMIN","ENTERTAIN","TRAVEL_VEHICLE","PROFESSIONAL","TAX"])' \
    "月度分析-十类行序与文档一致"
  assert_jq '.code==200 and ([.data.rows[] | select(.budgetAmount == null or .cumulativeOccurred == null or .forecastFinal == null or .occurredBasis == null or .paidBasis == null)] | length == 0)' \
    "月度分析-六列与两个口径标记均非空"
  # 直接费四类有付款数据源（APPROVAL_WRITEBACK），间接费六类为 NO_PAYMENT_SOURCE 且 paid 为 null
  assert_jq '.code==200 and ([.data.rows[] | select(.categoryCode == "LABOR" or .categoryCode == "MATERIAL" or .categoryCode == "MACHINE" or .categoryCode == "SUBCONTRACT") | select(.paidBasis != "APPROVAL_WRITEBACK")] | length == 0)' \
    "月度分析-直接费四类累计支付为审批回写口径"
  assert_jq '.code==200 and ([.data.rows[] | select(.paidBasis == "NO_PAYMENT_SOURCE") | select(.cumulativePaid != null or .payableOutstanding != null)] | length == 0)' \
    "月度分析-无付款数据源的类为空而非 0"
  # 本月发生口径受控；无上月基期时必须为 null（不得用 0 冒充）
  assert_jq '.code==200 and ([.data.rows[] | select(.occurredBasis != "VS_LAST_MONTH" and .occurredBasis != "NO_BASELINE" and .occurredBasis != "NO_DATA_SOURCE")] | length == 0)' \
    "月度分析-本月发生口径值域受控"
  assert_jq '.code==200 and ([.data.rows[] | select(.occurredBasis != "VS_LAST_MONTH") | select(.currentMonthOccurred != null)] | length == 0)' \
    "月度分析-无基期/无数据源时本月发生为 null"
  # 合计口径：应付未付合计 = 有付款数据源类的累计发生 − 累计支付（不混入间接费）
  assert_jq '.code==200 and (.data.totals | ((.directOccurredTotal - .cumulativePaid - .payableOutstanding) | fabs) < 0.01)' \
    "月度分析-应付未付合计=直接费累计发生−累计支付"
  assert_jq '.code==200 and (.data.totals.paidScope == "DIRECT_FOUR_CATEGORIES")' \
    "月度分析-合计口径范围已标明"

  # 5) 负向：未来月份 / 非法格式 / 缺项目均被拒绝（不静默按当月处理）
  local next_month
  next_month=$(date -d "+1 month" +%Y-%m 2>/dev/null || date -v+1m +%Y-%m 2>/dev/null || echo "2099-01")
  call POST "/api/v1/finance/monthly-analysis/generate?projectId=${pid}&month=${next_month}"
  assert_body_not_success "月度分析-未来月份被拒绝"
  call POST "/api/v1/finance/monthly-analysis/generate?projectId=${pid}&month=bad-month"
  assert_body_not_success "月度分析-月份格式非法被拒绝"
  call GET "/api/v1/finance/monthly-analysis?month=${month}"
  assert_body_not_success "月度分析-缺 projectId 被拒绝"
}

# ---------------------------------------------------------------------------
# 应收台账 §10 下钻 8 级链（V2026_69）
# 会写库（登记后随即清空撤回），不残留测试数据。
# ---------------------------------------------------------------------------
test_receivable_drill() {
  log "▶ 测试：应收台账下钻八级链（§10）"
  call GET "/api/v1/finance/receivable/page?page=1&size=1"
  assert_http 2 "应收下钻-台账分页 HTTP"
  local rid
  rid=$(jq -r '.data.records[0].id // empty' /tmp/zwi_body 2>/dev/null)
  if [ -z "$rid" ]; then
    skip_case "应收下钻-八级链全部断言" "应收台账无数据（需先有已审批结算单）"
    return
  fi

  call GET "/api/v1/finance/receivable/drill/$rid"
  assert_http 2 "应收下钻-八级链 HTTP"
  assert_jq '.code==200 and (.data.chain | length == 8)' "应收下钻-共 8 级"
  assert_jq '.code==200 and ([.data.chain[].label] == ["项目","应收款","对应工程节点","应收日期","实际申请日期","甲方审核状态","负责人","下一步动作"])' \
    "应收下钻-八级标签与顺序符合 §10"
  assert_jq '.code==200 and ([.data.chain[].level] == [1,2,3,4,5,6,7,8])' "应收下钻-level 连续 1-8"
  assert_jq '.code==200 and ([.data.chain[] | select(.registered == null)] | length == 0)' \
    "应收下钻-每级均带 registered 标记"
  # 未登记的级 value 必须为 null（不得用“待审核”“未知”等默认词冒充已登记）
  assert_jq '.code==200 and ([.data.chain[] | select(.registered == false) | select(.value != null)] | length == 0)' \
    "应收下钻-未登记级 value 为 null"
  assert_jq '.code==200 and (.data | has("drillInfo") and has("openBalance") and has("overdueDays") and has("unregisteredCount") and has("complete"))' \
    "应收下钻-原始登记值与派生字段齐备"
  assert_jq '.code==200 and (.data | if (.receivableAmount - .writtenOffAmount) > 0 then ((.receivableAmount - .writtenOffAmount - .openBalance) | fabs) < 0.01 else (.openBalance == 0) end)' \
    "应收下钻-未结清余额=应收−已核销（下限 0）"

  # 负向：非法甲方审核状态被拒绝（不静默当作未登记）
  call PUT "/api/v1/finance/receivable/drill/$rid" '{"ownerReviewStatus":"WHATEVER"}'
  assert_body_not_success "应收下钻-非法审核状态被拒绝"

  # 登记：四项人工维护字段
  call PUT "/api/v1/finance/receivable/drill/$rid" '{"milestoneNode":"L3测试节点","ownerReviewStatus":"UNDER_REVIEW","ownerName":"L3测试负责人","nextAction":"L3测试动作"}'
  assert_http 2 "应收下钻-登记 HTTP"
  call GET "/api/v1/finance/receivable/drill/$rid"
  assert_jq '.code==200 and (.data.drillInfo.milestoneNode == "L3测试节点") and (.data.drillInfo.ownerReviewStatus == "UNDER_REVIEW") and (.data.drillInfo.ownerName == "L3测试负责人")' \
    "应收下钻-登记已落库"
  # 八级链中审核状态返回中文标签（不直接把 UNDER_REVIEW 丢给老板）
  assert_jq '.code==200 and ([.data.chain[] | select(.label == "甲方审核状态") | select(.value == "甲方审核中")] | length == 1)' \
    "应收下钻-审核状态展示中文标签"
  assert_jq '.code==200 and (.data.complete == false or .data.complete == true)' "应收下钻-complete 标记存在"

  # 清空（撤回登记）：空串 → null，且审核日期与负责人ID 连带撤回（不残留孤立值）
  call PUT "/api/v1/finance/receivable/drill/$rid" '{"milestoneNode":"","ownerReviewStatus":"","ownerName":"","nextAction":""}'
  assert_http 2 "应收下钻-清空登记 HTTP"
  call GET "/api/v1/finance/receivable/drill/$rid"
  assert_jq '.code==200 and (.data.drillInfo.milestoneNode == null) and (.data.drillInfo.ownerReviewStatus == null) and (.data.drillInfo.ownerReviewDate == null) and (.data.drillInfo.ownerName == null) and (.data.drillInfo.ownerId == null)' \
    "应收下钻-清空后为未登记（审核日期/负责人ID 连带撤回，不残留测试数据）"

  call GET "/api/v1/finance/receivable/drill/999999999"
  assert_body_not_success "应收下钻-记录不存在被拒绝"
}

test_get_settlement_nonexistent() {
  log "▶ 测试：查询不存在的结算单"
  call GET "/api/v1/project-settlements/999999999"
  local code
  code=$(cat /tmp/zwi_last_code 2>/dev/null || echo "000")
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [[ "$code" =~ ^[2-4][0-9][0-9]$ ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] GET 不存在结算单无 5xx (HTTP $code)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] GET 不存在结算单返回 5xx (HTTP $code)"
  fi
}

# ===========================================================================
# 主流程
# ===========================================================================
main() {
  echo ""
  log "═══ L3 API 接口测试：财务模块 (test-api-finance.sh) ═══"
  log "时间: $(date '+%Y-%m-%d %H:%M:%S') | 服务: $BASE"
  echo ""

  # 确保登录
  login || { log "登录失败，无法执行测试"; exit 1; }

  # --- 付款申请 ---
  echo ""
  log "─── 付款申请接口测试 ───"
  test_create_payment_apply
  test_page_payment_apply
  test_page_payment_apply_by_project
  test_get_payment_apply_detail
  test_update_payment_apply
  test_submit_payment_apply
  test_delete_payment_apply

  # --- 收款登记 ---
  echo ""
  log "─── 收款登记接口测试 ───"
  test_create_payment_received
  test_page_payment_received
  test_page_payment_received_by_project
  test_get_payment_received_detail
  test_update_payment_received
  test_delete_payment_received

  # --- 银行账户 ---
  echo ""
  log "─── 银行账户接口测试 ───"
  test_create_bank_account
  test_page_bank_account
  test_page_bank_account_by_type
  test_delete_bank_account

  # --- 汇总查询 ---
  echo ""
  log "─── 项目结算汇总查询测试 ───"
  test_get_settlement_nonexistent

  # --- 月度经营分析表（V2026_67，资金流转 §9：10 类×6 列）---
  echo ""
  log "─── 月度经营分析表测试 ───"
  test_monthly_analysis

  # --- 应收台账下钻八级链（V2026_69，驾驶舱 §10）---
  echo ""
  log "─── 应收台账下钻八级链测试 ───"
  test_receivable_drill

  # 日志核对
  echo ""
  log "▶ 后端日志核对"
  check_logs 120

  # 输出汇总
  report_summary
  exit $?
}

main "$@"
