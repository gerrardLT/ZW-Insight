#!/usr/bin/env bash
###############################################################################
# test-api-batch3.sh — 批次3 API 层真实链路验证（劳务/机械/分包/付款闭环，v3 自包含版）
#
# v3 改造（2026-08-20，全量 CI run 32381101540 暴露数据态依赖后用户决策改造）：
#   原版硬编码租户 1 项目 PID=2089276036854378498 并依赖 prep 脚本补预算明细，
#   首次进全量 CI 即因部署环境残留失败（S5d「该项目+合同组合已存在结算单且审批中」）。
#   v3 自包含：登录隔离租户 9999（t9999admin，CI L3 前置 init-test-tenant.sh
#   已种全套编号规则/模块开通），自建 项目→立项→施工合同→预算(BLOCK 管控，
#   LABOR/MACHINE/SUBCONTRACT 各 500万科目明细) 全前置链；机械台账/合同名带
#   时间戳唯一后缀避免残留撞单。跑完 S1-S8 后逆序 API 清理 + SQL 兜底
#   （仅本轮 project_id + tenant_id=9999）。不再依赖任何既有数据态，可重复执行。
#   建链范式以 lifecycle-sim-v2.sh（租户 9999 实测 26/26 绿）为准。
#
# 业务链路：S1 供应商 / S2 劳务合同(管控在 save，正向50万+负向超额) /
#   S3 劳务结算(submit 回写累计结算，超合同被拒) / S4 机械合同(管控在 submit) /
#   S5 机械结算(台账→进场→工作日志→周期结算→Flowable 审批→合同累计回写) /
#   S6 分包合同 / S7 分包结算(明细行，超量被拒) /
#   S8 付款申请(超可付被拒→正向→审批→累计已付回写)。
#
# 编写约束（沿用批次3 v2 经验）：
#   1) 所有 POST body 一律 jq -n 构造
#   2) GET 查询中文参数一律 uri() 编码
#
# 依赖：verify-base.sh 登录基座（ZWI_USER/ZWI_PASS/ZWI_WORKDIR 覆盖）。
# 全程真实接口真实数据，禁止 mock。断言失败即 FAIL，退出码反映结果。
###############################################################################
set -uo pipefail
cd "$(dirname "$0")"
export ZWI_USER="${ZWI_USER:-t9999admin}"
export ZWI_PASS="${ZWI_PASS:-123456}"
export ZWI_WORKDIR="${ZWI_WORKDIR:-/root/zwi-deploy/batch3}"
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

# 逐个完成指定流程实例的待办任务（t9999admin 为租户 9999 SUPER_ADMIN 可办理候选组任务）
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

TS=$(date +%s)
PNAME="E2E_TEST_B3_${TS}"
PROJECT_ID=""; CC_ID=""; BUDGET_ID=""; CTRL_ID=""; SUPPLIER_ID=""
LABOR_CID=""; MACH_CID=""; LEDGER_ID=""; MACH_SID=""; SUB_CID=""; SUB_SID=""; PAY_ID=""

