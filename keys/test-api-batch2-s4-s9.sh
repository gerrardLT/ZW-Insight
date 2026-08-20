#!/usr/bin/env bash
###############################################################################
# test-api-batch2-s4-s9.sh — 批次2 S4-S9 API 层真实链路验证（v3 自包含版）
#
# v3 改造（2026-08-20，全量 CI run 32381101540 暴露数据态依赖后用户决策改造）：
#   原版取「列表第一个项目」并假设其已建预算（归零重建后租户 1 状态），
#   首次进全量 CI 即因部署环境状态漂移失败（S5a「该项目未创建预算」500）。
#   v3 自包含：登录隔离租户 9999（t9999admin，CI L3 前置 init-test-tenant.sh
#   已种全套编号规则/模块开通），自建 项目→立项→施工合同→预算(BLOCK管控)
#   全前置链，跑完 S4-S9 后逆序 API 清理 + SQL 兜底（仅本轮 project_id，
#   tenant_id=9999）。不再依赖任何既有数据态，可重复执行。
#   建链范式以 lifecycle-sim-v2.sh（租户 9999 实测 26/26 绿）为准。
#
# 业务链路：S4 预算 BLOCK 负向 / S5 正向合同+提交 / S6 入库 /
#           S7 超库存负向 / S8 出库正向 / S9 库存核对（100-30=70）。
#
# 依赖：verify-base.sh 登录基座（ZWI_USER/ZWI_PASS/ZWI_WORKDIR 覆盖）。
# 全程真实接口真实数据，禁止 mock。断言失败即 FAIL，退出码反映结果。
###############################################################################
set -uo pipefail
cd "$(dirname "$0")"
export ZWI_USER="${ZWI_USER:-t9999admin}"
export ZWI_PASS="${ZWI_PASS:-123456}"
export ZWI_WORKDIR="${ZWI_WORKDIR:-/root/zwi-deploy/batch2}"
source ./verify-base.sh usage >/dev/null 2>&1 || true

PASS=0; FAIL=0
result() { # result <PASS|FAIL> <step> <detail>
  echo "[$1] $2${3:+ — $3}"
  if [ "$1" = PASS ]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi
}

call2() { # call2 <METHOD> <PATH> [BODY]  → 写 /tmp/zwi_body2，echo HTTP code
  local method="$1" path="$2" body="${3:-}" token code
  token=$(get_token) || { echo "000"; return 1; }
  if [ -n "$body" ]; then
    code=$(curl -s -m 20 -o /tmp/zwi_body2 -w '%{http_code}' -X "$method" "$BASE$path" \
      -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "$body")
  else
    code=$(curl -s -m 20 -o /tmp/zwi_body2 -w '%{http_code}' -X "$method" "$BASE$path" \
      -H "Authorization: Bearer $token")
  fi
  echo "$code"
}

jqget() { jq -r "$1" /tmp/zwi_body2 2>/dev/null; }
ok200() { [ "$1" = 200 ] && { [ "$(jqget '.code')" = 200 ] || [ "$(jqget '.code')" = 0 ]; }; }
dbq() { docker exec zwi-mysql mysql -uroot -pzwinsight123 zw_insight -N -B -e "$1" 2>/dev/null | tr -d '\r' | head -1; }

# 逐个完成指定流程实例的待办任务（t9999admin 为租户 9999 SUPER_ADMIN 可办理候选组任务）
approve_flow() {
  local inst="$1" i=0 tid code
  while [ $i -lt 6 ]; do
    tid=$(dbq "SELECT ID_ FROM ACT_RU_TASK WHERE PROC_INST_ID_='$inst' ORDER BY CREATE_TIME_ ASC LIMIT 1;")
    [ -z "$tid" ] && return 0
    code=$(call2 POST /api/v1/workflow/approval/complete "$(jq -n --arg t "$tid" '{taskId:$t, comment:"批次2 自动审批通过"}')")
    if ! ok200 "$code"; then echo "      审批任务失败 taskId=$tid body=$(head -c 200 /tmp/zwi_body2)"; return 1; fi
    sleep 1; i=$((i+1))
  done
  tid=$(dbq "SELECT ID_ FROM ACT_RU_TASK WHERE PROC_INST_ID_='$inst' LIMIT 1;")
  [ -z "$tid" ] && return 0 || return 1
}

TS=$(date +%s)
PNAME="E2E_TEST_B2_${TS}"
PROJECT_ID=""; CC_ID=""; BUDGET_ID=""; CTRL_ID=""; CONTRACT_ID=""; INBOUND_ID=""; OUTBOUND_ID=""

