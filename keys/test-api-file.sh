#!/usr/bin/env bash
###############################################################################
# test-api-file.sh — L3 API 接口测试：文件模块（阶段四批 5）
#
# 覆盖端点（依据 zw-file 7 个 Controller 逐行核对）：
#   - /api/v1/file/upload + /list + DELETE   文件上传/列表/删除真实闭环（MinIO）
#   - /api/v1/file/preview-url               预览 URL（不存在文件负向）
#   - /api/v1/file/storage                   存储配置：分页契约 + 创建/更新/删除闭环
#   - /api/v1/file/serial                    编号规则：列表契约 + CRUD 闭环 + 生成编号
#   - /api/v1/file/template                  模板：列表契约 + 创建(返回ID)/渲染/更新/删除闭环
#   - /api/v1/print-template                 打印模板：分页契约 + 创建(返回ID)/详情/更新/删除闭环
#   - /api/v1/batch/template/{module} + export 批量导入模板下载 + 异步导出任务状态
#   - /api/v1/export-schedule                定时导出：分页/模块清单契约 + CRUD 闭环
#
# 设计要点：
#   - 上传/导出产生 MinIO 对象与 Redis 任务键（24h TTL），无 DB 残留；DB 侧全部闭环删除
#   - 标识字段全部 ASCII（L3 前缀 + 时间戳后缀）
#   - 依赖 verify-base.sh 登录基座；jq 断言规范同阶段四批 1-4
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
  log "文件模块 API 测试汇总"
  echo "═══════════════════════════════════════════════════════════"
  log "  通过: $PASS_COUNT"
  log "  失败: $FAIL_COUNT"
  log "  总计: $TOTAL_COUNT"
  echo "═══════════════════════════════════════════════════════════"
  [ "$FAIL_COUNT" -eq 0 ]
}

log "========== L3 文件模块 API 测试开始 =========="

# ---------- 只读契约 ----------
call GET "/api/v1/file/storage?page=1&size=5"
assert_http 2 "存储配置-分页 HTTP"
assert_jq "$PAGE_EXPR" "存储配置-分页结构"

call GET "/api/v1/file/serial"
assert_http 2 "编号规则-列表 HTTP"
assert_jq '.code==200 and (.data|type=="array")' "编号规则-列表为数组"

call GET "/api/v1/export-schedule/page?page=1&size=5"
assert_http 2 "定时导出-分页 HTTP"
assert_jq "$PAGE_EXPR" "定时导出-分页结构"

call GET "/api/v1/export-schedule/modules"
assert_http 2 "定时导出-模块清单 HTTP"
assert_jq '.code==200 and (.data|type=="array") and ((.data|length)>=10)' "定时导出-模块清单至少10个"

call GET "/api/v1/print-template/list?page=1&size=5"
assert_http 2 "打印模板-分页 HTTP"
assert_jq "$PAGE_EXPR" "打印模板-分页结构"

call GET "/api/v1/file/template"
assert_http 2 "模板-列表 HTTP"
assert_jq '.code==200 and (.data|type=="array")' "模板-列表为数组"

# 批量导入模板下载（EasyExcel 生成空模板，二进制流 HTTP 200）
DL_TOKEN=$(get_token)
DL_CODE=$(curl -s -m 20 -o /tmp/zwi_dl_template -w '%{http_code}' \
  -H "Authorization: Bearer $DL_TOKEN" "$BASE/api/v1/batch/template/MATERIAL")
echo "$DL_CODE" > /tmp/zwi_last_code
assert_http 2 "批量导入模板-下载 HTTP"
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ "$(wc -c < /tmp/zwi_dl_template)" -gt 1000 ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 批量导入模板-文件非空"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 批量导入模板-文件为空或过小"
fi

# 预览 URL 负向：文件不存在
call GET "/api/v1/file/preview-url?fileId=999999999"
assert_body_not_success "预览URL-不存在文件被拒绝"

