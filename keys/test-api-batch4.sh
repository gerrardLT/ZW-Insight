#!/usr/bin/env bash
###############################################################################
# test-api-batch4.sh — 批次4 API 层真实链路验证（开票/收款/竣工/竞工结算/结案/Dashboard，v3 自包含版）
#
# v3 改造（2026-08-20，全量 CI run 32381101540 暴露数据态依赖后用户决策改造）：
#   原版硬编码租户 1 项目 PID 与施工合同 CCID（依赖批次1 的 4000万合同/1000万累计产值），
#   首次进全量 CI 即因开票额度被先前轮次耗尽失败（S1c「可开票额度 0.00」）。
#   v3 自包含：登录隔离租户 9999（t9999admin，CI L3 前置 init-test-tenant.sh
#   已种全套编号规则/模块开通），自建 项目→立项→施工合同(1000万)→预算→产值报告
#   (1000万，审批后回写合同累计产值) 全前置链，金额链自洽
#   （合同额1000万=产值1000万=开票1000万=收款1000万，满足结案 close-check 款项结清）。
#   跑完 S1-S7 后逆序 API 清理 + SQL 兜底（仅本轮 project_id + tenant_id=9999）。
#   不再依赖任何既有数据态，可重复执行。
#   建链范式以 lifecycle-sim-v2.sh（租户 9999 实测 26/26 绿）为准。
#
# 业务链路：S1 开票申请(上限=累计产值-已开票，审批后回写合同累计开票)
#   → S2 收款登记(上限=累计开票-累计已收，回写项目总收入+合同累计收款)
#   → S3 未竣工结算负向 → S4 竣工验收(审批后项目 COMPLETED)
#   → S5 竞工结算(审批后 APPROVED+施工合同 SETTLED)
#   → S6 结案(close-check → CLOSING → 审批 → CLOSED) → S7 Dashboard 抽检。
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
export ZWI_WORKDIR="${ZWI_WORKDIR:-/root/zwi-deploy/batch4}"
source ./verify-base.sh usage >/dev/null 2>&1 || true

PASS=0; FAIL=0
result() { echo "[$1] $2${3:+ — $3}"; if [ "$1" = PASS ]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi; }

call2() { # call2 <METHOD> <PATH> [BODY] → 写 /tmp/zwi_body4，echo HTTP code
  local method="$1" path="$2" body="${3:-}" token code
  token=$(get_token) || { echo "000"; return 1; }
  if [ -n "$body" ]; then
    code=$(curl -s -m 20 -o /tmp/zwi_body4 -w '%{http_code}' -X "$method" "$BASE$path" \
      -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "$body")
  else
    code=$(curl -s -m 20 -o /tmp/zwi_body4 -w '%{http_code}' -X "$method" "$BASE$path" \
      -H "Authorization: Bearer $token")
  fi
  echo "$code"
}
jqget() { jq -r "$1" /tmp/zwi_body4 2>/dev/null; }
ok200() { [ "$1" = 200 ] && { [ "$(jqget '.code')" = 200 ] || [ "$(jqget '.code')" = 0 ]; }; }
uri() { printf '%s' "$1" | jq -sRr @uri; }
dbq() { docker exec zwi-mysql mysql -uroot -pzwinsight123 zw_insight -N -B -e "$1" 2>/dev/null | tr -d '\r' | head -1; }

approve_flow() { # 逐个完成指定流程实例待办（t9999admin 为租户 9999 SUPER_ADMIN 可办理候选组任务）
  local inst="$1" i=0 tid code
  while [ $i -lt 6 ]; do
    tid=$(dbq "SELECT ID_ FROM ACT_RU_TASK WHERE PROC_INST_ID_='$inst' ORDER BY CREATE_TIME_ ASC LIMIT 1;")
    [ -z "$tid" ] && return 0
    code=$(call2 POST /api/v1/workflow/approval/complete "$(jq -n --arg t "$tid" '{taskId:$t, comment:"批次4 自动审批通过"}')")
    if ! ok200 "$code"; then echo "      审批任务失败 taskId=$tid body=$(head -c 200 /tmp/zwi_body4)"; return 1; fi
    sleep 1; i=$((i+1))
  done
  tid=$(dbq "SELECT ID_ FROM ACT_RU_TASK WHERE PROC_INST_ID_='$inst' LIMIT 1;")
  [ -z "$tid" ] && return 0 || return 1
}

