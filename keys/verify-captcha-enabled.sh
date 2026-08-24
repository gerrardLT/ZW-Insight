#!/usr/bin/env bash
###############################################################################
# verify-captcha-enabled.sh  —  生产验证码开启（事项 4-A）灰度预检脚本
#
# 运行位置：部署服务器 129.204.3.200（需要 docker exec zwi-redis，与 verify-base.sh 同基座）
# 由本地 scp 上传后经 SSH 调用（同 keys/verify.ps1 模式）。
#
# 判据（开启状态下的实证验证，全部真实接口，禁止 mock）：
#   判据 1  正确验证码登录成功：/api/v1/captcha/image 取 uuid + Redis captcha:<uuid> 真码登录
#   判据 2  错误验证码被拒绝：取真码后篡改一位提交，必须被拒
#           （这是开关生效的实证判据——若开关未生效，错码也会放行，判据 2 必假）
#   判据 3  一次性消费：成功登录后 Redis captcha:<uuid> 键必须已删除；
#           同一 uuid+code 复用登录必须被拒
#
# 退出码：0=三判据全过；1=任一判据失败（回滚预案：compose 撤 AUTH_CAPTCHA_ENABLED 环境变量重部署）；
#         2=环境异常（后端/Redis 不可达）
#
# 联动：判据全过后，k6 三场景需带验证码复跑一轮
#       （cd /root/zw-insight/tests/performance && ZWI_K6_FORCE=1 bash run-k6.sh all，bridge 链路实证）
###############################################################################
set -uo pipefail

BASE="${ZWI_BASE:-http://127.0.0.1:18080}"
USERNAME="${ZWI_USER:-admin}"
PASSWORD="${ZWI_PASS:-123456}"
REDIS_CT="${ZWI_REDIS_CT:-zwi-redis}"

log() { echo "[$(date +%H:%M:%S)] $*"; }
FAIL=0

# ---------------------------------------------------------------------------
# 脱敏：验证码/token 明文不回显（与 verify-base.sh 需求 11.2 一致）
# ---------------------------------------------------------------------------
mask() {
  sed -E \
    -e 's/("(accessToken|refreshToken|token)"[[:space:]]*:[[:space:]]*")[^"]+(")/\1<REDACTED:\2>\3/gI' \
    -e 's/(captchaCode)[^,}]+/\1:<REDACTED>/g'
}

# ---------------------------------------------------------------------------
# clear_login_locks：重置登录失败计数/锁定键（与 verify-base.sh 同因：
# 预检含故意失败请求，避免累加 IP 失败计数把后续套件登录锁死）
# ---------------------------------------------------------------------------
clear_login_locks() {
  docker exec "$REDIS_CT" redis-cli DEL \
    "login:ip:fail:127.0.0.1" "login:ip:lock:127.0.0.1" >/dev/null 2>&1 || true
  docker exec "$REDIS_CT" redis-cli DEL \
    "login_fail:$USERNAME" >/dev/null 2>&1 || true
}

# ---------------------------------------------------------------------------
# get_captcha：取一个新验证码，输出 "uuid code"（真实组件，非伪造）
# ---------------------------------------------------------------------------
get_captcha() {
  local resp uuid code
  resp=$(curl -s -m 10 "$BASE/api/v1/captcha/image")
  uuid=$(echo "$resp" | grep -oE '"uuid"[[:space:]]*:[[:space:]]*"[^"]+"' | head -1 | sed -E 's/.*"uuid"[[:space:]]*:[[:space:]]*"//;s/"$//')
  if [ -z "$uuid" ]; then
    log "取验证码失败：响应未含 uuid（后端可能未开启验证码或接口异常）" >&2
    return 1
  fi
  code=$(timeout 10 docker exec "$REDIS_CT" redis-cli GET "captcha:$uuid" | tr -d '\r"')
  if [ -z "$code" ]; then
    log "Redis key=captcha:<uuid> 为空或已过期，无法取真码" >&2
    return 1
  fi
  echo "$uuid $code"
  return 0
}

# ---------------------------------------------------------------------------
# attempt_login <uuid> <code>：提交登录，成功输出 token 并返回 0；失败返回 1
# ---------------------------------------------------------------------------
attempt_login() {
  local uuid="$1" code="$2" resp token
  resp=$(curl -s -m 10 -X POST "$BASE/api/v1/auth/login" \
        -H 'Content-Type: application/json' \
        -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"captchaUuid\":\"$uuid\",\"captchaCode\":\"$code\"}")
  token=$(echo "$resp" | grep -oE '"(accessToken|token)"[[:space:]]*:[[:space:]]*"[^"]+"' | head -1 | sed -E 's/.*:[[:space:]]*"//;s/"$//')
  if [ -n "$token" ]; then
    echo "$token"
    return 0
  fi
  log "  登录未通过，响应(脱敏): $(echo "$resp" | mask | head -c 240)" >&2
  return 1
}

