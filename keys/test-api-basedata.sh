#!/usr/bin/env bash
###############################################################################
# test-api-basedata.sh — L3 API 接口测试：基础数据模块（阶段四批 4）
#
# 覆盖端点（依据 zw-basedata 8 个 Controller 逐行核对）：
#   - /api/v1/basedata/company            自持公司：分页契约 + CRUD 零残留闭环
#   - /api/v1/basedata/owner              甲方单位：分页契约 + CRUD 零残留闭环
#   - /api/v1/basedata/inspection-scheme  检查方案：分页契约 + CRUD 零残留闭环
#   - /api/v1/basedata/material           材料字典：分页契约 + CRUD 零残留闭环 + 分类树
#   - /api/v1/basedata/material-category  材料分类：树契约（只读）
#   - /api/v1/basedata/supplier           供应商：分页契约 + CRUD 零残留闭环
#   - /api/v1/basedata/supplier-evaluation 供应商评价：闭环（用不存在 supplierId 隔离）+ 平均分契约
#   - /api/v1/basedata/supplier-blacklist 黑名单：只读契约（add/remove 会残留行，不做写入）
#
# 设计要点：
#   - 分页均为 GET 根路径 + /page 别名（抽测别名一致性）
#   - 黑名单 add 落 status=1 行、remove 仅置 status=0（物理行残留），故只读不写入
#   - 标识字段全部 ASCII（companyName/ownerName/schemeName/materialName/
#     supplierName/templateName 带时间戳后缀）
#   - 依赖 verify-base.sh 登录基座；jq 断言规范同阶段四批 1-3
###############################################################################
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/verify-base.sh" login 2>/dev/null || true

PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0
TS_SUFFIX=$(date +%s)

assert_http() {
  local expected_prefix="$1" test_name="$2" actual_code
  actual_code=$(cat /tmp/zwi_last_code 2>/dev/null || echo "000")
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [[ "$actual_code" == ${expected_prefix}* ]]; then
    PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] $test_name (HTTP $actual_code)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] $test_name (HTTP $actual_code, 期望 ${expected_prefix}xx)"
  fi
}

assert_body_code() {
  local expected="$1" test_name="$2" actual
  actual=$(grep -oE '"code"\s*:\s*\"?[0-9]+' /tmp/zwi_body 2>/dev/null | head -1 | grep -oE '[0-9]+$')
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [ "$actual" = "$expected" ]; then
    PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] $test_name (code=$actual)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] $test_name (code=$actual, 期望=$expected)"
  fi
}

# 负向断言：业务 code 不得为 200（兼容 HTTP 级/业务级两种错误映射方式）
assert_body_not_success() {
  local test_name="$1" actual
  actual=$(grep -oE '"code"\s*:\s*\"?[0-9]+' /tmp/zwi_body 2>/dev/null | head -1 | grep -oE '[0-9]+$')
  local http_code
  http_code=$(cat /tmp/zwi_last_code 2>/dev/null || echo "000")
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [ "$actual" != "200" ] || [[ "$http_code" == 4* ]] || [[ "$http_code" == 5* ]]; then
    PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] $test_name (code=$actual, HTTP $http_code)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] $test_name (期望非 200, 实际 code=$actual HTTP $http_code)"
  fi
}

assert_jq() {
  local expr="$1" test_name="$2" result
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if ! command -v jq >/dev/null 2>&1; then
    FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] $test_name (jq 未安装)"; return 1
  fi
  result=$(jq -e "$expr" /tmp/zwi_body 2>/dev/null | head -1)
  if [ "$result" = "true" ]; then
    PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] $test_name"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] $test_name (jq 表达式不满足: $expr)"
  fi
}

# 分页结构契约：code=200 + records 数组 + total 数字或字符串（雪花 Long→String 序列化）
PAGE_EXPR='.code==200 and (.data.records|type=="array") and ((.data.total|type=="number") or (.data.total|type=="string"))'

report_summary() {
  echo ""
  echo "═══════════════════════════════════════════════════════════"
  log "基础数据模块 API 测试汇总"
  echo "═══════════════════════════════════════════════════════════"
  log "  通过: $PASS_COUNT"
  log "  失败: $FAIL_COUNT"
  log "  总计: $TOTAL_COUNT"
  echo "═══════════════════════════════════════════════════════════"
  [ "$FAIL_COUNT" -eq 0 ]
}