TS=$(date +%s)
PNAME="E2E_TEST_B4_${TS}"
PROJECT_ID=""; CC_ID=""; BUDGET_ID=""; OUTPUT_ID=""; INV_ID=""; ACC_ID=""; SETTLE_ID=""

# 逆序清理（API 优先，失败不阻断；结尾 SQL 兜底限本轮 project_id + tenant 9999）
cleanup() {
  echo "== 清理：逆序删除本轮自建数据 =="
  [ -n "$SETTLE_ID" ] && call2 DELETE "/api/v1/project-settlements/$SETTLE_ID" >/dev/null
  [ -n "$ACC_ID" ]    && call2 DELETE "/api/v1/site/completion/$ACC_ID" >/dev/null
  [ -n "$INV_ID" ]    && call2 DELETE "/api/v1/finance/invoice-apply/$INV_ID" >/dev/null
  [ -n "$OUTPUT_ID" ] && call2 DELETE "/api/v1/contract/output/$OUTPUT_ID" >/dev/null
  [ -n "$BUDGET_ID" ] && call2 DELETE "/api/v1/budget/$BUDGET_ID" >/dev/null
  [ -n "$CC_ID" ]     && call2 DELETE "/api/v1/contract/$CC_ID" >/dev/null
  [ -n "$PROJECT_ID" ] && call2 DELETE "/api/v1/project/$PROJECT_ID" >/dev/null
  if [ -n "$PROJECT_ID" ]; then
    dbq "DELETE FROM biz_project_settlement WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_completion_acceptance WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_payment_received WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_invoice_apply WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_output_report WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_budget_detail WHERE budget_id IN (SELECT id FROM biz_budget WHERE project_id=$PROJECT_ID AND tenant_id=9999);" >/dev/null
    dbq "DELETE FROM biz_budget WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
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
BODY=$(jq -n --arg pn "$PNAME" '{projectName:$pn, projectNature:"新建", projectType:"公共建筑", ownerCompanyName:"批次4自包含业主", signingCompanyName:"批次4自包含承包方", projectOverview:"批次4 收入侧与收尾闭环自包含验证项目", projectAddress:"广州市天河区", contactName:"批次4", contactPhone:"13900000004", needTender:0, budgetAmount:10000000}')
code=$(call2 POST /api/v1/project "$BODY")
if ok200 "$code"; then result PASS "S0a 项目创建" ""; else result FAIL "S0a 项目创建" "http=$code body=$(head -c 300 /tmp/zwi_body4)"; trap - EXIT; exit 1; fi
call2 GET "/api/v1/project/page?page=1&size=5&projectName=$(uri "$PNAME")" >/dev/null
PROJECT_ID=$(jqget '.data.records[0].id')
[ -n "$PROJECT_ID" ] && [ "$PROJECT_ID" != "null" ] || { result FAIL "S0a 项目查询" "未找到 $PNAME"; trap - EXIT; exit 1; }
code=$(call2 POST "/api/v1/project/$PROJECT_ID/submit")
if ok200 "$code"; then result PASS "S0a2 立项提交(FILED)" ""; else result FAIL "S0a2 立项提交" "body=$(head -c 300 /tmp/zwi_body4)"; fi

echo "== S0b 自建施工合同（1000万，submit→EFFECTIVE，L4 范式） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" '{projectId:$pid, contractType:"REGISTER", partyAName:"批次4自包含业主", signingDate:"2026-08-19", startDate:"2026-08-20", endDate:"2026-12-31", contractAmount:10000000, taxRate:9.00}')
code=$(call2 POST /api/v1/contract "$BODY")
if ok200 "$code"; then result PASS "S0b 施工合同创建" ""; else result FAIL "S0b 施工合同创建" "http=$code body=$(head -c 300 /tmp/zwi_body4)"; fi
call2 GET "/api/v1/contract/page?page=1&size=5&projectId=$PROJECT_ID" >/dev/null
CC_ID=$(jqget '.data.records[0].id')
code=$(call2 POST "/api/v1/contract/$CC_ID/submit")
if ok200 "$code"; then result PASS "S0b2 施工合同提交" ""; else result FAIL "S0b2 施工合同提交" "body=$(head -c 300 /tmp/zwi_body4)"; fi
sleep 1
call2 GET "/api/v1/contract/$CC_ID" >/dev/null
ST=$(jqget '.data.status')
if [ "$ST" = "SUBMITTED" ]; then
  INST=$(dbq "SELECT workflow_instance_id FROM biz_construction_contract WHERE id=$CC_ID;")
  [ -n "$INST" ] && approve_flow "$INST" >/dev/null
  call2 GET "/api/v1/contract/$CC_ID" >/dev/null; ST=$(jqget '.data.status')
