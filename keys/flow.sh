#!/usr/bin/env bash
B=http://127.0.0.1:18080
OUT=/root/zwi-deploy/flow.out
> "$OUT"

echo "===== 业务流程：管理员登录 → 调用业务接口 =====" >>"$OUT"

# 1) 首次登录，触发验证码生成
R1=$(curl -s -m 10 -X POST "$B/api/v1/auth/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}')
echo "[1] 首次登录响应: $R1" >>"$OUT"
UUID=$(echo "$R1" | grep -oE '"uuid":"[^"]+"' | head -1 | sed 's/"uuid":"//;s/"//')
echo "[1] 提取 uuid = $UUID" >>"$OUT"

# 2) 从 Redis 读取该 uuid 的验证码（真实组件，非伪造）
CODE=$(docker exec zwi-redis redis-cli GET "captcha:$UUID" | tr -d '\r"')
echo "[2] Redis captcha:$UUID = $CODE" >>"$OUT"

# 3) 携带验证码再次登录
R2=$(curl -s -m 10 -X POST "$B/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"123456\",\"captchaUuid\":\"$UUID\",\"captchaCode\":\"$CODE\"}")
echo "[3] 带验证码登录响应: $R2" >>"$OUT"
TOKEN=$(echo "$R2" | grep -oE '"token":"[^"]+"' | head -1 | sed 's/"token":"//;s/"//')
if [ -z "$TOKEN" ]; then TOKEN=$(echo "$R2" | grep -oE '"accessToken":"[^"]+"' | head -1 | sed 's/"accessToken":"//;s/"//'); fi
echo "[3] 提取 token = ${TOKEN:0:40}..." >>"$OUT"

# 4) 用 Token 调用需要鉴权的业务接口（拉取用户菜单/信息）
echo "[4] 携带 Token 调用业务接口:" >>"$OUT"
for ep in /api/v1/auth/info /api/v1/system/menu/routes /api/v1/system/user/page; do
  c=$(curl -s -m 10 -o /tmp/body -w '%{http_code}' "$B$ep" -H "Authorization: Bearer $TOKEN")
  echo "    GET $ep -> $c | $(head -c 160 /tmp/body)" >>"$OUT"
done

cat "$OUT"