# 逆序清理（API 优先，失败不阻断；结尾 SQL 兜底限本轮 project_id + tenant 9999）
cleanup() {
  echo "== 清理：逆序删除本轮自建数据 =="
  [ -n "$PAY_ID" ]      && call2 DELETE "/api/v1/finance/payment-apply/$PAY_ID" >/dev/null
  [ -n "$SUB_SID" ]     && call2 DELETE "/api/v1/subcontract/settlement/$SUB_SID" >/dev/null
  [ -n "$SUB_CID" ]     && call2 DELETE "/api/v1/subcontract/contract/$SUB_CID" >/dev/null
  [ -n "$MACH_SID" ]    && call2 DELETE "/api/v1/machine/settlement/$MACH_SID" >/dev/null
  [ -n "$MACH_CID" ]    && call2 DELETE "/api/v1/machine/contract/$MACH_CID" >/dev/null
  [ -n "$LEDGER_ID" ]   && call2 DELETE "/api/v1/machine/ledger/$LEDGER_ID" >/dev/null
  [ -n "$LABOR_CID" ]   && call2 DELETE "/api/v1/labor/contract/$LABOR_CID" >/dev/null
  [ -n "$SUPPLIER_ID" ] && call2 DELETE "/api/v1/basedata/supplier/$SUPPLIER_ID" >/dev/null
  [ -n "$CTRL_ID" ]     && call2 DELETE "/api/v1/budget-control-configs/$CTRL_ID" >/dev/null
  [ -n "$BUDGET_ID" ]   && call2 DELETE "/api/v1/budget/$BUDGET_ID" >/dev/null
  [ -n "$CC_ID" ]       && call2 DELETE "/api/v1/contract/$CC_ID" >/dev/null
  [ -n "$PROJECT_ID" ]  && call2 DELETE "/api/v1/project/$PROJECT_ID" >/dev/null
  if [ -n "$PROJECT_ID" ]; then
    dbq "DELETE FROM biz_payment_apply WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_subcontract_settlement_detail WHERE settlement_id IN (SELECT id FROM biz_subcontract_settlement WHERE project_id=$PROJECT_ID AND tenant_id=9999);" >/dev/null
    dbq "DELETE FROM biz_subcontract_settlement WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_subcontract WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_machine_work_settlement_detail WHERE settlement_id IN (SELECT id FROM biz_machine_work_settlement WHERE project_id=$PROJECT_ID AND tenant_id=9999);" >/dev/null
    dbq "DELETE FROM biz_machine_work_settlement WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_machine_work_log WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_machine_entry WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_machine_contract WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    [ -n "$LEDGER_ID" ] && dbq "DELETE FROM biz_machine_ledger WHERE id=$LEDGER_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_labor_settlement WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_labor_contract WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    [ -n "$SUPPLIER_ID" ] && dbq "DELETE FROM bd_supplier WHERE id=$SUPPLIER_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_budget_detail WHERE budget_id IN (SELECT id FROM biz_budget WHERE project_id=$PROJECT_ID AND tenant_id=9999);" >/dev/null
    dbq "DELETE FROM biz_budget WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM sys_budget_control_config WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_construction_contract WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_project WHERE id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
  fi
  echo "清理完成"
}
trap cleanup EXIT

echo "== L0 登录（租户 9999 / $ZWI_USER） =="
get_token >/dev/null || { echo "[FAIL] L0 登录失败"; trap - EXIT; exit 1; }
result PASS "L0 登录获取 token" ""

echo "== S0a 自建项目（needTender=0 免招投标，L4 范式） =="
BODY=$(jq -n --arg pn "$PNAME" '{projectName:$pn, projectNature:"新建", projectType:"公共建筑", ownerCompanyName:"批次3自包含业主", signingCompanyName:"批次3自包含承包方", projectOverview:"批次3 劳务/机械/分包/付款自包含验证项目", projectAddress:"广州市天河区", contactName:"批次3", contactPhone:"13900000003", needTender:0, budgetAmount:15000000}')
code=$(call2 POST /api/v1/project "$BODY")
if ok200 "$code"; then result PASS "S0a 项目创建" ""; else result FAIL "S0a 项目创建" "http=$code body=$(head -c 300 /tmp/zwi_body3)"; trap - EXIT; exit 1; fi
call2 GET "/api/v1/project/page?page=1&size=5&projectName=$(uri "$PNAME")" >/dev/null
PROJECT_ID=$(jqget '.data.records[0].id')
[ -n "$PROJECT_ID" ] && [ "$PROJECT_ID" != "null" ] || { result FAIL "S0a 项目查询" "未找到 $PNAME"; trap - EXIT; exit 1; }
code=$(call2 POST "/api/v1/project/$PROJECT_ID/submit")
if ok200 "$code"; then result PASS "S0a2 立项提交(FILED)" ""; else result FAIL "S0a2 立项提交" "body=$(head -c 300 /tmp/zwi_body3)"; fi

