#!/usr/bin/env bash
###############################################################################
# test-api-tenant-isolation.sh — L3 跨租户水平越权探针（2026-08-14 新增）
#
# 验证目标：租户 1（admin）与租户 9999（t9999admin）互不可见/互不可改对方数据。
# 隔离机制现状（MybatisPlusConfig ignoreTable）：biz_*/file_* 由 TenantLine 自动
# 注入 tenant_id（跨租户语义是"不可见"404/空，不是 403）；sys_*/act_* 免租户过滤
# ——本脚本对两类面分别断言与记录现状。
#
# 结构（登录顺序固定，全程真实验证码登录）：
#   Step 1 admin（租户1）：采集租户 1 各模块资源 ID（隔离有效时 9999 侧拿不到这些 ID，
#          只能由本租户提供）+ 对照自读；顺带采 XT_ISO 残留探测
#   Step 2 t9999admin（租户9999）：方向 A 用 9999 token 探针租户 1 ID（只读零写入，
#          演示数据永不承写压）+ 造数 P9 项目/合同（XT_ISO 前缀自清）
#   Step 3 admin：方向 B 对 P9 读/写攻击探针 + sys_ 缺口专项（user/role/print-template）
#   Step 4 Flowable 有界探针：全程仅 1 个流程 + 立即 withdraw-by-business 回收
#          （防 ACT_ 膨胀）；SUPER_ADMIN 跨租户办理记录现状不判 FAIL（语义已文档化）
#   Step 5 t9999admin：写攻击复查 + trap EXIT 精确清理
#
# 前置：init-test-tenant.sh 种子（CI Init L4 tenant 步骤已含）；租户 9999 已部署 BPMN。
# 运行位置：服务器 129.204.3.200（依赖 verify-base.sh 真实验证码登录）。
# 清理：trap EXIT 按本脚本记录的资源 ID 精确清理，禁止全租户 wipe；清理失败计 FAIL。
###############################################################################
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE="${ZWI_BASE:-http://127.0.0.1:18080}"
WORKDIR="${ZWI_WORKDIR:-/root/zwi-deploy}"
TOKEN_FILE="$WORKDIR/.zwi_token"
TS=$(date +%s)
MARK="XT_ISO_${TS}"

PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0
TOTAL_COUNT=0
NOTE_COUNT=0

# 方向 B 自播种资源（清理用，trap 引用）
P9_ID=""
P9_CONTRACT_ID=""
FLOW_STARTED=0
FLOW_CLEANED=0

# 租户 1 演示数据特征标记（种子数据 31_V2026_26__seed_demo_data.sql 固定项目名）
T1_PROJECT_MARK="滨江花园一期"

# 租户 1 资源 ID 采集表（Step 1 填充：module<TAB>id）
T1_IDS_FILE="/tmp/zwi_t1_ids.$$"
: > "$T1_IDS_FILE"

log() { echo "[$(date +%H:%M:%S)] $*"; }

# ---------------- 基础调用（复用 test-api-authz.sh 模式） ----------------

# login_as <username> <password>：切换用户真实登录（先清旧 token，防复用他人会话）
#   单次尝试不重试：登录失败计入共享 IP 失败计数（同源 127.0.0.1），重试会加速
#   触发 IP 锁定连锁拖垮后续脚本；verify-base.sh 已在每次登录前清锁。
login_as() {
  local u="$1" p="$2"
  rm -f "$TOKEN_FILE"
  export ZWI_USER="$u" ZWI_PASS="$p"
  export ZWI_MAX_RETRY=1
  if bash "$SCRIPT_DIR/verify-base.sh" login >/dev/null 2>&1; then
    log "  登录成功: $u"
    return 0
  fi
  log "  登录失败: $u" >&2
  return 1
}

