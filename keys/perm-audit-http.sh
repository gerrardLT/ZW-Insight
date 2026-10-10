#!/usr/bin/env bash
###############################################################################
# perm-audit-http.sh — 权限一致性清查【真实登录 HTTP 面】
#
# 对每个目标用户执行「真实登录 → 真实接口」，把运行时实际下发的
#   ① 菜单集合   GET /api/v1/system/menu/user
#   ② 权限码集合 登录响应 permissions[]
# 与库中配置（sys_role_menu ⋈ sys_menu）推导的期望集合做双向差集比对。
#
# 全程真实登录 / 真实接口；不伪造 token、不 mock。只读（仅 GET）。
# 运行位置：目标服务器（依赖 docker exec <mysql/redis 容器> + 本机后端）。
# 用法：bash perm-audit-http.sh [--tenant <id>] [--users "a,b"] [--password <p>] [--base <url>]
#
# 退出码：0 = 全部用户双向零差异；1 = 存在差异或探测失败
###############################################################################
set -uo pipefail

MYSQL_CT="${ZWI_MYSQL_CT:-zwi-mysql}"
REDIS_CT="${ZWI_REDIS_CT:-zwi-redis}"
DB="${ZWI_DB:-zw_insight}"
BASE="${ZWI_BASE:-http://127.0.0.1:18080}"
TENANT="1"
PASSWORD="${ZWI_PASS:-123456}"
USERS=""

while [ $# -gt 0 ]; do
  case "$1" in
    --tenant)   TENANT="$2"; shift 2 ;;
    --users)    USERS="$2"; shift 2 ;;
    --password) PASSWORD="$2"; shift 2 ;;
    --base)     BASE="$2"; shift 2 ;;
    *) echo "未知参数: $1" >&2; exit 64 ;;
  esac
done

q() {
  { echo "SET SESSION group_concat_max_len=1048576;"; cat; } | docker exec -i "$MYSQL_CT" sh -c \
    "mysql --default-character-set=utf8mb4 -uroot -p\$MYSQL_ROOT_PASSWORD -N -B $DB" 2>/dev/null
}

get_captcha() {
  local uuid code
  uuid=$(curl -s -m 10 "$BASE/api/v1/captcha/image" | jq -r '.data.uuid // empty')
  [ -n "$uuid" ] || return 1
  code=$(docker exec "$REDIS_CT" redis-cli GET "captcha:$uuid" | tr -d '\r"')
  [ -n "$code" ] || return 1
  printf '%s %s' "$uuid" "$code"
}

clear_locks() {
  docker exec "$REDIS_CT" redis-cli DEL \
    "login:ip:fail:127.0.0.1" "login:ip:lock:127.0.0.1" >/dev/null 2>&1 || true
}

# 期望集合（与后端 SQL 等价）：
#   菜单 = 角色启用未删 & 菜单启用未删 & 非 BUTTON & 租户匹配
#   权限 = 角色启用未删 & 菜单启用未删 & permission 非空 & 租户匹配（含 BUTTON，与 selectPermissionsByUserId 一致）
expected_menus() {
  printf '%s\n' "
SELECT u.id, GROUP_CONCAT(DISTINCT m.id ORDER BY m.id)
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id=u.id
JOIN sys_role r ON r.id=ur.role_id AND r.status=1 AND r.deleted=0 AND (r.tenant_id=u.tenant_id OR r.tenant_id IS NULL)
JOIN sys_role_menu rm ON rm.role_id=r.id
JOIN sys_menu m ON m.id=rm.menu_id AND m.status=1 AND m.deleted=0 AND m.menu_type<>'BUTTON' AND (m.tenant_id=u.tenant_id OR m.tenant_id IS NULL)
WHERE u.deleted=0 AND u.status=1 AND u.tenant_id=$TENANT
GROUP BY u.id ORDER BY u.id;" | q
}

expected_perms() {
  printf '%s\n' "
SELECT u.id, GROUP_CONCAT(DISTINCT m.permission ORDER BY m.permission)
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id=u.id
JOIN sys_role r ON r.id=ur.role_id AND r.status=1 AND r.deleted=0 AND (r.tenant_id=u.tenant_id OR r.tenant_id IS NULL)
JOIN sys_role_menu rm ON rm.role_id=r.id
JOIN sys_menu m ON m.id=rm.menu_id AND m.status=1 AND m.deleted=0 AND (m.tenant_id=u.tenant_id OR m.tenant_id IS NULL)
WHERE u.deleted=0 AND u.status=1 AND u.tenant_id=$TENANT AND m.permission IS NOT NULL AND m.permission<>''
GROUP BY u.id ORDER BY u.id;" | q
}

# csv 排序规整：逗号分隔 -> 换行 -> 排序 -> 逗号连接
norm() { tr ',' '\n' <<<"$1" | sed '/^$/d' | LC_ALL=C sort -u | paste -sd, -; }
diffset() { # diffset <a-csv> <b-csv> => a 中不在 b 的
  comm -23 <(tr ',' '\n' <<<"$1" | sed '/^$/d' | LC_ALL=C sort -u) \
           <(tr ',' '\n' <<<"$2" | sed '/^$/d' | LC_ALL=C sort -u) | paste -sd, -
}