log "========== L3 基础数据模块 API 测试开始 =========="

# ---------- 分页契约（GET 根路径） ----------
call GET "/api/v1/basedata/company?page=1&size=5"
assert_http 2 "公司-分页 HTTP"
assert_jq "$PAGE_EXPR" "公司-分页结构"

call GET "/api/v1/basedata/owner?page=1&size=5"
assert_http 2 "甲方-分页 HTTP"
assert_jq "$PAGE_EXPR" "甲方-分页结构"

call GET "/api/v1/basedata/inspection-scheme?page=1&size=5"
assert_http 2 "检查方案-分页 HTTP"
assert_jq "$PAGE_EXPR" "检查方案-分页结构"

call GET "/api/v1/basedata/material?page=1&size=5"
assert_http 2 "材料-分页 HTTP"
assert_jq "$PAGE_EXPR" "材料-分页结构"

call GET "/api/v1/basedata/supplier?page=1&size=5"
assert_http 2 "供应商-分页 HTTP"
assert_jq "$PAGE_EXPR" "供应商-分页结构"

call GET "/api/v1/basedata/supplier-evaluation?page=1&size=5"
assert_http 2 "供应商评价-分页 HTTP"
assert_jq "$PAGE_EXPR" "供应商评价-分页结构"

call GET "/api/v1/basedata/supplier-blacklist?page=1&size=5"
assert_http 2 "供应商黑名单-分页 HTTP"
assert_jq "$PAGE_EXPR" "供应商黑名单-分页结构"

# /page 别名与根路径契约一致（抽测公司）
call GET "/api/v1/basedata/company/page?page=1&size=5"
assert_jq "$PAGE_EXPR" "公司-/page 别名分页结构"

# 材料分类树契约（只读）
call GET "/api/v1/basedata/material-category/tree"
assert_http 2 "材料分类树 HTTP"
assert_jq '.code==200 and (.data|type=="array")' "材料分类树-返回数组"

call GET "/api/v1/basedata/material/categories"
assert_http 2 "材料-分类列表 HTTP"
assert_jq '.code==200 and (.data|type=="array")' "材料-分类列表为数组"

# ---------- 黑名单只读契约（add/remove 会残留物理行，不做写入） ----------
call GET "/api/v1/basedata/supplier-blacklist/check/999999999"
assert_http 2 "黑名单-检查 HTTP"
assert_jq '.code==200 and (.data==false)' "黑名单-不存在供应商返回 false"

# ---------- 公司 CRUD 闭环（按 companyName 查回） ----------
call POST "/api/v1/basedata/company" "{\"companyName\":\"L3Company$TS_SUFFIX\",\"status\":1}"
assert_http 2 "公司-创建 HTTP"
assert_body_code 200 "公司-创建业务码"

call GET "/api/v1/basedata/company?page=1&size=20&companyName=L3Company$TS_SUFFIX"
COMPANY_ID=$(jq -r --arg cn "L3Company$TS_SUFFIX" '.data.records[] | select(.companyName==$cn) | .id' /tmp/zwi_body 2>/dev/null | head -1)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$COMPANY_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 公司-查回ID: $COMPANY_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 公司-创建后查回失败"
fi

if [ -n "$COMPANY_ID" ]; then
  call GET "/api/v1/basedata/company/$COMPANY_ID"
  assert_http 2 "公司-详情 HTTP"
  assert_jq '.code==200 and (.data.id!=null)' "公司-详情含ID"

  call PUT "/api/v1/basedata/company/$COMPANY_ID" "{\"companyName\":\"L3CompanyU$TS_SUFFIX\",\"status\":1}"
  assert_http 2 "公司-更新 HTTP"
  assert_body_code 200 "公司-更新业务码"

  call DELETE "/api/v1/basedata/company/$COMPANY_ID"
  assert_http 2 "公司-删除 HTTP"
  assert_body_code 200 "公司-删除业务码"

  # 负向：删除后再编辑必须拒绝
  call PUT "/api/v1/basedata/company/$COMPANY_ID" "{\"companyName\":\"L3CompanyX$TS_SUFFIX\"}"
  assert_body_not_success "公司-删除后编辑被拒绝"
fi

# 负向：详情不存在必须拒绝
call GET "/api/v1/basedata/company/999999999"
assert_body_not_success "公司-不存在详情被拒绝"

