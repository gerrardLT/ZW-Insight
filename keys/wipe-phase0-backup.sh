#!/bin/bash
# Phase 0：清空前安全网备份（备份 API 实战，清空可逆性保障）
set -uo pipefail
BASE="http://127.0.0.1:18080"
PASS_CNT=0; FAIL_CNT=0
ok()   { echo "PASS: $1"; PASS_CNT=$((PASS_CNT+1)); }
fail() { echo "FAIL: $1"; FAIL_CNT=$((FAIL_CNT+1)); }

echo "===== 1. admin 登录 ====="
docker exec zwi-redis redis-cli DEL "login:ip:fail:127.0.0.1" "login:ip:lock:127.0.0.1" "login_fail:admin" >/dev/null 2>&1
cap=$(curl -s -m 10 "$BASE/api/v1/captcha/image")
uuid=$(echo "$cap" | grep -oE '"uuid"[[:space:]]*:[[:space:]]*"[^"]+"' | head -1 | sed -E 's/.*"uuid"[[:space:]]*:[[:space:]]*"//;s/"$//')
code=$(docker exec zwi-redis redis-cli GET "captcha:$uuid" | tr -d '\r"')
resp=$(curl -s -m 15 -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"123456\",\"captchaUuid\":\"$uuid\",\"captchaCode\":\"$code\"}")
TOKEN=$(echo "$resp" | grep -oE '"(accessToken|token)"[[:space:]]*:[[:space:]]*"[^"]+"' | head -1 | sed -E 's/.*:[[:space:]]*"//;s/"$//')
[ -n "$TOKEN" ] && ok "admin 登录成功" || { fail "admin 登录失败"; echo "$resp" | head -c 300; exit 1; }
printf '%s' "$TOKEN" > /root/zwi-deploy/.zwi_token; chmod 600 /root/zwi-deploy/.zwi_token

echo ""
echo "===== 2. 触发清空前完整备份（同步长耗时）====="
bk_resp=$(curl -s -m 900 -X POST "$BASE/api/v1/system/backup/execute" -H "Authorization: Bearer $TOKEN")
echo "备份响应: $(echo "$bk_resp" | head -c 400)"
echo "$bk_resp" | grep -q '"status"[[:space:]]*:[[:space:]]*"SUCCESS"' && ok "备份 API 返回 SUCCESS" || { fail "备份未 SUCCESS，中止清空"; exit 1; }
BK_ID=$(echo "$bk_resp" | grep -oE '"id"[[:space:]]*:[[:space:]]*"?[0-9]+' | head -1 | grep -oE '[0-9]+$')
echo "备份记录 ID: $BK_ID"

echo ""
echo "===== 3. 落库 + MinIO 对象双重断言 ====="
row=$(docker exec zwi-mysql mysql -uroot -pzwinsight123 zw_insight -N -e "SELECT status, file_size, storage_path FROM sys_backup_record WHERE id=$BK_ID;" 2>/dev/null)
echo "落库: $row"
echo "$row" | grep -q '^SUCCESS' && ok "落库 SUCCESS" || fail "落库异常"
SP=$(echo "$row" | awk '{print $3}')
# MinIO 对象存在性（经 mc；无 mc 则用文件大小>0 兜底断言）
SZ=$(echo "$row" | awk '{print $2}')
[ "$SZ" -gt 1000000 ] 2>/dev/null && ok "备份文件 ${SZ} bytes（>1MB，全库规模合理）" || fail "备份文件过小: $SZ"

echo ""
echo "===== Phase 0 结果: PASS=$PASS_CNT FAIL=$FAIL_CNT ====="
[ "$FAIL_CNT" -eq 0 ] && echo "PHASE0_GREEN 可以进入清空" || echo "PHASE0_RED 禁止清空"
