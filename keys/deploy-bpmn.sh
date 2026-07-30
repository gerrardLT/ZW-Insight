#!/usr/bin/env bash
# 批量部署缺失的审批流程定义到当前租户（服务器侧运行）
# 走真实部署 API：POST /api/v1/workflow/process/deploy（multipart 上传 BPMN）
# 部署目标租户由登录账号决定：ZWI_USER/ZWI_PASS 环境变量传给 verify-base.sh
#   例：ZWI_USER=t9999admin ZWI_PASS=123456 ZWI_TENANT_ID=9999 bash deploy-bpmn.sh
cd /root/zwi-deploy || exit 1
rm -f /root/zwi-deploy/.zwi_token   # 清旧 token，确保用当前 ZWI_USER 重新登录
source ./verify-base.sh login >/dev/null 2>&1
TOKEN=$(cat /root/zwi-deploy/.zwi_token)
BASE="http://127.0.0.1:18080"
TENANT_ID="${ZWI_TENANT_ID:-1}"

for name in output_report_approval invoice_apply_approval payment_apply_approval project_settlement_approval purchase_contract_approval completion_acceptance_approval project_close_approval machine_settlement material_transfer_approval purchase_settlement_approval construction_contract_approval; do
  BPMN="/root/zwi-deploy/${name}.bpmn20.xml"
  if [ ! -f "$BPMN" ]; then
    echo "SKIP: $BPMN 不存在"; continue
  fi
  echo "--- deploy $name ---"
  curl -s -X POST "$BASE/api/v1/workflow/process/deploy" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$BPMN" \
    -F "name=$name" | grep -oE '"code":[0-9]+' | head -1
done

echo "--- verify in ACT_RE_PROCDEF (tenant $TENANT_ID) ---"
docker exec zwi-mysql mysql -uroot -pzwinsight123 zw_insight -e \
  "SELECT KEY_, TENANT_ID_, MAX(VERSION_) AS ver FROM ACT_RE_PROCDEF WHERE TENANT_ID_='$TENANT_ID' GROUP BY KEY_, TENANT_ID_;" 2>/dev/null
