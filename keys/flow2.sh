#!/usr/bin/env bash
B=http://127.0.0.1:18080
OUT=/root/zwi-deploy/flow2.out
> "$OUT"
# 复用 flow.sh 的登录拿 token
R1=$(curl -s -m 10 -X POST "$B/api/v1/auth/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}')
UUID=$(echo "$R1" | grep -oE '"uuid":"[^"]+"' | head -1 | sed 's/"uuid":"//;s/"//')
CODE=$(docker exec zwi-redis redis-cli GET "captcha:$UUID" | tr -d '\r"')
R2=$(curl -s -m 10 -X POST "$B/api/v1/auth/login" -H 'Content-Type: application/json' -d "{\"username\":\"admin\",\"password\":\"123456\",\"captchaUuid\":\"$UUID\",\"captchaCode\":\"$CODE\"}")
TOKEN=$(echo "$R2" | grep -oE '"token":"[^"]+"' | head -1 | sed 's/"token":"//;s/"//')
echo "token 获取: ${TOKEN:0:30}..." >>"$OUT"
echo "" >>"$OUT"
echo "=== 带 Token 调用 admin 有权限的真实业务接口 ===" >>"$OUT"
for ep in /api/v1/system/version/list /api/v1/system/version/current /api/v1/system/monitor/metrics /api/v1/system/backup/list; do
  c=$(curl -s -m 12 -o /tmp/b -w '%{http_code}' "$B$ep" -H "Authorization: Bearer $TOKEN")
  echo "GET $ep -> $c" >>"$OUT"
  echo "    body: $(head -c 220 /tmp/b)" >>"$OUT"
done
cat "$OUT"
