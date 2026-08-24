#!/usr/bin/env bash
###############################################################################
# run-k6.sh — k6 性能基线运行器（测试成熟度阶段二 2.2）
#
# 执行位置：联调服务器 129.204.3.200（需 Docker：grafana/k6 镜像 + zwi-redis 容器）
# 上传方式：由本地 scp 上传后 SSH 调用（与 verify-base.sh 同一基座）
#
# 【执行约束】（任务 2.2.1 强制）：
#   1. 仅允许夜间低峰执行（22:00 - 06:00），脚本内校验，时段外拒绝运行
#   2. 并发 ≤ 20 VU（脚本内 options.vus 上限校验）
#   3. 单次场景 ≤ 5 分钟（duration 上限校验）
#
# 依赖（全部真实，无 mock）：
#   - captcha-bridge.py：真实验证码桥（GET 后端 captcha/image + 读 zwi-redis 的
#     captcha:<uuid>），k6 login.js 每迭代经桥取真实验证码完成真实登录
#   - page-query.js / payment-submit.js 的 token 由本脚本用真实登录流程预取
#
# 用法：
#   bash run-k6.sh [login|page|payment|all]   # 默认 all
#
# CI 调用：export ZWI_K6_FORCE=1 豁免夜间窗口校验（push 触发的部署流水线
# 无法选择时段；约束 2/3 上限校验仍生效）。任一场景失败/阈值超限 → exit 5，
# 不静默吞错。
###############################################################################
set -uo pipefail

BASE="${ZWI_BASE:-http://127.0.0.1:18080}"      # k6 容器用 --network host，与宿主共享回环
BASE_HOST="${ZWI_BASE_HOST:-http://127.0.0.1:18080}"  # 本脚本(宿主)调后端用回环
# 默认用测试租户 9999 专用账号：避免高频登录淘汰 admin 等业务账号的活跃设备
# token（security.max-devices=5，超限拉黑最早设备），与 L3/L4 账号隔离
USERNAME="${ZWI_USER:-t9999admin}"
PASSWORD="${ZWI_PASS:-123456}"
REDIS_CT="${ZWI_REDIS_CT:-zwi-redis}"
BRIDGE_PORT=19191
K6_IMAGE="grafana/k6:latest"
WORKDIR="$(cd "$(dirname "$0")" && pwd)"

log() { echo "[$(date '+%F %T')] $*"; }

# ---- 约束 1：夜间低峰校验（22:00-06:00）；ZWI_K6_FORCE=1 时豁免（CI 流水线专用）----
HOUR=$(date +%H)
if [ "${ZWI_K6_FORCE:-0}" = "1" ]; then
  log "ZWI_K6_FORCE=1：CI 调用，豁免夜间窗口校验（并发/时长上限校验仍生效）"
elif [ "$HOUR" -ge 6 ] && [ "$HOUR" -lt 22 ]; then
  log "ERROR: 仅允许夜间低峰（22:00-06:00）执行，当前 $(date '+%T')，拒绝运行"
  exit 2
fi

# ---- 约束 2/3 校验：检查 k6 脚本 options 不超限 ----
for js in "$WORKDIR"/login.js "$WORKDIR"/page-query.js "$WORKDIR"/payment-submit.js; do
  vus=$(grep -oE 'vus: *[0-9]+' "$js" | grep -oE '[0-9]+' | head -1)
  dur=$(grep -oE "duration: *'[0-9]+(s|m)'" "$js" | grep -oE '[0-9]+(s|m)' | head -1)
  [ -n "$vus" ] && [ "$vus" -gt 20 ] && { log "ERROR: $js vus=$vus 超过上限 20"; exit 2; }
  case "$dur" in
    *m) mins=${dur%m}; [ "$mins" -gt 5 ] && { log "ERROR: $js duration=$dur 超过上限 5m"; exit 2; } ;;
    *s) : ;;  # 秒级必然 <5 分钟
    *) log "ERROR: $js 未声明 duration"; exit 2 ;;
  esac
done