# ---------- 文件上传真实闭环（MinIO） ----------
echo "L3 upload test $TS_SUFFIX" > /tmp/zwi_l3_upload.txt
UP_TOKEN=$(get_token)
UP_CODE=$(curl -s -m 30 -o /tmp/zwi_body -w '%{http_code}' \
  -H "Authorization: Bearer $UP_TOKEN" \
  -F "file=@/tmp/zwi_l3_upload.txt" \
  -F "businessType=L3TEST$TS_SUFFIX" \
  -F "businessId=1" \
  "$BASE/api/v1/file/upload")
echo "$UP_CODE" > /tmp/zwi_last_code
log "  POST /api/v1/file/upload (multipart) -> HTTP $UP_CODE"
assert_http 2 "文件上传 HTTP"
assert_body_code 200 "文件上传业务码"
FILE_ID=$(jq -r '.data.id // empty' /tmp/zwi_body 2>/dev/null)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$FILE_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 文件上传-返回ID: $FILE_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 文件上传-未返回ID"
fi

if [ -n "$FILE_ID" ]; then
  call GET "/api/v1/file/list?businessType=L3TEST$TS_SUFFIX&businessId=1"
  assert_http 2 "文件列表 HTTP"
  assert_jq '.code==200 and (.data|type=="array") and ((.data|length)>=1)' "文件列表-含上传文件"

  call DELETE "/api/v1/file/$FILE_ID"
  assert_http 2 "文件删除 HTTP"
  assert_body_code 200 "文件删除业务码"

  call GET "/api/v1/file/list?businessType=L3TEST$TS_SUFFIX&businessId=1"
  assert_jq '.code==200 and (.data|type=="array") and ((.data|length)==0)' "文件列表-删除后为空"
fi

# ---------- 存储配置闭环（首条倒序即新建记录） ----------
call POST "/api/v1/file/storage" "{\"storageType\":\"L3MINIO$TS_SUFFIX\",\"endpoint\":\"http://l3.example:9000\",\"accessKey\":\"l3ak\",\"secretKey\":\"l3sk\",\"bucket\":\"l3bucket\",\"status\":1}"
assert_http 2 "存储配置-创建 HTTP"
assert_body_code 200 "存储配置-创建业务码"

call GET "/api/v1/file/storage?page=1&size=1"
STORAGE_ID=$(jq -r --arg st "L3MINIO$TS_SUFFIX" '.data.records[] | select(.storageType==$st) | .id' /tmp/zwi_body 2>/dev/null | head -1)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$STORAGE_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 存储配置-查回ID: $STORAGE_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 存储配置-创建后查回失败"
fi

if [ -n "$STORAGE_ID" ]; then
  call PUT "/api/v1/file/storage/$STORAGE_ID" "{\"storageType\":\"L3MINIO$TS_SUFFIX\",\"endpoint\":\"http://l3u.example:9000\",\"accessKey\":\"l3ak\",\"secretKey\":\"l3sk\",\"bucket\":\"l3bucket-u\",\"status\":1}"
  assert_http 2 "存储配置-更新 HTTP"
  assert_body_code 200 "存储配置-更新业务码"

  call DELETE "/api/v1/file/storage/$STORAGE_ID"
  assert_http 2 "存储配置-删除 HTTP"
  assert_body_code 200 "存储配置-删除业务码"

  call GET "/api/v1/file/storage/$STORAGE_ID"
  assert_body_not_success "存储配置-删除后详情被拒绝"
fi

# ---------- 编号规则闭环 + 生成编号 ----------
call POST "/api/v1/file/serial" "{\"businessType\":\"L3BT$TS_SUFFIX\",\"rulePrefix\":\"L3P\",\"dateFormat\":\"yyyyMMdd\",\"seqLength\":4}"
assert_http 2 "编号规则-创建 HTTP"
assert_body_code 200 "编号规则-创建业务码"

call GET "/api/v1/file/serial"
SERIAL_ID=$(jq -r --arg bt "L3BT$TS_SUFFIX" '.data[] | select(.businessType==$bt) | .id' /tmp/zwi_body 2>/dev/null | head -1)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$SERIAL_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 编号规则-查回ID: $SERIAL_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 编号规则-创建后查回失败"
fi

