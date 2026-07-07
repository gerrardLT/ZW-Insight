#!/usr/bin/env bash
###############################################################################
# verify-base.sh  —  前后端联调「真实登录 + 日志核对」可复用验证基座
#
# 运行位置：服务器 129.204.3.200（需要 docker exec zwi-redis / docker logs zwi-backend）
# 由本地通过 keys/verify.ps1 经 SSH 上传并调用。
#
# 设计依据：frontend-backend-integration spec
#   - 需求 5.1：10s 内读 Redis captcha:<uuid> 取真实验证码，admin/123456 真实登录，
#               不绕过验证码、不 mock token
#   - 需求 5.2 / 9.3：登录失败记录原因并以新验证码重试；验证码失效(过期/错误key)重取 uuid
#   - 需求 5.4 / 9.2：后端容器日志确认无 404/405、无异常堆栈
#   - 需求 11.2：日志/Redis 读取对私钥、token、验证码、DB 口令脱敏，仅保留键名引用
#
# 全程真实接口、真实数据，禁止 mock 与假数据兜底。
###############################################################################
set -uo pipefail

BASE="${ZWI_BASE:-http://127.0.0.1:18080}"
USERNAME="${ZWI_USER:-admin}"
PASSWORD="${ZWI_PASS:-123456}"
REDIS_CT="${ZWI_REDIS_CT:-zwi-redis}"
BACKEND_CT="${ZWI_BACKEND_CT:-zwi-backend}"
MAX_RETRY="${ZWI_MAX_RETRY:-3}"
WORKDIR="${ZWI_WORKDIR:-/root/zwi-deploy}"
TOKEN_FILE="$WORKDIR/.zwi_token"     # 仅服务器本地缓存，权限 600，绝不回显明文
mkdir -p "$WORKDIR"

# ---------------------------------------------------------------------------
# 脱敏：对 token / 验证码 / 口令 / 私钥的明文做遮蔽，仅保留键名引用（需求 11.2）
# ---------------------------------------------------------------------------
mask() {
  sed -E \
    -e 's/("(accessToken|refreshToken|token)"[[:space:]]*:[[:space:]]*")[^"]+(")/\1<REDACTED:\2>\3/gI' \
    -e 's/("password"[[:space:]]*:[[:space:]]*")[^"]+(")/\1<REDACTED:password>\2/gI' \
    -e 's/\b(accessToken|refreshToken|token|password|passwd|pwd)([[:space:]]*=[[:space:]]*)[^[:space:]&"]+/\1\2<REDACTED:\1>/gI' \
    -e 's/(Bearer[[:space:]]+)[A-Za-z0-9._\-]+/\1<REDACTED:token>/g' \
    -e 's/(captcha-code=)[^[:space:]]+/\1<REDACTED:captcha-code>/g' \
    -e 's/-----BEGIN[^-]*PRIVATE KEY-----/<REDACTED:private-key>/g'
}

log() { echo "[$(date +%H:%M:%S)] $*"; }

# ---------------------------------------------------------------------------
# get_captcha：取一个新的验证码，输出 "uuid code"（真实组件，非伪造）
#   返回非零表示取码失败（无 uuid 或 Redis 中无对应 code / 已失效）
# ---------------------------------------------------------------------------
get_captcha() {
  local resp uuid code
  resp=$(curl -s -m 10 "$BASE/api/v1/captcha/image")
  uuid=$(echo "$resp" | grep -oE '"uuid"[[:space:]]*:[[:space:]]*"[^"]+"' | head -1 | sed -E 's/.*"uuid"[[:space:]]*:[[:space:]]*"//;s/"$//')
  if [ -z "$uuid" ]; then
    log "取验证码失败：响应未含 uuid" >&2
    return 1
  fi
  # 10s 内读取 Redis captcha:<uuid>（需求 5.1）；值为 JSON 字符串，剥离双引号/回车
  code=$(timeout 10 docker exec "$REDIS_CT" redis-cli GET "captcha:$uuid" | tr -d '\r"')
  if [ -z "$code" ]; then
    log "验证码失效：Redis key=captcha:<uuid> 为空或已过期(120s)，需重取 uuid" >&2   # 需求 9.2/9.3
    return 1
  fi
  echo "$uuid $code"
  return 0
}

# ---------------------------------------------------------------------------
# do_login：真实登录，成功则把 token 写入缓存文件（600），返回 0
#   登录失败记录原因并由调用方以新验证码重试（需求 5.2）
# ---------------------------------------------------------------------------
do_login() {
  local uuid code cap resp token
  cap=$(get_captcha) || return 1
  uuid="${cap%% *}"; code="${cap##* }"
  resp=$(curl -s -m 10 -X POST "$BASE/api/v1/auth/login" \
        -H 'Content-Type: application/json' \
        -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"captchaUuid\":\"$uuid\",\"captchaCode\":\"$code\"}")
  token=$(echo "$resp" | grep -oE '"(accessToken|token)"[[:space:]]*:[[:space:]]*"[^"]+"' | head -1 | sed -E 's/.*:[[:space:]]*"//;s/"$//')
  if [ -z "$token" ]; then
    # 登录失败：记录脱敏后的原因（需求 5.2，不伪造 token）
    log "登录失败，响应(脱敏): $(echo "$resp" | mask | head -c 240)" >&2
    return 1
  fi
  umask 077
  printf '%s' "$token" > "$TOKEN_FILE"
  chmod 600 "$TOKEN_FILE"
  log "登录成功，已缓存 token（键名引用: <REDACTED:token>，长度 ${#token}）"
  return 0
}

