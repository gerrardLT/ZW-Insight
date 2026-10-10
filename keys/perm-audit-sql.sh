#!/usr/bin/env bash
###############################################################################
# perm-audit-sql.sh — 权限一致性清查【只读 SQL 面】
#
# 覆盖维度：
#   D4 结构完整性  : 孤儿/悬空绑定、无角色用户、无菜单角色、跨租户串号、禁用菜单仍绑定、角色码重复
#   D1 期望菜单集  : 每个用户由 sys_role_menu 推导的期望菜单 id 集合（菜单下发口径）
#   D2 期望权限码  : 每个用户由 sys_menu.permission 推导的期望权限码集合
#   D3 支撑数据    : 每个用户的有效数据范围 + 部门/项目归属
#
# 全程只读（仅 SELECT / SHOW）；不写库、不改配置。
# 运行位置：目标服务器（依赖 docker exec <mysql 容器>）。
# 用法：bash perm-audit-sql.sh [--json-out <path>]
#
# 退出码：0 = 所有 D4 结构完整性断言通过；2 = 存在结构性问题
###############################################################################
set -uo pipefail

MYSQL_CT="${ZWI_MYSQL_CT:-zwi-mysql}"
DB="${ZWI_DB:-zw_insight}"
JSON_OUT=""

while [ $# -gt 0 ]; do
  case "$1" in
    --json-out) JSON_OUT="$2"; shift 2 ;;
    *) echo "未知参数: $1" >&2; exit 64 ;;
  esac
done

# 以 stdin 传 SQL，规避多层引号地狱；-N -B 输出制表符分隔、无表头
# 先放大 group_concat_max_len（默认 1024 字节会把超管的长权限码列表截断成假差异）
q() {
  { echo "SET SESSION group_concat_max_len=1048576;"; cat; } | docker exec -i "$MYSQL_CT" sh -c \
    "mysql --default-character-set=utf8mb4 -uroot -p\$MYSQL_ROOT_PASSWORD -N -B $DB" 2>/dev/null
}

# 严格版：保留退出码（docker exec 透传 mysql 退出码），供断言使用。
# stderr 仍丢弃（mysql 会把「Using a password on the command line」警告写到 stderr，
# 混入 stdout 会污染首行判定）；SQL 错误靠退出码识别，杜绝「报错 → 空 → 判为 0 → PASS」。
q_strict() {
  { echo "SET SESSION group_concat_max_len=1048576;"; cat; } | docker exec -i "$MYSQL_CT" sh -c \
    "mysql --default-character-set=utf8mb4 -uroot -p\$MYSQL_ROOT_PASSWORD -N -B $DB" 2>/dev/null
}

PASS=0
FAIL=0
note_pass() { PASS=$((PASS + 1)); echo "[PASS] $1"; }
note_fail() { FAIL=$((FAIL + 1)); echo "[FAIL] $1"; }

echo "=============================================================="
echo "权限一致性清查 · 只读 SQL 面"
echo "时间: $(date '+%Y-%m-%d %H:%M:%S')  库: $DB  容器: $MYSQL_CT"
echo "=============================================================="

# 前置连通性检查：数据库不可达时立即中止，绝不把「查不到」当成「无违规」
PRE=$(printf 'SELECT 1;\n' | q_strict)
if ! grep -q '^1$' <<<"$(tr -d '\r' <<<"$PRE")"; then
  echo "[FATAL] 数据库不可达或凭据无效，拒绝出报告（避免把报错当零违规）。输出：$(head -c 200 <<<"$PRE")" >&2
  exit 3
fi

###############################################################################
# D4 结构完整性
###############################################################################
echo
echo "---- D4 结构完整性 ----"

chk() { # chk <名称> <SQL> <期望值>
  local name="$1" sql="$2" expect="${3:-0}" out rc got
  out=$(printf '%s\n' "$sql" | q_strict); rc=$?
  got=$(printf '%s' "$out" | head -1 | tr -d '\r')
  # 查询失败或返回非数值一律判 FAIL —— 严禁「报错 → 空 → 0 → PASS」的静默失败
  if [ "$rc" -ne 0 ] || ! printf '%s' "$got" | grep -Eq '^-?[0-9]+$'; then
    note_fail "$name 查询失败或返回非数值（rc=$rc, out=$(printf '%s' "$out" | head -c 150)）"
    return
  fi
  if [ "$got" = "$expect" ]; then note_pass "$name = $got"; else note_fail "$name = $got (期望 $expect)"; fi
}