if [ -n "$SERIAL_ID" ]; then
  call POST "/api/v1/file/serial/generate/L3BT$TS_SUFFIX"
  assert_http 2 "编号生成 HTTP"
  assert_jq '.code==200 and (.data|type=="string") and (.data|startswith("L3P"))' "编号生成-前缀+序号格式"

  call PUT "/api/v1/file/serial/$SERIAL_ID" "{\"businessType\":\"L3BT$TS_SUFFIX\",\"rulePrefix\":\"L3PU\",\"dateFormat\":\"yyyyMMdd\",\"seqLength\":5}"
  assert_http 2 "编号规则-更新 HTTP"
  assert_body_code 200 "编号规则-更新业务码"

  call DELETE "/api/v1/file/serial/$SERIAL_ID"
  assert_http 2 "编号规则-删除 HTTP"
  assert_body_code 200 "编号规则-删除业务码"
fi

# 负向：未配置的编号规则生成被拒绝
call POST "/api/v1/file/serial/generate/L3NOEXIST$TS_SUFFIX"
assert_body_not_success "编号生成-未配置规则被拒绝"

# ---------- 模板（file/template）闭环：创建返回ID → 渲染 → 更新 → 删除 ----------
call POST "/api/v1/file/template" "{\"templateName\":\"L3Tpl$TS_SUFFIX\",\"moduleCode\":\"L3MOD\",\"templateType\":\"EXPORT\",\"templateContent\":\"hello {{name}}\"}"
assert_http 2 "模板-创建 HTTP"
assert_body_code 200 "模板-创建业务码"
TPL_ID=$(jq -r '.data.id // empty' /tmp/zwi_body 2>/dev/null)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$TPL_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 模板-创建返回ID: $TPL_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 模板-创建未返回ID"
fi

if [ -n "$TPL_ID" ]; then
  call POST "/api/v1/file/template/$TPL_ID/render" "{\"name\":\"L3World\"}"
  assert_http 2 "模板-渲染 HTTP"
  assert_jq '.code==200 and (.data=="hello L3World")' "模板-占位符渲染结果"

  call PUT "/api/v1/file/template/$TPL_ID" "{\"templateName\":\"L3TplU$TS_SUFFIX\",\"moduleCode\":\"L3MOD\",\"templateType\":\"EXPORT\",\"templateContent\":\"hi {{name}}\",\"isDefault\":0}"
  assert_http 2 "模板-更新 HTTP"
  assert_body_code 200 "模板-更新业务码"

  call DELETE "/api/v1/file/template/$TPL_ID"
  assert_http 2 "模板-删除 HTTP"
  assert_body_code 200 "模板-删除业务码"

  call POST "/api/v1/file/template/$TPL_ID/render" "{}"
  assert_body_not_success "模板-删除后渲染被拒绝"
fi

# ---------- 打印模板闭环：创建返回ID → 详情 → 更新 → 删除 ----------
call POST "/api/v1/print-template" "{\"templateName\":\"L3PT$TS_SUFFIX\",\"moduleCode\":\"L3MOD\",\"businessType\":\"L3BIZ\",\"templateContent\":\"<p>print</p>\"}"
assert_http 2 "打印模板-创建 HTTP"
assert_body_code 200 "打印模板-创建业务码"
PTPL_ID=$(jq -r '.data.id // empty' /tmp/zwi_body 2>/dev/null)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$PTPL_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 打印模板-创建返回ID: $PTPL_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 打印模板-创建未返回ID"
fi

