#!/usr/bin/env bash
###############################################################################
# test-api-batch3.sh — 批次3 API 层真实链路验证（劳务/机械/分包/付款闭环）
#
# 背景（2026-08-17 归零重建全链路 E2E）：
#   缺陷#9 劳务/机械合同表单无项目字段的前端修复已提交本地，待统一 CI 部署；
#   本脚本在 API 层（携带 projectId）先行验证批次3 业务链路正确性：
#   劳务合同+结算 / 机械合同+台账+日志+结算审批 / 分包合同+结算 /
#   付款申请提交+Flowable 审批+累计已付回写，含超合同结算、超可付金额负向用例。
#
# v2 修正（首跑取证）：
#   1) 所有 POST body 一律 jq -n 构造（首跑用 shell 拼接导致 JSON 残缺 parse error）
#   2) GET 查询中文参数一律 uri() 编码（Tomcat 拒绝 URL 裸中文返回 400）
#
# 前置：tmp/e2e/prep-batch3-api.sh（补预算明细 88002-88004 + 清残留）
# 依赖：verify-base.sh 登录基座；全程真实接口真实数据，断言失败即 FAIL。
###############################################################################
set -uo pipefail
cd "$(dirname "$0")"
source ./verify-base.sh usage >/dev/null 2>&1 || true

PASS=0; FAIL=0
result() { echo "[$1] $2${3:+ — $3}"; if [ "$1" = PASS ]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi; }

call2() { # call2 <METHOD> <PATH> [BODY] → 写 /tmp/zwi_body3，echo HTTP code
  local method="$1" path="$2" body="${3:-}" token code
  token=$(get_token) || { echo "000"; return 1; }
  if [ -n "$body" ]; then
    code=$(curl -s -m 20 -o /tmp/zwi_body3 -w '%{http_code}' -X "$method" "$BASE$path" \
      -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "$body")
  else
    code=$(curl -s -m 20 -o /tmp/zwi_body3 -w '%{http_code}' -X "$method" "$BASE$path" \
      -H "Authorization: Bearer $token")
  fi
  echo "$code"
}
jqget() { jq -r "$1" /tmp/zwi_body3 2>/dev/null; }
ok200() { [ "$1" = 200 ] && { [ "$(jqget '.code')" = 200 ] || [ "$(jqget '.code')" = 0 ]; }; }
uri() { printf '%s' "$1" | jq -sRr @uri; }
dbq() { docker exec zwi-mysql mysql -uroot -pzwinsight123 zw_insight -N -B -e "$1" 2>/dev/null | tr -d '\r' | head -1; }

# 逐个完成指定流程实例的待办任务（含候选组任务，admin 为 SUPER_ADMIN 可办理）
approve_flow() {
  local inst="$1" i=0 tid code
  while [ $i -lt 6 ]; do
    tid=$(dbq "SELECT ID_ FROM ACT_RU_TASK WHERE PROC_INST_ID_='$inst' ORDER BY CREATE_TIME_ ASC LIMIT 1;")
    [ -z "$tid" ] && return 0
    code=$(call2 POST /api/v1/workflow/approval/complete "$(jq -n --arg t "$tid" '{taskId:$t, comment:"批次3 自动审批通过"}')")
    if ! ok200 "$code"; then echo "      审批任务失败 taskId=$tid body=$(head -c 200 /tmp/zwi_body3)"; return 1; fi
    sleep 1; i=$((i+1))
  done
  tid=$(dbq "SELECT ID_ FROM ACT_RU_TASK WHERE PROC_INST_ID_='$inst' LIMIT 1;")
  [ -z "$tid" ] && return 0 || return 1
}

PID=2089276036854378498

echo "== L0 登录 =="
get_token >/dev/null || { echo "[FAIL] L0 登录失败"; exit 1; }
result PASS "L0 登录获取 token" ""