# 篡改验证码一位：数字按 +1 mod 10，字母替换为 X，确保必错
mangle_code() {
  local c="$1" first rest new
  first="${c:0:1}"; rest="${c:1}"
  if [[ "$first" =~ [0-9] ]]; then
    new=$(((first + 1) % 10))
  else
    new="X"
    [ "$first" = "X" ] && new="Y"
  fi
  echo "${new}${rest}"
}

echo "===== 验证码开启预检 $(date '+%F %T') base=$BASE ====="

# ---------- 0. 环境探活 ----------
if ! curl -s -m 10 -o /dev/null "$BASE/api/v1/captcha/image"; then
  log "[ENV-FAIL] 后端 $BASE 不可达" >&2
  exit 2
fi
if ! docker exec "$REDIS_CT" redis-cli PING >/dev/null 2>&1; then
  log "[ENV-FAIL] Redis 容器 $REDIS_CT 不可达" >&2
  exit 2
fi
clear_login_locks

# ---------- 判据 1：正确验证码登录成功 ----------
log "--- 判据 1：正确验证码（Redis 真码）登录 ---"
cap=$(get_captcha) || { log "[FAIL] 判据 1：无法取真码"; FAIL=1; }
if [ -n "$cap" ]; then
  uuid="${cap%% *}"; code="${cap##* }"
  if token=$(attempt_login "$uuid" "$code"); then
    log "[PASS] 判据 1：正确验证码登录成功（token 长度 ${#token}，<REDACTED>）"
  else
    log "[FAIL] 判据 1：正确验证码登录被拒（开关与环境可能异常）" >&2
    FAIL=1
  fi
fi

# ---------- 判据 2：错误验证码被拒绝（开关生效实证） ----------
clear_login_locks
log "--- 判据 2：错误验证码必须被拒 ---"
cap=$(get_captcha) || { log "[FAIL] 判据 2：无法取真码"; FAIL=1; }
if [ -n "$cap" ]; then
  uuid="${cap%% *}"; code="${cap##* }"
  wrong=$(mangle_code "$code")
  if attempt_login "$uuid" "$wrong" >/dev/null; then
    log "[FAIL] 判据 2：错误验证码竟登录成功——验证码开关未生效（假象放行）" >&2
    FAIL=1
  else
    log "[PASS] 判据 2：错误验证码被拒（开关生效实证）"
  fi
fi

# ---------- 判据 3：一次性消费（键删除 + 复用被拒） ----------
clear_login_locks
log "--- 判据 3：一次性消费（用后键删除、复用被拒） ---"
cap=$(get_captcha) || { log "[FAIL] 判据 3：无法取真码"; FAIL=1; }
if [ -n "$cap" ]; then
  uuid="${cap%% *}"; code="${cap##* }"
  if token=$(attempt_login "$uuid" "$code"); then
    remain=$(docker exec "$REDIS_CT" redis-cli GET "captcha:$uuid" | tr -d '\r')
    if [ -z "$remain" ] || [ "$remain" = "(nil)" ]; then
      log "[PASS] 判据 3a：登录后 Redis captcha:<uuid> 键已删除（一次性消费）"
    else
      log "[FAIL] 判据 3a：登录后 Redis 键仍存在（验证码未被消费）" >&2
      FAIL=1
    fi
    if attempt_login "$uuid" "$code" >/dev/null; then
      log "[FAIL] 判据 3b：同一 uuid+code 复用登录成功（可重放，严重）" >&2
      FAIL=1
    else
      log "[PASS] 判据 3b：复用被拒（不可重放）"
    fi
  else
    log "[FAIL] 判据 3：前置正确登录失败，无法验证一次性消费" >&2
    FAIL=1
  fi
fi

clear_login_locks
echo "===== 预检结论 ====="
if [ "$FAIL" -eq 0 ]; then
  log "[PASS] 三判据全过：验证码开关生效、一次性消费、不可重放"
  log "下一步：k6 三场景带验证码复跑（cd /root/zw-insight/tests/performance && ZWI_K6_FORCE=1 bash run-k6.sh all）"
  exit 0
fi
log "[FAIL] 存在未通过判据：回滚预案 = compose 撤 AUTH_CAPTCHA_ENABLED 环境变量 → push 重部署" >&2
exit 1