# authed_call <METHOD> <PATH> [JSON_BODY]：用当前缓存 token 调用
authed_call() {
  local method="$1" path="$2" body="${3:-}" token
  token=$(cat "$TOKEN_FILE" 2>/dev/null || echo "")
  if [ -z "$token" ]; then
    log "  无 token，跳过调用" >&2
    echo "000" > /tmp/zwi_last_code
    echo -n "" > /tmp/zwi_body
    return 1
  fi
  if [ -n "$body" ]; then
    curl -s -m 15 -o /tmp/zwi_body -w '%{http_code}' -X "$method" "$BASE$path" \
      -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "$body" > /tmp/zwi_last_code
  else
    curl -s -m 15 -o /tmp/zwi_body -w '%{http_code}' -X "$method" "$BASE$path" \
      -H "Authorization: Bearer $token" > /tmp/zwi_last_code
  fi
  log "  $method $path -> HTTP $(cat /tmp/zwi_last_code)"
}

last_code() { cat /tmp/zwi_last_code 2>/dev/null || echo "000"; }

body_has() { grep -q "$1" /tmp/zwi_body 2>/dev/null; }

extract_first_id() { grep -oE '"id":"?[0-9]+' /tmp/zwi_body 2>/dev/null | head -1 | grep -oE '[0-9]+'; }

# extract_id_of <name_marker>：从列表响应中提取含指定名称的记录 id
#   （记录可能含嵌套对象，grep 截块不可靠，用 jq——服务器已预装，L3 脚本标配）
extract_id_of() {
  jq -r --arg n "$1" '.data.records[]? | select(tostring | contains($n)) | .id' /tmp/zwi_body 2>/dev/null | head -1
}

# ---------------- 断言基座 ----------------

# assert_no_leak <name> <victim_marker>：跨租户不可见语义——
#   状态码 ∈ {200,403,404} 且响应体不含受害标记串（不锁死状态码，避免误报）
assert_no_leak() {
  local name="$1" marker="$2" code
  code=$(last_code)
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if { [ "$code" = "200" ] || [ "$code" = "403" ] || [ "$code" = "404" ]; } && ! body_has "$marker"; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] $name (HTTP $code, 无泄漏)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    if body_has "$marker"; then
      log "  FAIL [$TOTAL_COUNT] $name (HTTP $code, 响应含受害标记 [$marker] → 跨租户泄漏)"
    else
      log "  FAIL [$TOTAL_COUNT] $name (HTTP $code, 非预期状态码)"
    fi
  fi
}

# assert_own_tenant_ok <name>：对照断言——同 token 读本租户资源必须成功，
#   排除"端点本身坏了"的假阴性
assert_own_tenant_ok() {
  local name="$1" code
  code=$(last_code)
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [ "$code" = "200" ]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] $name (HTTP 200, 本租户可读)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] $name (HTTP $code, 本租户资源都读不到→端点故障,探针无效)"
  fi
}

# skip_probe <name> <reason>：前置不满足（如演示数据缺失）记 SKIP 不计 FAIL
skip_probe() {
  local name="$1" reason="$2"
  SKIP_COUNT=$((SKIP_COUNT + 1))
  log "  SKIP $name ($reason)"
}

# note_behavior <name>：记录现状不判 PASS/FAIL（决策项专用，如 SUPER_ADMIN 语义）
note_behavior() {
  local name="$1" code
  code=$(last_code)
  NOTE_COUNT=$((NOTE_COUNT + 1))
  log "  NOTE [$name] HTTP $code body_head=$(head -c 160 /tmp/zwi_body 2>/dev/null | tr '\n' ' ')"
}