echo "== S1 创建供应商（付款申请收款单位） =="
BODY=$(jq -n '{supplierName:"批次3劳务分包供应商",supplierType:"SUBCONTRACT",contactName:"张三",contactPhone:"13800138003"}')
code=$(call2 POST /api/v1/basedata/supplier "$BODY")
if ok200 "$code"; then result PASS "S1a 供应商创建" ""; else result FAIL "S1a 供应商创建" "http=$code body=$(head -c 200 /tmp/zwi_body3)"; fi
code=$(call2 GET "/api/v1/basedata/supplier/page?page=1&size=20&supplierName=$(uri 批次3劳务分包供应商)")
SUPPLIER_ID=$(jqget '.data.records[0].id')
if [ -n "$SUPPLIER_ID" ] && [ "$SUPPLIER_ID" != "null" ]; then result PASS "S1b 供应商可查" "id=$SUPPLIER_ID"; else result FAIL "S1b 供应商查询" "body=$(head -c 200 /tmp/zwi_body3)"; fi

echo "== S2 劳务合同（预算管控在 save，LABOR 额度 500万） =="
BODY=$(jq -n --argjson pid "$PID" '{projectId:$pid,contractName:"批次3-劳务超额负向",teamName:"批次3木工班组",contractAmount:99999999,startDate:"2026-08-01",endDate:"2026-12-31"}')
code=$(call2 POST /api/v1/labor/contract "$BODY")
MSG=$(jqget '.message // .msg // ""')
if ! ok200 "$code"; then result PASS "S2a 超额劳务合同被预算拦截" "msg=$MSG"; else result FAIL "S2a 超额劳务合同未被拦截(缺陷)" "msg=$MSG"; fi
BODY=$(jq -n --argjson pid "$PID" '{projectId:$pid,contractName:"批次3-劳务合同正向",teamName:"批次3木工班组",contractAmount:500000,startDate:"2026-08-01",endDate:"2026-12-31"}')
code=$(call2 POST /api/v1/labor/contract "$BODY")
if ok200 "$code"; then result PASS "S2b 劳务合同创建(50万)" ""; else result FAIL "S2b 劳务合同创建" "http=$code body=$(head -c 300 /tmp/zwi_body3)"; fi
call2 GET "/api/v1/labor/contract/page?page=1&size=10&projectId=$PID&contractName=$(uri 批次3-劳务合同正向)" >/dev/null
LABOR_CID=$(jqget '.data.records[0].id')
if [ -n "$LABOR_CID" ] && [ "$LABOR_CID" != "null" ]; then
  code=$(call2 POST "/api/v1/labor/contract/$LABOR_CID/submit")
  if ok200 "$code"; then result PASS "S2c 劳务合同提交" ""; else result FAIL "S2c 劳务合同提交" "body=$(head -c 300 /tmp/zwi_body3)"; fi
  call2 GET "/api/v1/labor/contract/$LABOR_CID" >/dev/null
  ST=$(jqget '.data.status')
  if [ "$ST" = "EFFECTIVE" ]; then result PASS "S2d 劳务合同=EFFECTIVE" ""; else result FAIL "S2d 劳务合同状态" "实际=$ST"; fi
else result FAIL "S2b 劳务合同查询" "未找到"; fi