fi
if [ "$ST" = "EFFECTIVE" ]; then result PASS "S0b3 施工合同=EFFECTIVE" ""; else result FAIL "S0b3 施工合同状态" "实际=$ST"; fi

echo "== S0c 自建预算（ORIGINAL 1000万，submit 直批 APPROVED） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" '{projectId:$pid, budgetType:"ORIGINAL", totalAmount:10000000, details:[{costCategory:"OTHER", itemName:"批次4综合预算", unit:"项", budgetQuantity:1, budgetUnitPrice:10000000, budgetTotalPrice:10000000}]}')
code=$(call2 POST /api/v1/budget "$BODY")
if ok200 "$code"; then result PASS "S0c 预算创建" ""; else result FAIL "S0c 预算创建" "http=$code body=$(head -c 300 /tmp/zwi_body4)"; fi
call2 GET "/api/v1/budget/project/$PROJECT_ID" >/dev/null
BUDGET_ID=$(jqget '.data.id // .data[0].id // empty')
code=$(call2 POST "/api/v1/budget/$BUDGET_ID/submit")
if ok200 "$code"; then result PASS "S0c2 预算提交(APPROVED)" ""; else result FAIL "S0c2 预算提交" "body=$(head -c 300 /tmp/zwi_body4)"; fi

echo "== S0d 产值报告（1000万，submit→output_report_approval 审批→合同累计产值回写） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" --argjson cid "$CC_ID" '{projectId:$pid,contractId:$cid,reportPeriod:"2026-08",currentOutput:10000000}')
code=$(call2 POST /api/v1/contract/output "$BODY")
if ok200 "$code"; then result PASS "S0d 产值报告创建" ""; else result FAIL "S0d 产值报告创建" "http=$code body=$(head -c 300 /tmp/zwi_body4)"; fi
sleep 1
call2 GET "/api/v1/contract/output?page=1&size=5&projectId=$PROJECT_ID" >/dev/null
OUTPUT_ID=$(jqget '.data.records[0].id')
if [ -n "$OUTPUT_ID" ] && [ "$OUTPUT_ID" != "null" ]; then
  code=$(call2 POST "/api/v1/contract/output/$OUTPUT_ID/submit")
  if ok200 "$code"; then result PASS "S0d2 产值提交(启动审批)" ""; else result FAIL "S0d2 产值提交" "body=$(head -c 300 /tmp/zwi_body4)"; fi
  sleep 1
  INST=$(dbq "SELECT workflow_instance_id FROM biz_output_report WHERE id=$OUTPUT_ID;")
  if [ -n "$INST" ] && approve_flow "$INST"; then result PASS "S0d3 产值审批流完成" ""; else result FAIL "S0d3 产值审批流" "inst=$INST"; fi
  call2 GET "/api/v1/contract/$CC_ID" >/dev/null
  CUM=$(jqget '.data.cumulativeOutput')
  case "$CUM" in 10000000|10000000.00|10000000.0000) result PASS "S0d4 合同累计产值=10000000" "" ;; *) result FAIL "S0d4 累计产值回写" "实际=$CUM" ;; esac
else result FAIL "S0d 产值报告查询" "未找到"; fi

