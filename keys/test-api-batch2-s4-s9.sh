#!/usr/bin/env bash
###############################################################################
# test-api-batch2-s4-s9.sh — 批次2 S4-S9 API 层真实链路验证
#
# 背景（2026-08-17 归零重建全链路 E2E）：
#   缺陷#7 采购合同表单无项目字段 → projectId=null → DB NOT NULL 直接 500
#   前端修复（purchase/contract.vue 增加 ProjectSelector）已提交本地，
#   待统一走 CI 部署。本脚本在 API 层先行验证 S4-S9 业务链路的正确性：
#   S4 预算 BLOCK 负向 / S5 正向合同+提交 / S6 入库 / S7 超库存负向 /
#   S8 出库正向 / S9 库存核对（100-30=70）。
#
# 依赖：verify-base.sh 的登录基座（token 缓存 /root/zwi-deploy/.zwi_token）
# 全程真实接口真实数据，禁止 mock。断言失败即 FAIL，退出码反映结果。
###############################################################################
set -uo pipefail
cd "$(dirname "$0")"
source ./verify-base.sh usage >/dev/null 2>&1 || true
# source 后 verify-base.sh 会执行 usage；重新定义所需函数来源已包含（usage 无副作用）

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

echo "== 0. 登录（复用 verify-base 基座） =="
get_token >/dev/null || { echo "[FAIL] L0 登录失败"; exit 1; }
echo "[PASS] L0 登录获取 token"

echo "== L1 项目 ID =="
code=$(call2 GET "/api/v1/project/list")
PROJECT_ID=$(jqget '.data[0].id')
PROJECT_NAME=$(jqget '.data[0].projectName')
if [ "$code" = 200 ] && [ -n "$PROJECT_ID" ] && [ "$PROJECT_ID" != "null" ]; then
  result PASS "L1 项目" "$PROJECT_ID ($PROJECT_NAME)"
else
  result FAIL "L1 项目" "code=$code body=$(head -c 200 /tmp/zwi_body2)"
  exit 1
fi

echo "== S4 采购合同负向：金额 99999999，预期预算 BLOCK 拦截 =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" '{projectId:$pid, contractName:"批次2负向-超额采购", supplierName:"华东建材供应商", signingDate:"2026-08-17", contractAmount:99999999, content:"批次2 API层负向"}')
code=$(call2 POST /api/v1/purchase/contract "$BODY")
BIZ=$(jqget '.code'); MSG=$(jqget '.message // .msg // ""')
if [ "$code" != 200 ] || { [ "$BIZ" != "200" ] && [ "$BIZ" != "0" ]; }; then
  result PASS "S4 超额合同被拦截" "http=$code msg=$MSG"
else
  # 未被拦截属于缺陷：尝试删除残留草稿
  result FAIL "S4 超额合同未被预算拦截(缺陷)" "http=$code msg=$MSG"
  call2 GET "/api/v1/purchase/contract/page?page=1&size=10" >/dev/null
  NEG_ID=$(jqget '.data.records[] | select(.contractName=="批次2负向-超额采购") | .id' | head -1)
  if [ -n "$NEG_ID" ]; then call2 DELETE "/api/v1/purchase/contract/$NEG_ID" >/dev/null; echo "      已清理残留草稿 id=$NEG_ID"; fi
fi

echo "== S5 采购合同正向：金额 1000000 =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" '{projectId:$pid, contractName:"批次2正向-钢材采购", supplierName:"华东建材供应商", signingDate:"2026-08-17", contractAmount:1000000, content:"批次2 API层正向"}')
code=$(call2 POST /api/v1/purchase/contract "$BODY")
if [ "$code" = 200 ] && [ "$(jqget '.code')" = 200 ]; then
  result PASS "S5a 正向合同创建" ""
else
  result FAIL "S5a 正向合同创建" "http=$code body=$(head -c 300 /tmp/zwi_body2)"
fi

call2 GET "/api/v1/purchase/contract/page?page=1&size=10" >/dev/null
CONTRACT_ID=$(jqget '.data.records[] | select(.contractName=="批次2正向-钢材采购") | .id' | head -1)
if [ -n "$CONTRACT_ID" ] && [ "$CONTRACT_ID" != "null" ]; then
  result PASS "S5b 合同可查到" "id=$CONTRACT_ID"
  code=$(call2 POST "/api/v1/purchase/contract/$CONTRACT_ID/submit")
  if [ "$code" = 200 ] && [ "$(jqget '.code')" = 200 ]; then
    result PASS "S5c 提交审批" "$(jqget '.message // .msg // ""')"
  else
    result FAIL "S5c 提交审批" "http=$code body=$(head -c 300 /tmp/zwi_body2)"
  fi
  code=$(call2 GET "/api/v1/purchase/contract/$CONTRACT_ID")
  STATUS=$(jqget '.data.status')
  if [ "$STATUS" = "EFFECTIVE" ]; then
    result PASS "S5d 合同状态=生效" ""
  else
    result FAIL "S5d 合同状态" "期望 EFFECTIVE 实际 $STATUS"
  fi
else
  result FAIL "S5b 合同查询" "未在分页中找到合同"
fi

echo "== S6 入库 100 吨（螺纹钢 HRB400 Φ20 @4000） =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" --argjson cid "${CONTRACT_ID:-null}" '{projectId:$pid, contractId:$cid, inboundDate:"2026-08-17", directOutbound:0, details:[{materialName:"螺纹钢 HRB400 Φ20", specification:"HRB400 Φ20", unit:"吨", quantity:100, unitPrice:4000}]}')
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
BODY=$(jq -n --argjson pid "$PROJECT_ID" '{projectId:$pid, outboundType:"PICK", outboundDate:"2026-08-17", operatorName:"系统管理员", details:[{materialName:"螺纹钢 HRB400 Φ20", specification:"HRB400 Φ20", unit:"吨", quantity:999, unitPrice:4000}]}')
code=$(call2 POST /api/v1/material/outbound "$BODY")
MSG=$(jqget '.message // .msg // ""')
if { [ "$code" != 200 ] || [ "$(jqget '.code')" != 200 ]; } && echo "$MSG" | grep -q "库存不足"; then
  result PASS "S7 超库存出库被拒" "msg=$MSG"
else
  result FAIL "S7 超库存出库未被拒(缺陷)" "http=$code msg=$MSG"
fi

echo "== S8 出库正向：30 吨 =="
BODY=$(jq -n --argjson pid "$PROJECT_ID" '{projectId:$pid, outboundType:"PICK", outboundDate:"2026-08-17", operatorName:"系统管理员", details:[{materialName:"螺纹钢 HRB400 Φ20", specification:"HRB400 Φ20", unit:"吨", quantity:30, unitPrice:4000}]}')
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
echo "===== 批次2 S4-S9 API 验证汇总: PASS=$PASS FAIL=$FAIL ====="
[ "$FAIL" -eq 0 ]