echo "== S3 劳务结算（submit 回写合同累计结算，上限=合同金额） =="
BODY=$(jq -n --argjson pid "$PID" --argjson cid "${LABOR_CID:-0}" '{projectId:$pid,contractId:$cid,settlementAmount:200000}')
code=$(call2 POST /api/v1/labor/settlement "$BODY")
if ok200 "$code"; then result PASS "S3a 劳务结算创建(20万)" ""; else result FAIL "S3a 劳务结算创建" "body=$(head -c 300 /tmp/zwi_body3)"; fi
call2 GET "/api/v1/labor/settlement/page?page=1&size=10&projectId=$PID" >/dev/null
LABOR_SID=$(jqget '[.data.records[] | select(.status=="DRAFT")][0].id // empty')
code=$(call2 POST "/api/v1/labor/settlement/$LABOR_SID/submit")
if ok200 "$code"; then result PASS "S3b 劳务结算提交(APPROVED)" ""; else result FAIL "S3b 劳务结算提交" "body=$(head -c 300 /tmp/zwi_body3)"; fi
call2 GET "/api/v1/labor/contract/$LABOR_CID" >/dev/null
CUM=$(jqget '.data.cumulativeSettlement')
case "$CUM" in 200000|200000.00|200000.0000) result PASS "S3c 合同累计结算=200000" "" ;; *) result FAIL "S3c 累计结算回写" "实际=$CUM" ;; esac
BODY=$(jq -n --argjson pid "$PID" --argjson cid "${LABOR_CID:-0}" '{projectId:$pid,contractId:$cid,settlementAmount:500000}')
code=$(call2 POST /api/v1/labor/settlement "$BODY")
call2 GET "/api/v1/labor/settlement/page?page=1&size=10&projectId=$PID" >/dev/null
NEG_SID=$(jqget '[.data.records[] | select(.status=="DRAFT")][0].id // empty')
if [ -n "$NEG_SID" ]; then
  code=$(call2 POST "/api/v1/labor/settlement/$NEG_SID/submit")
  MSG=$(jqget '.message // .msg // ""')
  if ! ok200 "$code" && echo "$MSG" | grep -q "超出合同金额"; then result PASS "S3d 超合同结算被拒(最大30万)" "msg=$MSG"; else result FAIL "S3d 超合同结算未被拒(缺陷)" "msg=$MSG"; fi
  call2 DELETE "/api/v1/labor/settlement/$NEG_SID" >/dev/null
else result FAIL "S3d 负向结算单准备" "未找到草稿结算单"; fi

echo "== S4 机械合同（预算管控在 submit，MACHINE 额度 500万，台班计价1500） =="
BODY=$(jq -n --argjson pid "$PID" '{projectId:$pid,contractName:"批次3-机械租赁正向",supplierName:"批次3机械租赁公司",machineName:"批次3号挖掘机",contractAmount:1500,rentalType:"台班",startDate:"2026-08-01",endDate:"2026-12-31"}')
code=$(call2 POST /api/v1/machine/contract "$BODY")
if ok200 "$code"; then result PASS "S4a 机械合同创建" ""; else result FAIL "S4a 机械合同创建" "http=$code body=$(head -c 300 /tmp/zwi_body3)"; fi
call2 GET "/api/v1/machine/contract/page?page=1&size=10&projectId=$PID&contractName=$(uri 批次3-机械租赁正向)" >/dev/null
MACH_CID=$(jqget '.data.records[0].id')
if [ -n "$MACH_CID" ] && [ "$MACH_CID" != "null" ]; then
  code=$(call2 POST "/api/v1/machine/contract/$MACH_CID/submit")
  if ok200 "$code"; then result PASS "S4b 机械合同提交(预算通过)" ""; else result FAIL "S4b 机械合同提交" "body=$(head -c 300 /tmp/zwi_body3)"; fi
  call2 GET "/api/v1/machine/contract/$MACH_CID" >/dev/null
  ST=$(jqget '.data.status')
  if [ "$ST" = "EFFECTIVE" ]; then result PASS "S4c 机械合同=EFFECTIVE" ""; else result FAIL "S4c 机械合同状态" "实际=$ST"; fi
else result FAIL "S4a 机械合同查询" "未找到"; fi