# ---------------------------------------------------------------------------
# login：带重试的真实登录（每次失败用新验证码重试，需求 5.2/9.3），不伪造 token
# ---------------------------------------------------------------------------
login() {
  local i
  for ((i=1; i<=MAX_RETRY; i++)); do
    log "登录尝试 $i/$MAX_RETRY ..."
    if do_login; then
      return 0
    fi
    log "第 $i 次失败，将以新验证码重试"
  done
  log "登录在 $MAX_RETRY 次重试内仍失败，停止（不伪造 token）" >&2
  return 1
}

# token：取缓存 token（内部用，不打印到面向用户日志；如缺失则先登录）
get_token() {
  if [ ! -s "$TOKEN_FILE" ]; then
    login >&2 || return 1
  fi
  cat "$TOKEN_FILE"
}

# ---------------------------------------------------------------------------
# call <METHOD> <PATH> [JSON_BODY]：带 Bearer token 调用真实接口
#   输出脱敏后的 状态码 + 响应体片段
# ---------------------------------------------------------------------------
call() {
  local method="$1" path="$2" body="${3:-}" token code
  token=$(get_token) || { log "无可用 token，调用中止" >&2; return 1; }
  if [ -n "$body" ]; then
    code=$(curl -s -m 15 -o /tmp/zwi_body -w '%{http_code}' -X "$method" "$BASE$path" \
          -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d "$body")
  else
    code=$(curl -s -m 15 -o /tmp/zwi_body -w '%{http_code}' -X "$method" "$BASE$path" \
          -H "Authorization: Bearer $token")
  fi
  log "$method $path -> HTTP $code"
  echo "    响应(脱敏): $(mask < /tmp/zwi_body | head -c 300)"
  echo "$code" > /tmp/zwi_last_code
  return 0
}

# ---------------------------------------------------------------------------
# check_logs <seconds>：核对后端容器日志，确认无 404/405、无异常堆栈（需求 5.4/9.2）
#   输出脱敏后的判定结论与命中行
# ---------------------------------------------------------------------------
check_logs() {
  local secs="${1:-60}" raw n404 nexc
  raw=$(docker logs --since "${secs}s" "$BACKEND_CT" 2>&1)
  n404=$(echo "$raw" | grep -E '(HTTP|status)[^0-9]*(404|405)|No mapping|Method Not Allowed|Not Found' | wc -l)
  nexc=$(echo "$raw" | grep -E 'Exception|ERROR|\bat [a-z0-9_]+(\.[A-Za-z0-9_$]+)+\(' | wc -l)
  log "日志核对（最近 ${secs}s, 容器=$BACKEND_CT）: 404/405命中=$n404, 异常/堆栈命中=$nexc"
  if [ "$n404" -gt 0 ]; then
    echo "  --- 404/405 命中行(脱敏) ---"
    echo "$raw" | grep -E '(HTTP|status)[^0-9]*(404|405)|No mapping|Method Not Allowed|Not Found' | tail -10 | mask
  fi
  if [ "$nexc" -gt 0 ]; then
    echo "  --- 异常/堆栈命中行(脱敏) ---"
    echo "$raw" | grep -E 'Exception|ERROR|\bat [a-z0-9_]+(\.[A-Za-z0-9_$]+)+\(' | tail -15 | mask
  fi
  if [ "$n404" -eq 0 ] && [ "$nexc" -eq 0 ]; then
    log "日志核对通过：无 404/405、无异常堆栈"
    return 0
  fi
  log "日志核对未通过：存在 404/405 或异常堆栈，按需求 9.1 回判定准则核对" >&2
  return 1
}

# ---------------------------------------------------------------------------
# loop <METHOD> <PATH> [JSON_BODY]：一次完整闭环 登录→拿token→调接口→核对日志
# ---------------------------------------------------------------------------
loop() {
  local method="${1:-GET}" path="${2:-/api/v1/system/menu/user}" body="${3:-}"
  echo "================ 真实联调闭环 ================"
  login || return 1
  call "$method" "$path" "$body"
  check_logs 60
  echo "============== 闭环结束 ======================"
}

usage() {
  cat <<EOF
用法: verify-base.sh <command> [args]
  login                         真实登录(带重试)，缓存 token
  call <METHOD> <PATH> [BODY]   带 token 调用真实接口，脱敏输出状态码+响应
  logs [SECONDS]                核对后端日志(默认60s) 无404/405与异常
  loop [METHOD] [PATH] [BODY]   完整闭环: 登录→拿token→调接口→核对日志
  clear-token                   清除缓存 token
EOF
}

cmd="${1:-usage}"; shift || true
case "$cmd" in
  login)        login ;;
  call)         call "$@" ;;
  logs)         check_logs "$@" ;;
  loop)         loop "$@" ;;
  clear-token)  rm -f "$TOKEN_FILE"; log "已清除缓存 token" ;;
  *)            usage ;;
esac