echo "=============================================================="
echo "权限一致性清查 · 真实登录 HTTP 面"
echo "时间: $(date '+%Y-%m-%d %H:%M:%S')  租户: $TENANT  BASE: $BASE"
echo "=============================================================="

# 目标用户列表
if [ -n "$USERS" ]; then
  ULIST=$(tr ',' '\n' <<<"$USERS" | sed '/^$/d')
else
  ULIST=$(printf '%s\n' "SELECT u.username FROM sys_user u WHERE u.deleted=0 AND u.status=1 AND u.tenant_id=$TENANT AND EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id AND r.deleted=0 AND r.status=1 WHERE ur.user_id=u.id) ORDER BY u.id;" | q | tr -d '\r')
fi

EXP_MENUS=$(expected_menus)
EXP_PERMS=$(expected_perms)

# 前置断言：目标用户非空却拿不到期望集合，说明 DB 查询失败；
# 若此时端点也返回空，双向差集皆空会被误判 PASS，故直接中止。
if [ -n "$ULIST" ] && [ -z "$EXP_MENUS" ] && [ -z "$EXP_PERMS" ]; then
  echo "[FATAL] 期望集合查询为空而目标用户非空，疑似 DB 不可达/查询失败；拒绝出结论" >&2
  exit 3
fi

PASS=0; FAIL=0
for U in $ULIST; do
  UID_ROW=$(printf '%s\n' "SELECT id FROM sys_user WHERE username='$U' AND tenant_id=$TENANT AND deleted=0 LIMIT 1;" | q | tr -d '\r')
  UID_ROW="${UID_ROW:-?}"
  E_M=$(awk -F'\t' -v id="$UID_ROW" '$1==id{print $2}' <<<"$EXP_MENUS")
  E_P=$(awk -F'\t' -v id="$UID_ROW" '$1==id{print $2}' <<<"$EXP_PERMS")
  E_M=$(norm "$E_M"); E_P=$(norm "$E_P")

  clear_locks
  CAP=$(get_captcha) || { echo "[FAIL] $U 取验证码失败"; FAIL=$((FAIL+1)); continue; }
  UUID="${CAP%% *}"; CODE="${CAP##* }"
  RESP=$(curl -s -m 15 -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
        -d "{\"username\":\"$U\",\"password\":\"$PASSWORD\",\"captchaUuid\":\"$UUID\",\"captchaCode\":\"$CODE\"}")
  TOKEN=$(echo "$RESP" | jq -r '.data.token // empty')
  if [ -z "$TOKEN" ]; then
    echo "[FAIL] $U 登录失败: $(echo "$RESP" | jq -rc '{code,message}' 2>/dev/null)"
    FAIL=$((FAIL+1)); continue
  fi
  A_P=$(echo "$RESP" | jq -r '.data.permissions // [] | join(",")')
  ROLES=$(echo "$RESP" | jq -r '.data.roles // [] | join(",")')
  if grep -q 'SUPER_ADMIN' <<<"$ROLES"; then E_P=$(norm "$E_P,*:*:*"); fi
  A_P=$(norm "$A_P")

  A_M=$(curl -s -m 15 "$BASE/api/v1/system/menu/user" -H "Authorization: Bearer $TOKEN" \
        | jq -r '.data // [] | map(.id) | join(",")')
  A_M=$(norm "$A_M")

  M_MISS=$(diffset "$E_M" "$A_M"); M_EXTRA=$(diffset "$A_M" "$E_M")
  P_MISS=$(diffset "$E_P" "$A_P"); P_EXTRA=$(diffset "$A_P" "$E_P")

  if [ -z "$M_MISS" ] && [ -z "$M_EXTRA" ] && [ -z "$P_MISS" ] && [ -z "$P_EXTRA" ]; then
    echo "[PASS] $U (id=$UID_ROW) menus=$(tr ',' '\n' <<<"$A_M" | sed '/^$/d' | wc -l) perms=$(tr ',' '\n' <<<"$A_P" | sed '/^$/d' | wc -l)"
    PASS=$((PASS+1))
  else
    echo "[FAIL] $U (id=$UID_ROW)"
    [ -n "$M_MISS" ]  && echo "      菜单应显未显: $M_MISS"
    [ -n "$M_EXTRA" ] && echo "      菜单越权多显: $M_EXTRA"
    [ -n "$P_MISS" ]  && echo "      权限码应得未得: $P_MISS"
    [ -n "$P_EXTRA" ] && echo "      权限码越权多得: $P_EXTRA"
    FAIL=$((FAIL+1))
  fi
done

echo "=============================================================="
echo "HTTP 面小结: PASS=$PASS FAIL=$FAIL"
echo "=============================================================="
[ "$FAIL" -eq 0 ] || exit 1
exit 0