echo "== S5 机械结算（台账+工作日志+周期结算+Flowable 审批） =="
BODY=$(jq -n --argjson pid "$PID" '{projectId:$pid,periodStart:"2025-01-01",periodEnd:"2025-01-31"}')
code=$(call2 POST /api/v1/machine/settlement "$BODY")
MSG=$(jqget '.message // .msg // ""')
if ! ok200 "$code" && echo "$MSG" | grep -q "无可结算"; then result PASS "S5a 无工作量周期被拒" "msg=$MSG"; else result FAIL "S5a 无工作量未被拒(缺陷)" "http=$code msg=$MSG"; fi
BODY=$(jq -n '{machineName:"批次3号挖掘机",machineCode:"JX-B3-001",machineType:"挖掘机",ownerType:"RENT"}')
code=$(call2 POST /api/v1/machine/ledger "$BODY")
if ok200 "$code"; then result PASS "S5b 机械台账创建(REGISTERED)" ""; else result FAIL "S5b 机械台账创建" "body=$(head -c 300 /tmp/zwi_body3)"; fi
call2 GET "/api/v1/machine/ledger/page?page=1&size=20&machineName=$(uri 批次3号挖掘机)" >/dev/null
LEDGER_ID=$(jqget '.data.records[0].id')
# 台账状态由进出场状态机维护（save 强制 REGISTERED），须走进场接口置 IN_FIELD 方可记日志
BODY=$(jq -n --argjson mid "${LEDGER_ID:-0}" --argjson pid "$PID" '{machineId:$mid,projectId:$pid,entryDate:"2026-08-01"}')
code=$(call2 POST /api/v1/machine/entry/in "$BODY")
if ok200 "$code"; then result PASS "S5b2 机械进场(IN_FIELD)" ""; else result FAIL "S5b2 机械进场" "body=$(head -c 300 /tmp/zwi_body3)"; fi
BODY=$(jq -n --argjson mid "${LEDGER_ID:-0}" --argjson pid "$PID" '{machineId:$mid,projectId:$pid,workDate:"2026-08-10",shiftCount:2,workQuantity:0}')
code=$(call2 POST /api/v1/machine/work-log "$BODY")
if ok200 "$code"; then result PASS "S5c 工作日志创建(2台班)" ""; else result FAIL "S5c 工作日志创建" "body=$(head -c 300 /tmp/zwi_body3)"; fi
BODY=$(jq -n --argjson pid "$PID" '{projectId:$pid,periodStart:"2026-08-01",periodEnd:"2026-08-31"}')
code=$(call2 POST /api/v1/machine/settlement "$BODY")
MACH_SID=$(jqget '.data.settlementId // .data.id // empty')
if ok200 "$code" && [ -n "$MACH_SID" ]; then
  result PASS "S5d 结算单创建" "id=$MACH_SID"
  call2 GET "/api/v1/machine/settlement/$MACH_SID" >/dev/null
  AMT=$(jqget '.data.totalAmount')
  case "$AMT" in 3000|3000.00|3000.0) result PASS "S5e 结算金额=3000(2台班×1500)" "" ;; *) result FAIL "S5e 结算金额" "实际=$AMT" ;; esac
  code=$(call2 POST "/api/v1/machine/settlement/$MACH_SID/submit")
  if ok200 "$code"; then result PASS "S5f 提交审批(启动流程)" ""; else result FAIL "S5f 提交审批" "body=$(head -c 300 /tmp/zwi_body3)"; fi
  INST=$(dbq "SELECT workflow_instance_id FROM biz_machine_work_settlement WHERE id=$MACH_SID;")
  if [ -n "$INST" ] && approve_flow "$INST"; then
    result PASS "S5g 审批流完成" ""
  else
    result FAIL "S5g 审批流完成" "inst=$INST"
  fi
  call2 GET "/api/v1/machine/settlement/$MACH_SID" >/dev/null
  ST=$(jqget '.data.status')
  if [ "$ST" = "2" ]; then result PASS "S5h 结算单=已审批(2)" ""; else result FAIL "S5h 结算单状态" "实际=$ST"; fi
  call2 GET "/api/v1/machine/contract/$MACH_CID" >/dev/null
  CUM=$(jqget '.data.cumulativeSettlement')
  case "$CUM" in 3000|3000.00|3000.0) result PASS "S5i 机械合同累计结算=3000" "" ;; *) result FAIL "S5i 机械合同累计结算" "实际=$CUM" ;; esac
else result FAIL "S5d 结算单创建" "http=$code body=$(head -c 300 /tmp/zwi_body3)"; fi