chk "D4.1 孤儿 sys_role_menu(菜单不存在或已删)" \
    "SELECT COUNT(*) FROM sys_role_menu rm LEFT JOIN sys_menu m ON m.id=rm.menu_id WHERE m.id IS NULL OR m.deleted=1;"

chk "D4.2 悬空 sys_user_role(角色不存在或已删)" \
    "SELECT COUNT(*) FROM sys_user_role ur LEFT JOIN sys_role r ON r.id=ur.role_id WHERE r.id IS NULL OR r.deleted=1;"

chk "D4.3 禁用/已删菜单仍被角色绑定" \
    "SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON m.id=rm.menu_id WHERE m.status=0 OR m.deleted=1;"

chk "D4.4 跨租户角色绑定(role.tenant_id 与 user.tenant_id 不符)" \
    "SELECT COUNT(*) FROM sys_user_role ur JOIN sys_user u ON u.id=ur.user_id JOIN sys_role r ON r.id=ur.role_id WHERE r.deleted=0 AND r.tenant_id IS NOT NULL AND u.tenant_id IS NOT NULL AND r.tenant_id<>u.tenant_id;"

chk "D4.5 同租户内角色码重复(未删除)" \
    "SELECT COUNT(*) FROM (SELECT tenant_id,role_code FROM sys_role WHERE deleted=0 GROUP BY tenant_id,role_code HAVING COUNT(*)>1) t;"

echo
echo "-- D4.6 无角色用户(启用未删) 明细 --"
printf '%s\n' "SELECT u.id,u.username,u.tenant_id FROM sys_user u WHERE u.deleted=0 AND u.status=1 AND NOT EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id AND r.deleted=0 AND r.status=1 WHERE ur.user_id=u.id) ORDER BY u.tenant_id,u.id;" | q

echo
echo "-- D4.7 无菜单角色(启用未删、非 SUPER_ADMIN) 明细 --"
printf '%s\n' "SELECT r.id,r.role_code,r.tenant_id FROM sys_role r WHERE r.deleted=0 AND r.status=1 AND r.role_code<>'SUPER_ADMIN' AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id) ORDER BY r.tenant_id,r.id;" | q

echo
echo "-- D4.8 角色数据范围取值分布(启用未删) --"
printf '%s\n' "SELECT r.data_scope,COUNT(*) FROM sys_role r WHERE r.deleted=0 AND r.status=1 GROUP BY r.data_scope ORDER BY r.data_scope;" | q

###############################################################################
# D1 / D2 期望集合（菜单下发口径 = SysMenuService.getMenusByUserId 的 SQL 等价式）
###############################################################################
echo
echo "---- D1/D2 每用户期望菜单集与期望权限码 ----"
echo "列: user_id | username | tenant_id | effective_scope | expected_menu_cnt | expected_perm_cnt"
echo "注: menu_cnt 排除 BUTTON（菜单下发口径）；perm_cnt 含 BUTTON（权限码口径，与 selectPermissionsByUserId 一致）"
printf '%s\n' "
SELECT u.id, u.username, u.tenant_id,
  COALESCE(SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT r.data_scope
      ORDER BY FIELD(r.data_scope,'ALL','DEPT_AND_CHILDREN','DEPT','PROJECT','SELF') SEPARATOR ','),',',1),'SELF') AS eff_scope,
  (SELECT COUNT(DISTINCT m.id) FROM sys_role_menu rm
     JOIN sys_menu m ON m.id=rm.menu_id AND m.status=1 AND m.deleted=0 AND m.menu_type<>'BUTTON'
       AND (m.tenant_id=u.tenant_id OR m.tenant_id IS NULL)
   WHERE rm.role_id IN (SELECT r2.id FROM sys_user_role ur2 JOIN sys_role r2 ON r2.id=ur2.role_id
       AND r2.status=1 AND r2.deleted=0 AND (r2.tenant_id=u.tenant_id OR r2.tenant_id IS NULL) WHERE ur2.user_id=u.id)) AS menu_cnt,
  (SELECT COUNT(DISTINCT m.permission) FROM sys_role_menu rm
     JOIN sys_menu m ON m.id=rm.menu_id AND m.status=1 AND m.deleted=0
       AND (m.tenant_id=u.tenant_id OR m.tenant_id IS NULL)
       AND m.permission IS NOT NULL AND m.permission<>''
   WHERE rm.role_id IN (SELECT r2.id FROM sys_user_role ur2 JOIN sys_role r2 ON r2.id=ur2.role_id
       AND r2.status=1 AND r2.deleted=0 AND (r2.tenant_id=u.tenant_id OR r2.tenant_id IS NULL) WHERE ur2.user_id=u.id)) AS perm_cnt
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id=u.id
JOIN sys_role r ON r.id=ur.role_id AND r.status=1 AND r.deleted=0 AND (r.tenant_id=u.tenant_id OR r.tenant_id IS NULL)
WHERE u.deleted=0 AND u.status=1
GROUP BY u.id,u.username,u.tenant_id
ORDER BY u.tenant_id,u.id;" | q