echo "== S1 开票申请（submit 校验≤累计产值-已开票，审批后回写合同累计开票） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" --argjson cid "$CC_ID" '{projectId:$pid,contractId:$cid,applyDate:"2026-08-20",invoiceType:"SPECIAL",invoiceAmount:11000000,taxRate:9,invoiceTitle:"批次4业主单位"}')
code=$(call2 POST /api/v1/finance/invoice-apply "$BODY")
if ok200 "$code"; then
  call2 GET "/api/v1/finance/invoice-apply/page?page=1&size=10&projectId=$PROJECT_ID" >/dev/null
  NEG_INV=$(jqget '[.data.records[] | select(.status=="DRAFT")][0].id // empty')
  if [ -n "$NEG_INV" ]; then
    code=$(call2 POST "/api/v1/finance/invoice-apply/$NEG_INV/submit")
    MSG=$(jqget '.message // .msg // ""')
    if ! ok200 "$code" && echo "$MSG" | grep -q "不能超过累计产值"; then result PASS "S1a 超产值开票提交被拒(1100万>1000万)" "msg=$MSG"; else result FAIL "S1a 超产值开票未被拒(缺陷)" "msg=$MSG"; fi
    call2 DELETE "/api/v1/finance/invoice-apply/$NEG_INV" >/dev/null
  else result FAIL "S1a 负向开票单准备" "未找到草稿"; fi
else result FAIL "S1a 负向开票单创建" "body=$(head -c 300 /tmp/zwi_body4)"; fi
BODY=$(jq -n --argjson pid "$PROJECT_ID" --argjson cid "$CC_ID" '{projectId:$pid,contractId:$cid,applyDate:"2026-08-20",invoiceType:"SPECIAL",invoiceAmount:10000000,taxRate:9,invoiceTitle:"批次4业主单位"}')
code=$(call2 POST /api/v1/finance/invoice-apply "$BODY")
if ok200 "$code"; then result PASS "S1b 开票申请创建(1000万)" ""; else result FAIL "S1b 开票申请创建" "body=$(head -c 300 /tmp/zwi_body4)"; fi
call2 GET "/api/v1/finance/invoice-apply/page?page=1&size=10&projectId=$PROJECT_ID" >/dev/null
INV_ID=$(jqget '[.data.records[] | select(.status=="DRAFT")][0].id // empty')
if [ -n "$INV_ID" ]; then
  code=$(call2 POST "/api/v1/finance/invoice-apply/$INV_ID/submit")
  if ok200 "$code"; then result PASS "S1c 开票提交(SUBMITTED,启动流程)" ""; else result FAIL "S1c 开票提交" "body=$(head -c 300 /tmp/zwi_body4)"; fi
  INST=$(dbq "SELECT workflow_instance_id FROM biz_invoice_apply WHERE id=$INV_ID;")
  if [ -n "$INST" ] && approve_flow "$INST"; then result PASS "S1d 开票审批流完成" ""; else result FAIL "S1d 开票审批流" "inst=$INST"; fi
  call2 GET "/api/v1/finance/invoice-apply/$INV_ID" >/dev/null
  ST=$(jqget '.data.status')
  if [ "$ST" = "APPROVED" ]; then result PASS "S1e 开票=APPROVED" ""; else result FAIL "S1e 开票状态" "实际=$ST"; fi
  INV_CUM=$(dbq "SELECT cumulative_invoice_amount FROM biz_construction_contract WHERE id=$CC_ID;")
  case "$INV_CUM" in 10000000|10000000.00|10000000.0000) result PASS "S1f 合同累计开票=10000000" "" ;; *) result FAIL "S1f 累计开票回写" "实际=$INV_CUM" ;; esac
else result FAIL "S1b 开票单查询" "未找到草稿开票申请"; fi