echo "== S6 分包合同（预算管控在 submit，SUBCONTRACT 额度 500万） =="
BODY=$(jq -n --argjson pid "$PID" '{projectId:$pid,contractName:"批次3-分包正向",subcontractor:"批次3劳务分包供应商",contractAmount:800000,content:"批次3土方分包",signingDate:"2026-08-01"}')
code=$(call2 POST /api/v1/subcontract/contract "$BODY")
if ok200 "$code"; then result PASS "S6a 分包合同创建(80万)" ""; else result FAIL "S6a 分包合同创建" "body=$(head -c 300 /tmp/zwi_body3)"; fi
call2 GET "/api/v1/subcontract/contract/page?page=1&size=10&projectId=$PID&contractName=$(uri 批次3-分包正向)" >/dev/null
SUB_CID=$(jqget '.data.records[0].id')
if [ -n "$SUB_CID" ] && [ "$SUB_CID" != "null" ]; then
  code=$(call2 POST "/api/v1/subcontract/contract/$SUB_CID/submit")
  if ok200 "$code"; then result PASS "S6b 分包合同提交(预算通过)" ""; else result FAIL "S6b 分包合同提交" "body=$(head -c 300 /tmp/zwi_body3)"; fi
  call2 GET "/api/v1/subcontract/contract/$SUB_CID" >/dev/null
  ST=$(jqget '.data.status')
  if [ "$ST" = "EFFECTIVE" ]; then result PASS "S6c 分包合同=EFFECTIVE" ""; else result FAIL "S6c 分包合同状态" "实际=$ST"; fi
else result FAIL "S6a 分包合同查询" "未找到"; fi

echo "== S7 分包结算（明细行创建，submit 回写累计结算） =="
BODY=$(jq -n --argjson cid "${SUB_CID:-0}" --argjson pid "$PID" '{contractId:$cid,projectId:$pid,details:[{itemName:"土方开挖",unit:"m3",quantity:1000,unitPrice:500}]}')
code=$(call2 POST /api/v1/subcontract/settlement "$BODY")
SUB_SID=$(jqget '.data // empty')
if ok200 "$code" && [ -n "$SUB_SID" ] && [ "$SUB_SID" != "null" ]; then result PASS "S7a 分包结算创建(50万)" "id=$SUB_SID"; else result FAIL "S7a 分包结算创建" "body=$(head -c 300 /tmp/zwi_body3)"; fi
BODY=$(jq -n --argjson cid "${SUB_CID:-0}" --argjson pid "$PID" '{contractId:$cid,projectId:$pid,details:[{itemName:"土方开挖超量",unit:"m3",quantity:2000,unitPrice:500}]}')
code=$(call2 POST /api/v1/subcontract/settlement "$BODY")
SUB_NEG_ID=$(jqget '.data // empty')
if [ -n "$SUB_NEG_ID" ] && [ "$SUB_NEG_ID" != "null" ]; then
  code=$(call2 POST "/api/v1/subcontract/settlement/$SUB_NEG_ID/submit")
  MSG=$(jqget '.message // .msg // ""')
  if ! ok200 "$code" && echo "$MSG" | grep -q "超出合同金额"; then result PASS "S7b 超合同结算被拒(100万>80万)" "msg=$MSG"; else result FAIL "S7b 超合同结算未被拒(缺陷)" "msg=$MSG"; fi
  call2 DELETE "/api/v1/subcontract/settlement/$SUB_NEG_ID" >/dev/null
else result FAIL "S7b 负向结算单准备" "body=$(head -c 200 /tmp/zwi_body3)"; fi
code=$(call2 POST "/api/v1/subcontract/settlement/$SUB_SID/submit")
if ok200 "$code"; then result PASS "S7c 分包结算提交(APPROVED)" ""; else result FAIL "S7c 分包结算提交" "body=$(head -c 300 /tmp/zwi_body3)"; fi
call2 GET "/api/v1/subcontract/contract/$SUB_CID" >/dev/null
CUM=$(jqget '.data.cumulativeSettlement')
case "$CUM" in 500000|500000.00|500000.0000) result PASS "S7d 分包合同累计结算=500000" "" ;; *) result FAIL "S7d 累计结算回写" "实际=$CUM" ;; esac