echo "== S0b 自建施工合同（1500万，submit→EFFECTIVE，L4 范式） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" '{projectId:$pid, contractType:"REGISTER", partyAName:"批次3自包含业主", signingDate:"2026-08-19", startDate:"2026-08-20", endDate:"2026-12-31", contractAmount:15000000, taxRate:9.00}')
code=$(call2 POST /api/v1/contract "$BODY")
if ok200 "$code"; then result PASS "S0b 施工合同创建" ""; else result FAIL "S0b 施工合同创建" "http=$code body=$(head -c 300 /tmp/zwi_body3)"; fi
call2 GET "/api/v1/contract/page?page=1&size=5&projectId=$PROJECT_ID" >/dev/null
CC_ID=$(jqget '.data.records[0].id')
code=$(call2 POST "/api/v1/contract/$CC_ID/submit")
if ok200 "$code"; then result PASS "S0b2 施工合同提交" ""; else result FAIL "S0b2 施工合同提交" "body=$(head -c 300 /tmp/zwi_body3)"; fi
sleep 1
call2 GET "/api/v1/contract/$CC_ID" >/dev/null
ST=$(jqget '.data.status')
if [ "$ST" = "SUBMITTED" ]; then
  INST=$(dbq "SELECT workflow_instance_id FROM biz_construction_contract WHERE id=$CC_ID;")
  [ -n "$INST" ] && approve_flow "$INST" >/dev/null
  call2 GET "/api/v1/contract/$CC_ID" >/dev/null; ST=$(jqget '.data.status')
fi
if [ "$ST" = "EFFECTIVE" ]; then result PASS "S0b3 施工合同=EFFECTIVE" ""; else result FAIL "S0b3 施工合同状态" "实际=$ST"; fi

echo "== S0c 自建预算（ORIGINAL 1500万 + LABOR/MACHINE/SUBCONTRACT 各500万科目明细，submit 直批 APPROVED） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" '{projectId:$pid, budgetType:"ORIGINAL", totalAmount:15000000, details:[{costCategory:"LABOR", itemName:"批次3劳务预算", unit:"项", budgetQuantity:1, budgetUnitPrice:5000000, budgetTotalPrice:5000000},{costCategory:"MACHINE", itemName:"批次3机械预算", unit:"项", budgetQuantity:1, budgetUnitPrice:5000000, budgetTotalPrice:5000000},{costCategory:"SUBCONTRACT", itemName:"批次3分包预算", unit:"项", budgetQuantity:1, budgetUnitPrice:5000000, budgetTotalPrice:5000000}]}')
code=$(call2 POST /api/v1/budget "$BODY")
if ok200 "$code"; then result PASS "S0c 预算创建" ""; else result FAIL "S0c 预算创建" "http=$code body=$(head -c 300 /tmp/zwi_body3)"; fi
call2 GET "/api/v1/budget/project/$PROJECT_ID" >/dev/null
BUDGET_ID=$(jqget '.data.id // .data[0].id // empty')
code=$(call2 POST "/api/v1/budget/$BUDGET_ID/submit")
if ok200 "$code"; then result PASS "S0c2 预算提交(APPROVED)" ""; else result FAIL "S0c2 预算提交" "body=$(head -c 300 /tmp/zwi_body3)"; fi

echo "== S0d 预算管控配置（BLOCK，阈值 80） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" '{projectId:$pid, controlMode:"BLOCK", warningThreshold:80}')
code=$(call2 POST /api/v1/budget-control-configs "$BODY")
if ok200 "$code"; then result PASS "S0d 管控配置 BLOCK" ""; else result FAIL "S0d 管控配置" "body=$(head -c 300 /tmp/zwi_body3)"; fi
call2 GET "/api/v1/budget-control-configs?page=1&size=5&projectId=$PROJECT_ID" >/dev/null
CTRL_ID=$(jqget '.data.records[0].id // .data[0].id // empty')

