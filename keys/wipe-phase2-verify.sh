#!/bin/bash
# Phase 2 补充验证：t9999admin 登录 + 编号规则一致性（5=5 实为正确）
set -uo pipefail
BASE="http://127.0.0.1:18080"
docker exec zwi-redis redis-cli DEL "login:ip:fail:127.0.0.1" "login:ip:lock:127.0.0.1" "login_fail:t9999admin" >/dev/null 2>&1
cap=$(curl -s -m 10 "$BASE/api/v1/captcha/image")
uuid=$(echo "$cap" | grep -oE '"uuid"[[:space:]]*:[[:space:]]*"[^"]+"' | head -1 | sed -E 's/.*"uuid"[[:space:]]*:[[:space:]]*"//;s/"$//')
code=$(docker exec zwi-redis redis-cli GET "captcha:$uuid" | tr -d '\r"')
resp=$(curl -s -m 15 -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"t9999admin\",\"password\":\"123456\",\"captchaUuid\":\"$uuid\",\"captchaCode\":\"$code\"}")
echo "$resp" | grep -qE '"(accessToken|token)"' && echo "PASS: t9999admin 登录成功" || { echo "FAIL: t9999admin 登录失败: $(echo "$resp" | head -c 200)"; exit 1; }
T1=$(docker exec zwi-mysql mysql -uroot -pzwinsight123 zw_insight -N -e "SELECT COUNT(*) FROM serial_number_rule WHERE tenant_id=1 AND deleted=0;" 2>/dev/null)
T9=$(docker exec zwi-mysql mysql -uroot -pzwinsight123 zw_insight -N -e "SELECT COUNT(*) FROM serial_number_rule WHERE tenant_id=9999 AND deleted=0;" 2>/dev/null)
[ "$T1" = "$T9" ] && echo "PASS: 编号规则一致（租户1=$T1, 租户9999=$T9，init 复制语义正确）" || echo "FAIL: 规则不一致 $T1 vs $T9"
echo "PHASE2_SUPPLEMENT_DONE"
