#!/usr/bin/env bash
###############################################################################
# test-api-tender.sh — L3 API 接口测试：投标模块（阶段四批 2）
#
# 覆盖端点（依据 zw-tender 6 个 Controller 逐行核对）：
#   - /api/v1/tender/register      投标登记：分页/CRUD 闭环（create 无返回ID，按 ownerCompany 查回）
#   - /api/v1/tender/certificate   人员证书+企业证书：分页/CRUD 闭环/删除后编辑负向
#   - /api/v1/tender/deposit       保证金申请/退还：分页结构
#   - /api/v1/tender/fee           投标费用：分页/CRUD 闭环（DRAFT 链）
#   - /api/v1/tender/task          投标任务：列表
#   - /api/v1/tender/open-bid      开标记录：按登记ID查询
#
# 设计要点：
#   - 创建类 save 均返回 R<Void> 无 ID，统一「创建→按唯一标识查回取 ID→更新→删除」闭环，零残留
#   - 标识字段全部 ASCII（ownerCompany/personName/certificateName/feeType 带时间戳后缀）
#   - 依赖 verify-base.sh 登录基座；jq 断言规范同阶段四批 1
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
  log "投标模块 API 测试汇总"
  echo "═══════════════════════════════════════════════════════════"
  log "  通过: $PASS_COUNT"
  log "  失败: $FAIL_COUNT"
  log "  总计: $TOTAL_COUNT"
  echo "═══════════════════════════════════════════════════════════"
  [ "$FAIL_COUNT" -eq 0 ]
}

log "========== L3 投标模块 API 测试开始 =========="

# ---------- 只读分页契约 ----------
call GET "/api/v1/tender/register/page?page=1&size=5"
assert_http 2 "投标登记-分页 HTTP"
assert_jq "$PAGE_EXPR" "投标登记-分页结构"

call GET "/api/v1/tender/certificate/person?page=1&size=5"
assert_http 2 "人员证书-分页 HTTP"
assert_jq "$PAGE_EXPR" "人员证书-分页结构"

call GET "/api/v1/tender/certificate/company?page=1&size=5"
assert_http 2 "企业证书-分页 HTTP"
assert_jq "$PAGE_EXPR" "企业证书-分页结构"

call GET "/api/v1/tender/deposit/apply?page=1&size=5"
assert_http 2 "保证金申请-分页 HTTP"
assert_jq "$PAGE_EXPR" "保证金申请-分页结构"

call GET "/api/v1/tender/deposit/return?page=1&size=5"
assert_http 2 "保证金退还-分页 HTTP"
assert_jq "$PAGE_EXPR" "保证金退还-分页结构"

call GET "/api/v1/tender/fee/page?page=1&size=5"
assert_http 2 "投标费用-分页 HTTP"
assert_jq "$PAGE_EXPR" "投标费用-分页结构"

# ---------- 投标登记 CRUD 闭环（按 ownerCompany 查回） ----------
call GET "/api/v1/project/page?page=1&size=1"
PROJECT_ID=$(jq -r '.data.records[0].id // empty' /tmp/zwi_body 2>/dev/null)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$PROJECT_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 取到真实项目ID: $PROJECT_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 无法取到项目ID（种子数据缺失），后续闭环无法执行"
fi

REGISTER_ID=""
if [ -n "$PROJECT_ID" ]; then
  call POST "/api/v1/tender/register" "{\"projectId\":$PROJECT_ID,\"ownerCompany\":\"L3Tmp$TS_SUFFIX\",\"bidMethod\":\"L3TEST\"}"
  assert_http 2 "投标登记-创建 HTTP"
  assert_body_code 200 "投标登记-创建业务码"

  call GET "/api/v1/tender/register/page?page=1&size=50&projectId=$PROJECT_ID"
  REGISTER_ID=$(jq -r --arg oc "L3Tmp$TS_SUFFIX" '.data.records[] | select(.ownerCompany==$oc) | .id' /tmp/zwi_body 2>/dev/null | head -1)
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [ -n "$REGISTER_ID" ]; then
    PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 投标登记-查回ID: $REGISTER_ID"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 投标登记-创建后查回失败"
  fi

  if [ -n "$REGISTER_ID" ]; then
    call GET "/api/v1/tender/register/$REGISTER_ID"
    assert_http 2 "投标登记-详情 HTTP"
    assert_jq '.code==200 and (.data.id!=null)' "投标登记-详情含ID"

    call PUT "/api/v1/tender/register/$REGISTER_ID" "{\"projectId\":$PROJECT_ID,\"ownerCompany\":\"L3TmpU$TS_SUFFIX\",\"bidMethod\":\"L3TEST\"}"
    assert_http 2 "投标登记-更新 HTTP"
    assert_body_code 200 "投标登记-更新业务码"

    # ---------- 投标任务：按登记ID列表 ----------
    call GET "/api/v1/tender/task/$REGISTER_ID"
    assert_http 2 "投标任务-列表 HTTP"
    assert_jq '.code==200 and (.data|type=="array")' "投标任务-列表为数组"

    # ---------- 开标记录：按登记ID查询（无记录时 data=null 仍为合法契约） ----------
    call GET "/api/v1/tender/open-bid/$REGISTER_ID"
    assert_http 2 "开标记录-查询 HTTP"
    assert_body_code 200 "开标记录-查询业务码"

    # ---------- 投标费用 CRUD 闭环（挂在本登记下，按 feeType 查回） ----------
    call POST "/api/v1/tender/fee" "{\"registerId\":$REGISTER_ID,\"projectId\":$PROJECT_ID,\"feeType\":\"L3Fee$TS_SUFFIX\",\"feeAmount\":123.45}"
    assert_http 2 "投标费用-创建 HTTP"
    assert_body_code 200 "投标费用-创建业务码"

    call GET "/api/v1/tender/fee/page?page=1&size=50&registerId=$REGISTER_ID"
    FEE_ID=$(jq -r --arg ft "L3Fee$TS_SUFFIX" '.data.records[] | select(.feeType==$ft) | .id' /tmp/zwi_body 2>/dev/null | head -1)
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    if [ -n "$FEE_ID" ]; then
      PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 投标费用-查回ID: $FEE_ID"
    else
      FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 投标费用-创建后查回失败"
    fi

    if [ -n "$FEE_ID" ]; then
      call PUT "/api/v1/tender/fee/$FEE_ID" "{\"registerId\":$REGISTER_ID,\"projectId\":$PROJECT_ID,\"feeType\":\"L3Fee$TS_SUFFIX\",\"feeAmount\":456.78}"
      assert_http 2 "投标费用-DRAFT更新 HTTP"
      assert_body_code 200 "投标费用-DRAFT更新业务码"

      call DELETE "/api/v1/tender/fee/$FEE_ID"
      assert_http 2 "投标费用-删除 HTTP"
      assert_body_code 200 "投标费用-删除业务码"

      # 负向：删除后再编辑必须拒绝，不得静默成功
      call PUT "/api/v1/tender/fee/$FEE_ID" "{\"registerId\":$REGISTER_ID,\"feeType\":\"L3Fee$TS_SUFFIX\",\"feeAmount\":1}"
      assert_body_not_success "投标费用-删除后编辑被拒绝"
    fi

    call DELETE "/api/v1/tender/register/$REGISTER_ID"
    assert_http 2 "投标登记-删除 HTTP"
    assert_body_code 200 "投标登记-删除业务码"

    # 负向：删除后再编辑必须拒绝
    call PUT "/api/v1/tender/register/$REGISTER_ID" "{\"projectId\":$PROJECT_ID,\"ownerCompany\":\"L3TmpX$TS_SUFFIX\"}"
    assert_body_not_success "投标登记-删除后编辑被拒绝"
  fi