echo "== S1 创建供应商（付款申请收款单位，唯一名防残留撞单） =="
BODY=$(jq -n --arg pn "$PNAME" '{supplierName:($pn+"_供应商"),supplierType:"SUBCONTRACT",contactName:"张三",contactPhone:"13800138003"}')
code=$(call2 POST /api/v1/basedata/supplier "$BODY")
if ok200 "$code"; then result PASS "S1a 供应商创建" ""; else result FAIL "S1a 供应商创建" "http=$code body=$(head -c 200 /tmp/zwi_body3)"; fi
code=$(call2 GET "/api/v1/basedata/supplier/page?page=1&size=20&supplierName=$(uri "${PNAME}_供应商")")
SUPPLIER_ID=$(jqget '.data.records[0].id')
if [ -n "$SUPPLIER_ID" ] && [ "$SUPPLIER_ID" != "null" ]; then result PASS "S1b 供应商可查" "id=$SUPPLIER_ID"; else result FAIL "S1b 供应商查询" "body=$(head -c 200 /tmp/zwi_body3)"; fi

echo "== S2 劳务合同（预算管控在 save，LABOR 额度 500万） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" --arg pn "$PNAME" '{projectId:$pid,contractName:($pn+"_劳务超额负向"),teamName:"批次3木工班组",contractAmount:99999999,startDate:"2026-08-01",endDate:"2026-12-31"}')
code=$(call2 POST /api/v1/labor/contract "$BODY")
MSG=$(jqget '.message // .msg // ""')
if ! ok200 "$code"; then result PASS "S2a 超额劳务合同被预算拦截" "msg=$MSG"; else result FAIL "S2a 超额劳务合同未被拦截(缺陷)" "msg=$MSG"; fi
BODY=$(jq -n --argjson pid "$PROJECT_ID" --arg pn "$PNAME" '{projectId:$pid,contractName:($pn+"_劳务合同正向"),teamName:"批次3木工班组",contractAmount:500000,startDate:"2026-08-01",endDate:"2026-12-31"}')
code=$(call2 POST /api/v1/labor/contract "$BODY")
if ok200 "$code"; then result PASS "S2b 劳务合同创建(50万)" ""; else result FAIL "S2b 劳务合同创建" "http=$code body=$(head -c 300 /tmp/zwi_body3)"; fi
call2 GET "/api/v1/labor/contract/page?page=1&size=10&projectId=$PROJECT_ID&contractName=$(uri "${PNAME}_劳务合同正向")" >/dev/null
LABOR_CID=$(jqget '.data.records[0].id')
if [ -n "$LABOR_CID" ] && [ "$LABOR_CID" != "null" ]; then
  code=$(call2 POST "/api/v1/labor/contract/$LABOR_CID/submit")
  if ok200 "$code"; then result PASS "S2c 劳务合同提交" ""; else result FAIL "S2c 劳务合同提交" "body=$(head -c 300 /tmp/zwi_body3)"; fi
  call2 GET "/api/v1/labor/contract/$LABOR_CID" >/dev/null
  ST=$(jqget '.data.status')
  if [ "$ST" = "EFFECTIVE" ]; then result PASS "S2d 劳务合同=EFFECTIVE" ""; else result FAIL "S2d 劳务合同状态" "实际=$ST"; fi
else result FAIL "S2b 劳务合同查询" "未找到"; fi

