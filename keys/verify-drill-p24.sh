#!/usr/bin/env bash
###############################################################################
# verify-drill-p24.sh — P2-4 单据穿透链 L3 验证（服务器端执行）
#
# 覆盖端点：
#   - GET /api/v1/dashboard/cockpit/drill/{cost-categories,suppliers,contracts,
#     account-txn,contract-docs}
#   - GET /api/v1/workflow/approval/trace
#
# 设计：真实接口真实流程；断言不满足一律 FAIL 不静默跳过（无数据时 docs 环节
# 记 SKIP 并如实说明）。非法类别负向用例必须非 200。
###############################################################################
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/verify-base.sh" login || { echo "LOGIN FAILED"; exit 1; }

BASEQ="/api/v1/dashboard/cockpit"
PASS=0; FAIL=0; SKIP=0

t() { # t <name> <jq-expr>
  local name="$1" expr="$2" ok
  if ok=$(jq -e "$expr" /tmp/zwi_body 2>/dev/null) && [ "$ok" != "false" ] && [ "$ok" != "null" ]; then
    PASS=$((PASS+1)); echo "PASS  $name"
  else
    FAIL=$((FAIL+1)); echo "FAIL  $name  body=$(head -c 200 /tmp/zwi_body)"
  fi
}

# 1) 项目 → 成本分类（演示项目 90001 有 CBS 种子）
call GET "$BASEQ/drill/cost-categories?projectId=90001"
t 'cost-categories: code=200' '.code==200'
t 'cost-categories: rows 为数组' '.data.rows|type=="array"'
t 'cost-categories: 含口径 attachmentNote' '.data.attachmentNote|type=="string"'
CC=$(jq -r '[.data.rows[] | select(.contractCategory != "OTHER_EXPENSE")][0].contractCategory // empty' /tmp/zwi_body 2>/dev/null)
CC=${CC:-PURCHASE}
echo "      选取穿透类别: $CC"

# 2) 成本分类 → 供应商
call GET "$BASEQ/drill/suppliers?projectId=90001&contractCategory=$CC"
t 'suppliers: code=200 + rows 数组' '.code==200 and (.data.rows|type=="array")'
t 'suppliers: categoryName 下发' '.data.categoryName|type=="string"'

# 3) 供应商 → 合同（不带供应商 = 该类全部）
call GET "$BASEQ/drill/contracts?projectId=90001&contractCategory=$CC"
t 'contracts: code=200 + rows 数组' '.code==200 and (.data.rows|type=="array")'
CID=$(jq -r '.data.rows[0].id // empty' /tmp/zwi_body 2>/dev/null)

# 4) 合同 → 原始单据（无合同时如实 SKIP，不伪造合同ID）
if [ -n "$CID" ]; then
  call GET "$BASEQ/drill/contract-docs?contractCategory=$CC&contractId=$CID"
  t 'contract-docs: code=200 + rows 数组' '.code==200 and (.data.rows|type=="array")'
else
  SKIP=$((SKIP+1)); echo "SKIP  contract-docs（类别 $CC 下无合同，不伪造 contractId）"
fi

# 5) 成本分类 → 成本流水（OTHER_EXPENSE 覆盖 INDIRECT/OTHER）
call GET "$BASEQ/drill/account-txn?projectId=90001&contractCategory=OTHER_EXPENSE"
t 'account-txn: code=200 + rows 数组' '.code==200 and (.data.rows|type=="array")'
t 'account-txn: basis 下发' '.data.basis|type=="string"'

# 6) 负向：非法类别必须被拒（400，不静默当全部）
call GET "$BASEQ/drill/suppliers?projectId=90001&contractCategory=BOGUS"
t '非法类别: code=400 拒绝' '.code==400'

# 7) 审批轨迹端点（不存在的实例 → 200 + UNKNOWN + note，不伪造轨迹）
call GET "/api/v1/workflow/approval/trace?processInstanceId=zw-drill-nonexist"
t 'trace: code=200 + records 数组' '.code==200 and (.data.approvalRecords|type=="array")'
t 'trace: 实例不存在如实 UNKNOWN' '.data.status=="UNKNOWN" and (.data.note|type=="string")'

# 8) 日志核对：无 404/405、无异常堆栈
#    已知噪音源（2026-09-25 实测逐行复核）：① 18080 被外网 HTTPS 探测产生的
#    Tomcat “Invalid character found in method name” 扫描堆栈；② UrgeScheduleTask
#    租户上下文缺失（审计报告 7.5 已知残留，每 30 分钟一次）。两者命中时本项会
#    FAIL，需按上述归因人工复核后放行，不得直接改基线掩盖。
check_logs 120 && PASS=$((PASS+1)) || { echo "FAIL  check_logs（先排查 TLS 扫描噪音/UrgeScheduleTask 已知残留，见上方注释）"; FAIL=$((FAIL+1)); }

echo "=============================================="
echo "RESULT: PASS=$PASS FAIL=$FAIL SKIP=$SKIP"
[ "$FAIL" -eq 0 ]