echo "== S8 付款申请（校验≤累计结算-已付，Flowable 审批后回写累计已付） =="
BODY=$(jq -n --argjson pid "$PID" --argjson cid "${SUB_CID:-0}" --argjson sid "${SUPPLIER_ID:-0}" '{projectId:$pid,contractId:$cid,contractCategory:"SUBCONTRACT",supplierId:$sid,supplierName:"批次3劳务分包供应商",paymentAmount:600000,paymentDate:"2026-08-17"}')
code=$(call2 POST /api/v1/finance/payment-apply "$BODY")
if ok200 "$code"; then
  call2 GET "/api/v1/finance/payment-apply/page?page=1&size=10&projectId=$PID" >/dev/null
  NEG_PAY=$(jqget '[.data.records[] | select(.paymentAmount==600000 and .status=="DRAFT")][0].id // empty')
  if [ -n "$NEG_PAY" ]; then
    code=$(call2 POST "/api/v1/finance/payment-apply/$NEG_PAY/submit")
    MSG=$(jqget '.message // .msg // ""')
    if ! ok200 "$code" && echo "$MSG" | grep -q "超过"; then result PASS "S8a 超可付金额提交被拒(60万>结算50万)" "msg=$MSG"; else result FAIL "S8a 超可付金额未被拒(缺陷)" "msg=$MSG"; fi
    call2 DELETE "/api/v1/finance/payment-apply/$NEG_PAY" >/dev/null
  else result FAIL "S8a 负向付款单准备" "未找到60万草稿"; fi
else result FAIL "S8a 负向付款单创建" "body=$(head -c 300 /tmp/zwi_body3)"; fi
BODY=$(jq -n --argjson pid "$PID" --argjson cid "${SUB_CID:-0}" --argjson sid "${SUPPLIER_ID:-0}" '{projectId:$pid,contractId:$cid,contractCategory:"SUBCONTRACT",supplierId:$sid,supplierName:"批次3劳务分包供应商",paymentAmount:200000,paymentDate:"2026-08-17"}')
code=$(call2 POST /api/v1/finance/payment-apply "$BODY")
if ok200 "$code"; then result PASS "S8b 付款申请创建(20万)" ""; else result FAIL "S8b 付款申请创建" "body=$(head -c 300 /tmp/zwi_body3)"; fi
call2 GET "/api/v1/finance/payment-apply/page?page=1&size=10&projectId=$PID" >/dev/null
PAY_ID=$(jqget '[.data.records[] | select(.status=="DRAFT")][0].id // empty')
if [ -n "$PAY_ID" ]; then
  code=$(call2 POST "/api/v1/finance/payment-apply/$PAY_ID/submit")
  if ok200 "$code"; then result PASS "S8c 付款提交(SUBMITTED,启动流程)" ""; else result FAIL "S8c 付款提交" "body=$(head -c 300 /tmp/zwi_body3)"; fi
  call2 GET "/api/v1/finance/payment-apply/$PAY_ID" >/dev/null
  ST=$(jqget '.data.status'); INST=$(jqget '.data.workflowInstanceId')
  if [ "$ST" = "SUBMITTED" ]; then result PASS "S8d 状态=SUBMITTED" ""; else result FAIL "S8d 付款状态" "实际=$ST"; fi
  if [ -n "$INST" ] && [ "$INST" != "null" ] && approve_flow "$INST"; then
    result PASS "S8e 审批流完成" ""
  else
    result FAIL "S8e 审批流完成" "inst=$INST"
  fi
  call2 GET "/api/v1/finance/payment-apply/$PAY_ID" >/dev/null
  ST=$(jqget '.data.status')
  if [ "$ST" = "APPROVED" ]; then result PASS "S8f 付款=APPROVED" ""; else result FAIL "S8f 付款状态" "实际=$ST"; fi
  call2 GET "/api/v1/subcontract/contract/$SUB_CID" >/dev/null
  PAID=$(jqget '.data.cumulativePaid')
  case "$PAID" in 200000|200000.00|200000.0000) result PASS "S8g 分包合同累计已付=200000" "" ;; *) result FAIL "S8g 累计已付回写" "实际=$PAID" ;; esac
else result FAIL "S8b 付款单查询" "未找到草稿付款申请"; fi

echo ""
echo "===== 批次3 API 验证汇总: PASS=$PASS FAIL=$FAIL ====="
[ "$FAIL" -eq 0 ]