# ---------- 甲方 CRUD 闭环（按 ownerName 查回） ----------
call POST "/api/v1/basedata/owner" "{\"ownerName\":\"L3Owner$TS_SUFFIX\",\"status\":1}"
assert_http 2 "甲方-创建 HTTP"
assert_body_code 200 "甲方-创建业务码"

call GET "/api/v1/basedata/owner?page=1&size=20&ownerName=L3Owner$TS_SUFFIX"
OWNER_ID=$(jq -r --arg on "L3Owner$TS_SUFFIX" '.data.records[] | select(.ownerName==$on) | .id' /tmp/zwi_body 2>/dev/null | head -1)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$OWNER_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 甲方-查回ID: $OWNER_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 甲方-创建后查回失败"
fi

if [ -n "$OWNER_ID" ]; then
  call PUT "/api/v1/basedata/owner/$OWNER_ID" "{\"ownerName\":\"L3OwnerU$TS_SUFFIX\",\"status\":1}"
  assert_http 2 "甲方-更新 HTTP"
  assert_body_code 200 "甲方-更新业务码"

  call DELETE "/api/v1/basedata/owner/$OWNER_ID"
  assert_http 2 "甲方-删除 HTTP"
  assert_body_code 200 "甲方-删除业务码"

  call PUT "/api/v1/basedata/owner/$OWNER_ID" "{\"ownerName\":\"L3OwnerX$TS_SUFFIX\"}"
  assert_body_not_success "甲方-删除后编辑被拒绝"
fi

# ---------- 检查方案 CRUD 闭环（按 schemeName 查回） ----------
call POST "/api/v1/basedata/inspection-scheme" "{\"schemeName\":\"L3Scheme$TS_SUFFIX\",\"schemeType\":\"QUALITY\",\"status\":1}"
assert_http 2 "检查方案-创建 HTTP"
assert_body_code 200 "检查方案-创建业务码"

call GET "/api/v1/basedata/inspection-scheme?page=1&size=20&schemeName=L3Scheme$TS_SUFFIX"
SCHEME_ID=$(jq -r --arg sn "L3Scheme$TS_SUFFIX" '.data.records[] | select(.schemeName==$sn) | .id' /tmp/zwi_body 2>/dev/null | head -1)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$SCHEME_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 检查方案-查回ID: $SCHEME_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 检查方案-创建后查回失败"
fi

if [ -n "$SCHEME_ID" ]; then
  call PUT "/api/v1/basedata/inspection-scheme/$SCHEME_ID" "{\"schemeName\":\"L3SchemeU$TS_SUFFIX\",\"schemeType\":\"QUALITY\",\"status\":1}"
  assert_http 2 "检查方案-更新 HTTP"
  assert_body_code 200 "检查方案-更新业务码"

  call DELETE "/api/v1/basedata/inspection-scheme/$SCHEME_ID"
  assert_http 2 "检查方案-删除 HTTP"
  assert_body_code 200 "检查方案-删除业务码"

  call PUT "/api/v1/basedata/inspection-scheme/$SCHEME_ID" "{\"schemeName\":\"L3SchemeX$TS_SUFFIX\"}"
  assert_body_not_success "检查方案-删除后编辑被拒绝"
fi

# ---------- 材料 CRUD 闭环（按 materialName 查回） ----------
call POST "/api/v1/basedata/material" "{\"materialName\":\"L3Mat$TS_SUFFIX\",\"unit\":\"pcs\"}"
assert_http 2 "材料-创建 HTTP"
assert_body_code 200 "材料-创建业务码"

call GET "/api/v1/basedata/material?page=1&size=20&materialName=L3Mat$TS_SUFFIX"
MATERIAL_ID=$(jq -r --arg mn "L3Mat$TS_SUFFIX" '.data.records[] | select(.materialName==$mn) | .id' /tmp/zwi_body 2>/dev/null | head -1)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$MATERIAL_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 材料-查回ID: $MATERIAL_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 材料-创建后查回失败"
fi