# ---------------- 清理（trap EXIT，铁律：按记录 ID 精确清理，禁全租户 wipe） ----------------
cleanup() {
  local rc=$?
  rm -f "$T1_IDS_FILE"
  log "--- 清理（按本脚本创建的资源 ID 精确删除）---"
  if [ -n "$P9_ID" ]; then
    # 流程兜底回收：withdraw 幂等，重复调用无害；任务已被跨租户办理时静默略过
    if [ "$FLOW_STARTED" -eq 1 ] && [ "$FLOW_CLEANED" -eq 0 ]; then
      login_as "t9999admin" "123456" >/dev/null 2>&1 || true
      authed_call POST "/api/v1/workflow/approval/withdraw-by-business?businessType=CONSTRUCTION_CONTRACT&businessId=$P9_CONTRACT_ID" >/dev/null 2>&1 || true
      log "  兜底 withdraw 已尝试 (contract=$P9_CONTRACT_ID)"
    fi
    # 确保以 t9999admin 身份清理（trap 触发时 token 归属不确定，无条件重登录，
    # 成功登录不计 IP 失败计数，无锁定风险）
    login_as "t9999admin" "123456" >/dev/null 2>&1 || true
    if [ -n "$P9_CONTRACT_ID" ]; then
      authed_call DELETE "/api/v1/contract/$P9_CONTRACT_ID" >/dev/null 2>&1 || true
    fi
    authed_call DELETE "/api/v1/project/$P9_ID" >/dev/null 2>&1 || true
    # 复查残留：清理失败计 FAIL 不静默（Flowable 膨胀事故教训）
    authed_call GET "/api/v1/project/$P9_ID" >/dev/null 2>&1 || true
    if body_has "$MARK"; then
      FAIL_COUNT=$((FAIL_COUNT + 1))
      log "  FAIL 清理未完成：项目 $P9_ID 仍存在（$MARK 残留）"
    else
      log "  清理完成：$MARK 资源已删除"
    fi
  fi
  log "========== 跨租户隔离探针汇总: 通过=$PASS_COUNT 失败=$FAIL_COUNT 跳过=$SKIP_COUNT 现状记录=$NOTE_COUNT =========="
  [ "$FAIL_COUNT" -eq 0 ] && [ "$rc" -eq 0 ]
}
trap cleanup EXIT

log "========== L3 跨租户水平越权探针开始 (标记: $MARK) =========="

# =====================================================================
# Step 1：admin（租户 1）采集本租户资源 ID + 对照自读
#   （隔离有效时 9999 侧拿不到这些 ID，只能由本租户提供给攻击方使用）
# =====================================================================
if ! login_as "admin" "123456"; then
  log "admin 登录失败，终止（演示租户不可用？）" >&2
  exit 1
fi

log "▶ Step 1：admin 采集租户 1 资源 ID（供方向 A 探针）"
# 表驱动：module|page端点|详情路径模板（{id} 占位）
PROBES=(
  "project|/api/v1/project/page?page=1&size=1|/api/v1/project/{id}"
  "contract|/api/v1/contract/page?page=1&size=1|/api/v1/contract/{id}"
  "budget|/api/v1/budget/page?page=1&size=1|/api/v1/budget/{id}"
  "payment-apply|/api/v1/finance/payment-apply/page?page=1&size=1|/api/v1/finance/payment-apply/{id}"
  "purchase-contract|/api/v1/purchase/contract/page?page=1&size=1|/api/v1/purchase/contract/{id}"
  "labor-contract|/api/v1/labor/contract/page?page=1&size=1|/api/v1/labor/contract/{id}"
  "machine-settlement|/api/v1/machine/settlement/page?page=1&size=1|/api/v1/machine/settlement/{id}"
  "material-inbound|/api/v1/material/inbound/page?page=1&size=1|/api/v1/material/inbound/{id}"
)
for probe in "${PROBES[@]}"; do
  module="${probe%%|*}"; rest="${probe#*|}"
  page_path="${rest%%|*}"; detail_tpl="${rest#*|}"
  authed_call GET "$page_path" >/dev/null
  VID=$(extract_first_id)
  if [ -z "$VID" ]; then
    skip_probe "collect-$module" "租户 1 无数据"
    continue
  fi
  printf '%s\t%s\t%s\n' "$module" "$VID" "$detail_tpl" >> "$T1_IDS_FILE"
  # 对照自读：admin 必须读得到（排除采集到脏 ID）
  authed_call GET "${detail_tpl/\{id\}/$VID}" >/dev/null
  assert_own_tenant_ok "对照 admin 自读 $module id=$VID"
done

# =====================================================================
# Step 2：t9999admin（租户 9999）——方向 A 探针（只读）+ 造数
# =====================================================================
if ! login_as "t9999admin" "123456"; then
  log "t9999admin 登录失败，终止（种子缺失？先跑 init-test-tenant.sh）" >&2
  exit 1
