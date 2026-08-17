#!/bin/bash
# B4 端到端验证：付款申请审批回写链路（租户 9999 隔离，含完整清理）
# 链路：登录→创建项目→创建劳务合同→submit 生效→创建付款申请→submit→审批通过→验证 cumulative_paid
set -uo pipefail

HOST="http://127.0.0.1:18080"
USER="t9999admin"
PASS="123456"
TENANT=9999
CAPTCHA_KEY=""
TOKEN=""

# 清理变量
PROJECT_ID=""
CONTRACT_ID=""
SETTLEMENT_ID=""
PAYMENT_ID=""
CONFIG_ID=""
CREATED_LABOR_TEAM=""

cleanup() {
  echo ""
  echo "=== 清理测试数据（逆序） ==="
  if [ -n "$PAYMENT_ID" ]; then
    curl -s -o /dev/null -X DELETE -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" "$HOST/api/v1/finance/payment-apply/$PAYMENT_ID"
    echo "删除付款申请 $PAYMENT_ID"
  fi
  if [ -n "$SETTLEMENT_ID" ]; then
    curl -s -o /dev/null -X DELETE -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" "$HOST/api/v1/labor/settlement/$SETTLEMENT_ID"
    echo "删除劳务结算 $SETTLEMENT_ID"
  fi
  if [ -n "$CONTRACT_ID" ]; then
    curl -s -o /dev/null -X DELETE -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" "$HOST/api/v1/labor/contract/$CONTRACT_ID"
    echo "删除劳务合同 $CONTRACT_ID"
  fi
  if [ -n "$CONFIG_ID" ]; then
    curl -s -o /dev/null -X DELETE -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" "$HOST/api/v1/budget-control-configs/$CONFIG_ID"
    echo "删除预算控制配置 $CONFIG_ID"
  fi
  if [ -n "$PROJECT_ID" ]; then
    curl -s -o /dev/null -X DELETE -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" "$HOST/api/v1/project/$PROJECT_ID"
    echo "删除项目 $PROJECT_ID"
  fi
}
trap cleanup EXIT

check() {
  local desc="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    echo "  PASS: $desc ($actual)"
  else
    echo "  FAIL: $desc (期望 $expected, 实际 $actual)"
    exit 1
  fi
}

# 数值比较（容忍 50000 vs 50000.00 的格式差异）
check_num() {
  local desc="$1" expected="$2" actual="$3"
  if [ -z "$actual" ]; then
    echo "  FAIL: $desc (期望 $expected, 实际为空)"
    exit 1
  fi
  local eq=$(awk -v a="$expected" -v b="$actual" 'BEGIN{print (a==b)?"1":"0"}')
  if [ "$eq" = "1" ]; then
    echo "  PASS: $desc ($actual)"
  else
    echo "  FAIL: $desc (期望 $expected, 实际 $actual)"
    exit 1
  fi
}