if [ -n "$MATERIAL_ID" ]; then
  call PUT "/api/v1/basedata/material/$MATERIAL_ID" "{\"materialName\":\"L3MatU$TS_SUFFIX\",\"unit\":\"box\"}"
  assert_http 2 "材料-更新 HTTP"
  assert_body_code 200 "材料-更新业务码"

  call DELETE "/api/v1/basedata/material/$MATERIAL_ID"
  assert_http 2 "材料-删除 HTTP"
  assert_body_code 200 "材料-删除业务码"

  call PUT "/api/v1/basedata/material/$MATERIAL_ID" "{\"materialName\":\"L3MatX$TS_SUFFIX\"}"
  assert_body_not_success "材料-删除后编辑被拒绝"
fi

# ---------- 供应商 CRUD 闭环（按 supplierName 查回） ----------
call POST "/api/v1/basedata/supplier" "{\"supplierName\":\"L3Sup$TS_SUFFIX\",\"supplierType\":\"MATERIAL\",\"status\":1}"
assert_http 2 "供应商-创建 HTTP"
assert_body_code 200 "供应商-创建业务码"

call GET "/api/v1/basedata/supplier?page=1&size=20&supplierName=L3Sup$TS_SUFFIX"
SUPPLIER_ID=$(jq -r --arg sn "L3Sup$TS_SUFFIX" '.data.records[] | select(.supplierName==$sn) | .id' /tmp/zwi_body 2>/dev/null | head -1)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$SUPPLIER_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 供应商-查回ID: $SUPPLIER_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 供应商-创建后查回失败"
fi

if [ -n "$SUPPLIER_ID" ]; then
  call PUT "/api/v1/basedata/supplier/$SUPPLIER_ID" "{\"supplierName\":\"L3SupU$TS_SUFFIX\",\"supplierType\":\"MATERIAL\",\"status\":1}"
  assert_http 2 "供应商-更新 HTTP"
  assert_body_code 200 "供应商-更新业务码"

  call DELETE "/api/v1/basedata/supplier/$SUPPLIER_ID"
  assert_http 2 "供应商-删除 HTTP"
  assert_body_code 200 "供应商-删除业务码"

  call PUT "/api/v1/basedata/supplier/$SUPPLIER_ID" "{\"supplierName\":\"L3SupX$TS_SUFFIX\"}"
  assert_body_not_success "供应商-删除后编辑被拒绝"
fi

# ---------- 供应商评价闭环（用不存在 supplierId 隔离，避免依赖真实供应商） ----------
call POST "/api/v1/basedata/supplier-evaluation" "{\"supplierId\":999999999,\"supplierName\":\"L3EvalSup$TS_SUFFIX\",\"evaluationDate\":\"$(date +%F)\",\"qualityScore\":5,\"timelinessScore\":5,\"priceScore\":5,\"serviceScore\":5,\"cooperationScore\":5,\"totalScore\":5.0,\"evaluationType\":\"L3TEST\"}"
assert_http 2 "供应商评价-创建 HTTP"
assert_body_code 200 "供应商评价-创建业务码"

call GET "/api/v1/basedata/supplier-evaluation?page=1&size=20&supplierId=999999999"
EVAL_ID=$(jq -r --arg sn "L3EvalSup$TS_SUFFIX" '.data.records[] | select(.supplierName==$sn) | .id' /tmp/zwi_body 2>/dev/null | head -1)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$EVAL_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 供应商评价-查回ID: $EVAL_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 供应商评价-创建后查回失败"
fi

if [ -n "$EVAL_ID" ]; then
  call PUT "/api/v1/basedata/supplier-evaluation/$EVAL_ID" "{\"supplierId\":999999999,\"supplierName\":\"L3EvalSup$TS_SUFFIX\",\"evaluationDate\":\"$(date +%F)\",\"qualityScore\":4,\"timelinessScore\":4,\"priceScore\":4,\"serviceScore\":4,\"cooperationScore\":4,\"totalScore\":4.0,\"evaluationType\":\"L3TEST\"}"
  assert_http 2 "供应商评价-更新 HTTP"
  assert_body_code 200 "供应商评价-更新业务码"

  call DELETE "/api/v1/basedata/supplier-evaluation/$EVAL_ID"
  assert_http 2 "供应商评价-删除 HTTP"
  assert_body_code 200 "供应商评价-删除业务码"
fi

# 平均分契约（无评价时 code=200，data 允许 null/0）
call GET "/api/v1/basedata/supplier-evaluation/avg-score/999999999"
assert_http 2 "供应商平均分 HTTP"
assert_body_code 200 "供应商平均分业务码"

report_summary