fi

log "▶ 方向 A：t9999admin 探针租户 1 资源（只读零写入）"
while IFS=$'\t' read -r module VID detail_tpl; do
  [ -z "$module" ] && continue
  authed_call GET "${detail_tpl/\{id\}/$VID}" >/dev/null
  assert_no_leak "A-$module 跨租户读租户1 id=$VID" "$VID"
done < "$T1_IDS_FILE"

log "▶ A-list：t9999admin 的项目列表不得夹带租户 1 记录"
authed_call GET "/api/v1/project/page?page=1&size=50" >/dev/null
assert_no_leak "A-list 项目列表无租户1记录" "$T1_PROJECT_MARK"

log "▶ 造数：t9999admin 创建临时项目 P9 ($MARK)"
authed_call POST "/api/v1/project" "{\"projectName\":\"${MARK}_项目\",\"projectType\":\"BUILDING\",\"projectAddress\":\"跨租户探针\",\"needTender\":0}" >/dev/null
authed_call GET "/api/v1/project/page?page=1&size=5&projectName=$MARK" >/dev/null
P9_ID=$(extract_first_id)
if [ -z "$P9_ID" ]; then
  log "P9 创建失败，方向 B 与 Flowable 探针跳过（清理无事可做）" >&2
  P9_ID=""
else
  log "  P9_ID=$P9_ID"
  # 对照断言：本租户自己必须读得到（排除端点故障假阴性）
  authed_call GET "/api/v1/project/$P9_ID" >/dev/null
  assert_own_tenant_ok "B-对照 t9999admin 读本租户 P9"

  # 造合同（Flowable 探针载体；实体无 contractName 字段，用 partyAName 定位）
  authed_call POST "/api/v1/contract" "{\"projectId\":$P9_ID,\"contractType\":\"CONSTRUCTION\",\"contractName\":\"${MARK}_合同\",\"partyAName\":\"探针甲方\",\"contractAmount\":100000,\"signingDate\":\"2026-01-01\"}" >/dev/null
  authed_call GET "/api/v1/contract/page?page=1&size=50" >/dev/null
  P9_CONTRACT_ID=$(jq -r '.data.records[]? | select(.partyAName == "探针甲方") | .id' /tmp/zwi_body 2>/dev/null | head -1)
  [ "$P9_CONTRACT_ID" = "null" ] && P9_CONTRACT_ID=""
  log "  P9_CONTRACT_ID=${P9_CONTRACT_ID:-<空>}"
fi

# =====================================================================
# Step 3：admin（租户 1）——方向 B 攻击探针 + sys_ 缺口专项
# =====================================================================
if ! login_as "admin" "123456"; then
  log "admin 登录失败，终止" >&2
  exit 1
fi

if [ -n "$P9_ID" ]; then
  log "▶ 方向 B：admin（租户1）攻击租户 9999 临时资源"
  authed_call GET "/api/v1/project/$P9_ID" >/dev/null
  assert_no_leak "B-读 admin GET 租户9999项目" "$MARK"

  authed_call PUT "/api/v1/project/$P9_ID" "{\"projectName\":\"${MARK}_被篡改\"}" >/dev/null
  assert_no_leak "B-写 admin PUT 租户9999项目" "$MARK"

  authed_call DELETE "/api/v1/project/$P9_ID" >/dev/null
  assert_no_leak "B-删 admin DELETE 租户9999项目" "$MARK"
fi

log "▶ sys_ 缺口专项：admin 视角不得见租户 9999 用户/角色/模板"
authed_call GET "/api/v1/system/user?page=1&size=100" >/dev/null
assert_no_leak "sys-user admin 列表无 t9999admin" "t9999admin"
authed_call GET "/api/v1/system/user?page=1&size=100&username=t9999" >/dev/null
assert_no_leak "sys-user admin 按名搜索 t9999*" "t9999admin"
authed_call GET "/api/v1/system/role?page=1&size=100" >/dev/null
assert_no_leak "sys-role admin 列表无 T9999 角色" "T9999"
authed_call GET "/api/v1/print-template/list" >/dev/null
assert_no_leak "sys_template admin 列表无 T9999 模板" "T9999"