echo "== S3 劳务结算（submit 回写合同累计结算，上限=合同金额） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" --argjson cid "${LABOR_CID:-0}" '{projectId:$pid,contractId:$cid,settlementAmount:200000}')
code=$(call2 POST /api/v1/labor/settlement "$BODY")
if ok200 "$code"; then result PASS "S3a 劳务结算创建(20万)" ""; else result FAIL "S3a 劳务结算创建" "body=$(head -c 300 /tmp/zwi_body3)"; fi
call2 GET "/api/v1/labor/settlement/page?page=1&size=10&projectId=$PROJECT_ID" >/dev/null
LABOR_SID=$(jqget '[.data.records[] | select(.status=="DRAFT")][0].id // empty')
code=$(call2 POST "/api/v1/labor/settlement/$LABOR_SID/submit")
if ok200 "$code"; then result PASS "S3b 劳务结算提交(APPROVED)" ""; else result FAIL "S3b 劳务结算提交" "body=$(head -c 300 /tmp/zwi_body3)"; fi
call2 GET "/api/v1/labor/contract/$LABOR_CID" >/dev/null
CUM=$(jqget '.data.cumulativeSettlement')
case "$CUM" in 200000|200000.00|200000.0000) result PASS "S3c 合同累计结算=200000" "" ;; *) result FAIL "S3c 累计结算回写" "实际=$CUM" ;; esac
BODY=$(jq -n --argjson pid "$PROJECT_ID" --argjson cid "${LABOR_CID:-0}" '{projectId:$pid,contractId:$cid,settlementAmount:500000}')
code=$(call2 POST /api/v1/labor/settlement "$BODY")
call2 GET "/api/v1/labor/settlement/page?page=1&size=10&projectId=$PROJECT_ID" >/dev/null
NEG_SID=$(jqget '[.data.records[] | select(.status=="DRAFT")][0].id // empty')
if [ -n "$NEG_SID" ]; then
  code=$(call2 POST "/api/v1/labor/settlement/$NEG_SID/submit")
  MSG=$(jqget '.message // .msg // ""')
  if ! ok200 "$code" && echo "$MSG" | grep -q "超出合同金额"; then result PASS "S3d 超合同结算被拒(最大30万)" "msg=$MSG"; else result FAIL "S3d 超合同结算未被拒(缺陷)" "msg=$MSG"; fi
  call2 DELETE "/api/v1/labor/settlement/$NEG_SID" >/dev/null
else result FAIL "S3d 负向结算单准备" "未找到草稿结算单"; fi

echo "== S4 机械合同（预算管控在 submit，MACHINE 额度 500万，台班计价1500） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" --arg pn "$PNAME" '{projectId:$pid,contractName:($pn+"_机械租赁正向"),supplierName:($pn+"_机械租赁公司"),machineName:($pn+"_挖掘机"),contractAmount:1500,rentalType:"台班",startDate:"2026-08-01",endDate:"2026-12-31"}')
code=$(call2 POST /api/v1/machine/contract "$BODY")
if ok200 "$code"; then result PASS "S4a 机械合同创建" ""; else result FAIL "S4a 机械合同创建" "http=$code body=$(head -c 300 /tmp/zwi_body3)"; fi
call2 GET "/api/v1/machine/contract/page?page=1&size=10&projectId=$PROJECT_ID&contractName=$(uri "${PNAME}_机械租赁正向")" >/dev/null
MACH_CID=$(jqget '.data.records[0].id')
if [ -n "$MACH_CID" ] && [ "$MACH_CID" != "null" ]; then
  code=$(call2 POST "/api/v1/machine/contract/$MACH_CID/submit")
  if ok200 "$code"; then result PASS "S4b 机械合同提交(预算通过)" ""; else result FAIL "S4b 机械合同提交" "body=$(head -c 300 /tmp/zwi_body3)"; fi
  call2 GET "/api/v1/machine/contract/$MACH_CID" >/dev/null
  ST=$(jqget '.data.status')
  if [ "$ST" = "EFFECTIVE" ]; then result PASS "S4c 机械合同=EFFECTIVE" ""; else result FAIL "S4c 机械合同状态" "实际=$ST"; fi
else result FAIL "S4a 机械合同查询" "未找到"; fi

