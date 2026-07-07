#!/usr/bin/env bash
B=http://127.0.0.1:18080
for p in /api/v1/auth/captcha /api/api/v1/auth/captcha /v1/auth/captcha; do
  code=$(curl -s -m 8 -o /dev/null -w '%{http_code}' "$B$p")
  echo "GET $p -> $code"
done
echo "=== captcha 正文(取生效路径)==="
curl -s -m 8 "$B/api/v1/auth/captcha" | head -c 300
echo
