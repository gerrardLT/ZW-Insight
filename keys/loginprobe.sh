#!/usr/bin/env bash
B=http://127.0.0.1:18080
{
echo "=== captcha 400 正文 ==="
curl -s -m 8 "$B/api/api/v1/auth/captcha"; echo
echo "=== 去掉 context-path 假设：/api/v1/auth/captcha 已 404，确认 ==="
curl -s -m 8 -o /dev/null -w '%{http_code}\n' "$B/api/v1/auth/captcha"
echo "=== 直接尝试登录 POST /api/api/v1/auth/login (admin/123456) ==="
curl -s -m 10 -X POST "$B/api/api/v1/auth/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}'; echo
} > /root/zwi-deploy/login.out 2>&1
cat /root/zwi-deploy/login.out