# 逆序清理（API 优先，失败不阻断；结尾 SQL 兜底限本轮 project_id + tenant 9999）
cleanup() {
  echo "== 清理：逆序删除本轮自建数据 =="
  [ -n "$OUTBOUND_ID" ] && call2 DELETE "/api/v1/material/outbound/$OUTBOUND_ID" >/dev/null
  [ -n "$INBOUND_ID" ]  && call2 DELETE "/api/v1/material/inbound/$INBOUND_ID" >/dev/null
  [ -n "$CONTRACT_ID" ] && call2 DELETE "/api/v1/purchase/contract/$CONTRACT_ID" >/dev/null
  [ -n "$CTRL_ID" ]     && call2 DELETE "/api/v1/budget-control-configs/$CTRL_ID" >/dev/null
  [ -n "$BUDGET_ID" ]   && call2 DELETE "/api/v1/budget/$BUDGET_ID" >/dev/null
  [ -n "$CC_ID" ]       && call2 DELETE "/api/v1/contract/$CC_ID" >/dev/null
  [ -n "$PROJECT_ID" ]  && call2 DELETE "/api/v1/project/$PROJECT_ID" >/dev/null
  if [ -n "$PROJECT_ID" ]; then
    dbq "DELETE FROM biz_project_material_stock WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_material_outbound_detail WHERE outbound_id IN (SELECT id FROM biz_material_outbound WHERE project_id=$PROJECT_ID AND tenant_id=9999);" >/dev/null
    dbq "DELETE FROM biz_material_outbound WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_material_inbound_detail WHERE inbound_id IN (SELECT id FROM biz_material_inbound WHERE project_id=$PROJECT_ID AND tenant_id=9999);" >/dev/null
    dbq "DELETE FROM biz_material_inbound WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
    dbq "DELETE FROM biz_purchase_contract WHERE project_id=$PROJECT_ID AND tenant_id=9999;" >/dev/null
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
BODY=$(jq -n --arg pn "$PNAME" '{projectName:$pn, projectNature:"新建", projectType:"公共建筑", ownerCompanyName:"批次2自包含业主", signingCompanyName:"批次2自包含承包方", projectOverview:"批次2 S4-S9 自包含验证项目", projectAddress:"广州市天河区", contactName:"批次2", contactPhone:"13900000002", needTender:0, budgetAmount:2000000}')
code=$(call2 POST /api/v1/project "$BODY")
if ok200 "$code"; then result PASS "S0a 项目创建" ""; else result FAIL "S0a 项目创建" "http=$code body=$(head -c 300 /tmp/zwi_body2)"; trap - EXIT; exit 1; fi
call2 GET "/api/v1/project/page?page=1&size=5&projectName=$PNAME" >/dev/null
PROJECT_ID=$(jqget '.data.records[0].id')
[ -n "$PROJECT_ID" ] && [ "$PROJECT_ID" != "null" ] || { result FAIL "S0a 项目查询" "未找到 $PNAME"; trap - EXIT; exit 1; }
code=$(call2 POST "/api/v1/project/$PROJECT_ID/submit")
if ok200 "$code"; then result PASS "S0a2 立项提交(FILED)" ""; else result FAIL "S0a2 立项提交" "body=$(head -c 300 /tmp/zwi_body2)"; fi

echo "== S0b 自建施工合同（200万，submit→EFFECTIVE，L4 范式） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" '{projectId:$pid, contractType:"REGISTER", partyAName:"批次2自包含业主", signingDate:"2026-08-19", startDate:"2026-08-20", endDate:"2026-12-31", contractAmount:2000000, taxRate:9.00}')
code=$(call2 POST /api/v1/contract "$BODY")
if ok200 "$code"; then result PASS "S0b 施工合同创建" ""; else result FAIL "S0b 施工合同创建" "http=$code body=$(head -c 300 /tmp/zwi_body2)"; fi
call2 GET "/api/v1/contract/page?page=1&size=5&projectId=$PROJECT_ID" >/dev/null
CC_ID=$(jqget '.data.records[0].id')
code=$(call2 POST "/api/v1/contract/$CC_ID/submit")
if ok200 "$code"; then result PASS "S0b2 施工合同提交" ""; else result FAIL "S0b2 施工合同提交" "body=$(head -c 300 /tmp/zwi_body2)"; fi
sleep 1
call2 GET "/api/v1/contract/$CC_ID" >/dev/null
ST=$(jqget '.data.status')
if [ "$ST" = "SUBMITTED" ]; then
  INST=$(dbq "SELECT workflow_instance_id FROM biz_construction_contract WHERE id=$CC_ID;")
  [ -n "$INST" ] && approve_flow "$INST" >/dev/null
  call2 GET "/api/v1/contract/$CC_ID" >/dev/null; ST=$(jqget '.data.status')
fi
if [ "$ST" = "EFFECTIVE" ]; then result PASS "S0b3 施工合同=EFFECTIVE" ""; else result FAIL "S0b3 施工合同状态" "实际=$ST"; fi

