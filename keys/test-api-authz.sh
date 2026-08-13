#!/usr/bin/env bash
###############################################################################
# test-api-authz.sh — L3 API 安全边界测试：认证与越权负向断言（2026-08-13 新增）
#
# 覆盖安全边界（测试体系评估遗留项：越权负向测试空白）：
#   A1. 无 token 访问受保护接口 → 401（AuthInterceptor）
#   A2. 无效 token 访问 → 401（validateToken 拒绝）
#   A3. 低权限用户（t9999user，无权限点）访问未标注接口 → 200（opt-in 放行，
#       证明拦截不是一刀切）
#   A4. 低权限用户调用 @RequiresPermission("project:delete") 接口 → 403
#   A5. 低权限用户调用 @RequiresPermission("system:user:delete") 接口 → 403
#   A6. 对照：t9999admin（SUPER_ADMIN 豁免）调用同一接口 → 非 403
#       （业务错误如"用户不存在"允许，唯独 403 不允许）
#
# 前置：init-test-tenant.sh 已创建 t9999user/123456（T9999_LIMITED 角色，
#       无任何权限点，data_scope=SELF）。CI 的 Init L4 test tenant 步骤已含。
#
# 运行位置：服务器 129.204.3.200（依赖 docker exec zwi-redis 取验证码）
# 全程真实登录/真实拦截，禁止伪造 token。
###############################################################################
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE="${ZWI_BASE:-http://127.0.0.1:18080}"
WORKDIR="${ZWI_WORKDIR:-/root/zwi-deploy}"
TOKEN_FILE="$WORKDIR/.zwi_token"

PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0

log() { echo "[$(date +%H:%M:%S)] $*"; }

assert_code() {
  local expected="$1" test_name="$2"
  local actual
  actual=$(cat /tmp/zwi_last_code 2>/dev/null || echo "000")
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [ "$actual" = "$expected" ]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] $test_name (HTTP $actual)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] $test_name (HTTP $actual, 期望 $expected)"
  fi
}

# assert_not_403 <test_name>：对照断言——超管豁免，业务错误允许但 403 不允许
assert_not_403() {
  local test_name="$1"
  local actual
  actual=$(cat /tmp/zwi_last_code 2>/dev/null || echo "000")
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
  if [ "$actual" != "403" ]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    log "  PASS [$TOTAL_COUNT] $test_name (HTTP $actual, 非 403)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log "  FAIL [$TOTAL_COUNT] $test_name (HTTP 403, SUPER_ADMIN 应豁免)"
  fi
}

# raw_call <METHOD> <PATH> [TOKEN]：不带自动登录的直接调用（401 场景专用）
raw_call() {
  local method="$1" path="$2" token="${3:-}"
  if [ -n "$token" ]; then
    curl -s -m 15 -o /tmp/zwi_body -w '%{http_code}' -X "$method" "$BASE$path" \
      -H "Authorization: Bearer $token" > /tmp/zwi_last_code
  else
    curl -s -m 15 -o /tmp/zwi_body -w '%{http_code}' -X "$method" "$BASE$path" > /tmp/zwi_last_code
  fi
  log "  $method $path -> HTTP $(cat /tmp/zwi_last_code)"
}

# login_as <username> <password>：切换用户真实登录（先清旧 token，防复用他人会话）
#   单次尝试不重试：本脚本的登录失败会计入共享 IP 失败计数（与其他脚本同源 127.0.0.1），
#   重试会加速触发 IP 锁定连锁拖垮后续脚本；verify-base.sh 已在每次登录前清锁。
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

# authed_call <METHOD> <PATH>：用当前缓存 token 调用
authed_call() {
  local method="$1" path="$2" token
  token=$(cat "$TOKEN_FILE" 2>/dev/null || echo "")
  if [ -z "$token" ]; then
    log "  无 token，跳过调用" >&2
    echo "000" > /tmp/zwi_last_code
    return 1
  fi
  curl -s -m 15 -o /tmp/zwi_body -w '%{http_code}' -X "$method" "$BASE$path" \
    -H "Authorization: Bearer $token" > /tmp/zwi_last_code
  log "  $method $path -> HTTP $(cat /tmp/zwi_last_code)"
}

log "========== L3 安全边界测试开始 =========="

# ---------- A1/A2：未认证访问 ----------
log "▶ A1 无 token 访问受保护接口"
raw_call GET "/api/v1/project/page?page=1&size=1"
assert_code 401 "无 token 访问 /api/v1/project/page"

log "▶ A2 无效 token 访问"
raw_call GET "/api/v1/project/page?page=1&size=1" "invalid.token.forged-value"
assert_code 401 "无效 token 访问 /api/v1/project/page"

# ---------- A3~A5：低权限用户（t9999user） ----------
log "▶ 切换低权限用户 t9999user"
if ! login_as "t9999user" "123456"; then
  log "低权限用户登录失败，终止（种子缺失？先跑 init-test-tenant.sh）" >&2
  echo "通过: $PASS_COUNT / 失败: $((FAIL_COUNT + 1))"
  exit 1
fi

log "▶ A3 低权限访问未标注接口（opt-in 放行）"
authed_call GET "/api/v1/project/page?page=1&size=1"
assert_code 200 "低权限访问未标注接口 /api/v1/project/page"

log "▶ A4 低权限调用 project:delete 接口"
authed_call DELETE "/api/v1/project/999999999999"
assert_code 403 "低权限 DELETE /api/v1/project（需 project:delete）"

log "▶ A5 低权限调用 system:user:delete 接口"
authed_call DELETE "/api/v1/system/user/999999999999"
assert_code 403 "低权限 DELETE /api/v1/system/user（需 system:user:delete）"

# ---------- A6：超管对照（SUPER_ADMIN 豁免） ----------
log "▶ 切换 t9999admin（SUPER_ADMIN 对照）"
if ! login_as "t9999admin" "123456"; then
  log "t9999admin 登录失败，跳过对照" >&2
else
  log "▶ A6 超管调用同一接口（豁免对照）"
  authed_call DELETE "/api/v1/system/user/999999999999"
  assert_not_403 "超管 DELETE /api/v1/system/user（SUPER_ADMIN 豁免，业务错误允许）"
fi

echo ""
log "========== L3 安全边界测试汇总: 通过=$PASS_COUNT 失败=$FAIL_COUNT =========="
[ "$FAIL_COUNT" -eq 0 ]