echo "== S5 机械结算（台账+工作日志+周期结算+Flowable 审批） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" '{projectId:$pid,periodStart:"2025-01-01",periodEnd:"2025-01-31"}')
code=$(call2 POST /api/v1/machine/settlement "$BODY")
MSG=$(jqget '.message // .msg // ""')
if ! ok200 "$code" && echo "$MSG" | grep -q "无可结算"; then result PASS "S5a 无工作量周期被拒" "msg=$MSG"; else result FAIL "S5a 无工作量未被拒(缺陷)" "http=$code msg=$MSG"; fi
BODY=$(jq -n --arg pn "$PNAME" '{machineName:($pn+"_挖掘机"),machineCode:("JX-B3-"+$pn),machineType:"挖掘机",ownerType:"RENT"}')
code=$(call2 POST /api/v1/machine/ledger "$BODY")
if ok200 "$code"; then result PASS "S5b 机械台账创建(REGISTERED)" ""; else result FAIL "S5b 机械台账创建" "body=$(head -c 300 /tmp/zwi_body3)"; fi
call2 GET "/api/v1/machine/ledger/page?page=1&size=20&machineName=$(uri "${PNAME}_挖掘机")" >/dev/null
LEDGER_ID=$(jqget '.data.records[0].id')
# 台账状态由进出场状态机维护（save 强制 REGISTERED），须走进场接口置 IN_FIELD 方可记日志
BODY=$(jq -n --argjson mid "${LEDGER_ID:-0}" --argjson pid "$PROJECT_ID" '{machineId:$mid,projectId:$pid,entryDate:"2026-08-01"}')
code=$(call2 POST /api/v1/machine/entry/in "$BODY")
if ok200 "$code"; then result PASS "S5b2 机械进场(IN_FIELD)" ""; else result FAIL "S5b2 机械进场" "body=$(head -c 300 /tmp/zwi_body3)"; fi
BODY=$(jq -n --argjson mid "${LEDGER_ID:-0}" --argjson pid "$PROJECT_ID" '{machineId:$mid,projectId:$pid,workDate:"2026-08-10",shiftCount:2,workQuantity:0}')
code=$(call2 POST /api/v1/machine/work-log "$BODY")
if ok200 "$code"; then result PASS "S5c 工作日志创建(2台班)" ""; else result FAIL "S5c 工作日志创建" "body=$(head -c 300 /tmp/zwi_body3)"; fi
BODY=$(jq -n --argjson pid "$PROJECT_ID" '{projectId:$pid,periodStart:"2026-08-01",periodEnd:"2026-08-31"}')
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
BODY=$(jq -n --argjson pid "$PROJECT_ID" --arg pn "$PNAME" '{projectId:$pid,contractName:($pn+"_分包正向"),subcontractor:($pn+"_供应商"),contractAmount:800000,content:"批次3土方分包",signingDate:"2026-08-01"}')
code=$(call2 POST /api/v1/subcontract/contract "$BODY")
if ok200 "$code"; then result PASS "S6a 分包合同创建(80万)" ""; else result FAIL "S6a 分包合同创建" "body=$(head -c 300 /tmp/zwi_body3)"; fi
call2 GET "/api/v1/subcontract/contract/page?page=1&size=10&projectId=$PROJECT_ID&contractName=$(uri "${PNAME}_分包正向")" >/dev/null
SUB_CID=$(jqget '.data.records[0].id')
if [ -n "$SUB_CID" ] && [ "$SUB_CID" != "null" ]; then
  code=$(call2 POST "/api/v1/subcontract/contract/$SUB_CID/submit")
  if ok200 "$code"; then result PASS "S6b 分包合同提交(预算通过)" ""; else result FAIL "S6b 分包合同提交" "body=$(head -c 300 /tmp/zwi_body3)"; fi
  call2 GET "/api/v1/subcontract/contract/$SUB_CID" >/dev/null
  ST=$(jqget '.data.status')
  if [ "$ST" = "EFFECTIVE" ]; then result PASS "S6c 分包合同=EFFECTIVE" ""; else result FAIL "S6c 分包合同状态" "实际=$ST"; fi