echo "== S0c 自建预算（ORIGINAL 200万 + MATERIAL 科目明细，submit 直批 APPROVED） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" '{projectId:$pid, budgetType:"ORIGINAL", totalAmount:2000000, details:[{costCategory:"MATERIAL", itemName:"批次2材料预算", unit:"项", budgetQuantity:1, budgetUnitPrice:2000000, budgetTotalPrice:2000000}]}')
code=$(call2 POST /api/v1/budget "$BODY")
if ok200 "$code"; then result PASS "S0c 预算创建" ""; else result FAIL "S0c 预算创建" "http=$code body=$(head -c 300 /tmp/zwi_body2)"; fi
call2 GET "/api/v1/budget/project/$PROJECT_ID" >/dev/null
BUDGET_ID=$(jqget '.data.id // .data[0].id // empty')
code=$(call2 POST "/api/v1/budget/$BUDGET_ID/submit")
if ok200 "$code"; then result PASS "S0c2 预算提交(APPROVED)" ""; else result FAIL "S0c2 预算提交" "body=$(head -c 300 /tmp/zwi_body2)"; fi

echo "== S0d 预算管控配置（BLOCK，阈值 80） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" '{projectId:$pid, controlMode:"BLOCK", warningThreshold:80}')
code=$(call2 POST /api/v1/budget-control-configs "$BODY")
if ok200 "$code"; then result PASS "S0d 管控配置 BLOCK" ""; else result FAIL "S0d 管控配置" "body=$(head -c 300 /tmp/zwi_body2)"; fi
call2 GET "/api/v1/budget-control-configs?page=1&size=5&projectId=$PROJECT_ID" >/dev/null
CTRL_ID=$(jqget '.data.records[0].id // .data[0].id // empty')

echo "== S4 采购合同负向：金额 99999999，预期预算 BLOCK 拦截 =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" --arg pn "$PNAME" '{projectId:$pid, contractName:($pn+"_负向超额"), supplierName:"批次2供应商", signingDate:"2026-08-20", contractAmount:99999999, content:"批次2 自包含负向"}')
code=$(call2 POST /api/v1/purchase/contract "$BODY")
BIZ=$(jqget '.code'); MSG=$(jqget '.message // .msg // ""')
if [ "$code" != 200 ] || { [ "$BIZ" != "200" ] && [ "$BIZ" != "0" ]; }; then
  result PASS "S4 超额合同被拦截" "http=$code msg=$MSG"
else
  result FAIL "S4 超额合同未被预算拦截(缺陷)" "http=$code msg=$MSG"
  NEG_ID=$(jqget '.data // empty')
  [ -n "$NEG_ID" ] && [ "$NEG_ID" != "null" ] && call2 DELETE "/api/v1/purchase/contract/$NEG_ID" >/dev/null
fi

echo "== S5 采购合同正向：金额 1000000 =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" --arg pn "$PNAME" '{projectId:$pid, contractName:($pn+"_正向钢材"), supplierName:"批次2供应商", signingDate:"2026-08-20", contractAmount:1000000, content:"批次2 自包含正向"}')
code=$(call2 POST /api/v1/purchase/contract "$BODY")
if [ "$code" = 200 ] && [ "$(jqget '.code')" = 200 ]; then
  result PASS "S5a 正向合同创建" ""
else
  result FAIL "S5a 正向合同创建" "http=$code body=$(head -c 300 /tmp/zwi_body2)"
fi
call2 GET "/api/v1/purchase/contract/page?page=1&size=10&projectId=$PROJECT_ID" >/dev/null
CONTRACT_ID=$(jqget '.data.records[0].id')
if [ -n "$CONTRACT_ID" ] && [ "$CONTRACT_ID" != "null" ]; then
  result PASS "S5b 合同可查到" "id=$CONTRACT_ID"
  code=$(call2 POST "/api/v1/purchase/contract/$CONTRACT_ID/submit")
  if ok200 "$code"; then result PASS "S5c 提交审批" "$(jqget '.message // .msg // ""')"; else result FAIL "S5c 提交审批" "http=$code body=$(head -c 300 /tmp/zwi_body2)"; fi
  sleep 1
  call2 GET "/api/v1/purchase/contract/$CONTRACT_ID" >/dev/null
  STATUS=$(jqget '.data.status')
  if [ "$STATUS" = "SUBMITTED" ]; then
    INST=$(dbq "SELECT workflow_instance_id FROM biz_purchase_contract WHERE id=$CONTRACT_ID;")
    [ -n "$INST" ] && approve_flow "$INST" >/dev/null
    call2 GET "/api/v1/purchase/contract/$CONTRACT_ID" >/dev/null; STATUS=$(jqget '.data.status')
  fi
  if [ "$STATUS" = "EFFECTIVE" ]; then
    result PASS "S5d 合同状态=生效" ""
  else
    result FAIL "S5d 合同状态" "期望 EFFECTIVE 实际 $STATUS"
  fi