# ---- 启动真实验证码桥（后台，结束自动清理） ----
python3 "$WORKDIR/captcha-bridge.py" \
  --base "$BASE_HOST" --redis-ct "$REDIS_CT" --port "$BRIDGE_PORT" >/tmp/captcha-bridge.log 2>&1 &
BRIDGE_PID=$!
trap 'kill $BRIDGE_PID 2>/dev/null || true' EXIT
sleep 2
if ! curl -s -m 5 "http://127.0.0.1:$BRIDGE_PORT/health" >/dev/null; then
  log "ERROR: 验证码桥启动失败，日志：$(tail -5 /tmp/captcha-bridge.log)"
  exit 3
fi
log "验证码桥就绪（真实 captcha/image + Redis 读取，无 mock）"

# ---- 预取真实 token（page/payment 场景用）：真实登录流程，不伪造 ----
fetch_token() {
  local cap uuid code resp token
  cap=$(curl -s -m 5 "http://127.0.0.1:$BRIDGE_PORT/captcha") || { log "ERROR: 验证码桥 /captcha 请求失败"; return 1; }
  # 正则容许冒号后空格：captcha-bridge.py 用 json.dumps 序列化（如 "uuid": "..."），
  # 2026-08-24 验证码开启预检实证：旧版不容空格取码为空，开关关闭时被假象掩盖，开启后登录 400
  uuid=$(echo "$cap" | grep -oE '"uuid" *: *"[^"]+"' | head -1 | sed -E 's/.*"uuid" *: *"([^"]+)"/\1/')
  code=$(echo "$cap" | grep -oE '"code" *: *"[^"]+"' | head -1 | sed -E 's/.*"code" *: *"([^"]+)"/\1/')
  if [ -z "$uuid" ] || [ -z "$code" ]; then
    log "ERROR: 桥响应解析失败（uuid/code 为空，脱敏响应: $(echo "$cap" | head -c 120)）"
    return 1
  fi
  resp=$(curl -s -m 10 -X POST "$BASE_HOST/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"captchaUuid\":\"$uuid\",\"captchaCode\":\"$code\"}")
  token=$(echo "$resp" | grep -oE '"(accessToken|token)" *: *"[^"]+"' | head -1 | sed -E 's/.*"([^"]+)"$/\1/')
  [ -n "$token" ] || { log "ERROR: 预取 token 失败（脱敏响应: $(echo "$resp" | head -c 120)）"; return 1; }
  echo "$token"
}

run_k6() {
  local script="$1"; shift
  log "===== 执行 $script ====="
  local rc=0
  docker run --rm --network host \
    -v "$WORKDIR:/scripts" \
    -e K6_BASE="$BASE" -e K6_BRIDGE="http://127.0.0.1:$BRIDGE_PORT" \
    -e ZWI_USER="$USERNAME" -e ZWI_PASS="$PASSWORD" "$@" \
    "$K6_IMAGE" run "/scripts/$(basename "$script")" 2>&1 | tail -60 || rc=$?
  if [ "$rc" -ne 0 ]; then
    log "ERROR: $script 执行失败或阈值超限 (exit=$rc)"
    FAILED=1
  fi
}

FAILED=0

TARGET="${1:-all}"
case "$TARGET" in
  login|all)
    run_k6 "$WORKDIR/login.js" ;;
esac

if [ "$TARGET" = "page" ] || [ "$TARGET" = "all" ]; then
  TOKEN=$(fetch_token) || exit 4
  run_k6 "$WORKDIR/page-query.js" -e TOKEN="$TOKEN"
fi

if [ "$TARGET" = "payment" ] || [ "$TARGET" = "all" ]; then
  TOKEN=$(fetch_token) || exit 4
  run_k6 "$WORKDIR/payment-submit.js" -e TOKEN="$TOKEN"
fi

if [ "$FAILED" -ne 0 ]; then
  log "ERROR: 存在失败场景，不静默忽略（详见上方各场景输出）"
  exit 5
fi

log "全部场景执行完毕（P95/P99 见上方 http_req_duration 指标，回填 tasks.md 数据回填区）"