echo "== S2 收款登记（save 校验≤累计开票-累计已收，回写项目总收入+合同累计收款） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" --argjson cid "$CC_ID" '{projectId:$pid,contractId:$cid,receiveDate:"2026-08-20",receiveAmount:10000001,receiveType:"BANK"}')
code=$(call2 POST /api/v1/finance/payment-received "$BODY")
MSG=$(jqget '.message // .msg // ""')
if ! ok200 "$code" && echo "$MSG" | grep -q "不能超过已开票未收金额"; then result PASS "S2a 超可收金额被拒(1000万零1>1000万)" "msg=$MSG"; else result FAIL "S2a 超可收金额未被拒(缺陷)" "http=$code msg=$MSG"; fi
BODY=$(jq -n --argjson pid "$PROJECT_ID" --argjson cid "$CC_ID" '{projectId:$pid,contractId:$cid,receiveDate:"2026-08-20",receiveAmount:10000000,receiveType:"BANK"}')
code=$(call2 POST /api/v1/finance/payment-received "$BODY")
if ok200 "$code"; then result PASS "S2b 收款登记(1000万)" ""; else result FAIL "S2b 收款登记" "body=$(head -c 300 /tmp/zwi_body4)"; fi
INCOME=$(dbq "SELECT total_income FROM biz_project WHERE id=$PROJECT_ID;")
case "$INCOME" in 10000000|10000000.00|10000000.0000) result PASS "S2c 项目总收入=10000000" "" ;; *) result FAIL "S2c 项目总收入回写" "实际=$INCOME" ;; esac
RECV_CUM=$(dbq "SELECT cumulative_received_amount FROM biz_construction_contract WHERE id=$CC_ID;")
case "$RECV_CUM" in 10000000|10000000.00|10000000.0000) result PASS "S2d 合同累计收款=10000000" "" ;; *) result FAIL "S2d 合同累计收款回写" "实际=$RECV_CUM" ;; esac

echo "== S3 竞工结算前置负向（项目未竣工不可结算） =="
code=$(call2 POST "/api/v1/project-settlements?projectId=$PROJECT_ID")
MSG=$(jqget '.message // .msg // ""')
if ! ok200 "$code" && echo "$MSG" | grep -q "未竣工"; then result PASS "S3a 未竣工项目结算被拒" "msg=$MSG"; else result FAIL "S3a 未竣工结算未被拒(缺陷)" "http=$code msg=$MSG"; fi

echo "== S4 竣工验收（submit 启动流程，审批后项目 COMPLETED，body 用 L4 范式） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" '{projectId:$pid,acceptanceDate:"2026-08-20",acceptanceResult:"合格",remark:"批次4自包含竣工验收：各分部工程验收合格"}')
code=$(call2 POST /api/v1/site/completion "$BODY")
if ok200 "$code"; then result PASS "S4a 竣工验收单创建" ""; else result FAIL "S4a 竣工验收单创建" "body=$(head -c 300 /tmp/zwi_body4)"; fi
call2 GET "/api/v1/site/completion/page?page=1&size=10&projectId=$PROJECT_ID" >/dev/null
ACC_ID=$(jqget '[.data.records[] | select(.status=="DRAFT")][0].id // empty')
if [ -n "$ACC_ID" ]; then
  code=$(call2 POST "/api/v1/site/completion/$ACC_ID/submit")
  if ok200 "$code"; then result PASS "S4b 竣工验收提交(SUBMITTED)" ""; else result FAIL "S4b 竣工验收提交" "body=$(head -c 300 /tmp/zwi_body4)"; fi
  INST=$(dbq "SELECT workflow_instance_id FROM biz_completion_acceptance WHERE id=$ACC_ID;")
  if [ -n "$INST" ] && approve_flow "$INST"; then result PASS "S4c 竣工验收审批流完成" ""; else result FAIL "S4c 竣工验收审批流" "inst=$INST"; fi
  PST=$(dbq "SELECT status FROM biz_project WHERE id=$PROJECT_ID;")
  if [ "$PST" = "COMPLETED" ]; then result PASS "S4d 项目=COMPLETED" ""; else result FAIL "S4d 项目竣工状态" "实际=$PST"; fi
else result FAIL "S4a 竣工验收单查询" "未找到草稿验收单"; fi