else
  result FAIL "S5b 合同查询" "未在分页中找到合同"
fi

echo "== S6 入库 100 吨（螺纹钢 HRB400 Φ20 @4000） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" --argjson cid "${CONTRACT_ID:-null}" --arg pn "$PNAME" '{projectId:$pid, contractId:$cid, inboundDate:"2026-08-20", directOutbound:0, remark:("E2E_TEST_入库_" + $pn), details:[{materialName:"螺纹钢 HRB400 Φ20", specification:"HRB400 Φ20", unit:"吨", quantity:100, unitPrice:4000}]}')
code=$(call2 POST /api/v1/material/inbound "$BODY")
if [ "$code" = 200 ] && [ "$(jqget '.code')" = 200 ]; then
  result PASS "S6a 入库单创建" ""
else
  result FAIL "S6a 入库单创建" "http=$code body=$(head -c 300 /tmp/zwi_body2)"
fi
call2 GET "/api/v1/material/inbound/page?page=1&size=10&projectId=$PROJECT_ID" >/dev/null
INBOUND_ID=$(jqget '.data.records[0].id')
INBOUND_STATUS=$(jqget '.data.records[0].status')
if [ -n "$INBOUND_ID" ] && [ "$INBOUND_ID" != "null" ]; then
  code=$(call2 POST "/api/v1/material/inbound/$INBOUND_ID/submit")
  if [ "$code" = 200 ] && [ "$(jqget '.code')" = 200 ]; then
    result PASS "S6b 入库提交(APPROVED+回写库存)" ""
  else
    result FAIL "S6b 入库提交" "http=$code body=$(head -c 300 /tmp/zwi_body2)"
  fi
else
  result FAIL "S6b 入库单查询" "未找到入库单(首条状态=$INBOUND_STATUS)"
fi

echo "== S7 出库负向：999 吨，预期创建时被拒（库存不足） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" '{projectId:$pid, outboundType:"PICK", outboundDate:"2026-08-20", operatorName:"批次2自包含", details:[{materialName:"螺纹钢 HRB400 Φ20", specification:"HRB400 Φ20", unit:"吨", quantity:999, unitPrice:4000}]}')
code=$(call2 POST /api/v1/material/outbound "$BODY")
MSG=$(jqget '.message // .msg // ""')
if { [ "$code" != 200 ] || [ "$(jqget '.code')" != 200 ]; } && echo "$MSG" | grep -q "库存不足"; then
  result PASS "S7 超库存出库被拒" "msg=$MSG"
else
  result FAIL "S7 超库存出库未被拒(缺陷)" "http=$code msg=$MSG"
fi

echo "== S8 出库正向：30 吨 =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" '{projectId:$pid, outboundType:"PICK", outboundDate:"2026-08-20", operatorName:"批次2自包含", details:[{materialName:"螺纹钢 HRB400 Φ20", specification:"HRB400 Φ20", unit:"吨", quantity:30, unitPrice:4000}]}')
code=$(call2 POST /api/v1/material/outbound "$BODY")
if [ "$code" = 200 ] && [ "$(jqget '.code')" = 200 ]; then
  result PASS "S8a 出库30吨创建" ""
  call2 GET "/api/v1/material/outbound/page?page=1&size=10&projectId=$PROJECT_ID" >/dev/null
  OUTBOUND_ID=$(jqget '.data.records[0].id')
  code=$(call2 POST "/api/v1/material/outbound/$OUTBOUND_ID/submit")
  if [ "$code" = 200 ] && [ "$(jqget '.code')" = 200 ]; then
    result PASS "S8b 出库提交(APPROVED)" ""
  else
    result FAIL "S8b 出库提交" "http=$code body=$(head -c 300 /tmp/zwi_body2)"
  fi
else
  result FAIL "S8a 出库30吨创建" "http=$code body=$(head -c 300 /tmp/zwi_body2)"
fi

echo "== S9 库存核对（预期 100-30=70） =="
code=$(call2 GET "/api/v1/material/stock/$PROJECT_ID")
STOCK_QTY=$(jqget '.data[] | select(.materialName=="螺纹钢 HRB400 Φ20") | .stockQuantity' | head -1)
if [ "$code" = 200 ] && { [ "$STOCK_QTY" = "70" ] || [ "$STOCK_QTY" = "70.00" ] || [ "$STOCK_QTY" = "70.0000" ]; }; then
  result PASS "S9 库存=70" ""
else
  result FAIL "S9 库存核对" "期望 70 实际 '$STOCK_QTY' http=$code"
fi

echo ""
echo "===== 批次2 S4-S9 API 验证汇总(自包含): PASS=$PASS FAIL=$FAIL ====="
[ "$FAIL" -eq 0 ]