if [ -n "$PTPL_ID" ]; then
  call GET "/api/v1/print-template/$PTPL_ID"
  assert_http 2 "打印模板-详情 HTTP"
  assert_jq '.code==200 and (.data.templateName!=null)' "打印模板-详情含名称"

  call PUT "/api/v1/print-template/$PTPL_ID" "{\"templateName\":\"L3PTU$TS_SUFFIX\",\"moduleCode\":\"L3MOD\",\"businessType\":\"L3BIZ\",\"templateContent\":\"<p>print-u</p>\"}"
  assert_http 2 "打印模板-更新 HTTP"
  assert_body_code 200 "打印模板-更新业务码"

  call DELETE "/api/v1/print-template/$PTPL_ID"
  assert_http 2 "打印模板-删除 HTTP"
  assert_body_code 200 "打印模板-删除业务码"

  call GET "/api/v1/print-template/$PTPL_ID"
  assert_body_not_success "打印模板-删除后详情被拒绝"
fi

# 负向：打印模板名称为空被拒绝
call POST "/api/v1/print-template" "{\"moduleCode\":\"L3MOD\",\"businessType\":\"L3BIZ\"}"
assert_body_not_success "打印模板-空名称被拒绝"

# ---------- 异步导出任务 + 状态查询 ----------
call POST "/api/v1/batch/export" "{\"moduleCode\":\"SUPPLIER\",\"params\":{}}"
assert_http 2 "异步导出-发起 HTTP"
assert_body_code 200 "异步导出-发起业务码"
EXPORT_TASK_ID=$(jq -r '.data // empty' /tmp/zwi_body 2>/dev/null)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$EXPORT_TASK_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 异步导出-任务ID: $EXPORT_TASK_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 异步导出-未返回任务ID"
fi

if [ -n "$EXPORT_TASK_ID" ]; then
  sleep 3
  call GET "/api/v1/batch/export/$EXPORT_TASK_ID/status"
  assert_http 2 "异步导出-状态查询 HTTP"
  assert_jq '.code==200 and (.data.status|IN("PENDING","PROCESSING","COMPLETED","FAILED"))' "异步导出-状态为合法枚举"
fi

# 负向：不存在的导出任务状态查询被拒绝
call GET "/api/v1/batch/export/999999999/status"
assert_body_not_success "异步导出-不存在任务被拒绝"

# ---------- 定时导出配置闭环 ----------
call POST "/api/v1/export-schedule" "{\"scheduleName\":\"L3Sched$TS_SUFFIX\",\"moduleCode\":\"SUPPLIER\",\"cronExpression\":\"0 0 3 * * ?\",\"recipients\":\"l3@example.com\",\"exportParams\":\"{}\"}"
assert_http 2 "定时导出-创建 HTTP"
assert_body_code 200 "定时导出-创建业务码"

call GET "/api/v1/export-schedule/page?page=1&size=5"
SCHED_ID=$(jq -r --arg sn "L3Sched$TS_SUFFIX" '.data.records[] | select(.scheduleName==$sn) | .id' /tmp/zwi_body 2>/dev/null | head -1)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [ -n "$SCHED_ID" ]; then
  PASS_COUNT=$((PASS_COUNT + 1)); log "  PASS [$TOTAL_COUNT] 定时导出-查回ID: $SCHED_ID"
else
  FAIL_COUNT=$((FAIL_COUNT + 1)); log "  FAIL [$TOTAL_COUNT] 定时导出-创建后查回失败"
fi

if [ -n "$SCHED_ID" ]; then
  call PUT "/api/v1/export-schedule/$SCHED_ID" "{\"scheduleName\":\"L3Sched$TS_SUFFIX\",\"moduleCode\":\"SUPPLIER\",\"cronExpression\":\"0 0 4 * * ?\",\"recipients\":\"l3u@example.com\"}"
  assert_http 2 "定时导出-更新 HTTP"
  assert_body_code 200 "定时导出-更新业务码"

  call DELETE "/api/v1/export-schedule/$SCHED_ID"
  assert_http 2 "定时导出-删除 HTTP"
  assert_body_code 200 "定时导出-删除业务码"
fi

# 负向：无效 Cron 被拒绝
call POST "/api/v1/export-schedule" "{\"scheduleName\":\"L3Bad$TS_SUFFIX\",\"moduleCode\":\"SUPPLIER\",\"cronExpression\":\"not-a-cron\"}"
assert_body_not_success "定时导出-无效Cron被拒绝"

report_summary