echo "=== Step 1: 登录（验证码关闭环境） ==="
CAPTCHA_KEY="b4-$(date +%s)"
LOGIN_RESP=$(curl -s -X POST "$HOST/api/v1/auth/login" -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\",\"captchaKey\":\"$CAPTCHA_KEY\",\"captchaCode\":\"\"}")
TOKEN=$(echo "$LOGIN_RESP" | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then
  echo "FAIL: 登录失败: $LOGIN_RESP"
  exit 1
fi
echo "  PASS: 登录成功"

echo ""
echo "=== Step 2: 创建测试项目 ==="
TS=$(date +%s)
PROJ_RESP=$(curl -s -X POST "$HOST/api/v1/project" -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" -H "Content-Type: application/json" \
  -d "{\"projectName\":\"B4验证项目_$TS\",\"projectType\":\"BUILDING\",\"projectAddress\":\"B4测试\",\"needTender\":0}")
echo "  响应: $PROJ_RESP" | head -c 200
PAGE_RESP=$(curl -s -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" "$HOST/api/v1/project/page?page=1&size=5&projectName=B4%E9%AA%8C%E8%AF%81%E9%A1%B9%E7%9B%AE_$TS")
PROJECT_ID=$(echo "$PAGE_RESP" | grep -o '"id":"*[0-9]*' | head -1 | grep -o '[0-9]*$')
if [ -z "$PROJECT_ID" ]; then
  echo "FAIL: 项目创建失败"
  exit 1
fi
echo ""
echo "  PASS: 项目创建成功 ID=$PROJECT_ID"

echo ""
echo "=== Step 2.5: 设置预算控制为 EXEMPT（豁免，防新项目无额度拦截） ==="
CFG_RESP=$(curl -s -X POST "$HOST/api/v1/budget-control-configs" -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" -H "Content-Type: application/json" \
  -d "{\"projectId\":$PROJECT_ID,\"controlMode\":\"EXEMPT\",\"warningThreshold\":80}")
echo "  响应: $CFG_RESP" | head -c 200
EFF_RESP=$(curl -s -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" "$HOST/api/v1/budget-control-configs/project/$PROJECT_ID")
CONFIG_ID=$(echo "$EFF_RESP" | grep -o '"id":"*[0-9]*' | head -1 | grep -o '[0-9]*$')
echo ""
echo "  预算配置 ID=$CONFIG_ID"

echo ""
echo "=== Step 3: 创建劳务合同（关联项目） ==="
CONTRACT_RESP=$(curl -s -X POST "$HOST/api/v1/labor/contract" -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" -H "Content-Type: application/json" \
  -d "{\"projectId\":$PROJECT_ID,\"contractName\":\"B4劳务合同_$TS\",\"contractCode\":\"B4_LABOR_$TS\",\"teamName\":\"B4测试班组\",\"contractAmount\":100000}")
echo "  响应: $CONTRACT_RESP" | head -c 200
CPAGE_RESP=$(curl -s -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" "$HOST/api/v1/labor/contract/page?page=1&size=5&projectId=$PROJECT_ID")
CONTRACT_ID=$(echo "$CPAGE_RESP" | grep -o '"id":"*[0-9]*' | head -1 | grep -o '[0-9]*$')
if [ -z "$CONTRACT_ID" ]; then
  echo "FAIL: 劳务合同创建失败"
  exit 1
fi
echo ""
echo "  PASS: 劳务合同创建成功 ID=$CONTRACT_ID"

echo ""
echo "=== Step 4: 提交劳务合同（直批模式：submit 即置 EFFECTIVE，无 Flowable 流程） ==="
SUBMIT_RESP=$(curl -s -X POST "$HOST/api/v1/labor/contract/$CONTRACT_ID/submit" -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT")
echo "  submit 响应: $SUBMIT_RESP" | head -c 200
echo ""
sleep 1
CONTRACT_STATUS=$(curl -s -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" "$HOST/api/v1/labor/contract/$CONTRACT_ID" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
check "劳务合同状态" "EFFECTIVE" "$CONTRACT_STATUS"

echo ""
echo "=== Step 4.5: 创建劳务结算（50000 元）并提交（直批回写合同累计结算） ==="
SETTLE_RESP=$(curl -s -X POST "$HOST/api/v1/labor/settlement" -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" -H "Content-Type: application/json" \
  -d "{\"projectId\":$PROJECT_ID,\"contractId\":$CONTRACT_ID,\"settlementAmount\":50000,\"settlementDate\":\"2026-08-17\",\"remark\":\"B4验证结算\"}")
echo "  响应: $SETTLE_RESP" | head -c 200
echo ""
SPAGE_RESP=$(curl -s -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" "$HOST/api/v1/labor/settlement/page?page=1&size=5&contractId=$CONTRACT_ID")
SETTLEMENT_ID=$(echo "$SPAGE_RESP" | grep -o '"id":"*[0-9]*' | head -1 | grep -o '[0-9]*$')
if [ -z "$SETTLEMENT_ID" ]; then
  echo "FAIL: 劳务结算创建失败"
  exit 1
fi
SSUBMIT_RESP=$(curl -s -X POST "$HOST/api/v1/labor/settlement/$SETTLEMENT_ID/submit" -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT")
echo "  submit 响应: $SSUBMIT_RESP" | head -c 200
echo ""
sleep 1
CONTRACT_DETAIL_S=$(curl -s -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" "$HOST/api/v1/labor/contract/$CONTRACT_ID")
CUM_SETTLE=$(echo "$CONTRACT_DETAIL_S" | grep -o '"cumulativeSettlement":"*[0-9.]*' | head -1 | grep -o '[0-9.]*$')
check_num "劳务合同 cumulativeSettlement（结算回写）" "50000" "$CUM_SETTLE"

echo ""
echo "=== Step 5: 创建付款申请（LABOR，30000 元） ==="
PAY_RESP=$(curl -s -X POST "$HOST/api/v1/finance/payment-apply" -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" -H "Content-Type: application/json" \
  -d "{\"projectId\":$PROJECT_ID,\"contractId\":$CONTRACT_ID,\"contractCategory\":\"LABOR\",\"paymentAmount\":30000,\"paymentDate\":\"2026-08-17\"}")
echo "  响应: $PAY_RESP" | head -c 200
echo ""
PAYPAGE_RESP=$(curl -s -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" "$HOST/api/v1/finance/payment-apply/page?page=1&size=5&projectId=$PROJECT_ID&contractId=$CONTRACT_ID")
PAYMENT_ID=$(echo "$PAYPAGE_RESP" | grep -o '"id":"*[0-9]*' | head -1 | grep -o '[0-9]*$')
if [ -z "$PAYMENT_ID" ]; then
  echo "FAIL: 付款申请创建失败"
  exit 1
fi
echo "  PASS: 付款申请创建成功 ID=$PAYMENT_ID"

echo ""
echo "=== Step 6: 提交付款审批 ==="
PSUBMIT_RESP=$(curl -s -X POST "$HOST/api/v1/finance/payment-apply/$PAYMENT_ID/submit" -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT")
echo "  响应: $PSUBMIT_RESP" | head -c 200
echo ""
sleep 2

echo ""
echo "=== Step 7: 逐级审批付款申请（两级：项目负责人审批→财务审核） ==="
HANDLED=0
for ROUND in 1 2 3; do
  PTASK_ID=$(docker exec zwi-mysql mysql -uroot -pzwinsight123 zw_insight -N -B -e "SELECT ID_ FROM ACT_RU_TASK WHERE TENANT_ID_='$TENANT' ORDER BY CREATE_TIME_ ASC LIMIT 1;" 2>/dev/null | tr -d '\r')
  if [ -z "$PTASK_ID" ]; then
    echo "  第${ROUND}级：无待办，流程已结束"
    break
  fi
  echo "  第${ROUND}级待办 taskId: $PTASK_ID"
  PAPPROVE_RESP=$(curl -s -X POST "$HOST/api/v1/workflow/approval/complete" -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" -H "Content-Type: application/json" \
    -d "{\"taskId\":\"$PTASK_ID\",\"comment\":\"B4验证同意付款第${ROUND}级\"}")
  echo "  approve 响应: $PAPPROVE_RESP" | head -c 120
  echo ""
  HANDLED=$((HANDLED+1))
  sleep 2
done
if [ "$HANDLED" -eq 0 ]; then
  echo "FAIL: 未处理任何待办"
  exit 1
fi
sleep 2

echo ""
echo "=== Step 8: 验证回写结果（核心断言） ==="
PAY_STATUS=$(curl -s -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" "$HOST/api/v1/finance/payment-apply/$PAYMENT_ID" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
check "付款申请状态" "APPROVED" "$PAY_STATUS"

CONTRACT_DETAIL=$(curl -s -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" "$HOST/api/v1/labor/contract/$CONTRACT_ID")
CUM_PAID=$(echo "$CONTRACT_DETAIL" | grep -o '"cumulativePaid":"*[0-9.]*' | head -1 | grep -o '[0-9.]*$')
check_num "劳务合同 cumulativePaid" "30000" "$CUM_PAID"

PROJECT_DETAIL=$(curl -s -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" "$HOST/api/v1/project/$PROJECT_ID")
TOTAL_EXPENSE=$(echo "$PROJECT_DETAIL" | grep -o '"totalExpense":"*[0-9.]*' | head -1 | grep -o '[0-9.]*$')
check_num "项目 totalExpense" "30000" "$TOTAL_EXPENSE"

echo ""
echo "=== B4 端到端验证全部通过 ==="