echo
echo "-- D1 每用户期望菜单 id 明细 --"
printf '%s\n' "
SELECT u.id, GROUP_CONCAT(DISTINCT m.id ORDER BY m.id)
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id=u.id
JOIN sys_role r ON r.id=ur.role_id AND r.status=1 AND r.deleted=0 AND (r.tenant_id=u.tenant_id OR r.tenant_id IS NULL)
JOIN sys_role_menu rm ON rm.role_id=r.id
JOIN sys_menu m ON m.id=rm.menu_id AND m.status=1 AND m.deleted=0 AND m.menu_type<>'BUTTON' AND (m.tenant_id=u.tenant_id OR m.tenant_id IS NULL)
WHERE u.deleted=0 AND u.status=1
GROUP BY u.id ORDER BY u.id;" | q

echo
echo "-- D2 每用户期望权限码明细 --"
printf '%s\n' "
SELECT u.id, GROUP_CONCAT(DISTINCT m.permission ORDER BY m.permission)
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id=u.id
JOIN sys_role r ON r.id=ur.role_id AND r.status=1 AND r.deleted=0 AND (r.tenant_id=u.tenant_id OR r.tenant_id IS NULL)
JOIN sys_role_menu rm ON rm.role_id=r.id
JOIN sys_menu m ON m.id=rm.menu_id AND m.status=1 AND m.deleted=0 AND (m.tenant_id=u.tenant_id OR m.tenant_id IS NULL)
WHERE u.deleted=0 AND u.status=1 AND m.permission IS NOT NULL AND m.permission<>''
GROUP BY u.id ORDER BY u.id;" | q

echo
echo "-- D2 全库菜单提供码目录(启用未删) --"
printf '%s\n' "SELECT DISTINCT permission FROM sys_menu WHERE permission IS NOT NULL AND permission<>'' AND deleted=0 AND status=1 ORDER BY permission;" | q

###############################################################################
# D3 支撑数据
###############################################################################
echo
echo "---- D3 数据范围支撑数据 ----"
echo "-- 用户有效范围与部门 --"
printf '%s\n' "
SELECT u.id,u.username,u.tenant_id,u.org_id,
  COALESCE(SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT r.data_scope
      ORDER BY FIELD(r.data_scope,'ALL','DEPT_AND_CHILDREN','DEPT','PROJECT','SELF') SEPARATOR ','),',',1),'SELF') AS eff_scope
FROM sys_user u
LEFT JOIN sys_user_role ur ON ur.user_id=u.id
LEFT JOIN sys_role r ON r.id=ur.role_id AND r.status=1 AND r.deleted=0
WHERE u.deleted=0 AND u.status=1
GROUP BY u.id,u.username,u.tenant_id,u.org_id ORDER BY u.tenant_id,u.id;" | q

echo "-- 部门树 --"
printf '%s\n' "SELECT id,parent_id,ancestors,tenant_id FROM sys_org WHERE deleted=0 ORDER BY id;" | q

echo "-- 用户项目归属 --"
printf '%s\n' "SELECT user_id,GROUP_CONCAT(project_id ORDER BY project_id) FROM sys_user_project GROUP BY user_id ORDER BY user_id;" | q

echo "-- 角色-菜单绑定计数(启用未删角色) --"
printf '%s\n' "SELECT r.id,r.role_code,r.data_scope,COUNT(rm.menu_id) FROM sys_role r LEFT JOIN sys_role_menu rm ON rm.role_id=r.id WHERE r.deleted=0 AND r.status=1 GROUP BY r.id,r.role_code,r.data_scope ORDER BY r.tenant_id,r.id;" | q

echo
echo "=============================================================="
echo "D4 结构完整性小结: PASS=$PASS FAIL=$FAIL"
echo "=============================================================="
[ "$FAIL" -eq 0 ] || exit 2
exit 0