# =====================================================================
# Step 4：Flowable 有界探针：1 个流程 + 记录 SUPER_ADMIN 跨租户办理现状
# =====================================================================
if [ -n "$P9_CONTRACT_ID" ]; then
  log "▶ Flowable 探针：t9999admin 提交合同审批 → admin 跨租户尝试办理"
  if login_as "t9999admin" "123456"; then
    authed_call POST "/api/v1/contract/$P9_CONTRACT_ID/submit" >/dev/null
    SUBMIT_CODE=$(last_code)
    sleep 1
    authed_call GET "/api/v1/workflow/approval/todo?page=1&size=20" >/dev/null
    TASK_ID=$(jq -r --arg b "$P9_CONTRACT_ID" '.data.records[]? | select((.businessId|tostring) == $b) | .taskId' /tmp/zwi_body 2>/dev/null | head -1)
    [ "$TASK_ID" = "null" ] && TASK_ID=""
    if [ "$SUBMIT_CODE" = "200" ] && [ -n "$TASK_ID" ]; then
      FLOW_STARTED=1
      log "  流程已发起 taskId=$TASK_ID"
      # 切 admin（租户1，SUPER_ADMIN）尝试跨租户办理——记录现状不判 FAIL
      if login_as "admin" "123456"; then
        authed_call POST "/api/v1/workflow/approval/complete" "{\"taskId\":\"$TASK_ID\",\"comment\":\"跨租户探针\"}" >/dev/null
        C_CODE=$(last_code)
        if [ "$C_CODE" = "200" ] && ! body_has '"code":4' && ! body_has '"code":5'; then
          log "  ⚠ 现状记录：SUPER_ADMIN 跨租户办理任务成功（无租户边界）→ 登记决策项，本探针不判 FAIL"
          NOTE_COUNT=$((NOTE_COUNT + 1))
          FLOW_CLEANED=1   # 任务已被办理，流程推进，无需 withdraw
        else
          note_behavior "SUPER_ADMIN 跨租户办理被拒/失败（已隔离或业务错误）"
        fi
      fi
      # 切回 t9999admin 立即回收（O(1) withdraw，防 ACT_ 膨胀）
      if [ "$FLOW_CLEANED" -eq 0 ]; then
        if login_as "t9999admin" "123456"; then
          authed_call POST "/api/v1/workflow/approval/withdraw-by-business?businessType=CONSTRUCTION_CONTRACT&businessId=$P9_CONTRACT_ID" >/dev/null
          if [ "$(last_code)" = "200" ]; then
            FLOW_CLEANED=1
            log "  流程已 withdraw 回收"
          else
            log "  ⚠ withdraw 返回 HTTP $(last_code)，清理段兜底重试" >&2
          fi
        fi
      fi
    else
      skip_probe "flowable" "提交未成功(HTTP $SUBMIT_CODE)或待办无任务（BPMN 未部署/assignee 非发起人）"
    fi
  fi
fi

# =====================================================================
# Step 5：方向 B 复查：写攻击不得生效（换回 t9999admin 验证数据完好）
# =====================================================================
if [ -n "$P9_ID" ]; then
  if login_as "t9999admin" "123456"; then
    log "▶ 复查：写攻击后租户 9999 数据完好性"
    authed_call GET "/api/v1/project/$P9_ID" >/dev/null
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    if [ "$(last_code)" = "200" ] && body_has "$MARK" && ! body_has "${MARK}_被篡改"; then
      PASS_COUNT=$((PASS_COUNT + 1))
      log "  PASS [$TOTAL_COUNT] B-复查 写攻击未生效，P9 数据完好"
    else
      FAIL_COUNT=$((FAIL_COUNT + 1))
      log "  FAIL [$TOTAL_COUNT] B-复查 P9 被跨租户修改或删除！"
    fi
  fi
fi

exit 0