fi

# ---------- 人员证书 CRUD 闭环（按 personName 查回） ----------
call POST "/api/v1/tender/certificate/person" "{\"personName\":\"L3Person$TS_SUFFIX\",\"certificateType\":\"L3TEST\",\"certificateNo\":\"L3PC$TS_SUFFIX\",\"status\":1}"
assert_http 2 "人员证书-创建 HTTP"
assert_body_code 200 "人员证书-创建业务码"

call GET "/api/v1/tender/certificate/person?page=1&size=20&personName=L3Person$TS_SUFFIX"
PERSON_CERT_ID=$(jq -r --arg no "L3PC$TS_SUFFIX" '.data.records[] | select(.certificateNo==$no) | .id' /tmp/zwi_body 2>/dev/null | head -1)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$PERSON_CERT_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 人员证书-查回ID: $PERSON_CERT_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 人员证书-创建后查回失败"
fi

if [ -n "$PERSON_CERT_ID" ]; then
  call PUT "/api/v1/tender/certificate/person/$PERSON_CERT_ID" "{\"personName\":\"L3Person$TS_SUFFIX\",\"certificateType\":\"L3TEST\",\"certificateNo\":\"L3PC$TS_SUFFIX\",\"status\":0}"
  assert_http 2 "人员证书-更新 HTTP"
  assert_body_code 200 "人员证书-更新业务码"

  call DELETE "/api/v1/tender/certificate/person/$PERSON_CERT_ID"
  assert_http 2 "人员证书-删除 HTTP"
  assert_body_code 200 "人员证书-删除业务码"

  # 负向：删除后再编辑必须拒绝
  call PUT "/api/v1/tender/certificate/person/$PERSON_CERT_ID" "{\"personName\":\"L3Person$TS_SUFFIX\",\"certificateNo\":\"L3PC$TS_SUFFIX\"}"
  assert_body_not_success "人员证书-删除后编辑被拒绝"
fi

# ---------- 企业证书 CRUD 闭环（按 certificateName 查回） ----------
call POST "/api/v1/tender/certificate/company" "{\"certificateName\":\"L3Company$TS_SUFFIX\",\"certificateType\":\"L3TEST\",\"certificateNo\":\"L3CC$TS_SUFFIX\",\"status\":1}"
assert_http 2 "企业证书-创建 HTTP"
assert_body_code 200 "企业证书-创建业务码"

call GET "/api/v1/tender/certificate/company?page=1&size=20&certificateName=L3Company$TS_SUFFIX"
COMPANY_CERT_ID=$(jq -r --arg no "L3CC$TS_SUFFIX" '.data.records[] | select(.certificateNo==$no) | .id' /tmp/zwi_body 2>/dev/null | head -1)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$COMPANY_CERT_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 企业证书-查回ID: $COMPANY_CERT_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 企业证书-创建后查回失败"
fi

if [ -n "$COMPANY_CERT_ID" ]; then
  call PUT "/api/v1/tender/certificate/company/$COMPANY_CERT_ID" "{\"certificateName\":\"L3CompanyU$TS_SUFFIX\",\"certificateType\":\"L3TEST\",\"certificateNo\":\"L3CC$TS_SUFFIX\",\"status\":0}"
  assert_http 2 "企业证书-更新 HTTP"
  assert_body_code 200 "企业证书-更新业务码"

  call DELETE "/api/v1/tender/certificate/company/$COMPANY_CERT_ID"
  assert_http 2 "企业证书-删除 HTTP"
  assert_body_code 200 "企业证书-删除业务码"
fi

report_summary