echo "== S5 竞工结算（项目最终结算：创建汇总→提交审批→APPROVED+施工合同 SETTLED） =="
code=$(call2 POST "/api/v1/project-settlements?projectId=$PROJECT_ID")
SETTLE_ID=$(jqget '.data // empty')
if ok200 "$code" && [ -n "$SETTLE_ID" ] && [ "$SETTLE_ID" != "null" ]; then result PASS "S5a 竞工结算单创建" "id=$SETTLE_ID"; else result FAIL "S5a 竞工结算单创建" "body=$(head -c 300 /tmp/zwi_body4)"; fi
call2 GET "/api/v1/project-settlements/$SETTLE_ID" >/dev/null
ST=$(jqget '.data.status')
if [ "$ST" = "DRAFT" ]; then result PASS "S5b 结算单=DRAFT(自动汇总)" ""; else result FAIL "S5b 结算单状态" "实际=$ST"; fi
code=$(call2 POST "/api/v1/project-settlements/$SETTLE_ID/submit")
if ok200 "$code"; then result PASS "S5c 结算单提交(SUBMITTED)" ""; else result FAIL "S5c 结算单提交" "body=$(head -c 300 /tmp/zwi_body4)"; fi
INST=$(dbq "SELECT workflow_instance_id FROM biz_project_settlement WHERE id=$SETTLE_ID;")
if [ -n "$INST" ] && approve_flow "$INST"; then result PASS "S5d 结算审批流完成" ""; else result FAIL "S5d 结算审批流" "inst=$INST"; fi
call2 GET "/api/v1/project-settlements/$SETTLE_ID" >/dev/null
ST=$(jqget '.data.status')
if [ "$ST" = "APPROVED" ]; then result PASS "S5e 结算单=APPROVED" ""; else result FAIL "S5e 结算单状态" "实际=$ST"; fi
CST=$(dbq "SELECT status FROM biz_construction_contract WHERE id=$CC_ID;")
if [ "$CST" = "SETTLED" ]; then result PASS "S5f 施工合同=SETTLED" ""; else result FAIL "S5f 施工合同状态" "实际=$CST"; fi

echo "== S6 结案（close-check 四条件预检 → CLOSING → 审批 → CLOSED） =="
call2 GET "/api/v1/project/$PROJECT_ID/close-check" >/dev/null
ALL=$(jqget '.data.allPassed')
if [ "$ALL" = "true" ]; then result PASS "S6a close-check 全部条件通过" ""; else result FAIL "S6a close-check" "allPassed=$ALL reasons=$(jqget '.data.failedReasons')"; fi
code=$(call2 POST "/api/v1/project/$PROJECT_ID/close")
if ok200 "$code"; then result PASS "S6b 发起结项(CLOSING)" ""; else result FAIL "S6b 发起结项" "body=$(head -c 300 /tmp/zwi_body4)"; fi
PST=$(dbq "SELECT status FROM biz_project WHERE id=$PROJECT_ID;")
if [ "$PST" = "CLOSING" ]; then result PASS "S6c 项目=CLOSING" ""; else result FAIL "S6c 项目结项状态" "实际=$PST"; fi
INST=$(dbq "SELECT workflow_instance_id FROM biz_project WHERE id=$PROJECT_ID;")
if [ -n "$INST" ] && approve_flow "$INST"; then result PASS "S6d 结项审批流完成" ""; else result FAIL "S6d 结项审批流" "inst=$INST"; fi
PST=$(dbq "SELECT status FROM biz_project WHERE id=$PROJECT_ID;")
if [ "$PST" = "CLOSED" ]; then result PASS "S6e 项目=CLOSED(结案完成)" ""; else result FAIL "S6e 项目结案状态" "实际=$PST"; fi

echo "== S7 Dashboard 数据接口抽检（真实聚合数据） =="
code=$(call2 GET /api/v1/dashboard/company-overview)
if ok200 "$code"; then result PASS "S7a company-overview 200" ""; else result FAIL "S7a company-overview" "http=$code"; fi
code=$(call2 GET "/api/v1/dashboard/project/$PROJECT_ID/overview")
if ok200 "$code"; then result PASS "S7b 项目看板 overview 200" ""; else result FAIL "S7b 项目看板" "http=$code body=$(head -c 200 /tmp/zwi_body4)"; fi
code=$(call2 GET /api/v1/dashboard/project-ranking)
if ok200 "$code"; then result PASS "S7c project-ranking 200" ""; else result FAIL "S7c project-ranking" "http=$code"; fi

echo ""
echo "===== 批次4 API 验证汇总(自包含): PASS=$PASS FAIL=$FAIL ====="
[ "$FAIL" -eq 0 ]