else result FAIL "S6a 分包合同查询" "未找到"; fi

echo "== S7 分包结算（明细行创建，submit 回写累计结算） =="
BODY=$(jq -n --argjson cid "${SUB_CID:-0}" --argjson pid "$PROJECT_ID" '{contractId:$cid,projectId:$pid,details:[{itemName:"土方开挖",unit:"m3",quantity:1000,unitPrice:500}]}')
code=$(call2 POST /api/v1/subcontract/settlement "$BODY")
SUB_SID=$(jqget '.data // empty')
if ok200 "$code" && [ -n "$SUB_SID" ] && [ "$SUB_SID" != "null" ]; then result PASS "S7a 分包结算创建(50万)" "id=$SUB_SID"; else result FAIL "S7a 分包结算创建" "body=$(head -c 300 /tmp/zwi_body3)"; fi
BODY=$(jq -n --argjson cid "${SUB_CID:-0}" --argjson pid "$PROJECT_ID" '{contractId:$cid,projectId:$pid,details:[{itemName:"土方开挖超量",unit:"m3",quantity:2000,unitPrice:500}]}')
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
BODY=$(jq -n --argjson pid "$PROJECT_ID" --argjson cid "${SUB_CID:-0}" --argjson sid "${SUPPLIER_ID:-0}" --arg pn "$PNAME" '{projectId:$pid,contractId:$cid,contractCategory:"SUBCONTRACT",supplierId:$sid,supplierName:($pn+"_供应商"),paymentAmount:600000,paymentDate:"2026-08-20"}')
code=$(call2 POST /api/v1/finance/payment-apply "$BODY")
if ok200 "$code"; then
  call2 GET "/api/v1/finance/payment-apply/page?page=1&size=10&projectId=$PROJECT_ID" >/dev/null
  NEG_PAY=$(jqget '[.data.records[] | select(.paymentAmount==600000 and .status=="DRAFT")][0].id // empty')
  if [ -n "$NEG_PAY" ]; then
    code=$(call2 POST "/api/v1/finance/payment-apply/$NEG_PAY/submit")
    MSG=$(jqget '.message // .msg // ""')
    if ! ok200 "$code" && echo "$MSG" | grep -q "超过"; then result PASS "S8a 超可付金额提交被拒(60万>结算50万)" "msg=$MSG"; else result FAIL "S8a 超可付金额未被拒(缺陷)" "msg=$MSG"; fi
    call2 DELETE "/api/v1/finance/payment-apply/$NEG_PAY" >/dev/null
  else result FAIL "S8a 负向付款单准备" "未找到60万草稿"; fi
else result FAIL "S8a 负向付款单创建" "body=$(head -c 300 /tmp/zwi_body3)"; fi
BODY=$(jq -n --argjson pid "$PROJECT_ID" --argjson cid "${SUB_CID:-0}" --argjson sid "${SUPPLIER_ID:-0}" --arg pn "$PNAME" '{projectId:$pid,contractId:$cid,contractCategory:"SUBCONTRACT",supplierId:$sid,supplierName:($pn+"_供应商"),paymentAmount:200000,paymentDate:"2026-08-20"}')
code=$(call2 POST /api/v1/finance/payment-apply "$BODY")
if ok200 "$code"; then result PASS "S8b 付款申请创建(20万)" ""; else result FAIL "S8b 付款申请创建" "body=$(head -c 300 /tmp/zwi_body3)"; fi
call2 GET "/api/v1/finance/payment-apply/page?page=1&size=10&projectId=$PROJECT_ID" >/dev/null
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
echo "===== 批次3 API 验证汇总(自包含): PASS=$PASS FAIL=$FAIL ====="
[ "$FAIL" -eq 0 ]
